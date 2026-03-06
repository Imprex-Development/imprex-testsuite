package dev.imprex.testsuite.velocity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import dev.imprex.testsuite.api.ConnectionResult;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;
import net.kyori.adventure.text.Component;

public class VelocityPlayer implements TestsuitePlayer {

	static VelocityPlugin plugin;

	private static final Map<String, TestsuitePlayer> CACHE = new ConcurrentHashMap<>();

	static void add(Player player) {
		VelocityPlayer.CACHE.put(player.getUsername().toLowerCase(), new VelocityPlayer(player));
	}

	static void remove(Player player) {
		VelocityPlayer.CACHE.remove(player.getUsername().toLowerCase());
	}

	public static TestsuitePlayer get(String name) {
		return VelocityPlayer.CACHE.get(name.toLowerCase());
	}

	public static TestsuitePlayer get(UUID uuid) {
		return VelocityPlayer.CACHE.values().stream()
			.filter(player -> player.getUUID() == uuid)
			.findFirst()
			.orElse(null);
	}

	public static TestsuitePlayer get(Player player) {
		return VelocityPlayer.get(player.getUsername());
	}

	private final Player player;

	private VelocityPlayer(Player player) {
		this.player = player;
	}

	@Override
	public void sendMessage(Component component) {
		this.player.sendMessage(component);
	}

	@Override
	public CompletableFuture<ConnectionResult> connect(TestsuiteServer server) {
		Optional<RegisteredServer> proxyServer = VelocityPlayer.plugin.getProxy().getServer(server.getName());
		if (proxyServer.isEmpty()) {
			return CompletableFuture.completedFuture(ConnectionResult.CONNECTION_CANCELLED);
		}

		return this.player.createConnectionRequest(proxyServer.get())
				.connect()
				.thenApply(result -> switch (result.getStatus()) {
				case SUCCESS -> ConnectionResult.SUCCESS;
				case ALREADY_CONNECTED -> ConnectionResult.ALREADY_CONNECTED;
				case CONNECTION_IN_PROGRESS -> ConnectionResult.CONNECTION_IN_PROGRESS;
				case CONNECTION_CANCELLED -> ConnectionResult.CONNECTION_CANCELLED;
				case SERVER_DISCONNECTED -> ConnectionResult.SERVER_DISCONNECTED;
				});
	}

	@Override
	public void sendPlayerList(Component header, Component footer) {
		this.player.sendPlayerListHeaderAndFooter(header, footer);
	}

	@Override
	public void sendPlayerListHeader(Component header) {
		this.player.sendPlayerListHeader(header);
	}

	@Override
	public void sendPlayerListFooter(Component footer) {
		this.player.sendPlayerListFooter(footer);
	}

	@Override
	public String getName() {
		return this.player.getUsername();
	}

	@Override
	public UUID getUUID() {
		return this.player.getUniqueId();
	}

	@Override
	public TestsuiteServer getServer() {
		Optional<ServerConnection> server = this.player.getCurrentServer();
		if (server.isEmpty()) {
			return null;
		}

		return VelocityPlayer.plugin.getServer(server.get().getServerInfo().getName());
	}

}
