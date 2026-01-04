package com.embeddedmc;

import com.embeddedmc.config.ModConfig;
import com.embeddedmc.server.ServerManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EmbeddedMC implements ModInitializer {
    public static final String MOD_ID = "embeddedmc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EmbeddedMC instance;
    private ModConfig config;
    private ServerManager serverManager;
    private Path dataPath;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("EmbeddedMC initializing...");

        // Setup data directory
        dataPath = Paths.get(System.getProperty("user.dir"), "embeddedmc");

        // Load config
        config = ModConfig.load(dataPath.resolve("config.json"));

        // Initialize server manager
        serverManager = new ServerManager(dataPath.resolve("instances"));

        // Add shutdown hook to stop all servers when game closes
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down EmbeddedMC...");
            if (serverManager != null) {
                serverManager.stopAllServers();
            }
        }, "EmbeddedMC-Shutdown"));

        LOGGER.info("EmbeddedMC initialized! Data path: {}", dataPath);
    }

    public static EmbeddedMC getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public Path getDataPath() {
        return dataPath;
    }
}
