package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ServerSettingsScreen extends Screen {
    private final Screen parent;
    private final ServerInstance instance;

    private TextFieldWidget nameField;
    private TextFieldWidget portField;
    private int selectedRam;
    private int selectedSlots;

    // Shared layout constants so init() and render() always agree on positions.
    private static final int FIELD_WIDTH = 200;
    private static final int COLUMN_GAP = 20;
    private static final int START_Y = 50;
    private static final int ROW_SPACING = 30;

    public ServerSettingsScreen(Screen parent, ServerInstance instance) {
        super(Text.translatable("embeddedmc.screen.server_settings"));
        this.parent = parent;
        this.instance = instance;
        this.selectedRam = instance.getRamMB();
        this.selectedSlots = instance.getMaxPlayers();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Two columns: input fields/sliders on the left, action buttons on the right.
        int totalWidth = FIELD_WIDTH * 2 + COLUMN_GAP;
        int leftX = centerX - totalWidth / 2;
        int rightX = leftX + FIELD_WIDTH + COLUMN_GAP;

        // Name field
        this.nameField = new TextFieldWidget(this.textRenderer, leftX, START_Y, FIELD_WIDTH, 20, Text.translatable("embeddedmc.label.name"));
        this.nameField.setText(instance.getName());
        this.nameField.setMaxLength(32);
        this.addSelectableChild(this.nameField);

        // Port field
        this.portField = new TextFieldWidget(this.textRenderer, leftX, START_Y + ROW_SPACING, FIELD_WIDTH, 20, Text.translatable("embeddedmc.label.port"));
        this.portField.setText(String.valueOf(instance.getPort()));
        this.portField.setMaxLength(5);
        this.addSelectableChild(this.portField);

        // RAM slider
        this.addDrawableChild(new SliderWidget(leftX, START_Y + ROW_SPACING * 2, FIELD_WIDTH, 20,
                Text.literal("RAM: " + selectedRam + " MB"), (selectedRam - 512) / 7680.0) {
            @Override
            protected void updateMessage() {
                selectedRam = 512 + (int) (this.value * 7680);
                selectedRam = (selectedRam / 256) * 256;
                this.setMessage(Text.literal("RAM: " + selectedRam + " MB"));
            }

            @Override
            protected void applyValue() {
                selectedRam = 512 + (int) (this.value * 7680);
                selectedRam = (selectedRam / 256) * 256;
            }
        });

        // Slots slider (1-100 players)
        this.addDrawableChild(new SliderWidget(leftX, START_Y + ROW_SPACING * 3, FIELD_WIDTH, 20,
                Text.translatable("embeddedmc.label.slots_value", selectedSlots), (selectedSlots - 1) / 99.0) {
            @Override
            protected void updateMessage() {
                selectedSlots = 1 + (int) (this.value * 99);
                this.setMessage(Text.translatable("embeddedmc.label.slots_value", selectedSlots));
            }

            @Override
            protected void applyValue() {
                selectedSlots = 1 + (int) (this.value * 99);
            }
        });

        // Right-hand action column: aligned with the four rows on the left,
        // so the block stays exactly as tall as the fields/sliders next to it.

        // Plugins button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.plugins"),
                button -> this.client.setScreen(new PluginManagerScreen(this, instance))
        ).dimensions(rightX, START_Y, FIELD_WIDTH, 20).build());

        // Files button (Config Editor)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.files"),
                button -> this.client.setScreen(new FileListScreen(this, instance))
        ).dimensions(rightX, START_Y + ROW_SPACING, FIELD_WIDTH, 20).build());

        // Console button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.console"),
                button -> this.client.setScreen(new ConsoleScreen(this, instance))
        ).dimensions(rightX, START_Y + ROW_SPACING * 2, FIELD_WIDTH, 20).build());

        // Delete button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.delete"),
                button -> deleteServer()
        ).dimensions(rightX, START_Y + ROW_SPACING * 3, FIELD_WIDTH, 20).build());

        // Save button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                button -> saveSettings()
        ).dimensions(centerX - 105, this.height - 52, 100, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.back"),
                button -> this.client.setScreen(parent)
        ).dimensions(centerX + 5, this.height - 52, 100, 20).build());
    }

    private void saveSettings() {
        instance.setName(nameField.getText().trim());

        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port > 0 && port < 65536) {
                instance.setPort(port);
            }
        } catch (NumberFormatException ignored) {}

        instance.setRamMB(selectedRam);
        instance.setMaxPlayers(selectedSlots);

        try {
            instance.save();
            EmbeddedMC.LOGGER.info("Saved settings for instance: {}", instance.getName());
        } catch (Exception e) {
            EmbeddedMC.LOGGER.error("Failed to save instance settings", e);
        }

        this.client.setScreen(parent);
    }

    private void deleteServer() {
        if (parent instanceof ServerSelectScreen selectScreen) {
            selectScreen.deleteServer(instance);
        }
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Server info subtitle
        String info = instance.getType().getDisplayName() + " " + instance.getMcVersion();
        context.drawCenteredTextWithShadow(this.textRenderer, info, this.width / 2, 30, 0xFFAAAAAA);

        // Labels sit to the left of the (left-column) input fields.
        int centerX = this.width / 2;
        int totalWidth = FIELD_WIDTH * 2 + COLUMN_GAP;
        int leftX = centerX - totalWidth / 2;
        int labelX = leftX - 90;

        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.name"), labelX, START_Y + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.port"), labelX, START_Y + ROW_SPACING + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.ram"), labelX, START_Y + ROW_SPACING * 2 + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.slots"), labelX, START_Y + ROW_SPACING * 3 + 6, 0xFFAAAAAA);

        // Render text fields
        this.nameField.render(context, mouseX, mouseY, delta);
        this.portField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}