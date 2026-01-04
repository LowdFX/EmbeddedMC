package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class PluginManagerScreen extends Screen {
    private final Screen parent;
    private final ServerInstance instance;

    private PluginListWidget pluginList;

    public PluginManagerScreen(Screen parent, ServerInstance instance) {
        super(Text.translatable("embeddedmc.screen.plugin_manager"));
        this.parent = parent;
        this.instance = instance;
    }

    @Override
    protected void init() {
        int listWidth = this.width - 40;
        int listHeight = this.height - 145;

        this.pluginList = new PluginListWidget(this.client, listWidth, listHeight, 50, 24);
        this.pluginList.setX(20);
        this.addSelectableChild(this.pluginList);

        loadPlugins();

        // Open plugins folder button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.plugin.open_folder"),
                button -> openPluginsFolder()
        ).dimensions(this.width / 2 - 100, this.height - 82, 200, 20).build());

        // Refresh button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.plugin.refresh"),
                button -> loadPlugins()
        ).dimensions(this.width / 2 - 100, this.height - 58, 95, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.back"),
                button -> this.client.setScreen(parent)
        ).dimensions(this.width / 2 + 5, this.height - 58, 95, 20).build());
    }

    private void loadPlugins() {
        pluginList.clearEntries();
        Path pluginsDir = instance.getPluginsDir();

        if (Files.exists(pluginsDir)) {
            List<Path> plugins = new ArrayList<>();
            try (Stream<Path> files = Files.list(pluginsDir)) {
                files.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(plugins::add);
            } catch (IOException e) {
                EmbeddedMC.LOGGER.error("Failed to list plugins", e);
            }

            // Sort alphabetically
            plugins.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));

            for (Path plugin : plugins) {
                pluginList.addEntry(new PluginListWidget.PluginEntry(plugin, this));
            }
        }
    }

    void openPluginFolder(Path pluginJar) {
        String pluginName = getPluginName(pluginJar);
        Path dataFolder = instance.getPluginsDir().resolve(pluginName);

        // Create folder if it doesn't exist
        if (!Files.exists(dataFolder)) {
            try {
                Files.createDirectories(dataFolder);
            } catch (IOException e) {
                EmbeddedMC.LOGGER.error("Failed to create plugin folder: {}", dataFolder, e);
            }
        }

        this.client.setScreen(new FileListScreen(this, instance, dataFolder));
    }

    /**
     * Read the plugin name from plugin.yml inside the JAR file.
     * Falls back to JAR filename without version suffix.
     */
    private String getPluginName(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    String content = new String(is.readAllBytes());
                    for (String line : content.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("name:")) {
                            String name = trimmed.substring(5).trim();
                            // Remove quotes if present
                            name = name.replace("\"", "").replace("'", "");
                            if (!name.isEmpty()) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            EmbeddedMC.LOGGER.warn("Could not read plugin.yml from {}", jarPath, e);
        }

        // Fallback: JAR filename without .jar and version suffix
        String fileName = jarPath.getFileName().toString().replace(".jar", "");
        // Remove version patterns like -1.0.0, -SNAPSHOT, -1.0-SNAPSHOT
        return fileName.replaceAll("-[0-9].*$", "");
    }

    private void openPluginsFolder() {
        try {
            Path pluginsDir = instance.getPluginsDir();
            Files.createDirectories(pluginsDir);

            // Open folder in file explorer
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", pluginsDir.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", pluginsDir.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", pluginsDir.toString());
            }
            pb.start();
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to open plugins folder", e);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Subtitle
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(instance.getName()),
                this.width / 2, 30, 0xFFAAAAAA);

        // Plugin list
        this.pluginList.render(context, mouseX, mouseY, delta);

        // Hint text
        if (pluginList.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("embeddedmc.plugin.no_plugins"),
                    this.width / 2, this.height / 2 - 20, 0xFF888888);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("embeddedmc.plugin.add_hint"),
                    this.width / 2, this.height / 2 - 5, 0xFF666666);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("embeddedmc.version.hint"),
                    this.width / 2, this.height - 106, 0xFF666666);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    public static class PluginListWidget extends AlwaysSelectedEntryListWidget<PluginListWidget.PluginEntry> {

        public PluginListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        public void clearEntries() {
            super.clearEntries();
        }

        @Override
        public int addEntry(PluginEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        public static class PluginEntry extends AlwaysSelectedEntryListWidget.Entry<PluginEntry> {
            private final Path pluginJar;
            private final PluginManagerScreen parent;
            private final String displayName;
            private final boolean hasDataFolder;
            private long lastClickTime = 0;

            public PluginEntry(Path pluginJar, PluginManagerScreen parent) {
                this.pluginJar = pluginJar;
                this.parent = parent;
                this.displayName = pluginJar.getFileName().toString();

                // Check if data folder exists
                String pluginName = displayName.replace(".jar", "");
                Path dataFolder = parent.instance.getPluginsDir().resolve(pluginName);
                this.hasDataFolder = Files.exists(dataFolder) && Files.isDirectory(dataFolder);
            }

            @Override
            public boolean mouseClicked(Click click, boolean handled) {
                if (click.button() == 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 250) {
                        // Double click - open plugin folder
                        parent.openPluginFolder(pluginJar);
                    }
                    lastClickTime = now;
                }
                return super.mouseClicked(click, handled);
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                MinecraftClient client = MinecraftClient.getInstance();

                int x = this.getContentX();
                int y = this.getContentY();

                // Plugin icon/prefix
                String prefix = hasDataFolder ? "[P] " : "    ";
                int color = hasDataFolder ? 0xFF88FF88 : 0xFFFFFFFF;

                context.drawTextWithShadow(client.textRenderer, prefix + displayName, x, y + 4, color);

                // Data folder indicator
                if (hasDataFolder) {
                    Text hint = Text.translatable("embeddedmc.plugin.has_config");
                    int hintX = x + parent.pluginList.getRowWidth() - client.textRenderer.getWidth(hint) - 10;
                    context.drawTextWithShadow(client.textRenderer, hint, hintX, y + 4, 0xFF888888);
                }
            }

            @Override
            public Text getNarration() {
                return Text.literal(displayName);
            }
        }
    }
}
