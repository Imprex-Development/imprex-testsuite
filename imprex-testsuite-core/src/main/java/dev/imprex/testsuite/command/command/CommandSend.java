package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.api.TestsuiteServer;
import dev.imprex.testsuite.command.ArgumentBuilder;
import dev.imprex.testsuite.command.suggestion.CommandSuggestion;
import dev.imprex.testsuite.util.Chat;

public class CommandSend {

	private final TestsuitePlugin plugin;
	private final CommandSuggestion suggestion;

	public CommandSend(TestsuitePlugin plugin) {
		this.plugin = plugin;
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("send")
				.then(
						argument("player", StringArgumentType.string())
						.suggests(this.suggestPlayers())
						.then(
								argument("server", StringArgumentType.greedyString())
								.suggests(this.suggestion.server()
										.filter(server -> server.getStatus() == UtilizationState.RUNNING)
										.buildSuggest("server"))
								.executes(this::sendPlayer)));
	}

	public SuggestionProvider<TestsuiteSender> suggestPlayers() {
		return (context, builder) -> {
			this.suggestion.player()
				.map(TestsuitePlayer::getName)
				.buildSuggest("player")
				.getSuggestions(context, builder);

			String input = ArgumentBuilder.getSafeStringArgument(context, "player", "");
			if ("all".startsWith(input)) {
				builder.suggest("all");
			}

			return builder.buildFuture();
		};
	}
	
	public int sendPlayer(CommandContext<TestsuiteSender> context) {
		TestsuitePlayer player = (TestsuitePlayer) context.getSource();

		String serverName = context.getArgument("server", String.class);
		TestsuiteServer targetServer = this.plugin.getServer(serverName);
		if (targetServer == null) {
			Chat.builder().append("Unable to find targetServer server!").send(player);
			return Command.SINGLE_SUCCESS;
		}

		String executorName = context.getSource() instanceof TestsuitePlayer executor ? executor.getName() : "CONSOLE";
		String playername = context.getArgument("player", String.class);
		if (playername.equalsIgnoreCase("all")) {
			int sendingCount = 0;
			for (TestsuitePlayer targetPlayer : this.plugin.getPlayers()) {
				sendingCount++;
				this.sendToServer(targetPlayer, targetServer);
				Chat.builder().append("{0} sending you to {1}", executorName, targetServer.getName()).send(targetPlayer);
			}
			
			Chat.builder().append("Sending {0} players to {1}", sendingCount, targetServer.getName()).send(context);
		} else {
			TestsuitePlayer targetPlayer = this.plugin.getPlayer(playername);
			if (targetPlayer == null) {
				Chat.builder().append("Unable to find player {0}!", playername).send(context);
				return Command.SINGLE_SUCCESS;
			}

			this.sendToServer(targetPlayer, targetServer);
			Chat.builder(targetServer).append("Sending {0} to {1}", targetPlayer.getName(), targetServer.getName()).send(context);
			Chat.builder().append("{0} sending you to {1}", executorName, targetServer.getName()).send(targetPlayer);
		}
		return Command.SINGLE_SUCCESS;
	}

	public void sendToServer(TestsuitePlayer player, TestsuiteServer targetServer) {
		player.connect(targetServer).whenComplete((result, error) -> {
			if (error != null) {
				error.printStackTrace();

				Chat.builder().append("Unable to connect too server! " + error.getMessage()).send(player);
				return;
			}

			switch (result) {
			case SUCCESS -> {
				Chat.builder().append("Successful connected to server {0}.", targetServer.getName()).send(player);
			}
			case ALREADY_CONNECTED -> Chat.builder().append("Your already connected").send(player);
			case CONNECTION_CANCELLED -> Chat.builder().append("Connection was cancelled").send(player);
			case CONNECTION_IN_PROGRESS -> Chat.builder().append("Connection is in progress").send(player);
			case SERVER_DISCONNECTED -> Chat.builder().append("Server disconnected").send(player);
			}
		});
	}
}