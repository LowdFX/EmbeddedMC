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

    public ServerSettingsScreen(Screen parent, ServerInstance instance) {
        super(Text.translatable("embeddedmc.screen.server_settings"));
        this.parent = parent;
        this.instance = instance;
        this.selectedRam = instance.getRamMB();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 50;
        int fieldWidth = 200;
        int spacing = 30;

        // Name field
        this.nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, 20, Text.translatable("embeddedmc.label.name"));
        this.nameField.setText(instance.getName());
        this.nameField.setMaxLength(32);
        this.addSelectableChild(this.nameField);

        // Port field
        this.portField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY + spacing, fieldWidth, 20, Text.translatable("embeddedmc.label.port"));
        this.portField.setText(String.valueOf(instance.getPort()));
        this.portField.setMaxLength(5);
        this.addSelectableChild(this.portField);

        // RAM slider
        this.addDrawableChild(new SliderWidget(centerX - fieldWidth / 2, startY + spacing * 2, fieldWidth, 20,
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

        // Server info (read-only)
        // Type and version display

        // Plugins button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.plugins"),
                button -> this.client.setScreen(new PluginManagerScreen(this, instance))
        ).dimensions(centerX - fieldWidth / 2, startY + spacing * 4, fieldWidth, 20).build());

        // Files button (Config Editor)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.files"),
                button -> this.client.setScreen(new FileListScreen(this, instance))
        ).dimensions(centerX - fieldWidth / 2, startY + spacing * 5, fieldWidth, 20).build());

        // Console button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.console"),
                button -> this.client.setScreen(new ConsoleScreen(this, instance))
        ).dimensions(centerX - fieldWidth / 2, startY + spacing * 6, fieldWidth, 20).build());

        // Delete button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.delete"),
                button -> deleteServer()
        ).dimensions(centerX - fieldWidth / 2, startY + spacing * 7, fieldWidth, 20).build());

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

        // Labels
        int centerX = this.width / 2;
        int labelX = centerX - 100 - 90;
        int startY = 50;
        int spacing = 30;

        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.name"), labelX, startY + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.port"), labelX, startY + spacing + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.ram"), labelX, startY + spacing * 2 + 6, 0xFFAAAAAA);

        // Render text fields
        this.nameField.render(context, mouseX, mouseY, delta);
        this.portField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
