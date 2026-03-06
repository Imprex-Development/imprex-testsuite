
package dev.imprex.testsuite.command.suggestion;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.server.ServerManager;
import dev.imprex.testsuite.server.meta.ServerType;
import dev.imprex.testsuite.server.meta.ServerVersionCache;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.template.ServerTemplateList;

public class CommandSuggestion {

	private final TestsuitePlugin plugin;
	private final ServerManager serverManager;
	private final ServerTemplateList templateList;
	private final ServerVersionCache versionCache;

	public CommandSuggestion(TestsuitePlugin plugin) {
		this.plugin = plugin;
		this.serverManager = plugin.getServerManager();
		this.templateList = plugin.getTemplateList();
		this.versionCache = plugin.getVersionCache();
	}
	public SuggestionBuilder<String, String> version(ServerType type) {
		return new SuggestionBuilder<>(() -> this.versionCache.getVersionList(type).stream());
	}

	public SuggestionBuilder<String, ServerTemplate> template() {
		return new SuggestionBuilder<>(() -> this.templateList.getTemplates().stream().map(template -> template.getName()));
	}

	public ServerSuggestionBuilder server() {
		return new ServerSuggestionBuilder(() -> this.serverManager.getServers().stream());
	}

	public SuggestionBuilder<TestsuitePlayer, TestsuitePlayer> player() {
		return new SuggestionBuilder<>(() -> this.plugin.getPlayers().stream());
	}
}
