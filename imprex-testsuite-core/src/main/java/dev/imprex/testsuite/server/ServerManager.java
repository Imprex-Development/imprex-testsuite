package dev.imprex.testsuite.server;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.mattmalec.pterodactyl4j.DataType;
import com.mattmalec.pterodactyl4j.EnvironmentValue;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationAllocation;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationEgg;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationServer;
import com.mattmalec.pterodactyl4j.application.entities.ApplicationUser;
import com.mattmalec.pterodactyl4j.application.entities.PteroApplication;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.PteroClient;

import dev.imprex.testsuite.TestsuiteLogger;
import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.config.ServerConfig;
import dev.imprex.testsuite.server.meta.ServerType;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.util.MinecraftVersion;
import dev.imprex.testsuite.util.PteroServerStatus;

public class ServerManager implements Runnable {

	private static final long UPDATE_TIME = TimeUnit.SECONDS.toMillis(30);

	private static final MinecraftVersion VERSION_1_21 = new MinecraftVersion("1.21");
	private static final MinecraftVersion VERSION_1_17_1 = new MinecraftVersion("1.17.1");
	private static final MinecraftVersion VERSION_26_1 = new MinecraftVersion("26.1");

	private final TestsuitePlugin plugin;
	private final PteroApplication pteroApplication;
	private final PteroClient pteroClient;

	private final ServerConfig serverConfig;
	private final AllocationAssignment allocationAssignment;

	private final Map<String, ServerInstance> serverInstances = new ConcurrentHashMap<>();
	private final List<String> serverInstallation = new CopyOnWriteArrayList<>();

	private AtomicLong lastUpdate = new AtomicLong(0);

	public ServerManager(TestsuitePlugin plugin) {
		this.plugin = plugin;
		this.pteroApplication = plugin.getPteroApplication();
		this.pteroClient = plugin.getPteroClient();

		this.serverConfig = plugin.getConfig().getServerConfig();
		this.allocationAssignment = new AllocationAssignment(plugin, plugin.getConfig().getAllocationConfig());
	}

