package dev.imprex.testsuite.command;

import static dev.imprex.testsuite.util.ArgumentBuilder.argument;
import static dev.imprex.testsuite.util.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.util.Chat;
import net.md_5.bungee.api.CommandSender;

public class CommandTestsuiteDelete {

	private final ServerManager serverManager;

	public CommandTestsuiteDelete(TestsuitePlugin plugin) {
		this.serverManager = plugin.getServerManager();
	}

	public LiteralArgumentBuilder<CommandSender> create() {
		return literal("delete").then(
				argument("name", StringArgumentType.greedyString())
				.suggests(this::suggestServers)
				.executes(this::deleteServer));
	}

	public int deleteServer(CommandContext<CommandSender> context) {
		String serverName = context.getArgument("name", String.class);
		ServerInstance server = this.serverManager.getServer(serverName);
		if (server == null) {
			Chat.send(context, "Server was not found!");
			return Command.SINGLE_SUCCESS;
		}

		Chat.send(context, "Deleting server {0}...", server.getName());
		server.delete().whenComplete((__, error) -> {
			if (error != null) {
				error.printStackTrace();
				Chat.send(context, "Server {0} is unable to delete! {1}", server.getName(), error.getMessage());
			} else {
				Chat.send(context, "Server {0} deleted.", server.getName());
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	public CompletableFuture<Suggestions> suggestServers(CommandContext<CommandSender> context, SuggestionsBuilder builder) {
		String input = builder.getRemaining().toLowerCase();
		this.serverManager.getServers().stream()
			.map(server -> server.getName())
			.filter(name -> name.toLowerCase().contains(input))
			.forEach(builder::suggest);
		return builder.buildFuture();
	}
}