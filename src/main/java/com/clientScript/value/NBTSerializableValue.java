package com.clientScript.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.language.API;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.AbstractNbtList;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtNull;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.StringNbtReader;

public class NBTSerializableValue extends Value implements ContainerValueInterface {
    private NbtElement nbt = null;

    public NBTSerializableValue(NbtElement nbt) {
        this.nbt = nbt;
    }

    public NBTSerializableValue(String nbtString) {
        try {
            this.nbt = (new StringNbtReader(new StringReader(nbtString))).parseElement();
        }
        catch (CommandSyntaxException exc) {
            throw new InternalExpressionException("Incorrect NBT data: " + nbtString);
        }
    }

    public NbtElement getNbt() {
        return this.nbt;
    }

    private ListValue toList(NbtElement nbt) {
        if (nbt instanceof NbtByteArray)
            ((NbtByteArray)nbt).getByteArray();
        if (nbt instanceof NbtIntArray)
            ((NbtIntArray)nbt).getIntArray();
        if (nbt instanceof NbtLongArray)
            ((NbtLongArray)nbt).getLongArray();
        if (nbt instanceof NbtList) {
            List<Value> ret = new ArrayList<>();
            int len = ((NbtList)nbt).size();
            byte type = ((NbtList)nbt).getHeldType();
            for (int i=0; i<len; i++) {
                if (type == 7|| type == 9 || type == 11 || type == 12)
                    ret.add(toList(((NbtList)nbt).get(i)));
                else {
                    NbtElement el = ((NbtList)nbt).get(i);
                    if (el instanceof AbstractNbtNumber)
                        ret.add(new NumericValue(((AbstractNbtNumber)el).doubleValue()));
                    if (el instanceof NbtString)
                        ret.add(new StringValue(el.asString()));
                    if (el instanceof NbtNull)
                        ret.add(NullValue.NULL);
                    if (el instanceof NbtCompound)
                        ret.add(new NBTSerializableValue(el));
                }
            }
            return new ListValue(ret);
        }
        return new ListValue(new ArrayList<>());
    }

    @Override
    public Value add(Value v) {
        if (this.nbt instanceof AbstractNbtNumber)
            return new NumericValue(((AbstractNbtNumber)this.nbt).doubleValue()).add(v);
        if (this.nbt instanceof AbstractNbtList)
            return toList(this.nbt).add(v);
        return super.add(v);
    }

    @Override
    public Value subtract(Value v) {
        if (this.nbt instanceof AbstractNbtNumber)
            return new NumericValue(((AbstractNbtNumber)this.nbt).doubleValue()).subtract(v);
        if (this.nbt instanceof AbstractNbtList)
            return toList(this.nbt).subtract(v);
        return super.subtract(v);
    }

    @Override
    public Value multiply(Value v) {
        if (this.nbt instanceof AbstractNbtNumber)
            return new NumericValue(((AbstractNbtNumber)this.nbt).doubleValue()).multiply(v);
        if (this.nbt instanceof AbstractNbtList)
            return toList(this.nbt).multiply(v);
        return super.multiply(v);
    }

    @Override
    public Value divide(Value v) {
        if (this.nbt instanceof AbstractNbtNumber)
            return new NumericValue(((AbstractNbtNumber)this.nbt).doubleValue()).divide(v);
        if (this.nbt instanceof AbstractNbtList)
            return toList(this.nbt).divide(v);
        return super.divide(v);
    }

