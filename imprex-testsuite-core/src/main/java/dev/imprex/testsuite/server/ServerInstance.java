package dev.imprex.testsuite.server;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.mattmalec.pterodactyl4j.UtilizationState;
import com.mattmalec.pterodactyl4j.client.entities.ClientAllocation;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.Directory;
import com.mattmalec.pterodactyl4j.client.entities.GenericFile;
import com.mattmalec.pterodactyl4j.client.entities.Utilization;
import com.mattmalec.pterodactyl4j.client.managers.WebSocketManager;

import dev.imprex.testsuite.TestsuiteLogger;
import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.api.TestsuitePlayer;
import dev.imprex.testsuite.api.TestsuiteServer;
import dev.imprex.testsuite.override.OverrideAction;
import dev.imprex.testsuite.override.OverrideException;
import dev.imprex.testsuite.template.ServerTemplate;
import dev.imprex.testsuite.template.ServerTemplateList;
import dev.imprex.testsuite.util.Chat;
import dev.imprex.testsuite.util.ChatMessageBuilder;
import dev.imprex.testsuite.util.EmptyUtilization;
import dev.imprex.testsuite.util.FancyComponent;
import dev.imprex.testsuite.util.PteroServerStatus;
import dev.imprex.testsuite.util.PteroUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class ServerInstance implements TestsuiteServer, Runnable {

	private static final long MAX_INACTIVE_TIME = TimeUnit.MINUTES.toMillis(5);

	private final ServerManager manager;
	private final ClientServer server;

	private WebSocketManager webSocketManager;
	private Lock webSocketLock = new ReentrantLock();

	private ServerTemplate template;
	private AtomicReference<Utilization> stats = new AtomicReference<>(new EmptyUtilization());
	private AtomicReference<UtilizationState> status = new AtomicReference<>(UtilizationState.OFFLINE);
	private AtomicReference<PteroServerStatus> serverStatus = new AtomicReference<>(PteroServerStatus.UNKNOWN);

	private AtomicLong inactiveTime = new AtomicLong(System.currentTimeMillis());
	private AtomicBoolean idleTimeout = new AtomicBoolean(false);
	
	private Set<UUID> joinAfterStart = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public ServerInstance(ServerManager manager, ClientServer server) {
		this.manager = manager;
		this.server = server;

		ServerTemplateList templateList = this.manager.getPlugin().getTemplateList();
		this.template = templateList.getTemplate(this.server.getDescription());

		TestsuitePlugin plugin = this.manager.getPlugin();
		plugin.registerServerList(this);

		this.server.retrieveUtilization().executeAsync(stats -> {
			this.updateStats(stats);
		}, error -> {
			TestsuiteLogger.error(error, "[{0}] Error fetching current server stats!", this.getName());
		});
	}

	@Override
	public void run() {
		if (this.template == null) {
			return;
		}

		this.webSocketLock.lock();
		try {
			if (this.webSocketManager == null) {
				return;
			}
		} finally {
			this.webSocketLock.unlock();
		}

		if (this.inactiveTime.get() > System.currentTimeMillis()) {
			return;
		}
		this.resetInactiveTime();

		UtilizationState status = this.status.get();
		if (status == UtilizationState.RUNNING &&
				this.idleTimeout.get() &&
				this.getPlayers().isEmpty()) {
			this.notifyMessage("Stopping duo to inactivity");
			this.stop();
		} else if (status == UtilizationState.OFFLINE) {
			PteroServerStatus serverStatus = this.serverStatus.get();
			if (serverStatus != PteroServerStatus.INSTALLING) {
				this.unsubscribe();
			}
		}
	}

	void updateServerStatus(PteroServerStatus serverStatus) {
		if (this.serverStatus.getAndSet(serverStatus) != serverStatus) {
			Chat.builder(this)
				.append(Component.text("Status: ").color(Chat.Color.GRAY))
				.append(FancyComponent.beautifyServerStatus(serverStatus))
				.broadcast();

			if (serverStatus == PteroServerStatus.INSTALLING) {
				this.subscribe();
			}
		}
	}

	void updateStats(Utilization stats) {
		this.stats.getAndSet(stats);
		this.updateStatus(stats.getState());
	}

	void updateStatus(UtilizationState status) {
		if (this.status.getAndSet(status) != status) {
			ChatMessageBuilder<?> message = Chat.builder(this)
				.append(Component.text("Status: ").color(Chat.Color.GRAY))
				.append(FancyComponent.beautifyUtilizationState(status));
			
			if (status == UtilizationState.RUNNING) {
				message.append(Component.text(" "));
				message.append(Component.empty()
						.append(Component.text("[").color(Chat.Color.GRAY))
						.append(Component.text("Join")
								.clickEvent(ClickEvent.runCommand("tc " + this.getName()))
								.hoverEvent(HoverEvent.showText(Component.text("Join server").color(Chat.Color.LIGHT_GREEN)))
								.color(Chat.Color.LIGHT_GREEN))
						.append(Component.text("]").color(Chat.Color.GRAY)));
			}
			message.broadcast();

			if (status == UtilizationState.RUNNING) {
				this.override(true).whenComplete((changes, error) -> {
					if (error != null) {
						error.printStackTrace();
						TestsuiteLogger.info("Unable to execute override file for server {0} because {1}", this.getName(), error.getMessage());
					} else if (changes != 0) {
						this.notifyMessage("override has changed {0} values", changes);
					}
				});
				
				if (!this.joinAfterStart.isEmpty()) {
					this.joinAfterStart.stream()
						.map(this.manager.getPlugin()::getPlayer)
						.forEach(this::sendToServer);
					this.joinAfterStart.clear();
				}
			}

			if (status == UtilizationState.OFFLINE) {
				// Wait 5min. for some other actions and then close the web socket
//				this.unsubscribe();
			} else {
				this.subscribe();
			}
		}
	}

	public void sendToServer(TestsuitePlayer player) {
		player.connect(this).whenComplete((result, error) -> {
			if (error != null) {
				error.printStackTrace();

				Chat.builder().append("Unable to connect too server! " + error.getMessage()).send(player);
				return;
			}

			switch (result) {
			case SUCCESS -> {
				Chat.builder().append("Successful connected to server {0}.", this.getName()).send(player);
			}
			case ALREADY_CONNECTED -> Chat.builder().append("Your already connected").send(player);
			case CONNECTION_CANCELLED -> Chat.builder().append("Connection was cancelled").send(player);
			case CONNECTION_IN_PROGRESS -> Chat.builder().append("Connection is in progress").send(player);
			case SERVER_DISCONNECTED -> Chat.builder().append("Server disconnected").send(player);
			}
		});
	}

	public void subscribe() {
		this.resetInactiveTime();

		this.webSocketLock.lock();
		try {
			if (this.webSocketManager == null) {
				this.resetInactiveTime();
				this.webSocketManager = this.server.getWebSocketBuilder()
						.addEventListeners(new ServerListener(this))
						.build();
				this.notifyMessage("Connecting to websocket...");
			}
		} finally {
			this.webSocketLock.unlock();
		}
	}

	public void unsubscribe() {
		this.webSocketLock.lock();
		try {
			if (this.webSocketManager != null) {
				try {
					this.webSocketManager.shutdown();
				} catch (IllegalStateException e) {
					// Ignore already shutdown message
				}

				this.webSocketManager = null;
				this.notifyMessage("Disonnected from websocket.");
			}
		} finally {
			this.webSocketLock.unlock();
		}
	}

	public CompletableFuture<Void> setupServer() {
		if (!this.hasTemplate()) {
			return CompletableFuture.failedFuture(new NullPointerException("No template instance"));
		}

		return this.deletePluginJars()
				.thenAccept(__ -> this.uploadServerFiles());
	}
	
	private CompletableFuture<Void> deletePluginJars() {
		return PteroUtil.execute(this.server.retrieveDirectory())
				.thenCompose(directory -> {
					Optional<Directory> optional = directory.getDirectoryByName("plugins");
					if (optional.isEmpty()) {
						return CompletableFuture.completedFuture(null);
					}
					
					return PteroUtil.execute(directory.into(optional.get()));
				})
				.thenCompose(directory -> {
					if (directory == null) {
						return CompletableFuture.completedFuture(null);
					}
					
					List<CompletableFuture<Void>> deleteRequest = new ArrayList<CompletableFuture<Void>>();
					for (GenericFile file : directory.getFiles()) {
						if (file.getName().endsWith(".jar")) {
							deleteRequest.add(PteroUtil.execute(file.delete()));
						}
					}
					
					return CompletableFuture.allOf(deleteRequest.toArray(CompletableFuture[]::new));
				});
	}
	
	private CompletableFuture<Void> uploadServerFiles() {
		List<Path> fileList = new CopyOnWriteArrayList<>(this.template.getFiles());
		int pathPrefix = this.template.getPath().toString().length();

		return PteroUtil.updateDirectory(server, pathPrefix, fileList)
				.thenAccept(__ -> System.gc()); // TODO test phase (memory issue with pterodactyl4j api)
	}

	public CompletableFuture<Void> executeCommand(String command) {
		this.subscribe();
		return PteroUtil.execute(this.server.sendCommand(command));
	}

	public CompletableFuture<Void> start() {
		this.setIdleTimeout(true);
		
//		this.subscribe(); // is already called in override
		return this.override(false).whenComplete((changes, error) -> {
			if (error != null) {
				if (error instanceof OverrideException) {
					TestsuiteLogger.info("Unable to execute override file for server {0} because {1}", this.getName(), error.getMessage());
				} else {
					error.printStackTrace();
					TestsuiteLogger.info("Unable to execute override file for server {0}", this.getName());
				}
			} else if (changes != 0) {
				this.notifyMessage("Override has changed {0} variables", changes);
			}
		}).thenCompose(__ -> {
			this.subscribe();
			return PteroUtil.execute(this.server.start());
		});
	}

	public CompletableFuture<Void> restart() {
		this.setIdleTimeout(true);

		this.subscribe();
		
		this.joinAfterStart.clear();
		this.getPlayers().stream()
			.map(player -> player.getUUID())
			.forEach(this.joinAfterStart::add);
		
		return PteroUtil.execute(this.server.restart());
	}

	public CompletableFuture<Void> stop() {
		this.subscribe();
		return PteroUtil.execute(this.server.stop());
	}

	public CompletableFuture<Void> kill() {
		this.subscribe();
		return PteroUtil.execute(this.server.kill());
	}

	public CompletableFuture<Void> reinstall() {
		this.subscribe();
		return PteroUtil.execute(this.server.getManager().reinstall());
	}

	public CompletableFuture<Integer> override() {
		return this.override(this.status.get() == UtilizationState.RUNNING);
	}

	public CompletableFuture<Integer> override(boolean overrideAfterStart) {
//		this.subscribe();
		return OverrideAction.override(this, overrideAfterStart);
	}

	public CompletableFuture<Void> delete() {
		return this.manager.deleteInstance(this.getClientServer().getInternalId());
	}

	public void resetInactiveTime() {
		this.inactiveTime.getAndSet(System.currentTimeMillis() + MAX_INACTIVE_TIME);
	}
	
	public boolean setIdleTimeout(boolean state) {
		return this.idleTimeout.compareAndSet(!state, state);
	}

	public boolean toggleIdleTimeout() {
		boolean state = this.idleTimeout.get();
		return this.idleTimeout.compareAndSet(state, !state) ? !state : state;
	}
	
	public boolean isIdleTimeout() {
		return this.idleTimeout.get();
	}

	public void notifyMessage(String message, Object... arguments) {
		Chat.builder(this).append(message, arguments).broadcast();
	}

	public void close() {
		this.notifyMessage("Removing server instance");
		this.manager.getPlugin().unregisterServerList(this.getName());
		this.unsubscribe();
	}

	public WebSocketManager getWebSocketManager() {
		this.webSocketLock.lock();
		try {
			return this.webSocketManager;
		} finally {
			this.webSocketLock.unlock();
		}
	}

	public Utilization getStats() {
		return this.stats.get();
	}

	public UtilizationState getStatus() {
		return this.status.get();
	}

	public PteroServerStatus getServerStatus() {
		return this.serverStatus.get();
	}

	public boolean hasTemplate() {
		return this.template != null;
	}

	public ServerTemplate getTemplate() {
		return this.template;
	}

	public String getIdentifier() {
		return this.server.getIdentifier();
	}

	public long getInactiveTime() {
		return this.inactiveTime.get();
	}

	public String getName() {
		return this.server.getName();
	}

	@Override
	public Optional<String> getAddress() {
		Optional<ClientAllocation> allocation = this.server.getPrimaryAllocation();
		return allocation.map(ClientAllocation::getIP);
	}

	@Override
	public Optional<Integer> getPort() {
		Optional<ClientAllocation> allocation = this.server.getPrimaryAllocation();
		return allocation.map(ClientAllocation::getPortInt);
	}

	public ClientServer getClientServer() {
		return this.server;
	}

	public ServerManager getManager() {
		return this.manager;
	}

	@Override
	public void broadcast(Component component) {
		this.getPlayers().forEach(player -> player.sendMessage(component));
	}

	@Override
	public List<TestsuitePlayer> getPlayers() {
		return this.manager.getPlugin().getPlayers(this);
	}
}