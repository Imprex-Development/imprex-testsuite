package de.imprex.testsuite.local;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;
import net.kyori.adventure.text.Component;

public record LocalServer(String identifier, String name, String address, int port) implements TestsuiteServer {

	@Override
	public void broadcast(Component component) {
	}

	@Override
	public String getIdentifier() {
		return this.identifier;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Optional<String> getAddress() {
		return Optional.of(this.address);
	}

	@Override
	public Optional<Integer> getPort() {
		return Optional.of(this.port);
	}

	@Override
	public List<TestsuitePlayer> getPlayers() {
		return Collections.emptyList();
	}
}
