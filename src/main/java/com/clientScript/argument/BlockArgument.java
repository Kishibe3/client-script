package com.clientScript.argument;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.language.Context;
import com.clientScript.value.BlockValue;
import com.clientScript.value.ListValue;
import com.clientScript.value.NumericValue;
import com.clientScript.value.NullValue;
import com.clientScript.value.StringValue;
import com.clientScript.value.Value;

import net.minecraft.util.math.BlockPos;

public class BlockArgument extends Argument {
	public final BlockValue block;
	public final String replacement;

	private BlockArgument(BlockValue b, int o) {
		super(o);
		this.block = b;
		this.replacement = null;
	}
	
	private BlockArgument(BlockValue b, int o, String replacement) {
		super(o);
		this.block = b;
		this.replacement = replacement;
	}
	
	public static BlockArgument findIn(Context c, List<Value> params, int offset, boolean acceptString) {
        return findIn(c, params, offset, acceptString, false, false);
    }

    public static BlockArgument findIn(Context c, List<Value> params, int offset, boolean acceptString, boolean optional, boolean anyString) {
        return findIn(c, params.listIterator(offset), offset, acceptString, optional, anyString);
    }
	
	public static BlockArgument findIn(Context c, Iterator<Value> params, int offset, boolean acceptString, boolean optional, boolean anyString) {
		try {
			Value v1 = params.next();
			if (optional && v1 instanceof NullValue)
				return new BlockArgument(null, offset + 1);
			if (anyString && v1 instanceof StringValue)
				return new BlockArgument(null, offset + 1, v1.getString());
			if (acceptString && v1 instanceof StringValue)
				return new BlockArgument(BlockValue.fromString(v1.getString()), offset + 1);
			if (v1 instanceof BlockValue)
				return new BlockArgument((BlockValue)v1, offset + 1);
			if (v1 instanceof ListValue) {
				List<Value> args = ((ListValue)v1).getItems();
				int xpos = (int)NumericValue.asNumber(args.get(0)).getLong();
                int ypos = (int)NumericValue.asNumber(args.get(1)).getLong();
                int zpos = (int)NumericValue.asNumber(args.get(2)).getLong();
                return new BlockArgument(
                	new BlockValue(null, new BlockPos(xpos, ypos, zpos)),
                	offset + 1
                );
			}
			int xpos = (int)NumericValue.asNumber(v1).getLong();
            int ypos = (int)NumericValue.asNumber(params.next()).getLong();
            int zpos = (int)NumericValue.asNumber(params.next()).getLong();
            return new BlockArgument(
                new BlockValue(null, new BlockPos(xpos, ypos, zpos)),
                offset + 3
            );
		}
		catch (IndexOutOfBoundsException | NoSuchElementException e) {
			String message = "Block-type argument should be defined either by three coordinates (a triple or by three arguments), or a block value";
	        if (acceptString)
	            message += ", or a string with block description";
	        if (optional)
	            message += ", or null";
	        throw new InternalExpressionException(message);
		}
	}
}
