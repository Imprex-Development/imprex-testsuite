package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.command.suggestion.CommandSuggestion;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.util.Chat;

public class CommandStartSetup {

	private final ServerManager serverManager;
	private final CommandSuggestion suggestion;

	public CommandStartSetup(TestsuitePlugin plugin) {
		this.serverManager = plugin.getServerManager();
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("startsetup").then(
				argument("name", StringArgumentType.greedyString())
				.suggests(this.suggestion.server()
						.hasStatus(UtilizationState.OFFLINE)
						.buildSuggest("name"))
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
				return;
			}

			Chat.builder(server).append("Server was setup.").send(context);
			this.startServer(context, server);
		});
		
		return Command.SINGLE_SUCCESS;
	}

	public void startServer(CommandContext<TestsuiteSender> context, ServerInstance server) {
		if (server.getStatus() != UtilizationState.OFFLINE) {
			Chat.builder(server).append("Server is not offline!").send(context);
			return;
		}

		Chat.builder(server).append("Requesting start...").send(context);
		server.start().whenComplete((___, error) -> {
			if (error != null) {
				Chat.builder(server).append("Server is unable to start! {0}", error.getMessage()).send(context);
			} else {
				Chat.builder(server).append("Starting server").send(context);
			}
		});
	}
}