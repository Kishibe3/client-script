package com.clientScript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.LiteralText;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.clientScript.language.Context;
import com.clientScript.language.Expression;
import com.clientScript.language.LazyValue;
import com.clientScript.language.Tokenizer;
import com.clientScript.language.ScriptHost.ClientScriptHost;
import com.clientScript.value.FunctionValue;
import com.clientScript.value.Value;

public class Main implements ClientModInitializer {
	
	@Override
	public void onInitializeClient() {
		try {
			Path globalFolder = FabricLoader.getInstance().getConfigDir().resolve("cscript");
			if (!Files.exists(globalFolder)) 
				Files.createDirectories(globalFolder);
		} catch (IOException e) {}

		ClientCommandManager.DISPATCHER.register(
			ClientCommandManager.literal("cscript")
			.then(ClientCommandManager.literal("run")
				.then(ClientCommandManager.argument("expr", StringArgumentType.greedyString())
					.executes(c -> compute(c, StringArgumentType.getString(c, "expr")))
				)
			)
			.then(ClientCommandManager.literal("globals")
				.executes(c -> listGlobals(c))
			)
			.then(ClientCommandManager.literal("load")
				.then(ClientCommandManager.argument("app", StringArgumentType.word())
					.executes(c -> loadScript(c, StringArgumentType.getString(c, "app")))
				)
			)
			.then(ClientCommandManager.literal("reload")
				.executes(c -> {
					ClientScriptHost.globalHost.reload();
					return 1;
				})
			)
		);
	}

	private static int compute(CommandContext<FabricClientCommandSource> context, String expr) throws CommandSyntaxException {
		FabricClientCommandSource source = context.getSource();
		Expression ex = new Expression(expr, source);
		//ex.asModule(null);
		Value result =  ex.eval(new Context(ClientScriptHost.globalHost));
		if (source != null)
			source.sendFeedback(new LiteralText(String.format("= %s", result.getString())));
		return (int)result.readInteger();
	}

	private static int listGlobals(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		FabricClientCommandSource source = context.getSource();
		if (source == null)
			return 0;
		ClientScriptHost host = ClientScriptHost.globalHost;
		source.sendFeedback(new LiteralText("Stored functions:"));
		host.globalFunctionNames(host.main, str -> !str.startsWith("__")).sorted().forEach(s -> {
			FunctionValue fun = host.getFunction(s);
			if (fun == null) {
				source.sendFeedback(new LiteralText(s + " - unused import"));
				return;
			}
			Expression expr = fun.getExpression();
			Tokenizer.Token tok = fun.getToken();
			List<String> snippet = expr.getExpressionSnippet(tok);
			source.sendFeedback(new LiteralText(fun.fullName() + " defined at: line " + (tok.lineno + 1) + " pos " + (tok.linepos + 1)));
			for (String snippetLine : snippet)
				source.sendFeedback(new LiteralText(snippetLine));
			source.sendFeedback(new LiteralText("----------------"));
		});
		source.sendFeedback(new LiteralText("Global variables:"));
		host.globalVariableNames(host.main, s -> s.startsWith("global_")).sorted().forEach(s -> {
			LazyValue variable = host.getGlobalVariable(s);
			if (variable == null)
				source.sendFeedback(new LiteralText(s + " - unused import"));
			else
				source.sendFeedback(new LiteralText(s + ": " + variable.evalValue(null).getPrettyString()));
		});
		return 1;
	}

	private int loadScript(CommandContext<FabricClientCommandSource> context, String moduleName) throws CommandSyntaxException {
		ClientScriptHost host = ClientScriptHost.globalHost;
		Expression.source = context.getSource();
		host.importModule(moduleName, true, true);
		return 1;
	}
}