    @Override
    public int compareTo(Value o) {
        if (!(o instanceof NBTSerializableValue))
			return getString().compareTo(o.getString());
        if (equals(o))
            return 0;
        return API.containNBT(this.nbt, ((NBTSerializableValue)o).getNbt())? 1 : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NBTSerializableValue)
            return this.nbt.asString().equals(((NBTSerializableValue)o).getNbt().asString());
        return super.equals(o);
    }

    @Override
    public String getString() {
        if (nbt == null)
            return "";
        return nbt.asString();
    }

    @Override
    public boolean getBoolean() {
        if (nbt instanceof NbtCompound)
            return !((NbtCompound)nbt).isEmpty();
        if (nbt instanceof AbstractNbtList)
            return ((AbstractNbtList<?>)nbt).isEmpty();
        if (nbt instanceof AbstractNbtNumber)
            return ((AbstractNbtNumber)nbt).doubleValue() != 0.0;
        if (nbt instanceof NbtString)
            return nbt.asString().isEmpty();
        return true;
    }

    @Override
    public boolean has(Value where) {
        return getPath(where.getString()).count(this.nbt) > 0;
    }

    @Override
    public Value get(Value value) {
        String valString = value.getString();
        NbtPathArgumentType.NbtPath path = getPath(valString);
        try {
            List<NbtElement> nbts = path.get(this.nbt);
            if (nbts.size() == 0)
                return Value.NULL;
            if (nbts.size() == 1 && !valString.endsWith("[]"))
                return NBTSerializableValue.decodeNbt(nbts.get(0));
            return ListValue.wrap(nbts.stream().map(NBTSerializableValue::decodeNbt).collect(Collectors.toList()));
        } catch (CommandSyntaxException exc) {
        }
        return Value.NULL;
    }

    @Override
    public boolean put(Value where, Value value) {
        NbtPathArgumentType.NbtPath path = getPath(where.getString());
        NbtElement nbtToInsert = value instanceof NBTSerializableValue ? ((NBTSerializableValue) value).getNbt() : new NBTSerializableValue(value.getString()).getNbt();
        return modify_replace(path, nbtToInsert);
    }

    @Override
    public boolean delete(Value where) {
        int removed = getPath(where.getString()).remove(this.nbt);
        return removed > 0;
    }

    private NbtPathArgumentType.NbtPath getPath(String arg) {
        try {
            return NbtPathArgumentType.nbtPath().parse(new StringReader(arg));
        } catch (CommandSyntaxException exc) {
            throw new InternalExpressionException("Incorrect nbt path: " + arg);
        }
    }

    private static Value decodeNbt(NbtElement t) {
        if (t instanceof AbstractNbtNumber)
            return new NumericValue(((AbstractNbtNumber) t).doubleValue());
        if (t instanceof NbtCompound)
            return new NBTSerializableValue(t);
        return new StringValue(t.asString());
    }

    private boolean modify_replace(NbtPathArgumentType.NbtPath nbtPath, NbtElement replacement) //nbtPathArgumentType$NbtPath_1, list_1)
    {
        String pathText = nbtPath.toString();
        if (pathText.endsWith("]")) { // workaround for array replacement or item in the array replacement
            if (nbtPath.remove(this.nbt) == 0)
                return false;
            
            Matcher m1 = Pattern.compile("\\[[^\\[]*]$").matcher(pathText);
            if (!m1.find()) // malformed path
                return false;
            
            String arrAccess = m1.group();
            int pos;
            if (arrAccess.length() == 2) // we just removed entire array
                pos = 0;
            else {
                try {
                    pos = Integer.parseInt(arrAccess.substring(1, arrAccess.length() - 1));
                }
                catch (NumberFormatException e) {
                    return false;
                }
            }
            NbtPathArgumentType.NbtPath newPath = getPath(pathText.substring(0, pathText.length() - arrAccess.length()));
            return modify_insert(pos, newPath, replacement, this.nbt);
        }
        try {
            nbtPath.put(this.nbt, () -> replacement);
        }
        catch (CommandSyntaxException e) {
            return false;
        }
        return true;
    }

    private boolean modify_insert(int index, NbtPathArgumentType.NbtPath nbtPath, NbtElement newElement, NbtElement currentTag) {
        Collection<NbtElement> targets;
        try {
            targets = nbtPath.getOrInit(currentTag, NbtList::new);
        }
        catch (CommandSyntaxException e) {
            return false;
        }

        boolean modified = false;
        for (NbtElement target: targets) {
            if (!(target instanceof AbstractNbtList))
                continue;
            try {
                AbstractNbtList<?> targetList = (AbstractNbtList<?>)target;
                if (!targetList.addElement(index < 0? targetList.size() + index + 1 : index, newElement.copy()))
                    return false;
                modified = true;
            }
            catch (IndexOutOfBoundsException exc) {}
        }

        return modified;
    }
}
