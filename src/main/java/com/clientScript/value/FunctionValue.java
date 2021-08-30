package com.clientScript.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.clientScript.exception.BreakStatement;
import com.clientScript.exception.ContinueStatement;
import com.clientScript.exception.ExpressionException;
import com.clientScript.exception.InternalExpressionException;
import com.clientScript.exception.ReturnStatement;
import com.clientScript.language.Context;
import com.clientScript.language.Expression;
import com.clientScript.language.Fluff;
import com.clientScript.language.LazyValue;
import com.clientScript.language.Module;
import com.clientScript.language.Tokenizer;

public class FunctionValue extends Value implements Fluff.ILazyFunction {
    private final Expression expression;
    private final Tokenizer.Token token;
    private final String name;
    private final LazyValue body;
    private Map<String, LazyValue> outerState;
    private final List<String> args;
    private final String varArgs;
    private static long variantCounter = 1;
    private long variant;

    public FunctionValue(Expression expression, Tokenizer.Token token, String name, LazyValue body, List<String> args, String varArgs, Map<String, LazyValue> outerState) {
        this.expression = expression;
        this.token = token;
        this.name = name;
        this.body = body;
        this.args = args;
        this.varArgs = varArgs;
        this.outerState = outerState;
        this.variant = FunctionValue.variantCounter++;
    }

    private FunctionValue(Expression expression, Tokenizer.Token token, String name, LazyValue body, List<String> args, String varArgs) {
        this.expression = expression;
        this.token = token;
        this.name = name;
        this.body = body;
        this.args = args;
        this.varArgs = varArgs;
        this.outerState = null;
        this.variant = 0L;
    }

    public Module getModule() {
        return this.expression.module;
    }
    
    public Tokenizer.Token getToken() {
        return this.token;
    }

    public Expression getExpression() {
        return this.expression;
    }

    @Override
    public String getString() {
        return this.name;
    }

    @Override
    public boolean getBoolean() {
        return true;
    }

    public List<String> getArguments() {
        return this.args;
    }

    public String getVarArgs() {
        return this.varArgs;
    }

    @Override
    public String getPrettyString() {
        List<String> stringArgs = new ArrayList<>(this.args);
        if (this.outerState != null)
            stringArgs.addAll(this.outerState.entrySet().stream().map(e -> "outer(" + e.getKey() + ") = " + e.getValue().evalValue(null).getPrettyString()).collect(Collectors.toList()));
        return (this.name.equals("_") ? "<lambda>" : this.name) + "(" + String.join(", ", stringArgs) + ")";
    }

    @Override
    public int getNumParams() {
        return this.args.size();
    }

    public String fullName() {
        return (this.name.equals("_") ? "<lambda>" : this.name) + (this.expression.module == null ? "" : "[" + this.expression.module.getName() + "]");
    }

    @Override
    public boolean numParamsVaries() {
        return this.varArgs != null;
    }

    @Override
    protected Value clone() {
        FunctionValue ret = new FunctionValue(this.expression, this.token, this.name, this.body, this.args, this.varArgs);
        ret.outerState = this.outerState;
        ret.variant = this.variant;
        return ret;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode() + (int)variant;
    }

