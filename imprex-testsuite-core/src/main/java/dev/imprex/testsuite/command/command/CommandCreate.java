package dev.imprex.testsuite.command.command;

import static dev.imprex.testsuite.command.ArgumentBuilder.argument;
import static dev.imprex.testsuite.command.ArgumentBuilder.literal;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuiteSender;
import dev.imprex.testsuite.command.SuggestionProvider;
import dev.imprex.testsuite.command.suggestion.CommandSuggestion;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.server.meta.ServerType;
import dev.imprex.testsuite.server.meta.ServerVersionCache;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.template.ServerTemplateList;
import dev.imprex.testsuite.util.Chat;

public class CommandCreate {

	private final ServerVersionCache versionCache;
	private final ServerTemplateList templateList;
	private final ServerManager serverManager;
	private final CommandSuggestion suggestion;

	public CommandCreate(TestsuitePlugin plugin) {
		this.versionCache = plugin.getVersionCache();
		this.templateList = plugin.getTemplateList();
		this.serverManager = plugin.getServerManager();
		this.suggestion = plugin.getCommandSuggestion();
	}

	public LiteralArgumentBuilder<TestsuiteSender> create() {
		return literal("create").then(
				argument("name", StringArgumentType.word())
				.suggests(this.suggestion.template()
						.map(ServerTemplate::getName)
						.buildSuggest("name"))
				.then(
						argument("type", StringArgumentType.word())
						.suggests((future, builder) -> SuggestionProvider.suggest(builder, ServerType.TYPE_NAMES))
						.then(
								argument("version", StringArgumentType.word())
								.suggests(this::suggestVersions)
								.executes(this::createServer)
								)
						)
				);
	}

	public int createServer(CommandContext<TestsuiteSender> context) {
		String name = context.getArgument("name", String.class);

		ServerTemplate template = this.templateList.getTemplate(name);

		ServerType serverType = ServerType.fromName(context.getArgument("type", String.class));
		if (serverType == null) {
			Chat.send(context, builder -> builder.append("Invalid server type"));
			return Command.SINGLE_SUCCESS;
		}

		String version = context.getArgument("version", String.class);
		if (!this.versionCache.getVersionList(serverType).contains(version)) {
			Chat.send(context, builder -> builder.append("Invalid version"));
			return Command.SINGLE_SUCCESS;
		}

		Chat.send(context, builder -> builder.append("Creating server " + (template != null ? "template " + template.getName() : name) + "..."));
		if (template != null) {
			String serverName = String.format("%s-%s-%s", template.getName(), serverType.name().toLowerCase(), version);
			if (this.serverManager.getServer(serverName) != null) {
				Chat.send(context, builder -> builder.append("Template aready exist"));
				return Command.SINGLE_SUCCESS;
			}

			this.serverManager.create(template, serverType, version).whenComplete((__, error) -> {
				if (error != null) {
					error.printStackTrace();
					Chat.send(context, builder -> builder.append("Created failed! " + error.getMessage()));
					return;
				}

				Chat.send(context, builder -> builder.append("Created success"));
			});
		} else {
			this.serverManager.create(name, serverType, version).whenComplete((__, error) -> {
				if (error != null) {
					error.printStackTrace();
					Chat.send(context, builder -> builder.append("Created failed! " + error.getMessage()));
					return;
				}

				Chat.send(context, builder -> builder.append("Created success"));
			});
		}
		return Command.SINGLE_SUCCESS;
	}

	public CompletableFuture<Suggestions> suggestVersions(CommandContext<TestsuiteSender> context, SuggestionsBuilder builder) throws CommandSyntaxException {
		String name = context.getArgument("name", String.class);
		ServerTemplate template = this.templateList.getTemplate(name);

		ServerType serverType = ServerType.fromName(context.getArgument("type", String.class));
		if (serverType == null) {
			return builder.buildFuture();
		}

		return this.suggestion.version(serverType)
				.filter(version -> {
					if (template == null) {
						return true;
					}
	
					String serverName = String.format("%s-%s-%s", template.getName().toLowerCase(), serverType.name(), version);
					return this.serverManager.getServer(serverName) == null;
					})
				.buildSuggest("version")
				.getSuggestions(context, builder);
	}
}