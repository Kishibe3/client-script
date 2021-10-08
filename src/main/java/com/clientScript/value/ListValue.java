package com.clientScript.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.language.LazyValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class ListValue extends AbstractListValue implements ContainerValueInterface {
    protected final List<Value> items;

    private ListValue() {
        this.items = new ArrayList<>();
    }

    public ListValue(Collection<? extends Value> list) {
        this.items = new ArrayList<>();
        this.items.addAll(list);
    }

    protected ListValue(List<Value> list) {
        this.items = list;
    }

    public static ListValue warp(List<Value> list) {
        return new ListValue(list);
    }
    
    @Override
    public String getString() {
        return "[" + this.items.stream().map(Value::getString).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean getBoolean() {
        return !this.items.isEmpty();
    }

    public List<Value> getItems() {
        return this.items;
    }

    @Override
    public void append(Value v) {
        this.items.add(v);
    }

    @Override
    public Value add(Value other) {
        ListValue output = new ListValue();
        if (other instanceof ListValue) {
            List<Value> other_list = ((ListValue)other).items;
            if (other_list.size() == this.items.size())
                for(int i=0, size=this.items.size(); i<size; i++)
                    output.items.add(this.items.get(i).add(other_list.get(i)));
            else
                throw new InternalExpressionException("Cannot add two lists of uneven sizes");
        }
        else
            for (Value v : this.items)
                output.items.add(v.add(other));
        return output;
    }

    public Value subtract(Value other) {
        ListValue output = new ListValue();
        if (other instanceof ListValue) {
            List<Value> other_list = ((ListValue)other).items;
            if (other_list.size() == this.items.size())
                for(int i=0, size=this.items.size(); i<size; i++)
                    output.items.add(this.items.get(i).subtract(other_list.get(i)));
            else
                throw new InternalExpressionException("Cannot subtract two lists of uneven sizes");
        }
        else
            for (Value v : this.items)
                output.items.add(v.subtract(other));
        return output;
    }

    public Value multiply(Value other) {
        ListValue output = new ListValue();
        if (other instanceof ListValue) {
            List<Value> other_list = ((ListValue)other).items;
            if (other_list.size() == this.items.size()) {
                for(int i=0, size=this.items.size(); i<size; i++)
                    output.items.add(this.items.get(i).multiply(other_list.get(i)));
            }
            else
                throw new InternalExpressionException("Cannot multiply two lists of uneven sizes");
        }
        else
            for (Value v : this.items)
                output.items.add(v.multiply(other));
        return output;
    }

    public Value divide(Value other) {
        ListValue output = new ListValue();
        if (other instanceof ListValue) {
            List<Value> other_list = ((ListValue)other).items;
            if (other_list.size() == this.items.size())
                for(int i=0, size=this.items.size(); i<size; i++)
                    output.items.add(this.items.get(i).divide(other_list.get(i)));
            else
                throw new InternalExpressionException("Cannot divide two lists of uneven sizes");
        }
        else
            for (Value v : this.items)
                output.items.add(v.divide(other));
        return output;
    }

    @Override
    public Value slice(long fromDesc, Long toDesc) {
        List<Value> items = getItems();
        int size = items.size();
        int from = normalizeIndex(fromDesc, size);
        if (toDesc == null)
            return new ListValue(new ArrayList<>(getItems().subList(from, size)));
        int to = normalizeIndex(toDesc, size + 1);
        if (from > to)
            return ListValue.of();
        return new ListValue(new ArrayList<>(getItems().subList(from, to)));
    }

    @Override
    public Value split(Value delimiter) {
        ListValue result = new ListValue();
        if (delimiter == null) {
            this.forEach(item -> result.items.add(of(item)));
            return result;
        }
        int startIndex = 0;
        int index = 0;
        for (Value val : this.items) {
            index++;
            if (val.equals(delimiter)) {
                result.items.add(new ListValue(new ArrayList<>(this.items.subList(startIndex, index - 1))));
                startIndex = index;
            }
        }
        result.items.add(new ListValue(new ArrayList<>(this.items.subList(startIndex, length()))));
        return result;
    }

    @Override
    public double readDoubleNumber() {
        return (double)this.items.size();
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (Value el : this.items)
            array.add(el.toJson());
        return array;
    }

    @Override
    public int hashCode() {
        return this.items.hashCode();
    }

    @Override
    public Value deepcopy() {
        List<Value> copyItems = new ArrayList<>(this.items.size());
        for (Value entry : this.items)
            copyItems.add(entry.deepcopy());
        return new ListValue(copyItems);
    }

    @Override
    public int length() {
        return this.items.size();
    }

    @Override
    public String getTypeString() {
        return "list";
    }

    @Override
    public Value in(Value value1) {
        for (int i=0; i<this.items.size(); i++) {
            Value v = this.items.get(i);
            if (v.equals(value1))
                return new NumericValue(i);
        }
        return Value.NULL;
    }

    @Override
    public boolean has(Value where) {
        long index = NumericValue.asNumber(where, "'address' to a list index").getLong();
        return index >= 0 && index < this.items.size();
    }

    @Override
    public boolean delete(Value where) {
        if (!(where instanceof NumericValue) || this.items.isEmpty())
            return false;
        long index = ((NumericValue)where).getLong();
        this.items.remove(normalizeIndex(index, this.items.size()));
        return true;
    }

    public Iterator<Value> iterator() {
        return new ArrayList<>(this.items).iterator();
    }

    public static ListValue wrap(List<Value> list) {
        return new ListValue(list);
    }

    public static ListValue of(Value ... list) {
        return new ListValue(new ArrayList<>(Arrays.asList(list)));
    }

    public static ListValue ofNums(Number ... list) {
        List<Value> valList = new ArrayList<>();
        for (Number i : list)
            valList.add(new NumericValue(i.doubleValue()));
        return new ListValue(valList);
    }

    public static LazyValue lazyEmpty() {
        Value ret = new ListValue();
        return (c, t) -> ret;
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof ListValue) {
            ListValue ol = (ListValue)o;
            int this_size = getItems().size();
            int o_size = ol.getItems().size();
            if (this_size != o_size)
                return this_size - o_size;
            if (this_size == 0)
                return 0;
            for (int i = 0; i < this_size; i++) {
                int res = this.items.get(i).compareTo(ol.items.get(i));
                if (res != 0)
                    return res;
            }
            return 0;
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public boolean put(Value ind, Value value) {
        return put(ind, value, true, false);
    }

    private boolean put(Value ind, Value value, boolean replace, boolean extend) {
        if (ind.isNull()) {
            if (extend && value instanceof AbstractListValue)
                ((AbstractListValue)value).iterator().forEachRemaining((v)-> this.items.add(v));
            else
                this.items.add(value);
        }
        else {
            int numitems = this.items.size();
            if (!(ind instanceof NumericValue))
                return false;
            int index = (int)((NumericValue)ind).getLong();
            if (index < 0) {
                // only for values < 0
                index = normalizeIndex(index, numitems);
            }
            if (replace) {
                while (index >= this.items.size())
                    this.items.add(Value.NULL);
                this.items.set(index, value);
                return true;
            }
            while (index > this.items.size())
                this.items.add(Value.NULL);

            if (extend && value instanceof AbstractListValue) {
                Iterable<Value> iterable = ((AbstractListValue)value)::iterator;
                List<Value> appendix = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
                this.items.addAll(index, appendix );
                return true;
            }
            this.items.add(index, value);
        }
        return true;
    }

    /**
     * Finds a proper list index >=0 and < len that correspont to the rolling index value of idx
     *
     * @param idx
     * @param len
     * @return
     */
    public static int normalizeIndex(long idx, int len) {
        if (idx >= 0 && idx < len)
            return (int)idx;
        long range = Math.abs(idx) / len;
        idx += (range + 2) * len;
        idx = idx % len;
        return (int)idx;
    }

    @Override
    public Value get(Value value) {
        int size = this.items.size();
        if (size == 0)
            return Value.NULL;
        return this.items.get(normalizeIndex(NumericValue.asNumber(value, "'address' to a list index").getLong(), size));
    }

    public static class ListConstructorValue extends ListValue {
        public ListConstructorValue(Collection<? extends Value> list) {
            super(list);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof ListValue)
            return getItems().equals(((ListValue)o).getItems());
        return false;
    }
}
