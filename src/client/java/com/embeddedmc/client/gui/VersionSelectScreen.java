package com.embeddedmc.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class VersionSelectScreen extends Screen {
    private final Screen parent;
    private final List<String> versions;
    private final String currentVersion;
    private final Consumer<String> onSelect;

    private VersionListWidget versionList;

    public VersionSelectScreen(Screen parent, List<String> versions, String currentVersion, Consumer<String> onSelect) {
        super(Text.translatable("embeddedmc.version.title"));
        this.parent = parent;
        this.versions = versions;
        this.currentVersion = currentVersion;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int listWidth = this.width - 80;
        int listHeight = this.height - 100;

        this.versionList = new VersionListWidget(this.client, listWidth, listHeight, 40, 20);
        this.versionList.setX(40);
        this.addSelectableChild(this.versionList);

        // Populate list
        if (versions != null) {
            for (String version : versions) {
                VersionListWidget.VersionEntry entry = new VersionListWidget.VersionEntry(version, this);
                versionList.addEntry(entry);

                // Pre-select current version
                if (version.equals(currentVersion)) {
                    versionList.setSelected(entry);
                }
            }
        }

        // Select button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.version.select"),
                button -> selectVersion()
        ).dimensions(this.width / 2 - 105, this.height - 52, 100, 20).build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.cancel"),
                button -> this.client.setScreen(parent)
        ).dimensions(this.width / 2 + 5, this.height - 52, 100, 20).build());
    }

    private void selectVersion() {
        VersionListWidget.VersionEntry selected = versionList.getSelectedOrNull();
        if (selected != null) {
            onSelect.accept(selected.getVersion());
        }
        this.client.setScreen(parent);
    }

    void selectAndClose(String version) {
        onSelect.accept(version);
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Hint
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("embeddedmc.version.hint"),
                this.width / 2, 28, 0xFF888888);

        // Version list
        this.versionList.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    public static class VersionListWidget extends AlwaysSelectedEntryListWidget<VersionListWidget.VersionEntry> {

        public VersionListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        @Override
        public int addEntry(VersionEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        public static class VersionEntry extends AlwaysSelectedEntryListWidget.Entry<VersionEntry> {
            private final String version;
            private final VersionSelectScreen parent;
            private long lastClickTime = 0;

            public VersionEntry(String version, VersionSelectScreen parent) {
                this.version = version;
                this.parent = parent;
            }

            public String getVersion() {
                return version;
            }

            @Override
            public boolean mouseClicked(Click click, boolean handled) {
                if (click.button() == 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 250) {
                        // Double click - select and close
                        parent.selectAndClose(version);
                        return true;
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

                int color = 0xFFFFFFFF;
                context.drawTextWithShadow(client.textRenderer, version, x + 5, y + 4, color);
            }

            @Override
            public Text getNarration() {
                return Text.literal(version);
            }
        }
    }
}
