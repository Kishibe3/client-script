package com.clientScript.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import com.mojang.serialization.Decoder;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.state.property.Property;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.clientScript.argument.BlockArgument;
import com.clientScript.exception.InternalExpressionException;
import com.clientScript.utils.ShapesRenderer.ShapeDispatcher;
import com.clientScript.utils.ShapesRenderer.ShapesRenderer;
import com.clientScript.value.BlockValue;
import com.clientScript.value.EntityValue;
import com.clientScript.value.FormattedTextValue;
import com.clientScript.value.ListValue;
import com.clientScript.value.NBTSerializableValue;
import com.clientScript.value.NumericValue;
import com.clientScript.value.Value;

public class API {
    public static Logger LOGGER = LogManager.getLogger();

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
    
    public static double[] CoordHelper(String str) {
    	Matcher m1 = Pattern.compile("^[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]\\s+[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]\\s+[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]$").matcher(str);
    	MinecraftClient mcc = MinecraftClient.getInstance();
    	double[] rtn = new double[3];
    	if (m1.find()) {
    		if (str.charAt(0) == '^') {
    			double x, y, z;
    			m1 = Pattern.compile("\\^-?\\d+(\\.\\d+)?|\\^").matcher(str);
        		m1.find();
        		x = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		if (!m1.find())
        			return new double[0];
        		y = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		if (!m1.find())
        			return new double[0];
        		z = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		double sy = Math.sin(mcc.player.getYaw() * Math.PI / 180D), cy = Math.cos(mcc.player.getYaw() * Math.PI / 180D);
        		double sp = Math.sin(mcc.player.getPitch() * Math.PI / 180D), cp = Math.cos(mcc.player.getPitch() * Math.PI / 180D);
        		rtn[0] = x * cy - y * sp * sy - z * cp * sy + mcc.player.getX();
        		rtn[1] = y * cp - z * sp + mcc.player.getY();
        		rtn[2] = x * sy + y * sp * cy + z * cp * cy + mcc.player.getZ();
        		return rtn;
    		}
    		else {
    			m1 = Pattern.compile("~?-?\\d+(\\.\\d+)?|~").matcher(str);
        		m1.find();
        		if (m1.group().charAt(0) == '~')
        			rtn[0] = m1.group().equals("~")? mcc.player.getX() : Double.parseDouble(m1.group().substring(1)) + mcc.player.getX();
        		else
        			rtn[0] = Double.parseDouble(m1.group());
        		if (!m1.find())
        			return new double[0];
        		if (m1.group().charAt(0) == '~')
        			rtn[1] = m1.group().equals("~")? mcc.player.getY() : Double.parseDouble(m1.group().substring(1)) + mcc.player.getY();
        		else
        			rtn[1] = Double.parseDouble(m1.group());
        		if (!m1.find())
        			return new double[0];
        		if (m1.group().charAt(0) == '~')
        			rtn[2] = m1.group().equals("~")? mcc.player.getZ() : Double.parseDouble(m1.group().substring(1)) + mcc.player.getZ();
        		else
        			rtn[2] = Double.parseDouble(m1.group());
        		return rtn;
    		}
    	}
    	return new double[0];
    }
    
    public static float[] AngleHelper(String str) {
    	Matcher m1 = Pattern.compile("^~?-?\\d+(\\.\\d+)?|~\\s+~?-?\\d+(\\.\\d+)?|~$").matcher(str);
    	MinecraftClient mcc = MinecraftClient.getInstance();
    	float[] rtn = new float[2];
    	if (m1.find()) {
    		m1 = Pattern.compile("~?-?\\d+(\\.\\d+)?|~").matcher(str);
    		m1.find();
    		if (m1.group().charAt(0) == '~')
    			rtn[0] = m1.group().equals("~")? mcc.player.getYaw() : (Float.parseFloat(m1.group().substring(1)) + mcc.player.getYaw()) % 360.0F;
    		else
    			rtn[0] = Float.parseFloat(m1.group()) % 360.0F;
    		m1.find();
    		if (m1.group().charAt(0) == '~')
    			rtn[1] = m1.group().equals("~")? mcc.player.getPitch() : MathHelper.clamp(Float.parseFloat(m1.group().substring(1)) + mcc.player.getPitch(), -90.0F, 90.0F) % 360.0F;
    		else
    			rtn[1] = MathHelper.clamp(Float.parseFloat(m1.group()), -90.0F, 90.0F) % 360.0F;
    		return rtn;
    	}
    	return new float[0];
    }
    
