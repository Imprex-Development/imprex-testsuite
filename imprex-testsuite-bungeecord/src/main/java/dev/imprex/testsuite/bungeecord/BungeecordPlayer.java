package dev.imprex.testsuite.bungeecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import dev.imprex.testsuite.api.ConnectionResult;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent.Reason;

public class BungeecordPlayer implements TestsuitePlayer {

	private static final Map<String, TestsuitePlayer> CACHE = new ConcurrentHashMap<>();

	static BungeecordPlayer add(ProxiedPlayer player) {
		BungeecordPlayer bungeePlayer = new BungeecordPlayer(player);
		BungeecordPlayer.CACHE.put(player.getName().toLowerCase(), bungeePlayer);
		return bungeePlayer;
	}

	static void remove(ProxiedPlayer player) {
		BungeecordPlayer.CACHE.remove(player.getName().toLowerCase());
	}

	public static TestsuitePlayer get(String name) {
		return BungeecordPlayer.CACHE.get(name.toLowerCase());
	}

	public static TestsuitePlayer get(UUID uuid) {
		return BungeecordPlayer.CACHE.values().stream()
			.filter(player -> player.getUUID() == uuid)
			.findFirst()
			.orElse(null);
	}

	public static TestsuitePlayer get(ProxiedPlayer player) {
		return BungeecordPlayer.get(player.getName());
	}

	private final ProxiedPlayer player;
	private final Audience audience;

	private BungeecordPlayer(ProxiedPlayer player) {
		this.player = player;
		this.audience = BungeecordPlugin.audiences.player(player);
	}

	@Override
	public void sendMessage(Component component) {
		this.audience.sendMessage(component);
	}

	@Override
	public CompletableFuture<ConnectionResult> connect(TestsuiteServer server) {
		ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(server.getName());
		if (serverInfo == null) {
			return CompletableFuture.completedFuture(ConnectionResult.CONNECTION_CANCELLED);
		}

		CompletableFuture<ConnectionResult> future = new CompletableFuture<>();
		this.player.connect(ServerConnectRequest.builder()
				.target(serverInfo)
				.reason(Reason.PLUGIN)
				.callback((result, error) -> {
					if (error != null) {
						future.completeExceptionally(error);
						return;
					}

					ProxyServer.getInstance().getScheduler().schedule(BungeecordPlugin.plugin, () -> {
						future.complete(switch (result) {
							case SUCCESS -> ConnectionResult.SUCCESS;
							case ALREADY_CONNECTED -> ConnectionResult.ALREADY_CONNECTED;
							case ALREADY_CONNECTING -> ConnectionResult.CONNECTION_IN_PROGRESS;
							case EVENT_CANCEL -> ConnectionResult.CONNECTION_CANCELLED;
							case FAIL -> ConnectionResult.SERVER_DISCONNECTED;
						});
					}, 2, TimeUnit.SECONDS);
				})
				.build());
		return future;
	}

	@Override
	public void sendPlayerList(Component header, Component footer) {
		this.audience.sendPlayerListHeaderAndFooter(header, footer);
	}

	@Override
	public void sendPlayerListHeader(Component header) {
		this.audience.sendPlayerListHeader(header);
	}

	@Override
	public void sendPlayerListFooter(Component footer) {
		this.audience.sendPlayerListFooter(footer);
	}

	@Override
	public String getName() {
		return this.player.getName();
	}

	@Override
	public UUID getUUID() {
		return this.player.getUniqueId();
	}

	@Override
	public TestsuiteServer getServer() {
		Server server = this.player.getServer();
		if (server == null) {
			return null;
		}

		return BungeecordPlugin.plugin.getTestsuite().getServer(server.getInfo().getName());
	}

	public ProxiedPlayer getProxiedPlayer() {
		return this.player;
	}
}
