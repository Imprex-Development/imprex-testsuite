package dev.imprex.testsuite.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {

	private static final Pattern VERSION_PATTERN = Pattern.compile("(?<major>\\d+)(?:\\.(?<minor>\\d+))(?:\\.(?<patch>\\d+))?");

	private final int major;
	private final int minor;
	private final int patch;

	public MinecraftVersion(String version) {
		Matcher matcher = VERSION_PATTERN.matcher(version);

		if (!matcher.find()) {
			throw new IllegalArgumentException("can't parse minecraft version: " + version);
		}

		this.major = Integer.parseInt(matcher.group("major"));
		this.minor = Integer.parseInt(matcher.group("minor"));

		String patch = matcher.group("patch");
		if (patch != null) {
			this.patch = Integer.parseInt(patch);
		} else {
			this.patch = 0;
		}
	}

	public int major() {
		return this.major;
	}

	public int minor() {
		return this.minor;
	}

	public int patch() {
		return this.patch;
	}

	public boolean isAbove(MinecraftVersion version) {
		return this.compareTo(version) > 0;
	}

	public boolean isAtOrAbove(MinecraftVersion version) {
		return this.compareTo(version) >= 0;
	}

	public boolean isAtOrBelow(MinecraftVersion version) {
		return this.compareTo(version) <= 0;
	}

	public boolean isBelow(MinecraftVersion version) {
		return this.compareTo(version) < 0;
	}

	@Override
	public int compareTo(MinecraftVersion other) {
		int major = Integer.compare(this.major, other.major);
		if (major != 0) {
			return major;
		}

		int minor = Integer.compare(this.minor, other.minor);
		if (minor != 0) {
			return minor;
		}

		return Integer.compare(this.patch, other.patch);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MinecraftVersion)) {
			return false;
		}
		MinecraftVersion other = (MinecraftVersion) obj;
		return major == other.major && minor == other.minor && patch == other.patch;
	}

	@Override
	public String toString() {
		return String.format("%s.%s.%s", this.major, this.minor, this.patch);
	}
}
