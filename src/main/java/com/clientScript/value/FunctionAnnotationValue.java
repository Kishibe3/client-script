package com.clientScript.value;

import com.clientScript.exception.InternalExpressionException;

public class FunctionAnnotationValue extends Value {
    public enum Type {
        GLOBAL, VARARG
    }

    public Type type;
    
    public FunctionAnnotationValue(Value variable, Type type) {
        if (variable.boundVariable == null)
            throw new InternalExpressionException("You can only borrow variables from the outer scope");
        this.boundVariable = variable.boundVariable;
        this.type = type;
    }

    @Override
    public String getString() {
        return this.boundVariable;
    }

    @Override
    public boolean getBoolean() {
        return false;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Global value cannot be used as a map key");
    }
}
