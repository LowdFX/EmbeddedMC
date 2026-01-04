package com.embeddedmc.download;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import com.embeddedmc.server.ServerType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DownloadManager {
    private static final String USER_AGENT = "EmbeddedMC/1.0.0 (https://github.com/marti/EmbeddedMC)";

    public static CompletableFuture<Boolean> downloadServer(ServerInstance instance, Consumer<DownloadProgress> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ServerType type = instance.getType();
                String version = instance.getMcVersion();

                EmbeddedMC.LOGGER.info("Downloading {} for version {}...", type.getDisplayName(), version);

                if (progressCallback != null) {
                    progressCallback.accept(new DownloadProgress(0, 0, "Resolving version..."));
                }

                // Get download URL based on server type
                String downloadUrl = resolveDownloadUrl(type, version, instance);

                if (downloadUrl == null) {
                    EmbeddedMC.LOGGER.error("Could not resolve download URL for {} {}", type, version);
                    return false;
                }

                EmbeddedMC.LOGGER.info("Download URL: {}", downloadUrl);

                // Download the file
                Path targetPath = instance.getServerJar();
                downloadFile(downloadUrl, targetPath, progressCallback);

                EmbeddedMC.LOGGER.info("Download complete: {}", targetPath);
                return true;

            } catch (Exception e) {
                EmbeddedMC.LOGGER.error("Download failed", e);
                if (progressCallback != null) {
                    progressCallback.accept(new DownloadProgress(-1, -1, "Error: " + e.getMessage()));
                }
                return false;
            }
        });
    }

    private static String resolveDownloadUrl(ServerType type, String version, ServerInstance instance) throws IOException {
        return switch (type) {
            case PAPER, FOLIA -> PaperAPI.getDownloadUrl(type, version);
            case PURPUR -> PurpurAPI.getDownloadUrl(version);
            case SPIGOT -> null; // Spigot needs BuildTools, not supported yet
        };
    }

    private static void downloadFile(String urlString, Path target, Consumer<DownloadProgress> progressCallback) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP error: " + responseCode);
        }

        long totalSize = connection.getContentLengthLong();
        Files.createDirectories(target.getParent());

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            long lastUpdate = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                // Update progress every 100ms
                if (System.currentTimeMillis() - lastUpdate > 100 && progressCallback != null) {
                    lastUpdate = System.currentTimeMillis();
                    String statusText;
                    if (totalSize > 0) {
                        int percent = (int) (downloaded * 100 / totalSize);
                        statusText = String.format("Downloading... %d%% (%.1f MB / %.1f MB)",
                            percent, downloaded / 1024.0 / 1024.0, totalSize / 1024.0 / 1024.0);
                    } else {
                        statusText = String.format("Downloading... %.1f MB", downloaded / 1024.0 / 1024.0);
                    }
                    progressCallback.accept(new DownloadProgress(downloaded, totalSize, statusText));
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(new DownloadProgress(totalSize, totalSize, "Complete!"));
        }
    }

    public record DownloadProgress(long downloaded, long total, String status) {
        public int getPercent() {
            if (total <= 0) return -1;
            return (int) (downloaded * 100 / total);
        }
    }
}
