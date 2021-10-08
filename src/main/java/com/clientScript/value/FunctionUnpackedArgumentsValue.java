package com.clientScript.value;

import java.util.List;

public class FunctionUnpackedArgumentsValue extends ListValue {
    
    public FunctionUnpackedArgumentsValue(List<Value> list) {
        super(list);
    }

    @Override
    public Value clone() {
        return new FunctionUnpackedArgumentsValue(this.items);
    }

    @Override
    public Value deepcopy() {
        ListValue copy = (ListValue)super.deepcopy();
        return new FunctionUnpackedArgumentsValue(copy.items);
    }
}
