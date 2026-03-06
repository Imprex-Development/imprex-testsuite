package dev.imprex.testsuite.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public interface TestsuiteApi {

	void scheduleTask(Runnable runnable, int delay, int repeat, TimeUnit unit);

	TestsuitePlayer getPlayer(String name);

	TestsuitePlayer getPlayer(UUID uuid);

	List<TestsuitePlayer> getPlayers();

	List<TestsuitePlayer> getPlayers(TestsuiteServer server);

	void registerServerList(TestsuiteServer server);

	boolean unregisterServerList(String name);
}
