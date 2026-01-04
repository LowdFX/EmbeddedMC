package com.embeddedmc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int defaultRamMB = 2048;
    private String javaPath = "java";
    private int defaultPort = 25565;
    private boolean autoAcceptEula = false;
    private String language = "en_us";

    private transient Path configPath;

    public static ModConfig load(Path path) {
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                config.configPath = path;
                return config;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModConfig config = new ModConfig();
        config.configPath = path;
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getters and Setters
    public int getDefaultRamMB() { return defaultRamMB; }
    public void setDefaultRamMB(int defaultRamMB) { this.defaultRamMB = defaultRamMB; }

    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }

    public int getDefaultPort() { return defaultPort; }
    public void setDefaultPort(int defaultPort) { this.defaultPort = defaultPort; }

    public boolean isAutoAcceptEula() { return autoAcceptEula; }
    public void setAutoAcceptEula(boolean autoAcceptEula) { this.autoAcceptEula = autoAcceptEula; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
