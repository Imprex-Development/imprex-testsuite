package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.command.suggestion.CommandSuggestion;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.template.ServerTemplateList;
import dev.imprex.testsuite.util.Chat;

public class CommandSetup {

	private final ServerManager serverManager;
	private final ServerTemplateList templateList;
	private final CommandSuggestion suggestion;

	public CommandSetup(TestsuitePlugin plugin) {
		this.serverManager = plugin.getServerManager();
		this.templateList = plugin.getTemplateList();
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("setup")
				.then(literal("template")
						.then(argument("template", StringArgumentType.greedyString())
							.suggests(this.suggestion.template().buildSuggest("template"))
							.executes(this::setupTemplate)))
				.then(argument("name", StringArgumentType.greedyString())
						.suggests(this.suggestion.server().buildSuggest("name"))
						.executes(this::setupServer));
	}

	public int setupServer(CommandContext<TestsuiteSender> context) {
		String serverName = context.getArgument("name", String.class);
		ServerInstance server = this.serverManager.getServer(serverName);
		if (server == null) {
			Chat.builder().append("Server was not found!").send(context);
			return Command.SINGLE_SUCCESS;
		}

		Chat.builder(server).append("Requesting setup...").send(context);
		server.setupServer().whenComplete((__, error) -> {
			if (error != null) {
				error.printStackTrace();
				Chat.builder(server).append("Server is unable to setup! {0}", error.getMessage()).send(context);
			} else {
				Chat.builder(server).append("Server was setup.").send(context);
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	public int setupTemplate(CommandContext<TestsuiteSender> context) {
		String templateName = context.getArgument("template", String.class);
		ServerTemplate template = this.templateList.getTemplate(templateName);
		if (template == null) {
			Chat.builder().append("Template was not found!").send(context);
			return Command.SINGLE_SUCCESS;
		}
		
		var list = this.serverManager.getServers().stream()
			.filter(ServerInstance::hasTemplate)
			.filter(server -> server.getTemplate().equals(template))
			.map(ServerInstance::setupServer)
			.toList();
		
		if (list.isEmpty()) {
			Chat.builder().append("This template has no server instances!").send(context);
			return Command.SINGLE_SUCCESS;
		}

		Chat.builder().append("Requesting setup for {0} ({1} servers) ...", template.getName(), list.size()).send(context);
		CompletableFuture.allOf(list.toArray(CompletableFuture[]::new)).whenComplete((__, error) -> {
			if (error != null) {
				error.printStackTrace();
				Chat.builder().append("A error occured inside setup! {0}", error.getMessage()).send(context);
			} else {
				Chat.builder().append("{0} Servers were setup.", list.size()).send(context);
			}
		});
		return Command.SINGLE_SUCCESS;
	}
}