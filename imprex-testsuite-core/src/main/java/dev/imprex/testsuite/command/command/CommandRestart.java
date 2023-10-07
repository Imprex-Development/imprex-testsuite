package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.util.ArgumentBuilder.argument;
import static dev.imprex.testsuite.util.ArgumentBuilder.literal;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.command.suggestion.CommandSuggestion;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.util.Chat;
import dev.imprex.testsuite.util.TestsuitePlayer;
import dev.imprex.testsuite.util.TestsuiteSender;
import dev.imprex.testsuite.util.TestsuiteServer;

public class CommandRestart {

	private final ServerManager serverManager;
	private final CommandSuggestion suggestion;

	public CommandRestart(TestsuitePlugin plugin) {
		this.serverManager = plugin.getServerManager();
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("restart")
				.executes(this::restartCurrentServer)
				.then(
					argument("name", StringArgumentType.greedyString())
					.suggests(this.suggestion.server()
							.hasStatus(UtilizationState.STARTING, UtilizationState.RUNNING)
							.buildSuggest("name"))
					.executes(this::restartTargetServer));
	}

	public int restartCurrentServer(CommandContext<TestsuiteSender> context) {
		if (context.getSource() instanceof TestsuitePlayer player) {
			TestsuiteServer serverConnection = player.getServer();
			if (serverConnection == null) {
				Chat.send(context, "Your currently not connected to any server!");
				return Command.SINGLE_SUCCESS;
			}

			String serverName = serverConnection.getName();
			ServerInstance server = this.serverManager.getServer(serverName);
			this.restartServer(context.getSource(), server);
		} else {
			Chat.send(context, "Server was not found!");
		}
		return Command.SINGLE_SUCCESS;
	}

	public int restartTargetServer(CommandContext<TestsuiteSender> context) {
		String serverName = context.getArgument("name", String.class);
		ServerInstance server = this.serverManager.getServer(serverName);
		this.restartServer(context.getSource(), server);
		return Command.SINGLE_SUCCESS;
	}

	public void restartServer(TestsuiteSender source, ServerInstance instance) {
		if (instance == null) {
			Chat.send(source, "Server was not found!");
			return;
		}

		Chat.send(source, "Restarting server {0}...", instance.getName());
		instance.restart().whenComplete((__, error) -> {
			if (error != null) {
				Chat.send(source, "Server {0} is unable to restart! {1}", instance.getName(), error.getMessage());
			} else {
				Chat.send(source, "Server {0} restarting", instance.getName());
			}
		});
	}
}