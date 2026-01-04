package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ServerListWidget extends AlwaysSelectedEntryListWidget<ServerListWidget.ServerEntry> {
    private final ServerSelectScreen parent;

    public ServerListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, ServerSelectScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;
    }

    public void clear() {
        this.clearEntries();
    }

    @Override
    public int addEntry(ServerEntry entry) {
        return super.addEntry(entry);
    }

    @Override
    public int getRowWidth() {
        return this.width - 40;
    }

    @Override
    protected int getScrollbarX() {
        return this.getX() + this.width - 6;
    }

    public static class ServerEntry extends AlwaysSelectedEntryListWidget.Entry<ServerEntry> {
        private final ServerInstance instance;
        private final ServerSelectScreen parent;
        private int entryIndex = 0;
        private long lastClickTime = 0;
        private long lastActionTime = 0;
        private static final long ACTION_COOLDOWN = 1000; // 1 Sekunde Cooldown
        private int deleteIconX = 0;
        private int deleteIconY = 0;
        private static final int DELETE_ICON_SIZE = 12;

        // Action button positions
        private int startButtonX = 0;
        private int stopButtonX = 0;
        private int editButtonX = 0;
        private int buttonY = 0;
        private static final int BUTTON_SIZE = 12;

        public ServerEntry(ServerInstance instance, ServerSelectScreen parent) {
            this.instance = instance;
            this.parent = parent;
        }

        public ServerInstance getInstance() {
            return instance;
        }

        public void setIndex(int index) {
            this.entryIndex = index;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            MinecraftClient client = MinecraftClient.getInstance();

            // Use the Entry's built-in position fields (set by the framework in MC 1.21.11+)
            int x = this.getContentX();
            int y = this.getContentY();
            ServerListWidget listWidget = parent.getServerList();
            int entryWidth = listWidget != null ? listWidget.getRowWidth() : 200;

            // Server name
            context.drawTextWithShadow(client.textRenderer, instance.getName(), x, y + 2, 0xFFFFFFFF);

            // Server type and version
            String typeInfo = instance.getType().getDisplayName() + " " + instance.getMcVersion();
            context.drawTextWithShadow(client.textRenderer, typeInfo, x, y + 13, 0xFFAAAAAA);

            // Port info
            Text portInfo = Text.translatable("embeddedmc.server.port", instance.getPort());
            context.drawTextWithShadow(client.textRenderer, portInfo, x, y + 24, 0xFF888888);

            // Check server status
            boolean isRunning = EmbeddedMC.getInstance().getServerManager().isRunning(instance.getId());
            boolean isStarting = instance.getStatus() == ServerInstance.ServerStatus.STARTING;

            // Action buttons (right side, all in one row)
            buttonY = y + 12;
            int btnX = x + entryWidth - 90;

            // Start/Connect button (▶)
            startButtonX = btnX;
            boolean hoveringStart = mouseX >= startButtonX && mouseX <= startButtonX + BUTTON_SIZE &&
                                    mouseY >= buttonY && mouseY <= buttonY + BUTTON_SIZE;
            int startColor = isStarting ? 0xFFFFFF55 : (hoveringStart ? 0xFFFFFFFF : 0xFFCCCCCC);
            String startIcon = isStarting ? "\uE004" : "\uE000";
            context.drawTextWithShadow(client.textRenderer, startIcon, startButtonX, buttonY, startColor);

            // Stop button (■) - only when running
            btnX += 18;
            stopButtonX = btnX;
            if (isRunning) {
                boolean hoveringStop = mouseX >= stopButtonX && mouseX <= stopButtonX + BUTTON_SIZE &&
                                       mouseY >= buttonY && mouseY <= buttonY + BUTTON_SIZE;
                int stopColor = hoveringStop ? 0xFFFFFFFF : 0xFFCCCCCC;
                context.drawTextWithShadow(client.textRenderer, "\uE001", stopButtonX, buttonY, stopColor);
            }

            // Edit button
            btnX += 18;
            editButtonX = btnX;
            boolean hoveringEdit = mouseX >= editButtonX && mouseX <= editButtonX + BUTTON_SIZE &&
                                   mouseY >= buttonY && mouseY <= buttonY + BUTTON_SIZE;
            int editColor = hoveringEdit ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawTextWithShadow(client.textRenderer, "\uE002", editButtonX, buttonY, editColor);

            // Delete icon (same row as other buttons)
            btnX += 18;
            deleteIconX = btnX;
            deleteIconY = buttonY;
            boolean hoveringDelete = mouseX >= deleteIconX && mouseX <= deleteIconX + DELETE_ICON_SIZE &&
                                     mouseY >= deleteIconY && mouseY <= deleteIconY + DELETE_ICON_SIZE;
            int deleteColor = hoveringDelete ? 0xFFFFFFFF : 0xFFCCCCCC;
            context.drawTextWithShadow(client.textRenderer, "\uE003", deleteIconX, deleteIconY, deleteColor);

            // Status text (left of buttons)
            Text statusText = getStatusText();
            int statusX = x + entryWidth - 145;
            context.drawTextWithShadow(client.textRenderer, statusText, statusX, y + 2, getStatusColor());
        }

        private Text getStatusText() {
            return switch (instance.getStatus()) {
                case RUNNING -> Text.translatable("embeddedmc.status.running");
                case STARTING -> Text.translatable("embeddedmc.status.starting");
                case STOPPING -> Text.translatable("embeddedmc.status.stopping");
                case ERROR -> Text.translatable("embeddedmc.status.error").formatted(Formatting.RED);
                default -> Text.translatable("embeddedmc.status.stopped");
            };
        }

        private int getStatusColor() {
            return switch (instance.getStatus()) {
                case RUNNING -> 0xFF55FF55;
                case STARTING, STOPPING -> 0xFFFFFF55;
                case ERROR -> 0xFFFF5555;
                default -> 0xFFAAAAAA;
            };
        }

        @Override
        public boolean mouseClicked(Click click, boolean handled) {
            if (click.button() == 0) {
                double mouseX = click.x();
                double mouseY = click.y();

                // Check if clicked on delete icon
                if (mouseX >= deleteIconX && mouseX <= deleteIconX + DELETE_ICON_SIZE &&
                    mouseY >= deleteIconY && mouseY <= deleteIconY + DELETE_ICON_SIZE) {
                    // Show confirmation dialog
                    MinecraftClient.getInstance().setScreen(
                        new ConfirmScreen(
                            confirmed -> {
                                if (confirmed) {
                                    parent.deleteServer(instance);
                                }
                                MinecraftClient.getInstance().setScreen(parent);
                            },
                            Text.translatable("embeddedmc.confirm.delete_server"),
                            Text.translatable("embeddedmc.confirm.delete_message", instance.getName())
                        )
                    );
                    return true;
                }

                // Check action buttons (in button row area)
                if (mouseY >= buttonY && mouseY <= buttonY + BUTTON_SIZE) {
                    // Start button
                    if (mouseX >= startButtonX && mouseX <= startButtonX + BUTTON_SIZE) {
                        // Cooldown check für Start/Stop
                        if (System.currentTimeMillis() - lastActionTime < ACTION_COOLDOWN) {
                            return true; // Ignoriere Klick während Cooldown
                        }
                        lastActionTime = System.currentTimeMillis();
                        parent.startServer(instance);
                        return true;
                    }
                    // Stop button
                    boolean isRunning = EmbeddedMC.getInstance().getServerManager().isRunning(instance.getId());
                    if (isRunning && mouseX >= stopButtonX && mouseX <= stopButtonX + BUTTON_SIZE) {
                        // Cooldown check für Start/Stop
                        if (System.currentTimeMillis() - lastActionTime < ACTION_COOLDOWN) {
                            return true; // Ignoriere Klick während Cooldown
                        }
                        lastActionTime = System.currentTimeMillis();
                        parent.stopServer(instance);
                        return true;
                    }
                    // Edit button
                    if (mouseX >= editButtonX && mouseX <= editButtonX + BUTTON_SIZE) {
                        parent.openSettings(instance);
                        return true;
                    }
                }

                // Double-click detection
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 250) {
                    // Double-click - open settings
                    parent.openSettings(instance);
                }
                lastClickTime = now;
            }
            return super.mouseClicked(click, handled);
        }

        @Override
        public Text getNarration() {
            return Text.literal(instance.getName());
        }
    }
}
