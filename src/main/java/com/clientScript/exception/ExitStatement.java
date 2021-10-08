package com.clientScript.exception;

import com.clientScript.value.Value;

/* Exception thrown to terminate execution mid expression (aka return statement) */
public class ExitStatement extends RuntimeException {
    public final Value retval;
    public ExitStatement(Value value) {
        retval = value;
    }
}
