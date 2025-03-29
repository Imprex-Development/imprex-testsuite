package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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

public class CommandReconnect {

	private final TestsuitePlugin plugin;
	private final CommandSuggestion suggestion;

	public CommandReconnect(TestsuitePlugin plugin) {
		this.plugin = plugin;
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("reconnect")
				.requires(source -> source instanceof TestsuitePlayer)
				.executes(this::reconnect)
				.then(
						argument("player", StringArgumentType.string())
						.suggests(this.suggestPlayers())
						.executes(this::reconnectPlayer));
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

	public int reconnect(CommandContext<TestsuiteSender> context) {
		TestsuitePlayer player = (TestsuitePlayer) context.getSource();

		TestsuiteServer lobby = this.plugin.getServer("lobby");
		if (lobby == null) {
			Chat.builder().append("Unable to find lobby server!").send(player);
			return Command.SINGLE_SUCCESS;
		}

		TestsuiteServer serverConnection = player.getServer();
		if (serverConnection == null) {
			Chat.builder().append("Unable to find current server!").send(player);
			return Command.SINGLE_SUCCESS;
		}

		if (lobby.equals(serverConnection)) {
			Chat.builder().append("You can only reconnect on non lobby servers!").send(player);
			return Command.SINGLE_SUCCESS;
		}

		this.reconnectPlayer(player, lobby, serverConnection);
		return Command.SINGLE_SUCCESS;
	}
	
	public int reconnectPlayer(CommandContext<TestsuiteSender> context) {
		TestsuitePlayer player = (TestsuitePlayer) context.getSource();

		TestsuiteServer lobby = this.plugin.getServer("lobby");
		if (lobby == null) {
			Chat.builder().append("Unable to find lobby server!").send(player);
			return Command.SINGLE_SUCCESS;
		}

		String executorName = context.getSource() instanceof TestsuitePlayer executor ? executor.getName() : "CONSOLE";
		String playername = context.getArgument("player", String.class);
		if (playername.equalsIgnoreCase("all")) {
			int reconnectCount = 0;
			for (TestsuitePlayer targetPlayer : this.plugin.getPlayers()) {
				TestsuiteServer serverConnection = targetPlayer.getServer();
				if (serverConnection != null && serverConnection.equals(lobby)) {
					continue;
				}

				reconnectCount++;
				this.reconnectPlayer(targetPlayer, lobby, serverConnection);
				Chat.builder().append("{0} reconnecting you", executorName, player.getName()).send(targetPlayer);
			}
			
			Chat.builder().append("Reconnecting {0} players", reconnectCount).send(context);
		} else {
			TestsuitePlayer targetPlayer = this.plugin.getPlayer(playername);
			if (targetPlayer == null) {
				Chat.builder().append("Unable to find player {0}!", playername).send(context);
				return Command.SINGLE_SUCCESS;
			}

			TestsuiteServer serverConnection = targetPlayer.getServer();
			if (serverConnection == null) {
				Chat.builder().append("Unable to find current server!").send(player);
				return Command.SINGLE_SUCCESS;
			}

			if (lobby.equals(serverConnection)) {
				Chat.builder().append("Can only reconnect on non lobby servers!").send(player);
				return Command.SINGLE_SUCCESS;
			}

			this.reconnectPlayer(targetPlayer, lobby, serverConnection);
			Chat.builder(serverConnection).append("Reconnecting {0} to \"{1}\"", targetPlayer.getName(), serverConnection.getName()).send(context);
			Chat.builder(serverConnection).append("{0} is reconnecting you", executorName).send(targetPlayer);
		}
		return Command.SINGLE_SUCCESS;
	}

	public void reconnectPlayer(TestsuitePlayer player, TestsuiteServer lobby, TestsuiteServer current) {
		player.connect(lobby != null ? lobby : current).whenComplete((result, error) -> {
			if (error != null) {
				error.printStackTrace();

				if (lobby == null) {
					Chat.builder().append("Unable to connect back! " + error.getMessage()).send(player);
				} else {
					Chat.builder().append("Unable to connect too lobby server! " + error.getMessage()).send(player);
				}
				return;
			}

			switch (result) {
			case SUCCESS -> {
				if (lobby == null) {
					Chat.builder().append("Successful reconnected.").send(player);
				} else {
					Chat.builder().append("Reconnecting in one second...").send(player);
					Executor executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);
					CompletableFuture.completedFuture(null).thenRunAsync(() -> this.reconnectPlayer(player, null, current), executor);
				}
			}
			case ALREADY_CONNECTED -> Chat.builder().append("Your already connected").send(player);
			case CONNECTION_CANCELLED -> Chat.builder().append("Connection was cancelled").send(player);
			case CONNECTION_IN_PROGRESS -> Chat.builder().append("Connection is in progress").send(player);
			case SERVER_DISCONNECTED -> Chat.builder().append("Server disconnected").send(player);
			}
		});
	}
}