package dev.imprex.testsuite.command;

import static dev.imprex.testsuite.util.ArgumentBuilder.argument;
import static dev.imprex.testsuite.util.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.TestsuiteSuggestion;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.util.Chat;
import net.md_5.bungee.api.CommandSender;

public class CommandTestsuiteStart {

	private final ServerManager serverManager;
	private final TestsuiteSuggestion suggestion;

	public CommandTestsuiteStart(TestsuitePlugin plugin) {
		this.serverManager = plugin.getServerManager();
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<CommandSender> create() {
		return literal("start").then(
				argument("name", StringArgumentType.greedyString())
				.suggests(this::suggestServers)
				.executes(this::startServer));
	}

	public int startServer(CommandContext<CommandSender> context) {
		String serverName = context.getArgument("name", String.class);
		ServerInstance server = this.serverManager.getServer(serverName);
		if (server == null) {
			Chat.send(context, "Server was not found!");
			return Command.SINGLE_SUCCESS;
		}

		if (server.getStatus() != UtilizationState.OFFLINE) {
			Chat.send(context, "Server {0} is not offline!", server.getName());
			return Command.SINGLE_SUCCESS;
		}

		Chat.send(context, "Starting server {0}...", server.getName());
		server.start().whenComplete((__, error) -> {
			if (error != null) {
				Chat.send(context, "Server {0} is unable to start! {1}", server.getName(), error.getMessage());
			} else {
				Chat.send(context, "Server {0} started", server.getName());
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	public CompletableFuture<Suggestions> suggestServers(CommandContext<CommandSender> context, SuggestionsBuilder builder) {
		return this.suggestion.suggestServers(context, builder, server -> server.getStatus() == UtilizationState.OFFLINE);
	}
}