package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import com.embeddedmc.download.DownloadManager;
import com.embeddedmc.server.ServerManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.util.List;

public class ServerSelectScreen extends Screen {
    private final Screen parent;
    private ServerListWidget serverList;

    public ServerSelectScreen(Screen parent) {
        super(Text.translatable("embeddedmc.screen.server_select"));
        this.parent = parent;
    }

    public Screen getParent() {
        return parent;
    }

    public ServerListWidget getServerList() {
        return serverList;
    }

    @Override
    protected void init() {
        int listWidth = this.width - 50;
        int listHeight = this.height - 100;

        // Server list (increased item height for action buttons)
        this.serverList = new ServerListWidget(this.client, listWidth, listHeight, 32, 42, this);
        this.serverList.setX(25);
        this.addSelectableChild(this.serverList);

        // Refresh list
        refreshServerList();

        // Buttons at bottom (only Create and Back - other actions are in server entries)
        int buttonY = this.height - 52;
        int buttonWidth = 100;
        int spacing = 10;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = (this.width - totalWidth) / 2;

        // Create new server button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.create"),
                button -> this.client.setScreen(new CreateServerScreen(this))
        ).dimensions(startX, buttonY, buttonWidth, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.back"),
                button -> this.client.setScreen(parent)
        ).dimensions(startX + buttonWidth + spacing, buttonY, buttonWidth, 20).build());
    }

    public void refreshServerList() {
        // Save current selection
        String selectedId = null;
        ServerListWidget.ServerEntry currentSelected = serverList.getSelectedOrNull();
        if (currentSelected != null) {
            selectedId = currentSelected.getInstance().getId();
        }

        serverList.clear();
        List<ServerInstance> instances = EmbeddedMC.getInstance().getServerManager().getInstances();
        int index = 0;
        for (ServerInstance instance : instances) {
            ServerListWidget.ServerEntry entry = new ServerListWidget.ServerEntry(instance, this);
            entry.setIndex(index);
            serverList.addEntry(entry);

            // Restore selection
            if (selectedId != null && instance.getId().equals(selectedId)) {
                serverList.setSelected(entry);
            }
            index++;
        }
    }

    public void startServer(ServerInstance instance) {
        ServerManager manager = EmbeddedMC.getInstance().getServerManager();

        // If already starting, ignore click
        if (instance.getStatus() == ServerInstance.ServerStatus.STARTING) {
            return;
        }

        if (manager.isRunning(instance.getId())) {
            // Already running, just connect
            connectToServer(instance);
            return;
        }

        // Check if server JAR exists
        if (!Files.exists(instance.getServerJar())) {
            // Need to download first - after download user can start manually
            this.client.setScreen(new DownloadProgressScreen(this, instance));
            return;
        }

        startAndConnect(instance);
    }

    private void startAndConnect(ServerInstance instance) {
        ServerManager manager = EmbeddedMC.getInstance().getServerManager();

        instance.setStatus(ServerInstance.ServerStatus.STARTING);
        refreshServerList();

        manager.startServer(instance.getId(), () -> {
            // Server is ready, connect
            if (this.client != null) {
                this.client.execute(() -> connectToServer(instance));
            }
        });

        // Check if start failed immediately (port in use, etc.)
        if (instance.getStatus() == ServerInstance.ServerStatus.ERROR) {
            refreshServerList();
        }
    }

    private void connectToServer(ServerInstance instance) {
        if (this.client != null) {
            // Connect to localhost:port
            net.minecraft.client.network.ServerInfo serverInfo = new net.minecraft.client.network.ServerInfo(
                    instance.getName(),
                    "localhost:" + instance.getPort(),
                    net.minecraft.client.network.ServerInfo.ServerType.OTHER
            );
            net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(
                    this,
                    this.client,
                    net.minecraft.client.network.ServerAddress.parse(serverInfo.address),
                    serverInfo,
                    false,
                    null
            );
        }
    }

    public void stopServer(ServerInstance instance) {
        ServerManager manager = EmbeddedMC.getInstance().getServerManager();

        if (manager.isRunning(instance.getId())) {
            manager.stopServer(instance.getId());
            refreshServerList();
        }
    }

    public void joinServer(ServerInstance instance) {
        connectToServer(instance);
    }

    public void openSettings(ServerInstance instance) {
        this.client.setScreen(new ServerSettingsScreen(this, instance));
    }

    public void deleteServer(ServerInstance instance) {
        EmbeddedMC.getInstance().getServerManager().deleteInstance(instance.getId());
        refreshServerList();
    }

    private int tickCounter = 0;

    @Override
    public void tick() {
        super.tick();

        // Refresh status every 60 ticks (3 seconds) to update status indicators
        tickCounter++;
        if (tickCounter >= 60) {
            tickCounter = 0;
            refreshServerList();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Render server list
        this.serverList.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
