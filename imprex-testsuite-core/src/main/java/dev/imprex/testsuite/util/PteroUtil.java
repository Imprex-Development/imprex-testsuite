package dev.imprex.testsuite.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.mattmalec.pterodactyl4j.PteroAction;
import com.mattmalec.pterodactyl4j.client.entities.ClientServer;
import com.mattmalec.pterodactyl4j.client.entities.Directory;
import com.mattmalec.pterodactyl4j.client.managers.UploadFileAction;

public class PteroUtil {

	public static <T> CompletableFuture<T> execute(PteroAction<T> action) {
		CompletableFuture<T> future = new CompletableFuture<>();
		action.executeAsync(future::complete, future::completeExceptionally);
		return future;
	}

	public static <T> CompletableFuture<T> execute(PteroAction<T> action, long delay, TimeUnit unit) {
		CompletableFuture<T> future = new CompletableFuture<>();
		action.delay(delay, unit).executeAsync(future::complete, future::completeExceptionally);
		return future;
	}

	public static CompletableFuture<Void> updateDirectory(ClientServer server, int skipPathPrefix, List<Path> fileList) {
		return execute(server.retrieveDirectory()).thenCompose(directory -> updateDirectory(server, skipPathPrefix, fileList, directory));
	}

	public static CompletableFuture<Void> updateDirectory(ClientServer server, int skipPathPrefix, List<Path> fileList, Directory directoryRoot) {
		String directoryPath = directoryRoot.getPath();
		if (directoryPath.startsWith("/")) {
			directoryPath = directoryPath.substring(1);
		}

		UploadFileAction uploadFileAction = directoryRoot.upload();
		boolean needUpload = false;

		List<CompletableFuture<Void>> futureList = new ArrayList<>();
		for (Path file : fileList) {
			String testPath = file.getParent().toString().substring(skipPathPrefix);
			String tylPath = (testPath.startsWith("/") || testPath.startsWith("\\")) ? testPath.substring(1) : testPath;

			if (!directoryPath.equals(tylPath)) {
				continue;
			}
			fileList.remove(file);

			CompletableFuture<Void> future = new CompletableFuture<>();
			futureList.add(future);

			if (Files.isDirectory(file)) {
				String direcoryName = file.getFileName().toString();
				String fullTylPath = directoryPath.length() == 0 ? direcoryName : (directoryPath + "/" + direcoryName);
				directoryRoot.createFolder(direcoryName).executeAsync(__ -> {
					server.retrieveDirectory(fullTylPath).executeAsync(directory -> {
						updateDirectory(server, skipPathPrefix, fileList, directory).whenComplete((___, error) -> {
							if (error != null) {
								future.completeExceptionally(error);
							} else {
								future.complete(null);
							}
						});
					}, error -> {
						future.completeExceptionally(error);
					});
				}, error -> {
					future.completeExceptionally(error);
				});
			} else {
				try {
					InputStream inputStream = Files.newInputStream(file); // will be closed after uploading
					uploadFileAction.addFile(inputStream, file.getFileName().toString());
					needUpload = true;
					future.complete(null);
				} catch (IOException e) {
					future.completeExceptionally(e);
				}
			}
		}

		if (needUpload) {
			CompletableFuture<Void> future = new CompletableFuture<>();
			uploadFileAction.executeAsync(__ -> {
				uploadFileAction.clearFiles(); // we need to clear all files because the input stream will not close after execution !?
				future.complete(null);
			},
			error -> {
				future.completeExceptionally(error);
			});
			futureList.add(future);
		}

		return CompletableFuture.allOf(futureList.toArray(CompletableFuture[]::new));
	}
}
