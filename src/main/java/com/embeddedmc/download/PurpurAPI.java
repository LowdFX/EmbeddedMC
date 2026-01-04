package com.embeddedmc.download;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PurpurAPI {
    private static final String API_BASE = "https://api.purpurmc.org/v2";
    private static final String USER_AGENT = "EmbeddedMC/1.0.0 (https://github.com/marti/EmbeddedMC)";
    private static final Gson GSON = new Gson();

    public static List<String> getVersions() throws IOException {
        String url = API_BASE + "/purpur";
        JsonObject response = fetchJson(url);

        List<String> versions = new ArrayList<>();
        JsonArray versionsArray = response.getAsJsonArray("versions");
        for (int i = versionsArray.size() - 1; i >= 0; i--) {
            versions.add(versionsArray.get(i).getAsString());
        }
        return versions;
    }

    public static String getLatestBuild(String version) throws IOException {
        String url = API_BASE + "/purpur/" + version;
        JsonObject response = fetchJson(url);

        JsonObject builds = response.getAsJsonObject("builds");
        return builds.get("latest").getAsString();
    }

    public static String getDownloadUrl(String version) throws IOException {
        String build = getLatestBuild(version);
        return getDownloadUrl(version, build);
    }

    public static String getDownloadUrl(String version, String build) {
        return API_BASE + "/purpur/" + version + "/" + build + "/download";
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
