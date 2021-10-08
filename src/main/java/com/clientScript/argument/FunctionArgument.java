package com.clientScript.argument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.language.Context;
import com.clientScript.language.Module;
import com.clientScript.value.FunctionValue;
import com.clientScript.value.Value;

public class FunctionArgument extends Argument {
    public FunctionValue function;
    public List<Value> args;

    private FunctionArgument(FunctionValue function, int offset, List<Value> args) {
        super(offset);
        this.function = function;
        this.args = args;
    }

    /**
     * @param c context
     * @param module module
     * @param params list of params
     * @param offset offset where to start looking for functional argument
     * @param allowNone none indicates no function present, otherwise it will croak
     * @param checkArgs whether the caller expects trailing parameters to fully resolve function argument list
     *                  if not - argument count check will not be performed and its up to the caller to verify
     *                  if the number of supplied arguments is right
     * @return argument data
     */
    public static FunctionArgument findIn(Context c, Module module, List<Value> params, int offset, boolean allowNone, boolean checkArgs) {
        Value functionValue = params.get(offset);
        if (functionValue.isNull()) {
            if (allowNone)
                return new FunctionArgument(null, offset + 1, Collections.emptyList());
            throw new InternalExpressionException("function argument cannot be null");
        }
        if (!(functionValue instanceof FunctionValue)) {
            String name = functionValue.getString();
            functionValue = c.host.getAssertFunction(module, name);
        }
        FunctionValue fun = (FunctionValue)functionValue;
        int argsize = fun.getArguments().size();
        if (checkArgs) {
            int extraargs = params.size() - argsize - offset - 1;
            if (extraargs < 0)
                throw new InternalExpressionException("Function " + fun.getPrettyString() + " requires at least " + fun.getArguments().size() + " arguments");
            if (extraargs > 0 && fun.getVarArgs() == null)
                throw new InternalExpressionException("Function " + fun.getPrettyString() + " requires " + fun.getArguments().size() + " arguments");
        }
        List<Value> lvargs = new ArrayList<>();
        for (int i = offset+1, mx = params.size(); i < mx; i++)
            lvargs.add(params.get(i));
        return new FunctionArgument(fun, offset + 1 + argsize, lvargs);
    }

    public List<Value> checkedArgs() {
        this.function.checkArgs(this.args.size());
        return this.args;
    }
}
