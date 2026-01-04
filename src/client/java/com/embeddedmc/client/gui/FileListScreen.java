package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileListScreen extends Screen {
    private final Screen parent;
    private final ServerInstance instance;
    private final Path currentPath;

    private FileListWidget fileList;
    private ButtonWidget deleteButton;
    private ButtonWidget editButton;

    // Input mode for creating/renaming files/folders
    private boolean isCreating = false;
    private boolean creatingFolder = false;
    private boolean isRenaming = false;
    private Path renamingPath = null;
    private TextFieldWidget nameInput;

    public FileListScreen(Screen parent, ServerInstance instance) {
        this(parent, instance, instance.getInstancePath());
    }

    public FileListScreen(Screen parent, ServerInstance instance, Path currentPath) {
        super(Text.translatable("embeddedmc.files.title"));
        this.parent = parent;
        this.instance = instance;
        this.currentPath = currentPath;
    }

    @Override
    protected void init() {
        int listWidth = this.width - 40;
        int listHeight = this.height - 95;  // More space for button area

        this.fileList = new FileListWidget(this.client, listWidth, listHeight, 50, 24);
        this.fileList.setX(20);
        this.addSelectableChild(this.fileList);

        refreshFileList();

        // Back button (top-left with arrow)
        Path instanceRoot = instance.getInstancePath();
        boolean canGoUp = !currentPath.equals(instanceRoot) && currentPath.startsWith(instanceRoot);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("← " + Text.translatable("embeddedmc.button.back").getString()),
                button -> {
                    if (canGoUp) {
                        // Go to parent folder
                        this.client.setScreen(new FileListScreen(parent, instance, currentPath.getParent()));
                    } else {
                        // At root - go back to parent screen
                        this.client.setScreen(parent);
                    }
                }
        ).dimensions(10, 5, 80, 20).build());

        // Button row at bottom
        int buttonY = this.height - 30;
        int buttonWidth = 60;
        int spacing = 4;
        int totalWidth = buttonWidth * 5 + spacing * 4;
        int startX = (this.width - totalWidth) / 2;

        // + File button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.files.new_file"),
                button -> startCreateFile()
        ).dimensions(startX, buttonY, buttonWidth, 20).build());

        // + Folder button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.files.new_folder"),
                button -> startCreateFolder()
        ).dimensions(startX + buttonWidth + spacing, buttonY, buttonWidth, 20).build());

        // Rename button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.files.rename"),
                button -> startRename()
        ).dimensions(startX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, 20).build());

        // Edit button
        this.editButton = ButtonWidget.builder(
                Text.translatable("embeddedmc.files.edit"),
                button -> openSelectedFile()
        ).dimensions(startX + (buttonWidth + spacing) * 3, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(this.editButton);

        // Delete button
        this.deleteButton = ButtonWidget.builder(
                Text.translatable("embeddedmc.button.delete"),
                button -> deleteSelected()
        ).dimensions(startX + (buttonWidth + spacing) * 4, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(this.deleteButton);

        // Name input field (hidden initially)
        this.nameInput = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 10, 200, 20, Text.literal("Name"));
        this.nameInput.setMaxLength(64);
        this.nameInput.setVisible(false);
    }

    private void refreshFileList() {
        fileList.clearFileEntries();

        if (!Files.exists(currentPath)) {
            return;
        }

        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(currentPath)) {
            stream.forEach(entries::add);
        } catch (IOException e) {
            return;
        }

        // Sort: directories first, then files, alphabetically
        entries.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase()));

        for (Path path : entries) {
            fileList.addEntry(new FileListWidget.FileEntry(path, this));
        }
    }

    private void openSelectedFile() {
        FileListWidget.FileEntry entry = fileList.getSelectedOrNull();
        if (entry == null) return;
        openEntry(entry);
    }

    void openEntry(FileListWidget.FileEntry entry) {
        Path path = entry.getPath();
        if (Files.isDirectory(path)) {
            // Navigate into directory
            this.client.setScreen(new FileListScreen(parent, instance, path));
        } else if (isEditableFile(path)) {
            // Open in editor
            this.client.setScreen(new ConfigEditorScreen(this, path));
        }
    }

    private void startCreateFile() {
        isCreating = true;
        creatingFolder = false;
        nameInput.setText("new_file.txt");
        nameInput.setVisible(true);
        nameInput.setFocused(true);
        this.addSelectableChild(nameInput);
    }

    private void startCreateFolder() {
        isCreating = true;
        creatingFolder = true;
        nameInput.setText("new_folder");
        nameInput.setVisible(true);
        nameInput.setFocused(true);
        this.addSelectableChild(nameInput);
    }

    private void startRename() {
        FileListWidget.FileEntry entry = fileList.getSelectedOrNull();
        if (entry == null) return;

        isCreating = true;
        isRenaming = true;
        renamingPath = entry.getPath();
        nameInput.setText(renamingPath.getFileName().toString());
        nameInput.setVisible(true);
        nameInput.setFocused(true);
        this.addSelectableChild(nameInput);
    }

    private void confirmCreate() {
        String name = nameInput.getText().trim();
        if (name.isEmpty()) {
            cancelCreate();
            return;
        }

        try {
            if (isRenaming && renamingPath != null) {
                // Rename existing file/folder
                Path newPath = renamingPath.getParent().resolve(name);
                Files.move(renamingPath, newPath);
                EmbeddedMC.LOGGER.info("Renamed: {} -> {}", renamingPath, newPath);
            } else if (creatingFolder) {
                Path newPath = currentPath.resolve(name);
                Files.createDirectories(newPath);
                EmbeddedMC.LOGGER.info("Created folder: {}", newPath);
            } else {
                Path newPath = currentPath.resolve(name);
                Files.createFile(newPath);
                EmbeddedMC.LOGGER.info("Created file: {}", newPath);
            }
            refreshFileList();
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to complete operation", e);
        }
        cancelCreate();
    }

    private void cancelCreate() {
        isCreating = false;
        isRenaming = false;
        renamingPath = null;
        creatingFolder = false;
        nameInput.setVisible(false);
        this.remove(nameInput);
    }

    private void deleteSelected() {
        FileListWidget.FileEntry entry = fileList.getSelectedOrNull();
        if (entry == null) return;

        Path path = entry.getPath();
        String name = path.getFileName().toString();
        boolean isDir = Files.isDirectory(path);

        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    try {
                        if (isDir) {
                            deleteDirectoryRecursively(path);
                        } else {
                            Files.delete(path);
                        }
                        EmbeddedMC.LOGGER.info("Deleted: {}", path);
                        refreshFileList();
                    } catch (IOException e) {
                        EmbeddedMC.LOGGER.error("Failed to delete: {}", path, e);
                    }
                }
                this.client.setScreen(this);
            },
            Text.translatable(isDir ? "embeddedmc.confirm.delete_folder" : "embeddedmc.confirm.delete_file"),
            Text.translatable("embeddedmc.confirm.delete_message", name)
        ));
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        EmbeddedMC.LOGGER.error("Failed to delete: {}", path, e);
                    }
                });
        }
    }

    boolean isEditableFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".properties") ||
               name.endsWith(".yml") ||
               name.endsWith(".yaml") ||
               name.endsWith(".json") ||
               name.endsWith(".toml") ||
               name.endsWith(".txt") ||
               name.endsWith(".conf") ||
               name.endsWith(".cfg");
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        if (isCreating) {
            // Nur Input-Feld-Klicks verarbeiten wenn Modal offen
            if (nameInput.isMouseOver(click.x(), click.y())) {
                nameInput.setFocused(true);
                return nameInput.mouseClicked(click, handled);
            }
            return true;  // Alle anderen Klicks blockieren
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (isCreating) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                confirmCreate();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelCreate();
                return true;
            }
            // Forward all other key events to the text field
            return nameInput.keyPressed(input);
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (isCreating) {
            return nameInput.charTyped(input);
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (isCreating) {
            return nameInput.mouseDragged(click, deltaX, deltaY);
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (isCreating) {
            return nameInput.mouseReleased(click);
        }
        return super.mouseReleased(click);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Current path
        Path instanceRoot = instance.getInstancePath();
        String relativePath = instanceRoot.relativize(currentPath).toString();
        if (relativePath.isEmpty()) relativePath = "/";
        context.drawCenteredTextWithShadow(this.textRenderer, relativePath, this.width / 2, 25, 0xFFAAAAAA);

        // Render file list
        this.fileList.render(context, mouseX, mouseY, delta);

        // Draw create dialog overlay
        if (isCreating) {
            // Dark overlay
            context.fill(0, 0, this.width, this.height, 0xAA000000);

            // Dialog box
            int dialogWidth = 240;
            int dialogHeight = 80;
            int dialogX = (this.width - dialogWidth) / 2;
            int dialogY = (this.height - dialogHeight) / 2;

            context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF2A2A2A);
            context.fill(dialogX + 1, dialogY + 1, dialogX + dialogWidth - 1, dialogY + dialogHeight - 1, 0xFF1A1A1A);

            // Title
            String titleKey = isRenaming ? "embeddedmc.dialog.rename" : (creatingFolder ? "embeddedmc.dialog.create_folder" : "embeddedmc.dialog.create_file");
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(titleKey), this.width / 2, dialogY + 10, 0xFFFFFFFF);

            // Hint
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("embeddedmc.dialog.hint"),
                    this.width / 2, dialogY + dialogHeight - 15, 0xFF666666);

            // Render input field
            this.nameInput.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void close() {
        if (isCreating) {
            cancelCreate();
            return;
        }
        this.client.setScreen(parent);
    }

    public static class FileListWidget extends AlwaysSelectedEntryListWidget<FileListWidget.FileEntry> {

        public FileListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }

        public void clearFileEntries() {
            super.clearEntries();
        }

        @Override
        public int addEntry(FileEntry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        public static class FileEntry extends AlwaysSelectedEntryListWidget.Entry<FileEntry> {
            private final Path path;
            private final FileListScreen parent;
            private final boolean isDirectory;
            private final String displayName;
            private long lastClickTime = 0;

            public FileEntry(Path path, FileListScreen parent) {
                this.path = path;
                this.parent = parent;
                this.isDirectory = Files.isDirectory(path);
                this.displayName = path.getFileName().toString();
            }

            @Override
            public boolean mouseClicked(Click click, boolean handled) {
                if (click.button() == 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 250) {
                        // Double click - open file/folder
                        parent.openEntry(this);
                    }
                    lastClickTime = now;
                }
                return super.mouseClicked(click, handled);
            }

            public Path getPath() {
                return path;
            }

            @Override
            public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                MinecraftClient client = MinecraftClient.getInstance();

                int x = this.getContentX();
                int y = this.getContentY();

                // Icon/prefix
                String prefix = isDirectory ? "[D] " : "    ";
                int color = isDirectory ? 0xFFFFFF55 : 0xFFFFFFFF;

                // Check if editable
                if (!isDirectory && !parent.isEditableFile(path)) {
                    color = 0xFF888888;
                }

                context.drawTextWithShadow(client.textRenderer, prefix + displayName, x, y + 4, color);

                // File size for files
                if (!isDirectory) {
                    try {
                        long size = Files.size(path);
                        String sizeStr = formatFileSize(size);
                        int sizeX = x + parent.fileList.getRowWidth() - client.textRenderer.getWidth(sizeStr) - 10;
                        context.drawTextWithShadow(client.textRenderer, sizeStr, sizeX, y + 4, 0xFF888888);
                    } catch (IOException ignored) {}
                }
            }

            private String formatFileSize(long bytes) {
                if (bytes < 1024) return bytes + " B";
                if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
                return (bytes / (1024 * 1024)) + " MB";
            }

            @Override
            public Text getNarration() {
                return Text.literal(displayName);
            }

            // mouseClicked is handled by the parent list widget
        }
    }
}
