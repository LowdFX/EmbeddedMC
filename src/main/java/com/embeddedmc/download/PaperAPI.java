package com.embeddedmc.download;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.server.ServerType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PaperAPI {
    private static final String API_BASE = "https://api.papermc.io/v2";
    private static final String USER_AGENT = "EmbeddedMC/1.0.0 (https://github.com/marti/EmbeddedMC)";
    private static final Gson GSON = new Gson();

    public static List<String> getVersions(ServerType type) throws IOException {
        String url = API_BASE + "/projects/" + type.getProjectId();
        JsonObject response = fetchJson(url);

        List<String> versions = new ArrayList<>();
        JsonArray versionsArray = response.getAsJsonArray("versions");
        for (int i = versionsArray.size() - 1; i >= 0; i--) {
            versions.add(versionsArray.get(i).getAsString());
        }
        return versions;
    }

    public static int getLatestBuild(ServerType type, String version) throws IOException {
        String url = API_BASE + "/projects/" + type.getProjectId() + "/versions/" + version + "/builds";
        JsonObject response = fetchJson(url);

        JsonArray builds = response.getAsJsonArray("builds");
        if (builds.isEmpty()) {
            throw new IOException("No builds found for version " + version);
        }

        // Get the latest build
        JsonObject latestBuild = builds.get(builds.size() - 1).getAsJsonObject();
        return latestBuild.get("build").getAsInt();
    }

    public static String getDownloadUrl(ServerType type, String version) throws IOException {
        int build = getLatestBuild(type, version);
        return getDownloadUrl(type, version, build);
    }

    public static String getDownloadUrl(ServerType type, String version, int build) throws IOException {
        String url = API_BASE + "/projects/" + type.getProjectId() + "/versions/" + version + "/builds/" + build;
        JsonObject response = fetchJson(url);

        JsonObject downloads = response.getAsJsonObject("downloads");
        JsonObject application = downloads.getAsJsonObject("application");
        String fileName = application.get("name").getAsString();

        return API_BASE + "/projects/" + type.getProjectId() + "/versions/" + version + "/builds/" + build + "/downloads/" + fileName;
    }

    private static JsonObject fetchJson(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP error: " + responseCode + " for URL: " + urlString);
        }

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }
}
