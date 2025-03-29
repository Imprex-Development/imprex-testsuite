package dev.imprex.testsuite.server.meta;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ServerType {

	VANILLA("vanilla minecraft", "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"),
	SPIGOT("spigot", "https://hub.spigotmc.org/versions/"),
	PAPER("paper", "https://api.papermc.io/v2/projects/paper"),
	FOLIA("folia", "https://api.papermc.io/v2/projects/folia"),
	MOHIST("mohist", "https://mohistmc.com/api/v2/projects/mohist");

	private static final Map<String, ServerType> TYPE_BY_NAME = Stream.of(ServerType.values()).collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

	public static final Set<String> TYPE_NAMES = Collections.unmodifiableSet(TYPE_BY_NAME.keySet());

	public static ServerType fromName(String name) {
		return TYPE_BY_NAME.get(name.toUpperCase());
	}

	public static boolean isValid(String name) {
		return TYPE_BY_NAME.containsKey(name.toUpperCase());
	}

	private final String eggName;
	private final String versionListUrl;

	private ServerType(String eggName, String versionListUrl) {
		this.eggName = eggName;
		this.versionListUrl = versionListUrl;
	}

	public String getEggName() {
		return this.eggName;
	}

	public String getVersionListUrl() {
		return this.versionListUrl;
	}
}
