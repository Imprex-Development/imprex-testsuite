package dev.imprex.testsuite.api;

import java.util.List;
import java.util.Optional;

import net.kyori.adventure.text.Component;

public interface TestsuiteServer {

	String getIdentifier();

	String getName();

	Optional<String> getAddress();

	Optional<Integer> getPort();

	void broadcast(Component component);

	List<TestsuitePlayer> getPlayers();
}