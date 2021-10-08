package com.clientScript.value;

import java.util.List;

import com.google.common.collect.Lists;

import com.clientScript.exception.InternalExpressionException;

public abstract class AbstractListValue extends Value implements Iterable<Value> {
    
    public List<Value> unpack() {
        return Lists.newArrayList(iterator());
    }

    public void append(Value v) {
        throw new InternalExpressionException("Cannot append a value to an abstract list");
    }
    public void fatality() {}

    @Override
    public Value fromConstant() {
        return deepcopy();
    }
}
