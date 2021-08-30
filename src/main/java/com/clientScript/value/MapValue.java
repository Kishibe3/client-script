package com.clientScript.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.clientScript.exception.InternalExpressionException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MapValue extends AbstractListValue implements ContainerValueInterface {
    private final Map<Value, Value> map;

    public MapValue(List<Value> kvPairs) {
        this();
        for (Value v : kvPairs)
            put(v);
    }

    private MapValue() {
        this.map = new HashMap<>();
    }

    private MapValue(Map<Value,Value> other) {
        this.map = other;
    }
    
    @Override
    public String getString() {
        return "{" + this.map.entrySet().stream().map(p -> p.getKey().getString() + ": " + p.getValue().getString()).collect(Collectors.joining(", ")) + "}";
    }

    @Override
    public String getPrettyString() {
        if (this.map.size() < 6)
            return "{" + this.map.entrySet().stream().map(p -> p.getKey().getPrettyString() + ": " + p.getValue().getPrettyString()).collect(Collectors.joining(", ")) + "}";
        List<Value> keys = new ArrayList<>(this.map.keySet());
        int max = keys.size();
        return "{" + keys.get(0).getPrettyString() + ": " + this.map.get(keys.get(0)).getPrettyString() + ", " +
            keys.get(1).getPrettyString() + ": " + this.map.get(keys.get(1)).getPrettyString() + ", ..., " +
            keys.get(max - 2).getPrettyString() + ": " + this.map.get(keys.get(max - 2)).getPrettyString() + ", " +
            keys.get(max - 1).getPrettyString() + ": " + this.map.get(keys.get(max - 1)).getPrettyString() + "}";
    }

    @Override
    public boolean getBoolean() {
        return !this.map.isEmpty();
    }

    public Map<Value, Value> getMap() {
        return this.map;
    }

    @Override
    public Value clone() {
        return new MapValue(this.map);
    }

    @Override
    public Value deepcopy() {
        Map<Value, Value> copyMap = new HashMap<>();
        this.map.forEach((key, value) -> copyMap.put(key.deepcopy(), value.deepcopy()));
        return new MapValue(copyMap);
    }

    @Override
    public boolean has(Value where) {
        return this.map.containsKey(where);
    }

    @Override
    public boolean delete(Value where) {
        Value ret = this.map.remove(where);
        return ret != null;
    }

    public void put(Value v) {
        if (!(v instanceof ListValue)) {
            this.map.put(v, Value.NULL);
            return;
        }
        ListValue pair = (ListValue)v;
        if (pair.getItems().size() != 2)
            throw new InternalExpressionException("Map constructor requires elements that have two items");
        this.map.put(pair.getItems().get(0), pair.getItems().get(1));
    }

    public static MapValue wrap(Map<Value,Value> other) {
        return new MapValue(other);
    }

    @Override
    public Iterator<Value> iterator() {
        return new ArrayList<>(this.map.keySet()).iterator();
    }

    @Override
    public Value get(Value v2) {
        return this.map.getOrDefault(v2, Value.NULL);
    }

    @Override
    public boolean put(Value key, Value value) {
        Value ret = this.map.put(key, value);
        return ret != null;
    }

    @Override
    public void append(Value v) {
        this.map.put(v, Value.NULL);
    }


    @Override
    public Value add(Value o) {
        Map<Value, Value> newItems = new HashMap<>(this.map);
        if (o instanceof MapValue)
            newItems.putAll(((MapValue)o).map);
        else if (o instanceof AbstractListValue) {
            Iterator<Value> it = ((AbstractListValue)o).iterator();
            while (it.hasNext())
                newItems.put(it.next(), Value.NULL);
        }
        else
            newItems.put(o, Value.NULL);
        return MapValue.wrap(newItems);
    }

    @Override
    public Value subtract(Value v) {
        throw new InternalExpressionException("Cannot subtract from a map value");
    }

    @Override
    public Value multiply(Value v) {
        throw new InternalExpressionException("Cannot multiply with a map value");
    }

    @Override
    public Value divide(Value v) {
        throw new InternalExpressionException("Cannot divide a map value");
    }

    @Override
    public int compareTo(Value o) {
        throw new InternalExpressionException("Cannot compare with a map value");
    }
    
    @Override
    public int length() {
        return this.map.size();
    }

    @Override
    public Value in(Value value1) {
        if (this.map.containsKey(value1))
            return value1;
        return Value.NULL;
    }

    @Override
    public Value slice(long from, Long to) {
        throw new InternalExpressionException("Cannot slice a map value");
    }

    @Override
    public Value split(Value delimiter) {
    	throw new InternalExpressionException("Cannot split a map value");
    }

    @Override
    public double readDoubleNumber() {
        return (double)this.map.size();
    }

    @Override
    public String getTypeString() {
        return "map";
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public JsonElement toJson() {
        JsonObject jsonMap = new JsonObject();
        List<Value> keys = new ArrayList<>(this.map.keySet());
        Collections.sort(keys);
        keys.forEach(k -> jsonMap.add(k.getString(), this.map.get(k).toJson()));
        return jsonMap;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof MapValue)
            return this.map.equals(((MapValue)o).map);
        return false;
    }
}
