package dev.imprex.testsuite.server.meta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.imprex.testsuite.TestsuiteLogger;

public class ServerVersionCache implements Runnable {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.create();

	private static final long UPDATE_COOLDOWN = TimeUnit.HOURS.toMillis(1);

	private final Path path;
	private final Map<ServerType, Set<String>> cache = new ConcurrentHashMap<>();

	private boolean needsUpdate = false;
	private long updateTime = 0;

	public ServerVersionCache(Path path) {
		this.path = path;
		this.load();
	}

	@Override
	public void run() {
		if (!this.needsUpdate && this.updateTime > System.currentTimeMillis()) {
			return;
		}

		for (ServerType serverType : ServerType.values()) {
			if (serverType.getVersionListUrl() == null) {
				return;
			}
			
			try {
				Set<String> versionSet = ServerVersion.fetchVersionList(serverType);
				if (versionSet == null) {
					return;
				}

				this.cache.computeIfAbsent(serverType, (key) -> new HashSet<>()).addAll(versionSet);
			} catch (Exception e) {
				TestsuiteLogger.error(e, "Unable to update version cache for " + serverType.name());
			}
		}

		TestsuiteLogger.info("Version cache successfully updated!");

		this.needsUpdate = false;
		this.updateTime = System.currentTimeMillis() + UPDATE_COOLDOWN;
		this.save();
	}

	private void load() {
		if (Files.notExists(this.path)) {
			this.needsUpdate = true;
			return;
		}

		try (BufferedReader bufferedReader = Files.newBufferedReader(this.path)) {
			JsonObject root = JsonParser.parseReader(bufferedReader).getAsJsonObject();
			this.updateTime = root.get("updateTime").getAsLong();

			JsonObject serverTypes = root.getAsJsonObject("serverTypes");
			for (Entry<String, JsonElement> entry : serverTypes.entrySet()) {
				ServerType serverType = ServerType.valueOf(entry.getKey());
				Set<String> versions = entry.getValue().getAsJsonArray().asList().stream()
						.map(JsonElement::getAsString)
						.collect(Collectors.toSet());

				this.cache.put(serverType, versions);
			}
		} catch (Exception e) {
			TestsuiteLogger.error(e, "Unable to load version cache file!");
			this.needsUpdate = true;
		}
	}

	private void save() {
		JsonObject root = new JsonObject();
		root.addProperty("updateTime", this.updateTime);

		JsonObject serverTypes = new JsonObject();
		for (Entry<ServerType, Set<String>> entry : this.cache.entrySet()) {
			JsonArray versions = new JsonArray();
			entry.getValue().stream()
				.sorted(ServerVersion::compareVersion)
				.forEach(versions::add);

			serverTypes.add(entry.getKey().name(), versions);
		}
		root.add("serverTypes", serverTypes);

		try {
			Files.createDirectories(this.path.getParent());

			try (BufferedWriter bufferedWriter = Files.newBufferedWriter(this.path)) {
				GSON.toJson(root, bufferedWriter);
			}
		} catch (IOException e) {
			TestsuiteLogger.error(e, "Unable to save version cache file!");
		}
	}

	public Set<String> getVersionList(ServerType serverType) {
		return Collections.unmodifiableSet(this.cache.getOrDefault(serverType, Collections.emptySet()));
	}
}