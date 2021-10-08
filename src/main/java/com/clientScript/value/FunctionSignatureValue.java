package com.clientScript.value;

import java.util.List;

public class FunctionSignatureValue extends FrameworkValue {
    private String identifier;
    private List<String> arguments;
    private String varArgs;
    private List<String> globals;

    public FunctionSignatureValue(String name, List<String> args, String varArgs, List<String> globals) {
        this.identifier = name;
        this.arguments = args;
        this.varArgs = varArgs;
        this.globals = globals;
    }
    
    public String getName() {
        return this.identifier;
    }

    public List<String> getArgs() {
        return this.arguments;
    }
    
    public String getVarArgs() {
        return this.varArgs;
    }
    
    public List<String> getGlobals() {
        return this.globals;
    }
}
