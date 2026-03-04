package dev.imprex.testsuite.bungeecord;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import dev.imprex.testsuite.api.TestsuiteApi;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class BungeecordApi implements TestsuiteApi {

	private final BungeecordPlugin plugin;
	private final ProxyServer proxy = ProxyServer.getInstance();

	public BungeecordApi(BungeecordPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void scheduleTask(Runnable runnable, int delay, int repeat, TimeUnit unit) {
		this.proxy.getScheduler().schedule(this.plugin, runnable, delay, repeat, unit);
	}

	@Override
	public TestsuitePlayer getPlayer(String name) {
		return BungeecordPlayer.get(name);
	}

	@Override
	public List<TestsuitePlayer> getPlayers() {
		return this.proxy.getPlayers().stream()
				.map(BungeecordPlayer::get)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<TestsuitePlayer> getPlayers(TestsuiteServer server) {
		ServerInfo serverInfo = this.proxy.getServerInfo(server.getName().toLowerCase());
		if (serverInfo == null) {
			return Collections.emptyList();
		}

		return serverInfo.getPlayers().stream()
				.map(ProxiedPlayer::getName)
				.map(this::getPlayer)
				.toList();
	}

	@Override
	public void registerServerList(TestsuiteServer server) {
		String name = server.getName().toLowerCase();
		String ip = server.getAddress().orElse(null);
		Integer port = server.getPort().orElse(null);
		
		// no allocation
		if (ip == null || port == null) {
			return;
		}

		ServerInfo serverInfoOptional = this.proxy.getServers().get(name);
		if (serverInfoOptional != null) {
			InetSocketAddress socketAddress = ((InetSocketAddress) serverInfoOptional.getSocketAddress());

			if (socketAddress.getHostName().equalsIgnoreCase(ip) &&
					socketAddress.getPort() == port) {
				return;
			}

			this.unregisterServerList(name);
		}

		ServerInfo serverInfo = this.proxy.constructServerInfo(name, new InetSocketAddress(ip, port), "", false);
		this.proxy.getServers().put(name, serverInfo);
	}

	@Override
	public boolean unregisterServerList(String name) {
		ServerInfo serverInfo = this.proxy.getServerInfo(name);
		if (serverInfo != null) {
			this.proxy.getServers().remove(serverInfo.getName());
			return true;
		}
		return false;
	}
}