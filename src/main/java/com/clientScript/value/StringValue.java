package com.clientScript.value;

public class StringValue extends Value {
    public static Value EMPTY = StringValue.of("");
    
    private String str;

    public StringValue(String str) {
        this.str = str;
    }

    @Override
    public String getString() {
        return this.str;
    }

    @Override
    public boolean getBoolean() {
        return this.str != null && !this.str.isEmpty();
    }

    public static Value of(String value) {
        if (value == null)
            return Value.NULL;
        return new StringValue(value);
    }

    @Override
    public Value clone() {
        return new StringValue(str);
    }

    @Override
    public String getTypeString() {
        return "string";
    }
}
