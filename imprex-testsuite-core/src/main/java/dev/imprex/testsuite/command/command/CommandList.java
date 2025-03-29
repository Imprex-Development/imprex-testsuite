package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.api.TestsuiteServer;
import dev.imprex.testsuite.command.ArgumentBuilder;
import dev.imprex.testsuite.command.SuggestionProvider;
import dev.imprex.testsuite.server.ServerInstance;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.server.meta.ServerType;
import dev.imprex.testsuite.server.meta.ServerVersion;
import dev.imprex.testsuite.server.meta.ServerVersionCache;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.template.ServerTemplateList;
import dev.imprex.testsuite.util.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class CommandList {

	private final ServerTemplateList templateList;
	private final ServerManager serverManager;
	private final ServerVersionCache versionCache;

	public CommandList(TestsuitePlugin plugin) {
		this.templateList = plugin.getTemplateList();
		this.serverManager = plugin.getServerManager();
		this.versionCache = plugin.getVersionCache();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("list").then(
				argument("name", StringArgumentType.string())
				.suggests(this::suggestTemplates)
				.executes(this::listServer))
			.executes(this::listServer);
	}

	public int listServer(CommandContext<TestsuiteSender> context) {		
		ServerTemplate template = null;
		String name = ArgumentBuilder.getSafeStringArgument(context, "name", null);
		if (name != null) {
			template = this.templateList.getTemplate(name);
			if (template == null) {
				Chat.builder().append("Unable to find template {0}", name).send(context);
				return Command.SINGLE_SUCCESS;
			}
		}

		Component component = Chat.PREFIX
				.append(Component.text("[] --- --- { Server List } --- --- []"))
				.append(Component.newline())
				.append(Component.newline());

		for (ServerInstance server : this.serverManager.getServers().stream()
				.sorted((a, b) -> a.getName().compareTo(b.getName()))
				.toList()) {

			if (template != null && !template.equals(server.getTemplate())) {
				continue;
			}

			Component serverInfo = Component.text("  - ")
					.color(Chat.Color.GRAY)
					.append(Chat.builder(false, server).build().color(Chat.Color.statusColor(server)));

			Component serverAction;
			if (server.getStatus() == UtilizationState.OFFLINE) {
				serverAction = Component.text("")
						.append(Component.text("[").color(Chat.Color.GRAY))
						.append(Component.text("Start").color(Chat.Color.LIGHT_GREEN))
						.append(Component.text("]").color(Chat.Color.GRAY))
						.clickEvent(ClickEvent.suggestCommand("/testsuite start " + server.getName()));
			} else if (server.getStatus() != UtilizationState.STOPPING) {
				Component playerCount = Component.text("(")
						.color(Chat.Color.GRAY)
						.append(Component.text(server.getPlayers().size())
								.color(Chat.Color.LIGHT_GREEN)
								.hoverEvent(HoverEvent.showText(Component.text("Player count")
										.color(Chat.Color.LIGHT_GREEN))));
				
				if (server.isIdleTimeout()) {
					playerCount = playerCount
						.append(Component.text(" | "))
						.append(Component.text(milliToSeconds(server.getInactiveTime()) + "s")
								.color(Chat.Color.DARK_GREEN)
								.hoverEvent(HoverEvent.showText(Component.text("Inactive time")
										.color(Chat.Color.DARK_GREEN))));
				}
				
				playerCount = playerCount.append(Component.text(")")
						.color(Chat.Color.GRAY));

				Component connectServer = Component.text("Connect")
						.color(Chat.Color.DARK_GREEN)
						.clickEvent(ClickEvent.runCommand("/connect " + server.getName()));

				serverAction = Component.text("")
						.append(playerCount)
						.appendSpace()
						.append(Component.text("[").color(Chat.Color.GRAY))
						.append(
							Component.text("Stop")
							.color(Chat.Color.RED)
							.clickEvent(ClickEvent.suggestCommand("/testsuite stop " + server.getName())));

				if (context.getSource() instanceof TestsuitePlayer player) {
					TestsuiteServer playerServer = player.getServer();
					if (playerServer == null || !playerServer.equals(server)) {
						serverAction = serverAction
								.appendSpace()
								.append(connectServer);
					}
				} else {
					serverAction = serverAction
							.appendSpace()
							.append(connectServer);
				}
				
				serverAction = serverAction.append(Component.text("]").color(Chat.Color.GRAY));
			} else {
				serverAction = Component.empty();
			}

			component = component
					.append(serverInfo)
					.appendSpace()
					.append(serverAction)
					.appendNewline();
		}

		context.getSource().sendMessage(component
				.append(Component.newline())
				.append(Chat.PREFIX)
				.append(Component.text("[] --- --- { Server List } --- --- []")));
		return Command.SINGLE_SUCCESS;
	}

	public int milliToSeconds(long time) {
		return (int) ((time - System.currentTimeMillis()) / 1000);
	}

	public CompletableFuture<Suggestions> suggestTemplates(CommandContext<TestsuiteSender> context, SuggestionsBuilder builder) {
		return SuggestionProvider.suggest(builder, this.templateList.getTemplates().stream()
				.map(template -> template.getName())
				.toList());
	}

	public CompletableFuture<Suggestions> suggestVersions(CommandContext<TestsuiteSender> context, SuggestionsBuilder builder) {
		String name = context.getArgument("name", String.class);
		ServerTemplate template = this.templateList.getTemplate(name);

		ServerType serverType = ServerType.fromName(context.getArgument("type", String.class));
		if (serverType == null) {
			return builder.buildFuture();
		}

		String input = builder.getRemaining().toLowerCase();
		this.versionCache.getVersionList(serverType).stream()
			.filter(version -> version.startsWith(input) || version.contains(input))
			.filter(version -> {
				if (template == null) {
					return true;
				}

				String serverName = String.format("%s-%s-%s", template.getName().toLowerCase(), serverType.name(), version);
				return this.serverManager.getServer(serverName) == null;
			})
			.sorted(ServerVersion::compareVersion)
			.forEachOrdered(builder::suggest);

		return builder.buildFuture();
	}
}