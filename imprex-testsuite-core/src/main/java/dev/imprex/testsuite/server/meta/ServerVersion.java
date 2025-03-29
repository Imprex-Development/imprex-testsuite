package dev.imprex.testsuite.server.meta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ServerVersion {

	public static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(.\\d+)?$");

	public static int compareVersion(String a, String b) {
		String[] aSplit = a.split("\\.");
		String[] bSplit = b.split("\\.");

		for (int i = 0; i < Math.min(aSplit.length, bSplit.length); i++) {
			int aValue = Integer.valueOf(aSplit[i]);
			int bValue = Integer.valueOf(bSplit[i]);

			int compare = Integer.compare(aValue, bValue);
			if (compare != 0) {
				return compare;
			}
		}

		return aSplit.length - bSplit.length;
	}

	public static Set<String> fetchVersionList(ServerType type) {
		try {
			String urlString = type.getVersionListUrl();
			if (urlString == null) {
				return Collections.emptySet();
			}

			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			try (InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream())) {
				Set<String> versionSet;
				if (type == ServerType.SPIGOT) {
					versionSet = readSpigotVersionList(inputStreamReader);
				} else {
					versionSet = readJsonVersionList(inputStreamReader);
				}

				return versionSet.stream()
						.filter(version -> VERSION_PATTERN.matcher(version).find())
						.collect(Collectors.toSet());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Set<String> readSpigotVersionList(InputStreamReader inputStreamReader) throws IOException {
		Set<String> versionList = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
			while (reader.ready()) {
				String line = reader.readLine();
				if (line.contains(".json")) {
					versionList.add(line.substring("<a href=\"".length(), line.indexOf(".json")));
				}
			}
		}
		return versionList;
	}

	private static Set<String> readJsonVersionList(InputStreamReader inputStreamReader) {
		JsonObject jsonObject = JsonParser.parseReader(inputStreamReader).getAsJsonObject();
		JsonElement versionListObject = jsonObject.get("versions");
		if (versionListObject == null || !versionListObject.isJsonArray()) {
			return null;
		}

		Set<String> versionList = new HashSet<>();
		JsonArray versionArray = versionListObject.getAsJsonArray();
		for (JsonElement versionElement : versionArray) {
			if (versionElement.isJsonObject()) {
				JsonObject versionObject = versionElement.getAsJsonObject();
				JsonElement version = versionObject.get("id");
				if (version != null && version.isJsonPrimitive()) {
					versionList.add(version.getAsString());
				}
			} else if (versionElement.isJsonPrimitive()) {
				versionList.add(versionElement.getAsString());
			}
		}

		return versionList;
	}
}
