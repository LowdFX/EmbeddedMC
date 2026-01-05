package com.embeddedmc.client.gui;

import com.embeddedmc.config.ServerInstance;
import com.embeddedmc.download.DownloadManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class DownloadProgressScreen extends Screen {
    private final Screen parent;
    private final ServerInstance instance;

    private String statusText = "Preparing download...";
    private int progress = 0;
    private boolean downloading = false;
    private boolean complete = false;
    private boolean error = false;

    public DownloadProgressScreen(Screen parent, ServerInstance instance) {
        super(Text.translatable("embeddedmc.status.downloading"));
        this.parent = parent;
        this.instance = instance;
    }

    @Override
    protected void init() {
        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.back"),
                button -> this.client.setScreen(parent)
        ).dimensions(this.width / 2 - 50, this.height - 52, 100, 20).build());

        // Start download
        if (!downloading) {
            startDownload();
        }
    }

    private void startDownload() {
        downloading = true;
        statusText = "Starting download...";

        DownloadManager.downloadServer(instance, progress -> {
            // UI updates müssen auf dem Render-Thread passieren!
            if (this.client != null) {
                this.client.execute(() -> {
                    this.progress = progress.getPercent();
                    this.statusText = progress.status();

                    if (progress.downloaded() < 0) {
                        this.error = true;
                    }
                });
            }
        }).thenAccept(success -> {
            if (this.client != null) {
                this.client.execute(() -> {
                    if (success) {
                        this.complete = true;
                        this.statusText = "Download complete!";

                        // Accept EULA
                        try {
                            instance.acceptEula();
                        } catch (Exception ignored) {}

                        // Get grandparent screen and create new ServerSelectScreen to start server
                        Screen grandParent = parent instanceof ServerSelectScreen sss ? sss.getParent() : parent;
                        ServerSelectScreen selectScreen = new ServerSelectScreen(grandParent);
                        this.client.setScreen(selectScreen);
                        selectScreen.startServer(instance);
                    } else {
                        this.error = true;
                        this.statusText = "Download failed!";
                    }
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 40, 0xFFFFFFFF);

        // Server name
        context.drawCenteredTextWithShadow(this.textRenderer,
                instance.getType().getDisplayName() + " " + instance.getMcVersion(),
                centerX, centerY - 25, 0xFFAAAAAA);

        // Progress bar background
        int barWidth = 200;
        int barHeight = 10;
        int barX = centerX - barWidth / 2;
        int barY = centerY;

        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF555555);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);

        // Progress bar fill
        int color = error ? 0xFFFF5555 : (complete ? 0xFF55FF55 : 0xFF5555FF);
        if (progress >= 0) {
            int fillWidth = (int) (barWidth * (progress / 100.0));
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, color);
        } else if (downloading && !error) {
            // Unknown size - show animated/pulsing bar
            long time = System.currentTimeMillis() / 10;
            int pulseWidth = barWidth / 3;
            int pulseX = (int) ((time % (barWidth + pulseWidth)) - pulseWidth);
            int startX = Math.max(barX, barX + pulseX);
            int endX = Math.min(barX + barWidth, barX + pulseX + pulseWidth);
            if (endX > startX) {
                context.fill(startX, barY, endX, barY + barHeight, 0xFF5555FF);
            }
        }

        // Progress text
        String progressText = progress >= 0 ? progress + "%" : "Downloading...";
        context.drawCenteredTextWithShadow(this.textRenderer, progressText, centerX, barY + barHeight + 5, 0xFFFFFFFF);

        // Status text
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, centerX, barY + barHeight + 20,
                error ? 0xFFFF5555 : 0xFFAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