    @Override
    public int compareTo(final Value o) {
        if (o instanceof FunctionValue) {
            int nameSame = this.name.compareTo(((FunctionValue)o).name);
            if (nameSame != 0)
                return nameSame;
            return (int)(this.variant - ((FunctionValue)o).variant);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public double readDoubleNumber() {
        return getNumParams();
    }

    @Override
    public String getTypeString() {
        return "function";
    }

    @Override
    public Value slice(long from, Long to) {
        throw new InternalExpressionException("Cannot slice a function");
    }

    @Override
    public LazyValue lazyEval(Context c, Context.Type type, Expression e, Tokenizer.Token t, List<LazyValue> lazyParams) {
        List<Value> resolvedParams = unpackArgs(lazyParams, c);
        return execute(c, type, e, t, resolvedParams);
    }

    public void checkArgs(int candidates) {
        int actual = getArguments().size();
        if (candidates < actual)
            throw new InternalExpressionException("Function " + getPrettyString() + " requires at least " + actual + " arguments");
        if (candidates > actual && getVarArgs() == null)
            throw new InternalExpressionException("Function " + getPrettyString() + " requires " + actual + " arguments");
    }

    public static List<Value> unpackArgs(List<LazyValue> lazyParams, Context c) {
        // TODO we shoudn't need that if all fuctions are not lazy really
        List<Value> params = new ArrayList<>();
        for (LazyValue lv : lazyParams) {
            Value param = lv.evalValue(c, Context.NONE);
            if (param instanceof FunctionUnpackedArgumentsValue) {
                //CarpetSettings.LOG.error("How did we get here?");  // TODO remove comment
                params.addAll(((ListValue) param).getItems());
            }
            else
                params.add(param);
        }
        return params;
    }

    public LazyValue execute(Context c, Context.Type type, Expression e, Tokenizer.Token t, List<Value> params) {
        assertArgsOk(params, fixedArgs -> {
            if (fixedArgs)  // wrong number of args for fixed args
                throw new ExpressionException(c, e, t, "Incorrect number of arguments for function " + this.name + ". Should be " + this.args.size() + ", not " + params.size() + " like " + this.args);
            else {  // too few args for varargs
                List<String> argList = new ArrayList<>(this.args);
                argList.add("... " + this.varArgs);
                throw new ExpressionException(c, e, t, "Incorrect number of arguments for function " + this.name + ". Should be at least " + this.args.size() + ", not " + params.size() + " like " + argList);
            }
        });
        Context newFrame = c.recreate();

        if (outerState != null)
            outerState.forEach(newFrame::setVariable);
        for (int i=0; i<this.args.size(); i++) {
            String arg = this.args.get(i);
            Value val = params.get(i).reboundedTo(arg); // TODO check if we need to copy that
            newFrame.setVariable(arg, (cc, tt) -> val);
        }
        if (this.varArgs != null) {
            List<Value> extraParams = new ArrayList<>();
            for (int i = this.args.size(), mx = params.size(); i < mx; i++)
                extraParams.add(params.get(i).reboundedTo(null)); // copy by value I guess
            Value rest = ListValue.wrap(extraParams).bindTo(this.varArgs); // didn't we just copied that?
            newFrame.setVariable(this.varArgs, (cc, tt) -> rest);
        }
        Value retVal;
        try {
            retVal = this.body.evalValue(newFrame, type); // TODO not sure if we need to propagete type / consider boolean context in defined functions - answer seems yes
        }
        catch (BreakStatement | ContinueStatement exc) {
            throw new ExpressionException(c, e, t, "'continue' and 'break' can only be called inside loop function bodies");
        }
        catch (ReturnStatement returnStatement) {
            retVal = returnStatement.retval;
        }
        Value otherRetVal = retVal;
        return (cc, tt) -> otherRetVal;
    }

    public void assertArgsOk(List<?> list, Consumer<Boolean> feedback) {
        int size = list.size();
        if (this.varArgs == null && this.args.size() != size) // wrong number of args for fixed args
            feedback.accept(true);
        else if (this.varArgs != null && this.args.size() > size) // too few args for varargs
            feedback.accept(false);
    }

    public LazyValue callInContext(Context c, Context.Type type, List<Value> params) {
        try {
            return execute(c, type, this.expression, this.token, params);
        }
        catch (ExpressionException exc) {
            exc.stack.add(this);
            throw exc;
        }
        catch (InternalExpressionException exc) {
            exc.stack.add(this);
            throw new ExpressionException(c, this.expression, this.token, exc.getMessage(), exc.stack);
        }
        catch (ArithmeticException exc) {
            throw new ExpressionException(c, this.expression, this.token, "Your math is wrong, " + exc.getMessage(), Collections.singletonList(this));
        }
    }

    @Override
    public boolean pure() {
        return false;
    }

    @Override
    public boolean transitive() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof FunctionValue)
            return this.name.equals(((FunctionValue)o).name) && this.variant == ((FunctionValue)o).variant;
        return false;
    }
}
