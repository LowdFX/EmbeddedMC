package com.embeddedmc.config;

import com.embeddedmc.server.ServerType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class ServerInstance {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String id;
    private String name;
    private ServerType type;
    private String mcVersion;
    private int build;
    private int ramMB;
    private int port;
    private int maxPlayers;
    private List<String> jvmArgs;
    private boolean autoStart;
    private transient Path instancePath;
    private transient ServerStatus status;

    public enum ServerStatus {
        STOPPED, STARTING, RUNNING, STOPPING, ERROR
    }

    public ServerInstance() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = "New Server";
        this.type = ServerType.PAPER;
        this.mcVersion = "1.21.11";
        this.build = -1; // Latest
        this.ramMB = 2048;
        this.port = 25565;
        this.maxPlayers = 20;
        this.jvmArgs = new ArrayList<>();
        this.jvmArgs.add("-XX:+UseG1GC");
        this.autoStart = false;
        this.status = ServerStatus.STOPPED;
    }

    public static ServerInstance load(Path instancePath) throws IOException {
        Path configFile = instancePath.resolve("instance.json");
        if (Files.exists(configFile)) {
            String json = Files.readString(configFile);
            ServerInstance instance = GSON.fromJson(json, ServerInstance.class);
            instance.instancePath = instancePath;
            instance.status = ServerStatus.STOPPED;
            return instance;
        }
        return null;
    }

    public void save() throws IOException {
        if (instancePath == null) {
            throw new IllegalStateException("Instance path not set");
        }
        Files.createDirectories(instancePath);
        Path configFile = instancePath.resolve("instance.json");
        Files.writeString(configFile, GSON.toJson(this));
    }

    public Path getServerJar() {
        return instancePath.resolve("server.jar");
    }

    public Path getPluginsDir() {
        return instancePath.resolve("plugins");
    }

    public Path getWorldDir() {
        return instancePath.resolve("world");
    }

    public Path getServerProperties() {
        return instancePath.resolve("server.properties");
    }

    public Path getEulaFile() {
        return instancePath.resolve("eula.txt");
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(instancePath);
        Files.createDirectories(getPluginsDir());
    }

    public void acceptEula() throws IOException {
        Files.writeString(getEulaFile(), "eula=true\n");
    }

    public Properties loadServerProperties() throws IOException {
        Properties props = new Properties();
        Path propsFile = getServerProperties();
        if (Files.exists(propsFile)) {
            props.load(Files.newBufferedReader(propsFile));
        } else {
            // Set defaults
            props.setProperty("server-port", String.valueOf(port));
            props.setProperty("online-mode", "false");
            props.setProperty("spawn-protection", "0");
            props.setProperty("max-players", String.valueOf(maxPlayers));
            props.setProperty("level-name", "world");
        }
        return props;
    }

    public void saveServerProperties(Properties props) throws IOException {
        props.store(Files.newBufferedWriter(getServerProperties()), "EmbeddedMC Server Properties");
    }

    /**
     * Ensures critical server properties are set correctly for embedded mode.
     * This should be called before each server start.
     */
    public void configureForEmbeddedMode() throws IOException {
        Properties props = loadServerProperties();
        // Force offline mode for local embedded server
        props.setProperty("online-mode", "false");
        // Set the configured port
        props.setProperty("server-port", String.valueOf(port));
        // Disable spawn protection for singleplayer experience
        props.setProperty("spawn-protection", "0");
        // Set max players
        props.setProperty("max-players", String.valueOf(maxPlayers));
        saveServerProperties(props);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ServerType getType() { return type; }
    public void setType(ServerType type) { this.type = type; }

    public String getMcVersion() { return mcVersion; }
    public void setMcVersion(String mcVersion) { this.mcVersion = mcVersion; }

    public int getBuild() { return build; }
    public void setBuild(int build) { this.build = build; }

    public int getRamMB() { return ramMB; }
    public void setRamMB(int ramMB) { this.ramMB = ramMB; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public List<String> getJvmArgs() { return jvmArgs; }
    public void setJvmArgs(List<String> jvmArgs) { this.jvmArgs = jvmArgs; }

    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }

    public Path getInstancePath() { return instancePath; }
    public void setInstancePath(Path instancePath) { this.instancePath = instancePath; }

    public ServerStatus getStatus() { return status; }
    public void setStatus(ServerStatus status) { this.status = status; }
}
