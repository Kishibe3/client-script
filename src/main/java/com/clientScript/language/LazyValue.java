package com.clientScript.language;

import com.clientScript.value.Value;

@FunctionalInterface
public interface LazyValue {
    LazyValue FALSE = (c, t) -> Value.FALSE;
    LazyValue TRUE = (c, t) -> Value.TRUE;
    LazyValue ZERO = (c, t) -> Value.ZERO;
    LazyValue NULL = (c, t) -> Value.NULL;

    Value evalValue(Context c, Context.Type type);

    default Value evalValue(Context c){
        return evalValue(c, Context.Type.NONE);
    }

    public static LazyValue ofConstant(Value val) {
        return new Constant(val);
    }
    
    @FunctionalInterface
    interface ContextFreeLazyValue extends LazyValue {
        Value evalType(Context.Type type);

        @Override
        default Value evalValue(Context c, Context.Type type) {
            return evalType(type);
        }
    }
    
    class Constant implements ContextFreeLazyValue {
        Value result;

        public Constant(Value value) {
            this.result = value;
        }

        public Value get() {
            return this.result;
        }

        @Override
        public Value evalType(Context.Type type) {

            return this.result.fromConstant();
        }

        @Override
        public Value evalValue(Context c, Context.Type type) {
            return this.result.fromConstant();
        }
    }
}