    public static int[] BlockPosHelper(String str) {
    	Matcher m1 = Pattern.compile("^[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]\\s+[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]\\s+[\\^~]?-?\\d+(\\.\\d+)?|[\\^~]$").matcher(str);
    	MinecraftClient mcc = MinecraftClient.getInstance();
    	int[] rtn = new int[3];
    	if (m1.find()) {
    		if (str.charAt(0) == '^') {
    			double x, y, z;
    			m1 = Pattern.compile("\\^-?\\d+(\\.\\d+)?|\\^").matcher(str);
        		m1.find();
        		x = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		if (!m1.find())
        			return new int[0];
        		y = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		if (!m1.find())
        			return new int[0];
        		z = m1.group().equals("^")? 0D : Double.parseDouble(m1.group().substring(1));
        		double sy = Math.sin(mcc.player.getYaw() * Math.PI / 180D), cy = Math.cos(mcc.player.getYaw() * Math.PI / 180D);
        		double sp = Math.sin(mcc.player.getPitch() * Math.PI / 180D), cp = Math.cos(mcc.player.getPitch() * Math.PI / 180D);
        		rtn[0] = (int)Math.floor(x * cy - y * sp * sy - z * cp * sy + mcc.player.getX());
        		rtn[1] = (int)Math.floor(y * cp - z * sp + mcc.player.getY());
        		rtn[2] = (int)Math.floor(x * sy + y * sp * cy + z * cp * cy + mcc.player.getZ());
        		return rtn;
    		}
    		else {
    			m1 = Pattern.compile("~?-?\\d+(\\.\\d+)?|~").matcher(str);
        		m1.find();
        		if (m1.group().charAt(0) == '~')
        			rtn[0] = (int)Math.floor(mcc.player.getX() + (m1.group().equals("~")? 0D : Double.parseDouble(m1.group().substring(1))));
        		else
        			rtn[0] = (int)Math.floor(Double.parseDouble(m1.group()));
        		if (!m1.find())
        			return new int[0];
        		if (m1.group().charAt(0) == '~')
        			rtn[1] = (int)Math.floor(mcc.player.getY() + (m1.group().equals("~")? 0D : Double.parseDouble(m1.group().substring(1))));
        		else
        			rtn[1] = (int)Math.floor(Double.parseDouble(m1.group()));
        		if (!m1.find())
        			return new int[0];
        		if (m1.group().charAt(0) == '~')
        			rtn[2] = (int)Math.floor(mcc.player.getZ() + (m1.group().equals("~")? 0D : Double.parseDouble(m1.group().substring(1))));
        		else
        			rtn[2] = (int)Math.floor(Double.parseDouble(m1.group()));
        		return rtn;
    		}
    	}
    	return new int[0];
    }

