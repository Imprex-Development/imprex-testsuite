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
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.util.Chat;

public class CommandExecute {

	public static LiteralArgumentBuilder<CommandSource> COMMAND;

	private final ProxyServer proxy;
	private final ServerManager serverManager;

	public CommandExecute(TestsuitePlugin plugin) {
		this.proxy = plugin.getProxy();
		this.serverManager = plugin.getServerManager();

		COMMAND = this.create();
		this.register();
	}

	public void register() {
		CommandManager commandManager = this.proxy.getCommandManager();
		CommandMeta commandMeta = commandManager.metaBuilder("exec")
				.aliases("execute")
				.plugin(this)
				.build();

		BrigadierCommand command = new BrigadierCommand(COMMAND);
		commandManager.register(commandMeta, command);
	}

	public LiteralArgumentBuilder<CommandSource> create() {
		return literal("exec").then(
				argument("name", StringArgumentType.word())
				.suggests(this::suggestServers)
				.then(
						argument("command", StringArgumentType.greedyString())
						.executes(this::executeCommand)));
	}

	public int executeCommand(CommandContext<CommandSource> context) {
		String serverName = context.getArgument("name", String.class);
		ServerInstance server = this.serverManager.getServer(serverName);
		if (server == null) {
			Chat.send(context, "Server was not found!");
			return Command.SINGLE_SUCCESS;
		}

		String command = context.getArgument("command", String.class);
		if (command.startsWith("/")) {
			command = command.substring(1);
		}

		Chat.send(context, "Executing command on {0}...", server.getName());
		server.executeCommand(command).whenComplete((__, error) -> {
			if (error != null) {
				Chat.send(context, "Server {0} is unable to execute command! {1}", server.getName(), error.getMessage());
			} else {
				Chat.send(context, "Server {0} executed command.", server.getName());
			}
		});
		return Command.SINGLE_SUCCESS;
	}

	public CompletableFuture<Suggestions> suggestServers(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
		String input = builder.getRemaining().toLowerCase();
		this.serverManager.getServers().stream()
			.filter(server -> server.getStatus() == UtilizationState.RUNNING)
			.map(server -> server.getName())
			.filter(name -> name.toLowerCase().contains(input))
			.forEach(builder::suggest);
		return builder.buildFuture();
	}
}