package com.embeddedmc.download;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.server.ServerType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PaperAPI {
    // NOTE: PaperMC's old v2 API (api.papermc.io) was fully decommissioned on
    // 2026-07-01 (it stopped receiving new builds on 2025-12-31 and was then
    // switched off entirely). All downloads now go through the new "Fill" v3 API.
    private static final String API_BASE = "https://fill.papermc.io/v3";
    // Fill requires a descriptive, non-generic User-Agent that includes contact
    // info (a URL or email address), otherwise requests may be rejected or rate-limited.
    private static final String USER_AGENT = "EmbeddedMC/1.0.0 (https://github.com/marti/EmbeddedMC)";
    private static final Gson GSON = new Gson();

    public static List<String> getVersions(ServerType type) throws IOException {
        String url = API_BASE + "/projects/" + type.getProjectId();
        JsonObject response = fetchJson(url);

        // Fill groups versions by release family, e.g.
        // {"1.21": ["1.21.8", "1.21.7", ...], "1.20": [...]}.
        // Groups (and the versions within each group) are ordered newest-first.
        JsonObject versionGroups = response.getAsJsonObject("versions");

        List<String> versions = new ArrayList<>();
        for (Map.Entry<String, JsonElement> group : versionGroups.entrySet()) {
            JsonArray groupVersions = group.getValue().getAsJsonArray();
            for (JsonElement version : groupVersions) {
                versions.add(version.getAsString());
            }
        }
        return versions;
    }

    public static int getLatestBuild(ServerType type, String version) throws IOException {
        JsonArray builds = getBuilds(type, version);
        if (builds.isEmpty()) {
            throw new IOException("No builds found for version " + version);
        }

        // Builds are returned newest-first, but we find the max id defensively
        // instead of relying on ordering.
        int latestBuild = -1;
        for (JsonElement element : builds) {
            int id = element.getAsJsonObject().get("id").getAsInt();
            if (id > latestBuild) {
                latestBuild = id;
            }
        }
        return latestBuild;
    }

    public static String getDownloadUrl(ServerType type, String version) throws IOException {
        int build = getLatestBuild(type, version);
        return getDownloadUrl(type, version, build);
    }

    public static String getDownloadUrl(ServerType type, String version, int build) throws IOException {
        // Fill embeds the ready-to-use download URL directly in the build object,
        // so there's no need for a second request to resolve a file name anymore.
        JsonArray builds = getBuilds(type, version);

        for (JsonElement element : builds) {
            JsonObject buildObject = element.getAsJsonObject();
            if (buildObject.get("id").getAsInt() == build) {
                JsonObject downloads = buildObject.getAsJsonObject("downloads");
                JsonObject serverDownload = downloads.getAsJsonObject("server:default");
                return serverDownload.get("url").getAsString();
            }
        }

        throw new IOException("Build " + build + " not found for version " + version);
    }

    private static JsonArray getBuilds(ServerType type, String version) throws IOException {
        // NOTE: unlike v2, this endpoint returns a plain JSON array, not an
        // object with a "builds" field.
        String url = API_BASE + "/projects/" + type.getProjectId() + "/versions/" + version + "/builds";
        return fetchJsonArray(url);
    }

    private static JsonObject fetchJson(String urlString) throws IOException {
        return fetchJsonElement(urlString).getAsJsonObject();
    }

    private static JsonArray fetchJsonArray(String urlString) throws IOException {
        return fetchJsonElement(urlString).getAsJsonArray();
    }

    private static JsonElement fetchJsonElement(String urlString) throws IOException {
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
            return GSON.fromJson(reader, JsonElement.class);
        }
    }
}