package dev.imprex.testsuite.server;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

import com.mattmalec.pterodactyl4j.application.entities.ApplicationAllocation;
import com.mattmalec.pterodactyl4j.application.entities.Node;
import com.mattmalec.pterodactyl4j.application.entities.PteroApplication;

import dev.imprex.testsuite.TestsuitePlugin;
import dev.imprex.testsuite.config.AllocationConfig;
import dev.imprex.testsuite.server.meta.PortRange;
import dev.imprex.testsuite.util.PteroUtil;

public class AllocationAssignment {

	private final PteroApplication pteroApplication;

	private String nodeName;
	private String aliasIdentifier;
	private String ipv4;
	private int minPort;
	private int maxPort;

	public AllocationAssignment(TestsuitePlugin plugin, AllocationConfig config) {
		this.pteroApplication = plugin.getPteroApplication();
		this.nodeName = config.nodeName();
		this.aliasIdentifier = config.aliasIdentifier();
		this.ipv4 = config.ipv4();
		this.minPort = config.minPort();
		this.maxPort = config.maxPort();
	}

	public CompletableFuture<ApplicationAllocation> createAllocation() {
		CompletableFuture<ApplicationAllocation> future = new CompletableFuture<>();

		this.receiveNode().whenComplete((node, error) -> {
			if (error != null) {
				future.completeExceptionally(error);
				return;
			}

			PortRange portRange = new PortRange(this.minPort, this.maxPort);

			PteroUtil.execute(node.retrieveAllocations().all())
				.thenApply(allocations -> this.selectAllocation(portRange, allocations))
				.whenComplete((allocation, error2) -> {
					if (error2 != null) {
						future.completeExceptionally(error2);
						return;
					}

					if (allocation != null) {
						future.complete(allocation);
						return;
					}

					this.createAllocation(node, portRange)
						.thenCompose(__ -> this.receiveNode())
						.thenCompose(node2 -> PteroUtil.execute(node2.retrieveAllocations().all()))
						.thenApply(allocations -> this.selectAllocation(new PortRange(this.minPort, this.maxPort), allocations))
						.whenComplete((allocation2, error3) -> {
							if (error3 != null) {
								error3.printStackTrace();
								future.completeExceptionally(error3);
							} else {
								future.complete(allocation2);
							}
						});
				});
		});

		return future;
	}

	private CompletableFuture<Void> createAllocation(Node node, PortRange portRange) {
		int port = portRange.peekFreePort();
		if (port == -1) {
			return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Port range is full"));
		}

		CompletableFuture<Void> future = new CompletableFuture<>();
		node.getAllocationManager().createAllocation()
			.setIP(this.ipv4)
			.setAlias(this.aliasIdentifier)
			.setPorts(Integer.toString(port))
			.executeAsync(future::complete, future::completeExceptionally);

		return future;
	}

	private ApplicationAllocation selectAllocation(PortRange portRange, List<ApplicationAllocation> allocations) {
		for (ApplicationAllocation allocation : allocations) {
			if (!this.isAllocation(allocation)) {
				portRange.markUsedPort(allocation.getPortInt());
				continue;
			}

			if (!allocation.isAssigned()) {
				return allocation;
			}

			portRange.markUsedPort(allocation.getPortInt());
		}
		return null;
	}

	private CompletableFuture<Node> receiveNode() {
		CompletableFuture<Node> future = new CompletableFuture<>();
		this.pteroApplication.retrieveNodesByName(this.nodeName, false).executeAsync(
			nodes -> {
				if (nodes.size() > 0) {
					future.complete(nodes.get(0));
				} else {
					future.completeExceptionally(new NoSuchElementException("No node named " + this.nodeName + " were found!"));
				}
			},
			error -> {
				future.completeExceptionally(error);
			});
		return future;
	}

	public boolean isAllocation(ApplicationAllocation allocation) {
		return allocation.getAlias() != null &&
				allocation.getAlias().equalsIgnoreCase(this.aliasIdentifier) &&
				allocation.getIP().equals(this.ipv4) &&
				allocation.getPortInt() >= this.minPort &&
				allocation.getPortInt() <= this.maxPort;
	}
}