package com.clientScript.language;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.clientScript.exception.InternalExpressionException;
import com.clientScript.value.Value;

public class Context {
    public enum Type {
        NONE, VOID, BOOLEAN, NUMBER, STRING, LIST, ITERATOR, SIGNATURE, LOCALIZATION, LVALUE, MAPDEF
    }
    public static final Type NONE = Type.NONE;
    public static final Type VOID = Type.VOID;
    public static final Type BOOLEAN = Type.BOOLEAN;
    public static final Type NUMBER = Type.NUMBER;
    public static final Type STRING = Type.STRING;
    public static final Type LIST = Type.LIST;
    public static final Type ITERATOR = Type.ITERATOR;
    public static final Type SIGNATURE = Type.SIGNATURE;
    public static final Type LOCALIZATION = Type.LOCALIZATION;
    public static final Type LVALUE = Type.LVALUE;
    public static final Type MAPDEF = Type.MAPDEF;

    public Map<String, LazyValue> variables = new HashMap<>();
    public final ScriptHost host;

    public Context(ScriptHost host) {
        this.host = host;
    }
    
    public LazyValue getVariable(String name) {
        return this.variables.get(name);
    }

    public Set<String> getAllVariableNames() {
        return variables.keySet();
    }

    public void setVariable(String name, LazyValue lv) {
        this.variables.put(name, lv);
    }

    public void delVariable(String variable) {
        this.variables.remove(variable);
    }

    public void removeVariablesMatching(String varname) {
        this.variables.entrySet().removeIf(e -> e.getKey().startsWith(varname));
    }

    public Context recreate() {
        Context ctx = duplicate();
        ctx.initialize();
        return ctx;
    }

    public Context duplicate() {
        return new Context(this.host);
    }

    protected void initialize() {
        // special variables for second order functions so we don't need to check them all the time
        this.variables.put("_", (c, t) -> Value.ZERO);
        this.variables.put("_i", (c, t) -> Value.ZERO);
        this.variables.put("_a", (c, t) -> Value.ZERO);
    }

    public ScriptHost.ErrorSnooper getErrorSnooper() {
        return this.host.errorSnooper;
    }

    /**
     * immutable context only for reason on reporting access violations in evaluating expressions in optimizization
     * mode detecting any potential violations that may happen on the way
     */
    public static class ContextForErrorReporting extends Context {
        //public ScriptHost.ErrorSnooper optmizerEerrorSnooper;  // TODO remove comment

        public ContextForErrorReporting(Context parent) {
            super(null);
            //this.optmizerEerrorSnooper =  parent.host.errorSnooper;
        }

        public void badProgrammer()
        {
            throw new InternalExpressionException("Attempting to access the execution context while optimizing the code;" +
                    " This is not the problem with your code, but the error cause by improper use of code compile optimizations" +
                    "of scarpet authors. Please report this issue directly to the scarpet issue tracker");
        }

        @Override
        public LazyValue getVariable(String name) {
            badProgrammer();
            return null;
        }

        @Override
        public Set<String> getAllVariableNames() {
            badProgrammer();
            return null;
        }

        @Override
        public void setVariable(String name, LazyValue lv) {
            badProgrammer();
        }

        @Override
        public void delVariable(String variable) {
            badProgrammer();
        }

        @Override
        public void removeVariablesMatching(String varname) {
            badProgrammer();
        }

        @Override
        public Context recreate() {
            badProgrammer();
            return null;
        }

        @Override
        public Context duplicate() {
            badProgrammer();
            return null;
        }

        @Override
        protected void initialize() {
            badProgrammer();
        }
    }
}
