package com.clientScript.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.clientScript.language.Context;
import com.clientScript.language.Expression;
import com.clientScript.language.Fluff;
import com.clientScript.language.Tokenizer;
import com.clientScript.value.FunctionValue;

public class ExpressionException extends RuntimeException {
    public final Context context;
    public final Tokenizer.Token token;
    public final List<FunctionValue> stack = new ArrayList<>();
    //private final Supplier<String> lazyMessage;  // TODO remove comment

    public ExpressionException(Context c, Expression e, String message) {
        this(c, e, Tokenizer.Token.NONE, message);
    }
    
    public ExpressionException(Context c, Expression e, Tokenizer.Token t, String message) {
        this(c, e, t, message, Collections.emptyList());
    }

    public ExpressionException(Context c, Expression e, Tokenizer.Token t, String message, List<FunctionValue> stack) {
        super(message);
        this.stack.addAll(stack);
        //this.lazyMessage = () -> makeMessage(c, e, t, message);  // TODO remove comment
        this.token = t;
        this.context = c;
    }

    public ExpressionException(Context c, Expression e, Tokenizer.Token t, Supplier<String> messageSupplier, List<FunctionValue> stack) {
        super(messageSupplier.get());
        this.stack.addAll(stack);
        //this.lazyMessage = () -> makeMessage(c, e, t, messageSupplier.get());  // TODO remove comment
        this.token = t;
        this.context = c;
    }

    private static final Fluff.TriFunction<Expression, Tokenizer.Token, String, List<String>> errorMaker = (expr, token, errmessage) -> {

        List<String> errMsg = new ArrayList<>();
        //errmessage += expr.getModuleName() == null ? "" : " in " + expr.getModuleName();
        if (token != null) {
            List<String> snippet = expr.getExpressionSnippet(token);
            errMsg.addAll(snippet);

            if (snippet.size() != 1)
                errmessage += " at line " + (token.lineno + 1) + ", pos " + (token.linepos + 1);
            else
                errmessage += " at pos " + (token.pos + 1);
        }
        errMsg.add(errmessage);
        return errMsg;
    };

    synchronized static String makeMessage(Context c, Expression e, Tokenizer.Token t, String message) throws ExpressionException {
        if (c.getErrorSnooper() != null) {
            List<String> alternative = c.getErrorSnooper().apply(e, t, message);
            if (alternative!= null)
            {
                return String.join("\n", alternative);
            }
        }
        return String.join("\n", ExpressionException.errorMaker.apply(e, t, message));
    }
}
