package com.embeddedmc.server;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ServerManager {
    private final Path instancesPath;
    private final Map<String, ServerInstance> instances = new HashMap<>();
    private final Map<String, EmbeddedServer> runningServers = new HashMap<>();

    public ServerManager(Path instancesPath) {
        this.instancesPath = instancesPath;
        loadInstances();
    }

    private void loadInstances() {
        if (!Files.exists(instancesPath)) {
            try {
                Files.createDirectories(instancesPath);
            } catch (IOException e) {
                EmbeddedMC.LOGGER.error("Failed to create instances directory", e);
            }
            return;
        }

        try (Stream<Path> dirs = Files.list(instancesPath)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    ServerInstance instance = ServerInstance.load(dir);
                    if (instance != null) {
                        instances.put(instance.getId(), instance);
                        EmbeddedMC.LOGGER.info("Loaded instance: {} ({})", instance.getName(), instance.getId());
                    }
                } catch (IOException e) {
                    EmbeddedMC.LOGGER.error("Failed to load instance from {}", dir, e);
                }
            });
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to list instances directory", e);
        }
    }

    public ServerInstance createInstance(String name, ServerType type, String mcVersion) {
        ServerInstance instance = new ServerInstance();
        instance.setName(name);
        instance.setType(type);
        instance.setMcVersion(mcVersion);
        instance.setRamMB(EmbeddedMC.getInstance().getConfig().getDefaultRamMB());
        instance.setPort(findAvailablePort());
        instance.setInstancePath(instancesPath.resolve(instance.getId()));

        try {
            instance.ensureDirectories();
            instance.save();
            instances.put(instance.getId(), instance);
            EmbeddedMC.LOGGER.info("Created instance: {} ({})", name, instance.getId());
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to create instance", e);
            return null;
        }

        return instance;
    }

    public void deleteInstance(String id) {
        ServerInstance instance = instances.remove(id);
        if (instance != null) {
            // Stop if running
            stopServer(id);

            // Delete files
            try {
                deleteDirectory(instance.getInstancePath());
                EmbeddedMC.LOGGER.info("Deleted instance: {}", id);
            } catch (IOException e) {
                EmbeddedMC.LOGGER.error("Failed to delete instance files", e);
            }
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            EmbeddedMC.LOGGER.error("Failed to delete: {}", p, e);
                        }
                    });
            }
        }
    }

    public void startServer(String id, Runnable onReady) {
        ServerInstance instance = instances.get(id);
        if (instance == null) {
            EmbeddedMC.LOGGER.error("Instance not found: {}", id);
            return;
        }

        if (runningServers.containsKey(id)) {
            EmbeddedMC.LOGGER.warn("Server already running: {}", id);
            return;
        }

        EmbeddedServer server = new EmbeddedServer(instance);
        boolean started = server.start(onReady);

        if (started) {
            runningServers.put(id, server);
        }
    }

    public void stopAllServers() {
        EmbeddedMC.LOGGER.info("Stopping all running servers...");
        for (String id : new ArrayList<>(runningServers.keySet())) {
            stopServer(id);
        }
        EmbeddedMC.LOGGER.info("All servers stopped.");
    }

    public void stopServer(String id) {
        EmbeddedServer server = runningServers.remove(id);
        if (server != null) {
            server.stop();
        }
    }

    public boolean isRunning(String id) {
        return runningServers.containsKey(id) && runningServers.get(id).isRunning();
    }

    public EmbeddedServer getServer(String id) {
        return runningServers.get(id);
    }

    /**
     * Get any running server instance for console access
     */
    public ServerInstance getAnyRunningInstance() {
        for (String id : runningServers.keySet()) {
            if (runningServers.get(id).isRunning()) {
                return instances.get(id);
            }
        }
        return null;
    }

    /**
     * Check if any server is currently running
     */
    public boolean hasRunningServer() {
        for (EmbeddedServer server : runningServers.values()) {
            if (server.isRunning()) {
                return true;
            }
        }
        return false;
    }

    public List<ServerInstance> getInstances() {
        return new ArrayList<>(instances.values());
    }

    public ServerInstance getInstance(String id) {
        return instances.get(id);
    }

    private int findAvailablePort() {
        int port = EmbeddedMC.getInstance().getConfig().getDefaultPort();
        while (isPortInUse(port)) {
            port++;
            if (port > 25600) {
                port = 25565;
                break;
            }
        }
        return port;
    }

    private boolean isPortInUse(int port) {
        for (ServerInstance instance : instances.values()) {
            if (instance.getPort() == port && isRunning(instance.getId())) {
                return true;
            }
        }
        return false;
    }
}