    public static BlockState BlockStateHelper(String str) throws Exception {
        str = str.replaceAll("\\s", "");
        Matcher m1 = Pattern.compile("[a-z_]+(\\[[a-z_]+=\\w+(,[a-z_]+=\\w+)*\\])?").matcher(str);
        if (!m1.find())
            throw new InternalExpressionException("Wrong BlockState format.");
        m1 = Pattern.compile("[a-z_]+$|[a-z_]+(?=\\[)").matcher(str);
        m1.find();
        Block block = Registry.BLOCK.get(new Identifier(m1.group()));
        m1 = Pattern.compile("[a-z_]+=\\w+").matcher(str);
        Map<Property<?>, Comparable<?>> blockProperties = Maps.newHashMap();
        Function<Block, BlockState> ds = Block::getDefaultState;
        Optional<?> opt;
        
        while (m1.find()) {
            Matcher m2 = Pattern.compile("[a-z_]+(?==)").matcher(m1.group());
            m2.find();
            Property<?> property = block.getStateManager().getProperty(m2.group());
            if (property == null)
                throw new InternalExpressionException("Unknown property.");
            if (blockProperties.containsKey(property))
                throw new InternalExpressionException("Duplicated property.");
            m2 = Pattern.compile("(?<==)\\w+").matcher(m1.group());
            m2.find();
            opt = property.parse(m2.group());
            if (opt.isPresent())
			    blockProperties.put(property, (Comparable<?>)opt.get());
            else
                throw new InternalExpressionException("Invalid property.");
        }

        return new BlockState(block, ImmutableMap.copyOf(blockProperties), MapCodec.of(Encoder.empty(), Decoder.unit(ds.apply(block))));
    }

    /*
     * return true if a contains b
     */
    public static boolean containNBT(NbtElement a, NbtElement b) {
		if (b == null)
			return true;
		if (a == null)
			return false;
		if (a.getType() != b.getType())
			return false;
		switch(a.getType()) {
			case 0:
				return b.getType() == 0;
			case 1:
				return ((NbtByte)a).equals((NbtByte)b);
			case 2:
				return ((NbtShort)a).equals((NbtShort)b);
			case 3:
				return ((NbtInt)a).equals((NbtInt)b);
			case 4:
				return ((NbtLong)a).equals((NbtLong)b);
			case 5:
				return ((NbtFloat)a).equals((NbtFloat)b);
			case 6:
				return ((NbtDouble)a).equals((NbtDouble)b);
			case 7: {
				byte[] aarray = ((NbtByteArray)a).getByteArray(), barray = ((NbtByteArray)b).getByteArray();
				Byte[] aArray = new Byte[aarray.length], bArray = new Byte[barray.length];
				Arrays.setAll(aArray, i -> aarray[i]);
				Arrays.setAll(bArray, i -> barray[i]);
				return compareList(aArray, bArray);
			}
			case 8:
				return ((NbtString)a).asString().contains(((NbtString)b).asString());
			case 9: {
				List<NbtElement> al = new ArrayList<>(), bl = new ArrayList<>();
				for (int i=0; i<((NbtList)a).size(); i++)
					al.add(((NbtList)a).get(i));
				for (int i=0; i<((NbtList)b).size(); i++)
					bl.add(((NbtList)b).get(i));
				Iterator<NbtElement> ibl = (new HashSet<>(bl)).iterator();
				while (ibl.hasNext()) {
					int count = 0;
					NbtElement iblitem = ibl.next();
					for (int i=0; i<al.size(); i++) {
						if (containNBT(al.get(i), iblitem))
							count++;
					}
					if (count < Collections.frequency(bl, iblitem))
						return false;
				}
				return true;
			}
			case 10: {
				Iterator<String> ib = ((NbtCompound)b).getKeys().iterator();
				while (ib.hasNext()) {
					String key = ib.next();
					if (!containNBT(((NbtCompound)a).get(key), ((NbtCompound)b).get(key)))
						return false;
				}
				return true;
			}
			case 11: {
				int[] aarray = ((NbtIntArray)a).getIntArray(), barray = ((NbtIntArray)b).getIntArray();
				Integer[] aArray = new Integer[aarray.length], bArray = new Integer[barray.length];
				Arrays.setAll(aArray, i -> aarray[i]);
				Arrays.setAll(bArray, i -> barray[i]);
				return compareList(aArray, bArray);
			}
			case 12: {
				long[] aarray = ((NbtLongArray)a).getLongArray(), barray = ((NbtLongArray)b).getLongArray();
				Long[] aArray = new Long[aarray.length], bArray = new Long[barray.length];
				Arrays.setAll(aArray, i -> aarray[i]);
				Arrays.setAll(bArray, i -> barray[i]);
				return compareList(aArray, bArray);
			}
		}
		return true;
	}
	
