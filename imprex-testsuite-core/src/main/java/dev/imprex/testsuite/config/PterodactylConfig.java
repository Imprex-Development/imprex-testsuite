package dev.imprex.testsuite.config;

public record PterodactylConfig(String url, String applicationToken, String clientToken) {

	public boolean valid() {
		return url.startsWith("http");
	}
}