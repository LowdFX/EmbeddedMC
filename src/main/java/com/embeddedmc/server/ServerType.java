package com.embeddedmc.server;

public enum ServerType {
    PAPER("Paper", "paper", "https://api.papermc.io/v2"),
    PURPUR("Purpur", "purpur", "https://api.purpurmc.org/v2"),
    FOLIA("Folia", "folia", "https://api.papermc.io/v2"),
    SPIGOT("Spigot", "spigot", null); // Spigot has no official API

    private final String displayName;
    private final String projectId;
    private final String apiBase;

    ServerType(String displayName, String projectId, String apiBase) {
        this.displayName = displayName;
        this.projectId = projectId;
        this.apiBase = apiBase;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getApiBase() {
        return apiBase;
    }

    public boolean hasApi() {
        return apiBase != null;
    }
}
