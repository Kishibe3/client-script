package com.clientScript.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanValue extends NumericValue {
    boolean boolValue;
    public static final BooleanValue FALSE = new BooleanValue(false);
    public static final BooleanValue TRUE = new BooleanValue(true);

    protected BooleanValue(boolean boolval) {
        super(boolval ? 1L : 0L);
        this.boolValue = boolval;
    }

    @Override
    public String getString() {
        return this.boolValue ? "true" : "false";
    }

    public static BooleanValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public String getPrettyString() {
        return getString();
    }

    @Override
    public String getTypeString() {
        return "bool";
    }

    @Override
    public Value clone() {
        return new BooleanValue(this.boolValue);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(this.boolValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(boolValue);
    }
}
