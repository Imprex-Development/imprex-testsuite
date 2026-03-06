package dev.imprex.testsuite.config;

public record PterodactylConfig(String url, String applicationToken, String clientToken) {

	public boolean valid() {
		return url.startsWith("http") &&
				this.applicationToken.startsWith("papp_") &&
				this.clientToken.startsWith("pacc_");
	}
}