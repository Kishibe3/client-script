package com.clientScript.value;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.StringReader;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.WorldChunk;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.exception.ThrowStatement;
import com.clientScript.exception.Throwables;
import com.clientScript.language.API;

public class BlockValue extends Value {
	public static final BlockValue NULL = new BlockValue(null, null);
	private BlockState blockState;
	private final BlockPos pos;
	private NbtCompound data;
	
	private static final Map<String, BlockValue> bvCache= new HashMap<>();
	
	public BlockValue(BlockState state, BlockPos position) {
		this.blockState = state;
		this.pos = position;
		this.data = null;
	}
	
	public BlockValue(BlockState state, BlockPos position, NbtCompound nbt) {
		this.blockState = state;
		this.pos = position;
		this.data = nbt;
	}
	
	public BlockState getBlockState() {
		if (this.blockState != null)
			return this.blockState;
		if (this.pos != null) {
			MinecraftClient mcc = MinecraftClient.getInstance();
			this.blockState = mcc.world.getBlockState(this.pos);
			return this.blockState;
		}
		//return null;
		throw new InternalExpressionException("Attempted to fetch block state without world or stored block state");
	}
	
	public static BlockEntity getBlockEntity(BlockPos pos) {
		MinecraftClient mcc = MinecraftClient.getInstance();
		if (mcc.getServer() != null)
			return mcc.getServer().getWorld(mcc.world.getRegistryKey()).getWorldChunk(pos).getBlockEntity(pos, WorldChunk.CreationType.IMMEDIATE);
		return null;
	}

	public BlockPos getPos() {
		return this.pos;
	}
	
	public static BlockValue fromString(String str) {
		int[] bos = API.BlockPosHelper(str);
		if (bos.length == 3)
			return new BlockValue(null, new BlockPos(bos[0], bos[1], bos[2]));
		
		try {
			BlockValue bv = BlockValue.bvCache.get(str);
			if (bv != null)
				return bv;
			
			BlockArgumentParser blockstateparser = (new BlockArgumentParser(new StringReader(str), false)).parse(true);
			BlockState bs = API.BlockStateHelper(str.contains("{")? str.substring(0, str.indexOf("{")) : str);
			if (bs != null) {
				NbtCompound bd = blockstateparser.getNbtData();
				if (bd == null)
					bd = new NbtCompound();
				bv = new BlockValue(bs, null, bd);
				if (BlockValue.bvCache.size() > 10000)
					BlockValue.bvCache.clear();
				BlockValue.bvCache.put(str, bv);
				return bv;
			}
		}
		catch (Exception e) {}
		throw new ThrowStatement(str, Throwables.UNKNOWN_BLOCK);
	}
	
	public NbtCompound getData() {
		if (this.data != null) {
			if (this.data.isEmpty())
				return null;
			return this.data;
		}
		if (this.pos != null) {
			BlockEntity be = BlockValue.getBlockEntity(this.pos);
			NbtCompound tag = new NbtCompound();
			if (be == null) {
				this.data = tag;
				return null;
			}
			this.data = be.writeNbt(tag);
			return this.data;
		}
		return null;
	}

	@Override
	public String getString() {
		Identifier id = Registry.BLOCK.getId(getBlockState().getBlock());
		if (id == null)  // should be Value.NULL
			return "";
		String str;
		if (id.getNamespace().equals("minecraft"))
            str = id.getPath();
		else
			str = id.toString();
		Matcher m = Pattern.compile("\\[.*\\]$").matcher(this.blockState.toString());
		if (m.find())
			str += m.group();
		if (getData() != null)
			str += this.data.asString();
		return str;
	}

	@Override
	public boolean getBoolean() {
		return this != NULL && !getBlockState().isAir();
	}
	
	@Override
	public boolean equals(final Object o) {
		if (o instanceof BlockValue) {
			BlockValue bv = (BlockValue)o;
			NbtCompound td = getData(), bd = bv.getData();
			BlockState ts = getBlockState(), bs = bv.getBlockState();
			if ((td == null && bd != null) || (td != null && bd == null))
				return false;
			if ((ts == null && bs != null) || (ts != null && bs == null))
				return false;
			if (td == null && bd == null && ts == null && bs == null)
				return true;
			if (td == null && bd == null)
				return ts.toString().equals(bs.toString());
			if (ts == null && bs == null)
				return td.equals(bd);
			return td.equals(bd) && ts.toString().equals(bs.toString());
		}
		return super.equals(o);
	}
	
	@Override
	public int compareTo(Value o) {
		if (!(o instanceof BlockValue))
			return getString().compareTo(o.getString());
		int ret = ((BlockValue)o).belongsTo(this);
        if (ret >= 0)
            return ret;
		ret = ((BlockValue)this).belongsTo(o);
		if (ret >= 0)
			return -ret;
		// Not belong to each other
        return 2;
	}

	/*
	 * Check whether this BlockValue belongs to o type of block if o is BlockValue
	 * ex. block('grass_block[showy=true]') belongs to block('grass_block')
	 */
	public int belongsTo(Value o) {
		if (!(o instanceof BlockValue))
			return -1;
		BlockValue bo = (BlockValue)o;
		if (equals(bo))
			return 0;
		if (!Registry.BLOCK.getId(this.blockState.getBlock()).toString().equals(Registry.BLOCK.getId(bo.blockState.getBlock()).toString()))
			return -1;
		Iterator<Entry<Property<?>, Comparable<?>>> boEntries = bo.blockState.getEntries().entrySet().stream().iterator();
		while (boEntries.hasNext()) {
			Entry<Property<?>, Comparable<?>> boEntry = boEntries.next();
			if (this.blockState.getEntries().get(boEntry.getKey()) != boEntry.getValue())
				return -1;
		}
		if (!API.containNBT(getData(), bo.getData()))
			return -1;
		return 1;
	}
}
