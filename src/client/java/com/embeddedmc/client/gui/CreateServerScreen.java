package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import com.embeddedmc.download.PaperAPI;
import com.embeddedmc.download.PurpurAPI;
import com.embeddedmc.server.ServerType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CreateServerScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget nameField;
    private ServerType selectedType = ServerType.PAPER;
    private String selectedVersion = "1.21.11";
    private int selectedRam = 2048;
    private List<String> availableVersions;

    // Server type buttons
    private ButtonWidget paperButton;
    private ButtonWidget purpurButton;
    private ButtonWidget foliaButton;
    private ButtonWidget versionButton;

    public CreateServerScreen(Screen parent) {
        super(Text.translatable("embeddedmc.screen.create_server"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 50;
        int fieldWidth = 200;
        int spacing = 30;

        // Name field
        this.nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, 20, Text.translatable("embeddedmc.label.name"));
        this.nameField.setText("My Server");
        this.nameField.setMaxLength(32);
        this.addSelectableChild(this.nameField);

        // Server type buttons (3 buttons side by side)
        int typeButtonWidth = 64;
        int typeButtonSpacing = 4;
        int totalTypeWidth = typeButtonWidth * 3 + typeButtonSpacing * 2;
        int typeStartX = centerX - totalTypeWidth / 2;

        this.paperButton = ButtonWidget.builder(
                Text.literal("Paper"),
                button -> selectServerType(ServerType.PAPER)
        ).dimensions(typeStartX, startY + spacing, typeButtonWidth, 20).build();
        this.addDrawableChild(this.paperButton);

        this.purpurButton = ButtonWidget.builder(
                Text.literal("Purpur"),
                button -> selectServerType(ServerType.PURPUR)
        ).dimensions(typeStartX + typeButtonWidth + typeButtonSpacing, startY + spacing, typeButtonWidth, 20).build();
        this.addDrawableChild(this.purpurButton);

        this.foliaButton = ButtonWidget.builder(
                Text.literal("Folia"),
                button -> selectServerType(ServerType.FOLIA)
        ).dimensions(typeStartX + (typeButtonWidth + typeButtonSpacing) * 2, startY + spacing, typeButtonWidth, 20).build();
        this.addDrawableChild(this.foliaButton);

        // Load versions
        loadVersions();

        // Version selector (click to open version list)
        this.versionButton = ButtonWidget.builder(
                Text.literal(selectedVersion),
                button -> openVersionSelector()
        ).dimensions(centerX - fieldWidth / 2, startY + spacing * 2, fieldWidth, 20).build();
        this.addDrawableChild(this.versionButton);

        // RAM slider
        this.addDrawableChild(new SliderWidget(centerX - fieldWidth / 2, startY + spacing * 3, fieldWidth, 20,
                Text.literal("RAM: " + selectedRam + " MB"), (selectedRam - 512) / 7680.0) {
            @Override
            protected void updateMessage() {
                selectedRam = 512 + (int) (this.value * 7680);
                selectedRam = (selectedRam / 256) * 256; // Round to 256MB
                this.setMessage(Text.literal("RAM: " + selectedRam + " MB"));
            }

            @Override
            protected void applyValue() {
                selectedRam = 512 + (int) (this.value * 7680);
                selectedRam = (selectedRam / 256) * 256;
            }
        });

        // Create button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.create"),
                button -> createServer()
        ).dimensions(centerX - 105, this.height - 52, 100, 20).build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.back"),
                button -> this.client.setScreen(parent)
        ).dimensions(centerX + 5, this.height - 52, 100, 20).build());
    }

    private void loadVersions() {
        CompletableFuture.runAsync(() -> {
            try {
                if (selectedType.hasApi()) {
                    availableVersions = switch (selectedType) {
                        case PAPER, FOLIA -> PaperAPI.getVersions(selectedType);
                        case PURPUR -> PurpurAPI.getVersions();
                        default -> List.of("1.21.11");
                    };
                    if (!availableVersions.isEmpty()) {
                        selectedVersion = availableVersions.get(0);
                        // Update button on main thread
                        if (client != null) {
                            client.execute(() -> {
                                if (versionButton != null) {
                                    versionButton.setMessage(Text.literal(selectedVersion));
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                EmbeddedMC.LOGGER.error("Failed to load versions", e);
                availableVersions = List.of("1.21.11", "1.21.10", "1.21.4", "1.21.1");
                selectedVersion = "1.21.11";
            }
        });
    }

    private void selectServerType(ServerType type) {
        if (selectedType != type) {
            selectedType = type;
            loadVersions();
        }
    }

    private void openVersionSelector() {
        if (availableVersions != null && !availableVersions.isEmpty()) {
            this.client.setScreen(new VersionSelectScreen(this, availableVersions, selectedVersion,
                version -> {
                    selectedVersion = version;
                    versionButton.setMessage(Text.literal(selectedVersion));
                }
            ));
        }
    }

    private void createServer() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "My Server";
        }

        ServerInstance instance = EmbeddedMC.getInstance().getServerManager()
                .createInstance(name, selectedType, selectedVersion);

        if (instance != null) {
            instance.setRamMB(selectedRam);
            try {
                instance.save();
            } catch (Exception e) {
                EmbeddedMC.LOGGER.error("Failed to save instance", e);
            }
        }

        // Create new ServerSelectScreen to ensure proper refresh
        Screen grandParent = parent instanceof ServerSelectScreen sss ? sss.getParent() : parent;
        this.client.setScreen(new ServerSelectScreen(grandParent));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);

        // Labels (positioned to left of fields with proper spacing)
        int centerX = this.width / 2;
        int fieldWidth = 200;
        int labelX = centerX - fieldWidth / 2 - 120;
        int startY = 50;
        int spacing = 30;

        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.name"), labelX, startY + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.type"), labelX, startY + spacing + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.version"), labelX, startY + spacing * 2 + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.label.ram"), labelX, startY + spacing * 3 + 6, 0xFFAAAAAA);

        // Draw selection indicator around selected server type button
        ButtonWidget selectedButton = switch (selectedType) {
            case PAPER -> paperButton;
            case PURPUR -> purpurButton;
            case FOLIA -> foliaButton;
            default -> null;
        };

        if (selectedButton != null) {
            int bx = selectedButton.getX() - 2;
            int by = selectedButton.getY() - 2;
            int bw = selectedButton.getWidth() + 4;
            int bh = selectedButton.getHeight() + 4;
            // Draw green border
            context.fill(bx, by, bx + bw, by + 1, 0xFF55FF55);  // Top
            context.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF55FF55);  // Bottom
            context.fill(bx, by, bx + 1, by + bh, 0xFF55FF55);  // Left
            context.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF55FF55);  // Right
        }

        // Render name field
        this.nameField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
