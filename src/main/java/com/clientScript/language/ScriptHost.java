package com.clientScript.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Predicate;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.fabricmc.loader.api.FabricLoader;

import com.clientScript.exception.ExpressionException;
import com.clientScript.exception.IntegrityException;
import com.clientScript.exception.InternalExpressionException;
import com.clientScript.value.FunctionValue;
import com.clientScript.value.Value;

public abstract class ScriptHost {
    public static Map<Value, Value> systemGlobals = new ConcurrentHashMap<>();
    private static final Map<Long, Random> randomizers = new Long2ObjectOpenHashMap<>();
    private final Map<Value, ThreadPoolExecutor> executorServices = new HashMap<>();
    private final Map<Value, Object> locks = new ConcurrentHashMap<>();
    
    public static Thread mainThread = null;
    protected boolean inTermination = false;
    public ErrorSnooper errorSnooper = null;
    private Map<Module,ModuleData> moduleData = new HashMap<>();
    private Map<String,Module> modules = new HashMap<>();
    public final Module main;

    protected ScriptHost(Module code/*, boolean perUser, ScriptHost parent*/) {
        //this.parent = parent;  // TODO remove comments?
        this.main = code;
        //this.perUser = perUser;
        //this.user = null;
        ModuleData moduleData = new ModuleData(code);
        this.moduleData.put(code, moduleData);
        this.modules.put(code == null ? null : code.getName(), code);
        ScriptHost.mainThread = Thread.currentThread();
    }

    public Random getRandom(long aLong) {
        if (ScriptHost.randomizers.size() > 65536)
            ScriptHost.randomizers.clear();
        return ScriptHost.randomizers.computeIfAbsent(aLong, Random::new);
    }

    public boolean resetRandom(long aLong) {
        return ScriptHost.randomizers.remove(aLong) != null;
    }

    private ModuleData getModuleData(Module module) {
        ModuleData data = this.moduleData.get(module);
        if (data == null)
            throw new IntegrityException("Module structure changed for the app. Did you reload the app with tasks running?");
        return data;
    }

    public LazyValue getGlobalVariable(String name) {
        return getGlobalVariable(this.main, name);
    }

    public LazyValue getGlobalVariable(Module module, String name) {
        ModuleData local = getModuleData(module);
        LazyValue ret = local.globalVariables.get(name); // most uses would be from local scope anyways
        if (ret != null)
            return ret;
        ModuleData target = local.globalsImports.get(name);
        if (target != null) {
            ret = target.globalVariables.get(name);
            if (ret != null)
                return ret;
        }
        // not in local scope - will need to travel over import links
        target = local.futureImports.get(name);
        if (target == null)
            return null;
        target = findModuleDataFromGlobalImports(name, target, 0);
        if (target == null)
            return null;
        local.futureImports.remove(name);
        local.globalsImports.put(name, target);
        return target.globalVariables.get(name);
    }

    public void setGlobalVariable(Module module, String name, LazyValue lv) {
        getModuleData(module).globalVariables.put(name, lv);
    }

    public void delFunction(Module module, String funName) {
        ModuleData data = getModuleData(module);
        data.globalFunctions.remove(funName);
        data.functionImports.remove(funName);
    }

