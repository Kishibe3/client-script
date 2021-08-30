package com.clientScript.value;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import com.clientScript.exception.ExitStatement;
import com.clientScript.exception.ExpressionException;
import com.clientScript.exception.InternalExpressionException;
import com.clientScript.language.Context;
import com.clientScript.language.Expression;
import com.clientScript.language.Tokenizer;

public class ThreadValue extends Value {
    private final CompletableFuture<Value> taskFuture;
    private final long id;
    private static long sequence = 0L;

    public ThreadValue(Value pool, FunctionValue function, Expression expr, Tokenizer.Token token, Context ctx, List<Value> args) {
        this.id = ThreadValue.sequence++;
        ExecutorService executor = ctx.host.getExecutor(pool);
        if (executor == null) {
            // app is shutting down - no more threads can be spawned.
            this.taskFuture = CompletableFuture.completedFuture(Value.NULL);
        }
        else {
            this.taskFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return function.execute(ctx, Context.NONE, expr, token, args).evalValue(ctx);
                }
                catch (ExitStatement exit) {
                    // app stopped
                    return exit.retval;
                }
                catch (ExpressionException exc) {
                    ctx.host.handleExpressionException("Thread failed\n", exc);
                    return Value.NULL;
                }
            }, ctx.host.getExecutor(pool));
        }
        Thread.yield();
    }

    @Override
    public String getString() {
        return this.taskFuture.getNow(Value.NULL).getString();
    }

    @Override
    public boolean getBoolean() {
        return this.taskFuture.getNow(Value.NULL).getBoolean();
    }

    public Value getValue() {
        return this.taskFuture.getNow(Value.NULL);
    }

    @Override
    public int compareTo(Value o) {
        if (!(o instanceof ThreadValue))
            throw new InternalExpressionException("Cannot compare tasks to other types");
        return (int)(this.id - ((ThreadValue)o).id);
    }

    public Value join() {
        try {
            return this.taskFuture.get();
        }
        catch (ExitStatement exit) {
            this.taskFuture.complete(exit.retval);
            return exit.retval;
        }
        catch (InterruptedException | ExecutionException e) {
            return Value.NULL;
        }
    }

    public boolean isFinished() {
        return this.taskFuture.isDone();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ThreadValue))
            return false;
        return ((ThreadValue)o).id == this.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.id);
    }

    @Override
    public String getTypeString() {
        return "task";
    }
}
