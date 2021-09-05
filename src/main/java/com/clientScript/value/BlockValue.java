package com.clientScript.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.exception.ThrowStatement;
import com.clientScript.exception.Throwables;
import com.clientScript.language.API;
import com.clientScript.language.Expression;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.chunk.WorldChunk;

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
		throw new InternalExpressionException("Attempted to fetch block state without world or stored block state");
	}
	
	public static BlockEntity getBlockEntity(BlockPos pos) {
		MinecraftClient mcc = MinecraftClient.getInstance();
		if (mcc.getServer() != null)
			return mcc.getServer().getWorld(mcc.world.getRegistryKey()).getWorldChunk(pos).getBlockEntity(pos, WorldChunk.CreationType.IMMEDIATE);
		return null;
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
			if (blockstateparser.getBlockState() != null) {
				NbtCompound bd = blockstateparser.getNbtData();
				if (bd == null)
					bd = new NbtCompound();
				bv = new BlockValue(blockstateparser.getBlockState(), null, bd);
				if (BlockValue.bvCache.size() > 10000)
					BlockValue.bvCache.clear();
				BlockValue.bvCache.put(str, bv);
				return bv;
			}
		}
		catch (CommandSyntaxException e) {}
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
			return getData().equals(bv.getData()) && getBlockState().toString().equals(bv.getBlockState().toString());
		}
		return super.equals(o);
	}
	
	/*
	 * Check whether this BlockValue belongs to o type of block if o is BlockValue
	 */
	@Override
	public int compareTo(Value o) {
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
		if (!containNBT(getData(), bo.getData()))
			return -1;
		return 1;
	}
	
	private boolean containNBT(NbtElement a, NbtElement b) {
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
	
	private <T> boolean compareList(T[] a, T[] b) {
		Iterator<T> ib = (new HashSet<>(Arrays.asList(b))).iterator();
		while (ib.hasNext()) {
			T bitem = ib.next();
			if (Collections.frequency(Arrays.asList(a), bitem) < Collections.frequency(Arrays.asList(b), bitem))
				return false;
		}
		return true;
	}
	
}