	@Override
	public void run() {
		for (ServerInstance instance : this.serverInstances.values()) {
			instance.run();
		}

		if (this.lastUpdate.get() > System.currentTimeMillis()) {
			return;
		}
		this.lastUpdate.getAndSet(System.currentTimeMillis() + UPDATE_TIME);

		this.pteroClient.retrieveServers().all().executeAsync((serverList) -> {
			try {
				for (ClientServer server : serverList) {
					if (!ServerType.isValid(server.getEgg().getName()) && !server.getName().equalsIgnoreCase("lobby")) {
						continue;
					}
					
					String identifier = server.getIdentifier();
					ServerInstance instance = this.serverInstances.get(identifier);

					if (instance == null) {
						instance = new ServerInstance(this, server);
						instance.setIdleTimeout(false); // only enable when started with command

						this.serverInstances.put(identifier, instance);

						instance.notifyMessage("Detected server instance");
					} else {
						if (server.isInstalling()) {
							instance.updateServerStatus(PteroServerStatus.INSTALLING);
							continue;
						}
						
						instance.updateStats(server.retrieveUtilization().execute());
					}

					if (server.isSuspended()) {
						instance.updateServerStatus(PteroServerStatus.SUSPENDED);
						continue;
					} else if (server.isInstalling()) {
						instance.updateServerStatus(PteroServerStatus.INSTALLING);
						continue;
					} else if (server.isTransferring()) {
						instance.updateServerStatus(PteroServerStatus.TRANSFERING);
						continue;
					}

					instance.updateServerStatus(PteroServerStatus.READY);

					if (this.serverInstallation.contains(identifier)) {
						if (server.isInstalling() || server.isSuspended() || server.isTransferring()) {
							continue;
						}

						this.serverInstallation.remove(identifier);
						if (instance.getTemplate() == null) {
							instance.notifyMessage("Installing skipped. No template!");
							continue;
						}

						final ServerInstance finalInstance = instance;
						finalInstance.notifyMessage("Installing template...");
						finalInstance.setupServer().whenComplete((__, error) -> {
							if (error != null) {
								TestsuiteLogger.error(error, "Install failed!");
								finalInstance.notifyMessage("Install failed!");
							} else {
								finalInstance.notifyMessage("Installed.");
							}
						});
					}
				}

				for (Iterator<ServerInstance> iterator = this.serverInstances.values().iterator(); iterator.hasNext(); ) {
					ServerInstance instance = iterator.next();
					boolean found = false;
					for (ClientServer server : serverList) {
						if (server.getIdentifier().equals(instance.getIdentifier())) {
							found = true;
							break;
						}
					}

					if (!found) {
						try {
							instance.close();
						} finally {
							iterator.remove();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, (error) -> {
			error.printStackTrace();
		});
	}

	public CompletableFuture<Void> deleteInstance(String identifier) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		this.pteroApplication.retrieveServerById(identifier).executeAsync(server -> {
			server.getController().delete(false).executeAsync((__) -> {
				this.lastUpdate.getAndSet(0);
				future.complete(__);
			}, future::completeExceptionally);
		}, future::completeExceptionally);

		return future;
	}

	public CompletableFuture<ApplicationServer> create(ServerTemplate template, ServerType type, String version) {
		return this.create(
				String.format("%s-%s-%s", template.getName(), type.name().toLowerCase(), version),
				template.getName(),
				type,
				version);
	}

	public CompletableFuture<ApplicationServer> create(String name, ServerType type, String version) {
		return this.create(
				name,
				"",
				type,
				version);
	}

	private CompletableFuture<ApplicationServer> create(String name, String description, ServerType type, String version) {
		CompletableFuture<ApplicationServer> future = new CompletableFuture<>();

		CompletableFuture<ApplicationUser> futureUser = this.receiveUser();
		CompletableFuture<ApplicationEgg> futureEgg = this.receiveEgg(type);
		CompletableFuture<ApplicationAllocation> futureAllocation = this.allocationAssignment.createAllocation();
		CompletableFuture.allOf(futureUser, futureEgg, futureAllocation).whenComplete((__, error) -> {
			if (error != null) {
				future.completeExceptionally(error);
				return;
			}

			ApplicationUser user = futureUser.getNow(null);
			if (user == null) {
				future.completeExceptionally(new IllegalArgumentException("unable to locale user"));
				return;
			}

			ApplicationEgg egg = futureEgg.getNow(null);
			if (egg == null) {
				future.completeExceptionally(new IllegalArgumentException("unable to locate egg"));
				return;
			}

			ApplicationAllocation allocation = futureAllocation.getNow(null);
			if (allocation == null) {
				future.completeExceptionally(new IllegalArgumentException("unable to create allocation"));
				return;
			}

			Map<String, EnvironmentValue<?>> environment = new HashMap<>();
			environment.put("MINECRAFT_VERSION", EnvironmentValue.of(version));
			environment.put("DL_VERSION", EnvironmentValue.of(version));

			MinecraftVersion minecraftVersion;
			try {
				minecraftVersion = new MinecraftVersion(version);
			} catch (Exception e) {
				future.completeExceptionally(new IllegalArgumentException("Minecraft version dosn't match pattern: <major>.<minor>.<patch>"));
				return;
			}

			String dockerImage = null;
			if (minecraftVersion.isAtOrAbove(VERSION_26_1)) {
				dockerImage = "ghcr.io/pelican-eggs/yolks:java_25";
			} else if (minecraftVersion.isAtOrAbove(VERSION_1_21)) {
				dockerImage = "ghcr.io/pelican-eggs/yolks:java_21";
			} else if (minecraftVersion.isAtOrAbove(VERSION_1_17_1)) {
				dockerImage = "ghcr.io/pelican-eggs/yolks:java_17";
			} else {
				dockerImage = "ghcr.io/pelican-eggs/yolks:java_8";
			}

			var createRequest = this.pteroApplication.createServer()
				.setName(name)
				.setDescription(description)
				.setOwner(user)
				.setEgg(egg)
				.setAllocation(allocation)
				.setEnvironment(environment)
				.setDockerImage(dockerImage)
				.startOnCompletion(false);

			if (serverConfig.storage() > 0) {
				createRequest.setDisk(serverConfig.storage(), DataType.GB);
			}
			
			if (serverConfig.memory() > 0) {
				createRequest.setMemory(serverConfig.memory(), DataType.GB);
			}
			
			createRequest.executeAsync((server) -> {
					this.serverInstallation.add(server.getIdentifier());
					this.lastUpdate.getAndSet(0);
					future.complete(server);
				}, future::completeExceptionally);
		});

		return future;
	}

	private CompletableFuture<ApplicationEgg> receiveEgg(ServerType type) {
		CompletableFuture<ApplicationEgg> future = new CompletableFuture<>();
		this.pteroApplication.retrieveEggs().executeAsync(
			eggs -> {
				for (ApplicationEgg egg : eggs) {
					if (egg.getName().equalsIgnoreCase(type.getEggName())) {
						future.complete(egg);
						return;
					}
				}
				future.complete(null);
			}, future::completeExceptionally);
		return future;
	}

	private CompletableFuture<ApplicationUser> receiveUser() {
		CompletableFuture<ApplicationUser> future = new CompletableFuture<>();
		this.pteroClient.retrieveAccount().executeAsync(
			account -> {
				this.pteroApplication.retrieveUserByUuid(account.getUuid()).executeAsync(
						future::complete,
						future::completeExceptionally);
			}, future::completeExceptionally);
		return future;
	}

	public ServerInstance getServer(String name) {
		for (ServerInstance instance : this.serverInstances.values()) {
			if (instance.getName().equalsIgnoreCase(name)) {
				return instance;
			}
		}
		return null;
	}

	public Collection<ServerInstance> getServers() {
		return Collections.unmodifiableCollection(this.serverInstances.values());
	}

	public TestsuitePlugin getPlugin() {
		return this.plugin;
	}
}