    public static void apply(Expression expression) {
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
                throw new InternalExpressionException("'player' function needs at least 1 argument to assign player's action.");
            MinecraftClient mcc = MinecraftClient.getInstance();
            if (mcc.player == null)
                return (cc, tt) -> Value.NULL;

            switch(lv.get(0).evalValue(c, Context.STRING).getString()) {
                case "attack":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
                    if (lv.size() > 3)
                        throw new InternalExpressionException("Too many arguments.");
                    else if (lv.size() == 1 || lv.size() == 2) {
                    	if (lv.size() == 2 && !lv.get(1).evalValue(c, Context.STRING).getString().equals("entity") && !lv.get(1).evalValue(c, Context.STRING).getString().equals("block"))
                    		throw new InternalExpressionException("The second argument should be either 'block' or 'entity'.");
                        if (mcc.crosshairTarget != null) {
                        	switch(mcc.crosshairTarget.getType()) {
	                            case ENTITY:
	                                if (lv.size() == 2 && !lv.get(1).evalValue(c, Context.STRING).getString().equals("entity"))
	                                    break;
	                                mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(((EntityHitResult)mcc.crosshairTarget).getEntity(), mcc.player.isSneaking()));
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
                        	int[] bos = API.BlockPosHelper(lv.get(2).evalValue(c, Context.STRING).getString());
                        	if (bos.length == 3) {
                        		mcc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, new BlockPos(bos[0], bos[1], bos[2]), Direction.UP));
                                mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        	}
                        	else
                                throw new InternalExpressionException("Wrong block position format at second argument.");
                        }
                        else if (lv.get(1).evalValue(c, Context.STRING).getString().equals("entity")) {
                            try {
                                Iterator<? extends Entity> entities = API.getEntities(lv.get(2).evalValue(c, Context.STRING).getString()).iterator();
                                while (entities.hasNext()) {
                                    mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entities.next(), mcc.player.isSneaking()));
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
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
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
                    		int[] bos = API.BlockPosHelper(lv.get(3).evalValue(c, Context.STRING).getString());
                    		if (bos.length == 3) {
                    			API.useItem(mcc, hand, null, new BlockPos(bos[0], bos[1], bos[2]), "block");
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
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
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
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
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
                            mcc.player.getInventory().selectedSlot = i;
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
                    float[] ang = API.AngleHelper(lv.get(1).evalValue(c, Context.STRING).getString());
                    if (ang.length == 2) {
                        mcc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(ang[0], ang[1], true));
                        mcc.player.setYaw(ang[0]);
                        mcc.player.setPitch(ang[1]);
                    }
                    else
                        throw new InternalExpressionException("Wrong angle format at second argument.");
                    break;
                }
                case "move": {  // TODO need to prevent teleport back to origin coordinate sometimes
                	if (lv.size() != 2)
                        throw new InternalExpressionException("Move action needs 2 arguments.");
                	double[] coord = API.CoordHelper(lv.get(1).evalValue(c, Context.STRING).getString());
                	if (coord.length == 3) {
                		mcc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(coord[0], coord[1], coord[2], false));
                		mcc.player.setPos(coord[0], coord[1], coord[2]);
                	}
                	else
                		throw new InternalExpressionException("Wrong block position format at second argument.");
                	break;
                }
                case "mine":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
                    break;
                case "eat":
                    mcc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mcc.player.getInventory().selectedSlot));
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
                    throw new InternalExpressionException("Unsupported player action.");
            }
            return (cc, tt) -> Value.NULL;
        });
        
        expression.addContextFunction("block", -1, (c, t, lv) -> {
        	if (lv.size() == 0)
                throw new InternalExpressionException("'block' function needs at least 1 argument.");
        	BlockValue retval = BlockArgument.findIn(c, lv, 0, true).block;
        	retval.getBlockState();
        	retval.getData();
        	return retval;
        });

        expression.addContextFunction("draw_shape", -1, (c, t, lv) -> {
            List<Pair<ShapeDispatcher.ExpiringShape, String>> shapes = new ArrayList<>();
            if (lv.size() == 1) {
                Value specLoad = lv.get(0);
                if (!(specLoad instanceof ListValue))
                    throw new InternalExpressionException("In bulk mode - shapes need to be provided as a list of shape specs");
                for (Value list: ((ListValue)specLoad).getItems()) {
                    if (!(list instanceof ListValue))
                        throw new InternalExpressionException("In bulk mode - shapes need to be provided as a list of shape specs");
                    shapes.add(ShapeDispatcher.fromFunctionArgs(((ListValue)list).getItems()));
                }
            }
            else
                shapes.add(ShapeDispatcher.fromFunctionArgs(lv));
            
            ShapesRenderer.addShape(shapes);
            return Value.TRUE;
        });

        expression.addContextFunction("entity_selector", -1, (c, t, lv) -> {
            if (lv.size() != 1)
                throw new InternalExpressionException("'entity_selector' function needs 1 argument.");
            List<Value> retList = new ArrayList<>();
            try {
                API.getEntities(lv.get(0).getString()).forEach(e -> {
                    retList.add(new EntityValue(e));
                });
            }
            catch (Exception e) {}
            return ListValue.warp(retList);
        });

        expression.addContextFunction("query", -1, (c, t, lv) -> {
            if (lv.size() != 2)
                throw new InternalExpressionException("'query' takes entity as a first argument, and queried path as a second.");
            List<? extends Entity> le = new ArrayList<>();
            try {
                le = API.getEntities(lv.get(0).getString());
            } catch (Exception exc) {}

            if (le.size() != 1)
                throw new InternalExpressionException("Should only select one entity.");
            
            try {
                NbtElement nbt = new EntityValue(le.get(0)).getData(lv.get(1).getString());
                return new NBTSerializableValue(nbt).toValue();
            } catch (Exception exc) {}
            return Value.NULL;
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
    					mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, mcc.player.isSneaking(), hand, vec3d));
    					ActionResult actionResult = entity.interactAt(mcc.player, vec3d, hand);
    					if (!actionResult.isAccepted()) {
    						mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interact(entity, mcc.player.isSneaking(), hand));
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
    					mcc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr));
    					ActionResult actionResult = mcc.world.getBlockState(bhr.getBlockPos()).onUse(mcc.world, mcc.player, hand, bhr);
    					if (actionResult.isAccepted()) {
    						if (actionResult.shouldSwingHand())
    							mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
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
    		mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interactAt(en, mcc.player.isSneaking(), hand, new Vec3d(0, 0, 0)));
    		ActionResult actionResult = en.interactAt(mcc.player, new Vec3d(0, 0, 0), hand);
			if (!actionResult.isAccepted()) {
				mcc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.interact(en, mcc.player.isSneaking(), hand));
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
			ActionResult actionResult = (mcc.player.getItemCooldownManager().isCoolingDown(itemStack.getItem()))? ActionResult.PASS : mcc.player.getStackInHand(hand).use(mcc.world, mcc.player, hand).getResult();
			if (actionResult.isAccepted()) {
                if (actionResult.shouldSwingHand())
                	mcc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
                return;
             }
		}
    }

	private static <T> boolean compareList(T[] a, T[] b) {
		Iterator<T> ib = (new HashSet<>(Arrays.asList(b))).iterator();
		while (ib.hasNext()) {
			T bitem = ib.next();
			if (Collections.frequency(Arrays.asList(a), bitem) < Collections.frequency(Arrays.asList(b), bitem))
				return false;
		}
		return true;
	}
}
