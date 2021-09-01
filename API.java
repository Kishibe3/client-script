package com.clientScript.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.value.FormattedTextValue;
import com.clientScript.value.NumericValue;
import com.clientScript.value.Value;

public class API {
	private static final Logger LOGGER = LogManager.getLogger();
	
    public static List<? extends Entity> getEntities(String selector) throws Exception {
        selector = selector.replaceAll("\\s", "");
        String entitySelectorOption = "type=!?[a-z_]+(?::[a-z_]+)?|sort=(nearest|furthest|random|arbitrary)|limit=\\d+|name=!?\\w+|distance=(\\d+(\\.\\d+)?\\.\\.\\d+(\\.\\d+)?|\\d+(\\.\\d+)?\\.\\.|(\\.\\.)?\\d+(\\.\\d+)?)|x=-?\\d+(\\.\\d+)?|y=-?\\d+(\\.\\d+)?|z=-?\\d+(\\.\\d+)?|dx=-?\\d+(\\.\\d+)?|dy=-?\\d+(\\.\\d+)?||dz=-?\\d+(\\.\\d+)?";
        if (selector.matches("@[aeprs]\\[(" + entitySelectorOption + ")?(?:,(" + entitySelectorOption + "))*\\]") || selector.matches("@[aeprs]")) {
            Matcher matcher = Pattern.compile(entitySelectorOption).matcher(selector);
            Predicate<Entity> predicate = Entity::isAlive;

            boolean typeAssign = false, typeNot = false, nameAssign = false, nameNot = false;
            int limit = -1;
            String sort = "";
            double distanceMin = -1, distanceMax = -1, distance = -1, x = -30000000, y = -30000000, z = -30000000, dx = -60000000, dy = -60000000, dz = -60000000;
            
            while (matcher.find()) {
                if (matcher.group() == "")
                    continue;
                Matcher m1 = Pattern.compile("[a-z]+(?==)").matcher(matcher.group());

                if (m1.find()) {
                    switch (m1.group()) {
                        case "type": {
                            if (typeAssign && !matcher.group().contains("!"))
                                throw new InternalExpressionException("Should only assign type once.");
                            if ((typeAssign && matcher.group().contains("!")) || (typeNot && !matcher.group().contains("!")))
                                throw new InternalExpressionException("Should not have type assignment here.");
                            typeAssign |= !matcher.group().contains("!");
                            typeNot |= matcher.group().contains("!");
                            
                            Matcher m2 = Pattern.compile("(?<==)!?[a-z_]+(:[a-z_]+)?").matcher(matcher.group());
                            m2.find();
                            String cmp = "entity." + (m2.group().charAt(0) == '!'?
                                (m2.group().contains(":")?
                                    m2.group().replace(':', '.').substring(1) : "minecraft." + m2.group().substring(1)
                                )
                                 : (m2.group().contains(":")?
                                    m2.group().replace(':', '.') : "minecraft." + m2.group()
                                )
                            );
                            predicate = predicate.and(e -> e.getType().toString().equals(cmp) != (m2.group().charAt(0) == '!'));
                            break;
                        }
                        case "sort": {
                            if (sort != "")
                                throw new InternalExpressionException("Should only assign sort once.");
                            Matcher m2 = Pattern.compile("(?<==)[a-z]+").matcher(matcher.group());
                            m2.find();
                            sort = m2.group();
                            break;
                        }
                        case "limit": {
                            if (limit > 0)
                                throw new InternalExpressionException("Should only assign limit once.");
                            Matcher m2 = Pattern.compile("(?<==)\\d+").matcher(matcher.group());
                            m2.find();
                            limit = Integer.parseInt(m2.group());
                            if (limit == 0)
                                throw new InternalExpressionException("Limit should be a positive integer.");
                            break;
                        }
                        case "name": {
                            if (nameAssign && !matcher.group().contains("!"))
                                throw new InternalExpressionException("Should only assign name once.");
                            if ((nameAssign && matcher.group().contains("!")) || (nameNot && !matcher.group().contains("!")))
                                throw new InternalExpressionException("Should not have name assignment here.");
                            nameAssign |= !matcher.group().contains("!");
                            nameNot |= matcher.group().contains("!");
    
                            Matcher m2 = Pattern.compile("(?<==)!?\\w+").matcher(matcher.group());
                            m2.find();
                            String cmp = m2.group().charAt(0) == '!'? m2.group().substring(1) : m2.group();
                            predicate = predicate.and(e -> e.getName().getString().equals(cmp) != (m2.group().charAt(0) == '!'));
                            break;
                        }
                        case "distance": {
                            if (distanceMin >= 0 || distanceMax >= 0 || distance >= 0)
                                throw new InternalExpressionException("Should only assign distance once.");
                            Matcher m2 = Pattern.compile("\\d+(\\.\\d+)?(?=\\.\\.)").matcher(matcher.group());
                            boolean b2 = m2.find(), b3;
                            if (b2)
                                distanceMin = Double.parseDouble(m2.group());
                            m2 = Pattern.compile("(?<=\\.\\.)\\d+(\\.\\d+)?").matcher(matcher.group());
                            b3 = m2.find();
                            if (b3)
                                distanceMax = Double.parseDouble(m2.group());
                            m2 = Pattern.compile("\\d+(\\.\\d+)?").matcher(matcher.group());
                            b3 = m2.find();
                            if (!b2 && !b3)
                                distance = Double.parseDouble(m2.group());
                            break;
                        }
                        case "x": {
                            if (x != -30000000)
                                throw new InternalExpressionException("Should only assign x once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            x = Double.parseDouble(m2.group());
                            break;
                        }
                        case "y": {
                            if (y != -30000000)
                                throw new InternalExpressionException("Should only assign y once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            y = Double.parseDouble(m2.group());
                            break;
                        }
                        case "z": {
                            if (z != -30000000)
                                throw new InternalExpressionException("Should only assign z once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            z = Double.parseDouble(m2.group());
                            break;
                        }
                        case "dx": {
                            if (dx != -60000000)
                                throw new InternalExpressionException("Should only assign dx once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            dx = Double.parseDouble(m2.group());
                            break;
                        }
                        case "dy": {
                            if (dy != -60000000)
                                throw new InternalExpressionException("Should only assign dy once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            dy = Double.parseDouble(m2.group());
                            break;
                        }
                        case "dz": {
                            if (dz != -60000000)
                                throw new InternalExpressionException("Should only assign dz once.");
                            Matcher m2 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(matcher.group());
                            m2.find();
                            dz = Double.parseDouble(m2.group());
                            break;
                        }
                    }
                }
                
            }
            
            MinecraftClient mcc = MinecraftClient.getInstance();
            Vec3d coord = (x == -30000000 || y == -30000000 || z == -30000000)? mcc.player.getPos() : new Vec3d(x, y, z);
            if (dx != -60000000 && dy != -60000000 && dz != -60000000) {
                boolean bl = dx < 0.0D, bl2 = dy < 0.0D, bl3 = dz < 0.0D;
                double d = bl ? x : 0.0D, e = bl2 ? y : 0.0D, f = bl3 ? z : 0.0D;
                double g = (bl ? 0.0D : x) + 1.0D, h = (bl2 ? 0.0D : y) + 1.0D, i = (bl3 ? 0.0D : z) + 1.0D;
                Box box = (new Box(d, e, f, g, h, i)).offset(coord);
                predicate = predicate.and(entity -> box.intersects(entity.getBoundingBox()));
            }
            else if (distance >= 0 || distanceMin >= 0 || distanceMax >= 0) {
                if (distanceMin >= 0) {
                    double d = distanceMin * distanceMin;
                    predicate = predicate.and(e -> e.squaredDistanceTo(coord) >= d);
                }
                if (distanceMax >= 0) {
                    double d = distanceMax * distanceMax;
                    predicate = predicate.and(e -> e.squaredDistanceTo(coord) <= d);
                }
                if (distance >= 0) {
                    double d = distance * distance;
                    predicate = predicate.and(e -> e.squaredDistanceTo(coord) == d);
                }
            }
            if (selector.charAt(1) == 's') {
                return (List<? extends Entity>)(Expression.source.getPlayer() != null && predicate.test(Expression.source.getPlayer())? Lists.newArrayList(Expression.source.getPlayer()) : new ArrayList<Entity>());
            }
            if (selector.charAt(1) == 'a' || selector.charAt(1) == 'p' || selector.charAt(1) == 'r')
                predicate = predicate.and(e -> e.getType().toString().equals("entity.minecraft.player"));
            if (selector.charAt(1) == 'p' || selector.charAt(1) == 'r')
                limit = 1;
            if ((selector.charAt(1) == 'a' || selector.charAt(1) == 'e') && limit == -1)
                limit = Integer.MAX_VALUE;
            if (sort == "") {
                if (selector.charAt(1) == 'a' || selector.charAt(1) == 'e')
                    sort = "arbitrary";
                if (selector.charAt(1) == 'p')
                    sort = "nearest";
                if (selector.charAt(1) == 'r')
                    sort = "random";
            }

            Iterator<Entity> allEntities = mcc.world.getEntities().iterator();
            List<Entity> list = new ArrayList<Entity>();
            while (allEntities.hasNext()) {
                Entity e = allEntities.next();
                if (predicate.test(e))
                    list.add(e);
            }
            BiConsumer<Vec3d, List<? extends Entity>> sorter = null;
            if (list.size() > 1) {
                switch (sort) {
                    case "nearest":
                        sorter = (vec3d, l) -> {
                            l.sort((entity, entity2) -> {
                                return Doubles.compare(entity.squaredDistanceTo(vec3d), entity2.squaredDistanceTo(vec3d));
                            });
                        };
                        break;
                    case "furthest":
                        sorter = (vec3d, l) -> {
                            l.sort((entity, entity2) -> {
                                return Doubles.compare(entity2.squaredDistanceTo(vec3d), entity.squaredDistanceTo(vec3d));
                            });
                        };
                        break;
                    case "random":
                        sorter = (vec3d, l) -> {
                            Collections.shuffle(l);
                        };
                        break;
                    case "arbitrary":
                        sorter = (vec3d, l) -> {};
                        break;
                }
                sorter.accept(coord, list);
            }
            return list.subList(0, Math.min(limit, list.size()));
        }
        throw new InternalExpressionException("Wrong entity selector format.");
    }

    public static void apply(Expression expression) {
        // run client side command, can only run commands registered to ClientCommandManager.DISPATCHER
        expression.addContextFunction("run", 1, (c, t, lv) -> {
            try {
                return new NumericValue(ClientCommandManager.DISPATCHER.execute(lv.get(0).getString(), Expression.source));
            }
            catch (Exception exc) {
                return new FormattedTextValue(new LiteralText(exc.getMessage()));
            }
        });

        expression.addLazyFunction("player", -1, (c, t, lv) -> {
            if (lv.size() == 0)
                throw new InternalExpressionException("'player' function needs at least 1 argument to assign player's action");
            MinecraftClient mcc = MinecraftClient.getInstance();
            if (mcc.player == null)
                return (cc, tt) -> Value.NULL;

            switch(lv.get(0).evalValue(c, Context.STRING).getString()) {
                case "attack":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    if (lv.size() > 3)
                        throw new InternalExpressionException("Too many arguments");
                    else if (lv.size() == 1 || lv.size() == 2) {
                    	if (lv.size() == 2 && !lv.get(1).evalValue(c, Context.STRING).getString().equals("entity") && !lv.get(1).evalValue(c, Context.STRING).getString().equals("block"))
                    		throw new InternalExpressionException("The second argument should be either 'block' or 'entity'.");
                        if (mcc.crosshairTarget != null) {
                        	switch(mcc.crosshairTarget.getType()) {
	                            case ENTITY:
	                                if (lv.size() == 2 && !lv.get(1).evalValue(c, Context.STRING).getString().equals("entity"))
	                                    break;
	                                mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(((EntityHitResult)mcc.crosshairTarget).getEntity(), mcc.player.isSneaking()));
	                                break;
	                            case BLOCK: {
	                                BlockHitResult bhr = (BlockHitResult)mcc.crosshairTarget;
	                                if (lv.size() == 2 && !lv.get(1).evalValue(c, Context.STRING).getString().equals("block"))
	                                    break;
	                                mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bhr.getBlockPos(), bhr.getSide()));
	                                break;
	                            }
	                            case MISS:
	                        }
                        }
                        mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                    }
                    else {
                        if (lv.get(1).evalValue(c, Context.STRING).getString().equals("block")) {
                            Matcher m1 = Pattern.compile("-?\\d+ -?\\d+ -?\\d+").matcher(lv.get(2).evalValue(c, Context.STRING).getString());
                            if (m1.find()) {
                                int x, y, z;
                                m1 = Pattern.compile("-?\\d+").matcher(lv.get(2).evalValue(c, Context.STRING).getString());
                                m1.find();
                                x = Integer.parseInt(m1.group());
                                m1.find();
                                y = Integer.parseInt(m1.group());
                                m1.find();
                                z = Integer.parseInt(m1.group());
                                mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(x, y, z), Direction.UP));
                                mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                            }
                            else
                                throw new InternalExpressionException("Wrong block position format at second argument.");
                        }
                        else if (lv.get(1).evalValue(c, Context.STRING).getString().equals("entity")) {
                            try {
                                Iterator<? extends Entity> entities = API.getEntities(lv.get(2).evalValue(c, Context.STRING).getString()).iterator();
                                while (entities.hasNext()) {
                                    mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(entities.next(), mcc.player.isSneaking()));
                                    mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        else
                            throw new InternalExpressionException("The second argument should be either 'block' or 'entity'.");
                    }
                    break;
                case "use": {
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    if (lv.size() > 4)
                        throw new InternalExpressionException("Too many arguments.");
                    if (lv.size() == 1) {
                    	Hand[] var1 = Hand.values();
                    	for (int i=0; i<var1.length; i++)
                    		API.useItem(mcc, var1[i], null, null, null);
                    }
                    else if (lv.size() == 2 || lv.size() == 3) {
                    	if (lv.size() == 3 && !lv.get(2).evalValue(c, Context.STRING).getString().equals("entity") && !lv.get(2).evalValue(c, Context.STRING).getString().equals("block"))
                    		throw new InternalExpressionException("The third argument should be either 'block' or 'entity'.");
	                    switch (lv.get(1).evalValue(c, Context.STRING).getString()) {
	                        case "mainHand":
	                            API.useItem(mcc, Hand.MAIN_HAND, null, null, lv.size() == 3? lv.get(2).evalValue(c, Context.STRING).getString() : null);
	                            break;
	                        case "offHand":
	                        	API.useItem(mcc, Hand.OFF_HAND, null, null, lv.size() == 3? lv.get(2).evalValue(c, Context.STRING).getString() : null);
	                            break;
	                        default:
	                            throw new InternalExpressionException("The second argument should be either 'mainHand' or 'offHand'.");
	                    }
                    }
                    else {
                    	Hand hand = null;
                    	switch (lv.get(1).evalValue(c, Context.STRING).getString()) {
	                        case "mainHand": {
	                        	hand = Hand.MAIN_HAND;
	                            break;
	                        }
	                        case "offHand": {
	                        	hand = Hand.OFF_HAND;
	                            break;
	                        }
	                        default:
	                            throw new InternalExpressionException("The second argument should be either 'mainHand' or 'offHand'.");
                    	}
                    	if (lv.get(2).evalValue(c, Context.STRING).getString().equals("block")) {
                    		Matcher m1 = Pattern.compile("-?\\d+ -?\\d+ -?\\d+").matcher(lv.get(3).evalValue(c, Context.STRING).getString());
                            if (m1.find()) {
                                int x, y, z;
                                m1 = Pattern.compile("-?\\d+").matcher(lv.get(3).evalValue(c, Context.STRING).getString());
                                m1.find();
                                x = Integer.parseInt(m1.group());
                                m1.find();
                                y = Integer.parseInt(m1.group());
                                m1.find();
                                z = Integer.parseInt(m1.group());
                                API.useItem(mcc, hand, null, new BlockPos(x, y, z), "block");
                            }
                            else
                                throw new InternalExpressionException("Wrong block position format at 4th argument.");
                    	}
                    	else if (lv.get(2).evalValue(c, Context.STRING).getString().equals("entity")) {
                    		try {
                                Iterator<? extends Entity> entities = API.getEntities(lv.get(3).evalValue(c, Context.STRING).getString()).iterator();
                                while (entities.hasNext())
                                	API.useItem(mcc, hand, entities.next(), null, "entity");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                    	}
                    	else
                    		throw new InternalExpressionException("The third argument should be either 'block' or 'entity'.");
                    }
                    break;
                }
                case "jump":
                    break;
                case "sneak":
                    mcc.player.setSneaking(true); // ?
                    break;
                case "sprint":
                    mcc.player.setSprinting(true); // ?
                    break;
                case "drop":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    if (lv.size() > 2)
                        throw new InternalExpressionException("Too many arguments.");
                    if (lv.size() == 1)
                        mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
                    else {
                        Matcher m1 = Pattern.compile("^\\d+$").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                        if (lv.get(1).evalValue(c, Context.STRING).getString().equals("all"))
                            mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
                        else if (m1.find()) {
                            int i = Integer.parseInt(m1.group());
                            if (i >= 0 && i < 64)
                                while (i-- > 0)
                                    mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                            else if (i >= 64)
                                mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, BlockPos.ORIGIN, Direction.DOWN));
                        }
                        else
                            throw new InternalExpressionException("Wrong second argument.");
                    }
                    break;
                case "swapHands":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    if (lv.size() > 1)
                        throw new InternalExpressionException("Too many arguments.");
                    mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    break;
                case "hotbar": {
                    if (lv.size() != 2)
                        throw new InternalExpressionException("Hotbar action needs 2 arguments.");
                    Matcher m1 = Pattern.compile("^\\d+$").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                    if (m1.find()) {
                        int i = Integer.parseInt(m1.group());
                        if (i >= 0 && i <= 8) {
                            mcc.player.inventory.selectedSlot = i;
                            mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(i));
                        }
                        else
                            throw new InternalExpressionException("Hotbar " + i + " does not exist.");
                    }
                    else
                        throw new InternalExpressionException("Wrong second argument.");
                    break;
                }
                case "look": {
                    if (lv.size() != 2)
                        throw new InternalExpressionException("Look action needs 2 arguments.");
                    Matcher m1 = Pattern.compile("-?\\d+(\\.\\d+)? -?\\d+(\\.\\d+)?").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                    if (m1.find()) {
                        float yaw, pitch;
                        m1 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                        m1.find();
                        yaw = Float.parseFloat(m1.group()) % 360.0F;
                        m1.find();
                        pitch = MathHelper.clamp(Float.parseFloat(m1.group()), -90.0F, 90.0F) % 360.0F;
                        mcc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookOnly(yaw, pitch, true));
                        mcc.player.yaw = yaw;
                        mcc.player.pitch = pitch;
                    }
                    else
                        throw new InternalExpressionException("Wrong angle format at second argument.");
                    break;
                }
                case "move": {  // TODO need to prevent teleport back to origin coordinate sometimes
                	if (lv.size() != 2)
                        throw new InternalExpressionException("Move action needs 2 arguments.");
                	Matcher m1 = Pattern.compile("-?\\d+(\\.\\d+)? -?\\d+(\\.\\d+)? -?\\d+(\\.\\d+)?").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                	if (m1.find() ) {
                		double x, y, z;
                		m1 = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(lv.get(1).evalValue(c, Context.STRING).getString());
                		m1.find();
                		x = Double.parseDouble(m1.group());
                		m1.find();
                		y = Double.parseDouble(m1.group());
                		m1.find();
                		z = Double.parseDouble(m1.group());
                		mcc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionOnly(x, y, z, false));
                		if (mcc.getNetworkHandler().getConnection().getPacketListener() instanceof ServerPlayNetworkHandler) {  // for update lastTickX, lastTickY, lastTickZ in net.minecraft.server.network/ServerPlayNetworkHandler.class
                			((ServerPlayNetworkHandler)mcc.getNetworkHandler().getConnection().getPacketListener()).syncWithPlayerPosition();
                			LOGGER.info("Sync player position.");
                		}
                		mcc.player.setPos(x, y, z);
                	}
                	else
                		throw new InternalExpressionException("Wrong block position format at second argument.");
                	break;
                }
                case "mine":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    break;
                case "eat":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.inventory.selectedSlot));
                    break;
                case "chat":
                    if (lv.size() != 2)
                        throw new InternalExpressionException("Chat action needs 2 arguments.");
                    mcc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(lv.get(1).evalValue(c, Context.STRING).getString()));
                    break;
                case "setCamera": {  // TODO need to render main player, and perhaps handle movement
                    if (lv.size() != 2)
                        throw new InternalExpressionException("SetCamera action needs 2 arguments.");
                    try {
                        List<? extends Entity> entity = API.getEntities(lv.get(1).evalValue(c, Context.STRING).getString());
                        if (entity.size() > 1)
                            throw new InternalExpressionException("Can set camera on only 1 entity.");
                        if (entity.size() == 1)
                            mcc.setCameraEntity(entity.get(0));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                default:
                    throw new InternalExpressionException("unsupported player action");
            }
            return (cc, tt) -> Value.NULL;
        });
    }
    
    private static void useItem(MinecraftClient mcc, Hand hand, Entity en, BlockPos bos, String action) {
    	ItemStack itemStack = mcc.player.getStackInHand(hand);
    	if (en == null && bos == null) {
    		if (mcc.crosshairTarget != null) {
    			switch(mcc.crosshairTarget.getType()) {
    				case ENTITY: {
    					if (action != null && !action.equals("entity"))
    						return;
    					Entity entity = ((EntityHitResult)mcc.crosshairTarget).getEntity();
    					Vec3d vec3d = ((EntityHitResult)mcc.crosshairTarget).getPos().subtract(entity.getPos());
    					mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(entity, hand, vec3d, mcc.player.isSneaking()));
    					ActionResult actionResult = entity.interactAt(mcc.player, vec3d, hand);
    					if (!actionResult.isAccepted()) {
    						mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(entity, hand, mcc.player.isSneaking()));
    						actionResult = mcc.player.interact(entity, hand);
    					}
    					if (actionResult.isAccepted()) {
                            if (actionResult.shouldSwingHand())
                            	mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                            return;
    					}
    					break;
    				}
    				case BLOCK: {
    					if (action != null && !action.equals("block"))
    						return;
    					BlockHitResult bhr = (BlockHitResult)mcc.crosshairTarget;
    					int ic = itemStack.getCount();
    					mcc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr));
    					ActionResult actionResult = mcc.world.getBlockState(bhr.getBlockPos()).onUse(mcc.world, mcc.player, hand, bhr);
    					if (actionResult.isAccepted()) {
    						if (actionResult.shouldSwingHand()) {
    							mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    							if (!itemStack.isEmpty() && itemStack.getCount() != ic)
    								mcc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
    						}
    						return;
    					}
    					if (actionResult == ActionResult.FAIL)
    						return;
    					break;
    				}
    				case MISS:
    			}
    		}
    	}
    	else if (en != null && action.equals("entity")){
    		mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(en, hand, new Vec3d(0, 0, 0), mcc.player.isSneaking()));
    		ActionResult actionResult = en.interactAt(mcc.player, new Vec3d(0, 0, 0), hand);
			if (!actionResult.isAccepted()) {
				mcc.getNetworkHandler().sendPacket(new PlayerInteractEntityC2SPacket(en, hand, mcc.player.isSneaking()));
				actionResult = mcc.player.interact(en, hand);
			}
			if (actionResult.isAccepted()) {
                if (actionResult.shouldSwingHand())
                	mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                return;
			}
    	}
    	else if (bos != null && action.equals("block")) {
    		BlockHitResult bhr = BlockHitResult.createMissed(new Vec3d(0, 0, 0), Direction.UP, bos);
			int ic = itemStack.getCount();
			mcc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr));
			ActionResult actionResult = mcc.world.getBlockState(bhr.getBlockPos()).onUse(mcc.world, mcc.player, hand, bhr);
			if (actionResult.isAccepted()) {
				if (actionResult.shouldSwingHand()) {
					mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
					if (!itemStack.isEmpty() && itemStack.getCount() != ic)
						mcc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
				}
				return;
			}
			if (actionResult == ActionResult.FAIL)
				return;
    	}
    	else
    		return;
    	
		if (!itemStack.isEmpty() && (action == null || action.equals("block") || action.equals("entity"))) {
			mcc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand));
			ActionResult actionResult = mcc.player.getStackInHand(hand).use(mcc.world, mcc.player, hand).getResult();
			if (actionResult.isAccepted()) {
                if (actionResult.shouldSwingHand())
                	mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                mcc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
                return;
             }
		}
    }
}
