package com.clientScript.exception;

import java.util.List;

import com.clientScript.language.Context;
import com.clientScript.language.Expression;
import com.clientScript.language.Tokenizer.Token;
import com.clientScript.value.FunctionValue;
import com.clientScript.value.Value;

public class ProcessedThrowStatement extends ExpressionException {
    public final Throwables thrownExceptionType;
    public final Value data;
    
    public ProcessedThrowStatement(Context c, Expression e, Token token, List<FunctionValue> stack, Throwables thrownExceptionType, Value data) {
        super(c, e, token, ()  -> "Unhandled " + thrownExceptionType.getId() + " exception: " + data.getString(), stack);
        this.thrownExceptionType = thrownExceptionType;
        this.data = data;
    }
}