    public void delFunctionWithPrefix(Module module, String prefix) {
        ModuleData data = getModuleData(module);
        data.globalFunctions.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        data.functionImports.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    public void delGlobalVariable(Module module, String varName) {
        ModuleData data = getModuleData(module);
        data.globalFunctions.remove(varName);
        data.functionImports.remove(varName);
    }

    public void delGlobalVariableWithPrefix(Module module, String prefix) {
        ModuleData data = getModuleData(module);
        data.globalVariables.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        data.globalsImports.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    public void importModule(String moduleName, boolean should_run_in_global, boolean should_run) {
        if (this.modules.containsKey(moduleName.toLowerCase(Locale.ROOT)))
            return;  // aready imported
        Module module = getModuleOrLibraryByName(moduleName);
        if (this.modules.containsKey(module.getName()))
            return;  // aready imported, once again, in case some discrepancies in names?
        this.modules.put(module.getName(), module);
        ModuleData data = new ModuleData(module);
        this.moduleData.put(module, data);
        if (should_run)
            runModuleCode(module, should_run_in_global);
        //moduleData.remove(module); // we are pooped already, but doesn't hurt to clean that up.
        //modules.remove(module.getName());
        //throw new InternalExpressionException("Failed to import a module "+moduleName);
    }

    protected abstract Module getModuleOrLibraryByName(String name);  // this should be shell out in the executor
    protected abstract void runModuleCode(Module module, boolean should_run_in_global);  // this should be shell out in the executor

    public FunctionValue getAssertFunction(Module module, String name) {
        FunctionValue ret = getFunction(module, name);
        if (ret == null) {
            if (module == this.main)
                throw new InternalExpressionException("Function '" + name + "' is not defined yet");
            else
                throw new InternalExpressionException("Function '" + name + "' is not defined nor visible by its name in the imported module '" + module.getName() + "'");
        }
        return ret;
    }

    public FunctionValue getFunction(String name) {
        return getFunction(this.main, name);
    }

    private FunctionValue getFunction(Module module, String name) {
        ModuleData local = getModuleData(module);
        FunctionValue ret = local.globalFunctions.get(name); // most uses would be from local scope anyways
        if (ret != null)
            return ret;
        ModuleData target = local.functionImports.get(name);
        if (target != null) {
            ret = target.globalFunctions.get(name);
            if (ret != null)
                return ret;
        }
        // not in local scope - will need to travel over import links
        target = local.futureImports.get(name);
        if (target == null)
            return null;
        target = findModuleDataFromFunctionImports(name, target, 0);
        if (target == null)
            return null;
        local.futureImports.remove(name);
        local.functionImports.put(name, target);
        return target.globalFunctions.get(name);
    }

    public void addUserDefinedFunction(Context ctx, Module module, String name, FunctionValue fun) {
        getModuleData(module).globalFunctions.put(name, fun);
    }

    private ModuleData findModuleDataFromFunctionImports(String name, ModuleData source, int ttl) {
        if (ttl > 64)
            throw new InternalExpressionException("Cannot import " + name + ", either your imports are too deep or too loopy");
        if (source.globalFunctions.containsKey(name))
            return source;
        if (source.functionImports.containsKey(name))
            return findModuleDataFromFunctionImports(name, source.functionImports.get(name), ttl + 1);
        if (source.futureImports.containsKey(name))
            return findModuleDataFromFunctionImports(name, source.futureImports.get(name), ttl + 1);
        return null;
    }

    private ModuleData findModuleDataFromGlobalImports(String name, ModuleData source, int ttl) {
        if (ttl > 64)
            throw new InternalExpressionException("Cannot import " + name + ", either your imports are too deep or too loopy");
        if (source.globalVariables.containsKey(name))
            return source;
        if (source.globalsImports.containsKey(name))
            return findModuleDataFromGlobalImports(name, source.globalsImports.get(name), ttl + 1);
        if (source.futureImports.containsKey(name))
            return findModuleDataFromGlobalImports(name, source.futureImports.get(name), ttl + 1);
        return null;
    }

    public void importNames(Context c, Module targetModule, String sourceModuleName, List<String> identifiers ) {
        if (!this.moduleData.containsKey(targetModule))
            throw new InternalExpressionException("Cannot import to module that doesn't exist");
        Module source = this.modules.get(sourceModuleName);
        ModuleData sourceData = this.moduleData.get(source);
        ModuleData targetData = this.moduleData.get(targetModule);
        if (sourceData == null || targetData == null)
            throw new InternalExpressionException("Cannot import from module that is not imported");
        for (String identifier: identifiers) {
            if (sourceData.globalFunctions.containsKey(identifier))
                targetData.functionImports.put(identifier, sourceData);
            else if (sourceData.globalVariables.containsKey(identifier))
                targetData.globalsImports.put(identifier, sourceData);
            else
                targetData.futureImports.put(identifier, sourceData);
        }
    }

    public Stream<String> availableImports(String moduleName)
    {
        Module source = modules.get(moduleName);
        ModuleData sourceData = moduleData.get(source);
        if (sourceData == null)
            throw new InternalExpressionException("Cannot import from module that is not imported");
        return Stream.concat(
            globalVariableNames(source, s -> s.startsWith("global_")),
            globalFunctionNames(source, s -> true)
        ).distinct().sorted();
    }

    public Stream<String> globalVariableNames(Module module, Predicate<String> predicate) {
        return Stream.concat(Stream.concat(
            getModuleData(module).globalVariables.keySet().stream(),
            getModuleData(module).globalsImports.keySet().stream()
        ), getModuleData(module).futureImports.keySet().stream().filter(s -> s.startsWith("global_"))).filter(predicate);
    }

    public Stream<String> globalFunctionNames(Module module, Predicate<String> predicate) {
        return Stream.concat(Stream.concat(
            getModuleData(module).globalFunctions.keySet().stream(),
            getModuleData(module).functionImports.keySet().stream()
        ),getModuleData(module).futureImports.keySet().stream().filter(s -> !s.startsWith("global_"))).filter(predicate);
    }

    public ThreadPoolExecutor getExecutor(Value pool) {
        if (this.inTermination)
            return null;
        return this.executorServices.computeIfAbsent(pool, v -> (ThreadPoolExecutor)Executors.newCachedThreadPool());
    }

    synchronized public void handleExpressionException(String msg, ExpressionException exc) {
        System.out.println(msg + ": " + exc);
    }

    public int taskCount() {
        return this.executorServices.values().stream().map(ThreadPoolExecutor::getActiveCount).reduce(0, Integer::sum);
    }

    public int taskCount(Value pool) {
        if (this.executorServices.containsKey(pool))
            return this.executorServices.get(pool).getActiveCount();
        return 0;
    }

    public Object getLock(Value name) {
        return this.locks.computeIfAbsent(name, n -> new Object());
    }

    public Module getModule(String name) {
        try {
            Path globalFolder = FabricLoader.getInstance().getConfigDir().resolve("cscript");
            if (!Files.exists(globalFolder)) 
                Files.createDirectories(globalFolder);
            Optional<Path> scriptPath = Files.walk(globalFolder)
                .filter(script -> script.getFileName().toString().equalsIgnoreCase(name + ".sc") ||
                script.getFileName().toString().equalsIgnoreCase(name + ".scl"))
                .findFirst();
            if (scriptPath.isPresent())
                return new Module(scriptPath.get());
        }
        catch (IOException e) {
            //CarpetSettings.LOG.error("Exception while loading the app: ", e);
        }

        return null;
    }

    public void reload(Module load, Module main) {
        if (this.modules.containsKey("load")) {
            this.moduleData.remove(this.modules.get("load"));
            this.modules.remove("load");
            
        }
        if (this.modules.containsKey("main")) {
            this.moduleData.remove(this.modules.get("main"));
            this.modules.remove("main");
        }
        if (load != null && load.getCode() != null) {
            this.modules.put(load.getName(), load);
            this.moduleData.put(load, new ModuleData(load));
            try {
                runModuleCode(load, true);
            } catch (Exception e) {}
        }
        if (main != null && main.getCode() != null) {
            this.modules.put(main.getName(), main);
            this.moduleData.put(main, new ModuleData(main));
        }
    }

    @FunctionalInterface
    public interface ErrorSnooper {
        List<String> apply(Expression expression, Tokenizer.Token token, String message);
    }

    public static class ModuleData {
        Module parent;
        public Map<String, FunctionValue> globalFunctions = new Object2ObjectOpenHashMap<>();
        public Map<String, LazyValue> globalVariables = new Object2ObjectOpenHashMap<>();
        public Map<String, ModuleData> functionImports = new Object2ObjectOpenHashMap<>(); // imported functions string to module
        public Map<String, ModuleData> globalsImports = new Object2ObjectOpenHashMap<>(); // imported global variables string to module
        public Map<String, ModuleData> futureImports = new Object2ObjectOpenHashMap<>(); // imports not known before used

        public ModuleData(Module parent) {
            this.parent = parent;
        }
    }

    public static class ClientScriptHost extends ScriptHost {
        public static ClientScriptHost globalHost = new ClientScriptHost(null);
        private Module loadModule, mainModule;

        public ClientScriptHost(Module code) {
            super(code);
            this.loadModule = getModule("load");
            this.mainModule = getModule("main");
        }

        @Override
        protected Module getModuleOrLibraryByName(String name) {
            Module module = getModule(name);
            if (module == null || module.getCode() == null)
                throw new InternalExpressionException("Unable to locate package: " + name);
            return module;
        }

        @Override
        protected void runModuleCode(Module module, boolean should_run_in_global) {
            Expression ex = new Expression(module.getCode(), Expression.source);
            ex.asATextSource();
            ex.asModule(should_run_in_global? null : module);
            ex.eval(new Context(this));
        }

        public void load() {
            try {
                if (this.loadModule != null && this.loadModule.getCode() != null)
                    importModule("load", true, true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (this.mainModule != null && this.mainModule.getCode() != null)
                    importModule("main", true, false);
            } catch (Exception e) {}
        }

        public void tick() {
            try {
                if (this.mainModule != null && this.mainModule.getCode() != null)
                    runModuleCode(this.mainModule, true);
            } catch (Exception e) {}
        }

        public void reload() {
            this.loadModule = getModule("load");
            this.mainModule = getModule("main");
            super.reload(this.loadModule, this.mainModule);
        }
    }
}
