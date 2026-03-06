package dev.imprex.testsuite.velocity;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import dev.imprex.testsuite.api.TestsuiteApi;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;

public class VelocityApi implements TestsuiteApi {

	private final VelocityPlugin plugin;
	private final ProxyServer proxy;

	public VelocityApi(VelocityPlugin plugin) {
		this.plugin = plugin;
		this.proxy = plugin.getProxy();
	}

	@Override
	public void scheduleTask(Runnable runnable, int delay, int repeat, TimeUnit unit) {
		this.proxy.getScheduler().buildTask(this.plugin, runnable)
				.delay(delay, unit)
				.repeat(repeat, unit)
				.schedule();
	}

	@Override
	public TestsuitePlayer getPlayer(String name) {
		return VelocityPlayer.get(name);
	}
	
	@Override
	public TestsuitePlayer getPlayer(UUID uuid) {
		return VelocityPlayer.get(uuid);
	}

	@Override
	public List<TestsuitePlayer> getPlayers() {
		return this.proxy.getAllPlayers().stream()
				.map(VelocityPlayer::get)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<TestsuitePlayer> getPlayers(TestsuiteServer server) {
		Optional<RegisteredServer> serverOptional = this.proxy.getServer(server.getName().toLowerCase());
		if (serverOptional.isEmpty()) {
			return Collections.emptyList();
		}

		return serverOptional.get().getPlayersConnected().stream()
				.map(Player::getUsername)
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

		Optional<RegisteredServer> serverOptional = this.proxy.getServer(name);
		if (serverOptional.isPresent()) {
			RegisteredServer registeredServer = serverOptional.get();
			ServerInfo serverInfo = registeredServer.getServerInfo();
			InetSocketAddress socketAddress = serverInfo.getAddress();

			if (socketAddress.getHostName().equalsIgnoreCase(ip) &&
					socketAddress.getPort() == port) {
				return;
			}

			this.unregisterServerList(name);
		}

		ServerInfo serverInfo = new ServerInfo(name, new InetSocketAddress(ip, port));
		this.proxy.registerServer(serverInfo);
	}

	@Override
	public boolean unregisterServerList(String name) {
		Optional<RegisteredServer> server = this.proxy.getServer(name.toLowerCase());
		if (server.isPresent()) {
			RegisteredServer registeredServer = server.get();
			this.proxy.unregisterServer(registeredServer.getServerInfo());
			return true;
		}
		return false;
	}
}