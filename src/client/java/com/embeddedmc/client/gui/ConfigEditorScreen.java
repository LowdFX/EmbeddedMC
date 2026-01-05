package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigEditorScreen extends Screen {
    private final Screen parent;
    private final Path filePath;

    private List<String> lines;
    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int scrollOffset = 0;
    private int maxVisibleLines;
    private boolean hasChanges = false;

    // Selection (start is anchor, end follows cursor)
    private int selectionStartLine = -1;
    private int selectionStartCol = -1;
    private boolean isSelecting = false;

    private static final int LINE_HEIGHT = 12;
    private static final int EDITOR_TOP = 40;
    private static final int EDITOR_PADDING = 10;

    public ConfigEditorScreen(Screen parent, Path filePath) {
        super(Text.literal("Edit: " + filePath.getFileName().toString()));
        this.parent = parent;
        this.filePath = filePath;
        this.lines = new ArrayList<>();
    }

    private int getLineHeight() {
        return LINE_HEIGHT;
    }

    @Override
    protected void init() {
        // Calculate visible lines
        int editorBottom = this.height - 60;
        maxVisibleLines = (editorBottom - EDITOR_TOP - 10) / getLineHeight();

        // Load file content
        loadFile();

        // Save button - saves and closes
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save"),
                button -> {
                    saveFile();
                    this.client.setScreen(parent);
                }
        ).dimensions(this.width / 2 - 105, this.height - 52, 100, 20).build());

        // Cancel button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.cancel"),
                button -> this.client.setScreen(parent)
        ).dimensions(this.width / 2 + 5, this.height - 52, 100, 20).build());
    }

    private void loadFile() {
        try {
            if (Files.exists(filePath)) {
                lines = new ArrayList<>(Files.readAllLines(filePath));
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to load file: {}", filePath, e);
            lines = new ArrayList<>();
            lines.add("# Error loading file");
        }
    }

    private void saveFile() {
        try {
            Files.writeString(filePath, String.join("\n", lines));
            hasChanges = false;
            EmbeddedMC.LOGGER.info("Saved file: {}", filePath);
        } catch (IOException e) {
            EmbeddedMC.LOGGER.error("Failed to save file: {}", filePath, e);
        }
    }

    private boolean hasSelection() {
        return selectionStartLine >= 0 && selectionStartCol >= 0;
    }

    private void clearSelection() {
        selectionStartLine = -1;
        selectionStartCol = -1;
    }

    private void startSelection() {
        if (!hasSelection()) {
            selectionStartLine = cursorLine;
            selectionStartCol = cursorColumn;
        }
    }

    private void deleteSelection() {
        if (!hasSelection()) return;

        // Normalize selection
        int startLine, endLine, startCol, endCol;
        if (selectionStartLine < cursorLine || (selectionStartLine == cursorLine && selectionStartCol < cursorColumn)) {
            startLine = selectionStartLine;
            startCol = selectionStartCol;
            endLine = cursorLine;
            endCol = cursorColumn;
        } else {
            startLine = cursorLine;
            startCol = cursorColumn;
            endLine = selectionStartLine;
            endCol = selectionStartCol;
        }

        if (startLine == endLine) {
            // Single line deletion
            String line = lines.get(startLine);
            String newLine = line.substring(0, startCol) + line.substring(Math.min(endCol, line.length()));
            lines.set(startLine, newLine);
        } else {
            // Multi-line deletion
            String firstLine = lines.get(startLine);
            String lastLine = lines.get(endLine);
            String newLine = firstLine.substring(0, startCol) + lastLine.substring(Math.min(endCol, lastLine.length()));
            lines.set(startLine, newLine);

            // Remove lines between
            for (int i = endLine; i > startLine; i--) {
                lines.remove(i);
            }
        }

        cursorLine = startLine;
        cursorColumn = startCol;
        clearSelection();
        hasChanges = true;
    }

    private String getSelectedText() {
        if (!hasSelection()) return "";

        int startLine, endLine, startCol, endCol;
        if (selectionStartLine < cursorLine || (selectionStartLine == cursorLine && selectionStartCol < cursorColumn)) {
            startLine = selectionStartLine;
            startCol = selectionStartCol;
            endLine = cursorLine;
            endCol = cursorColumn;
        } else {
            startLine = cursorLine;
            startCol = cursorColumn;
            endLine = selectionStartLine;
            endCol = selectionStartCol;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            String line = lines.get(i);
            if (startLine == endLine) {
                sb.append(line.substring(Math.min(startCol, line.length()), Math.min(endCol, line.length())));
            } else if (i == startLine) {
                sb.append(line.substring(Math.min(startCol, line.length())));
            } else if (i == endLine) {
                sb.append("\n").append(line.substring(0, Math.min(endCol, line.length())));
            } else {
                sb.append("\n").append(line);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        // Ctrl+S - Save
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            saveFile();
            return true;
        }

        // Ctrl+A - Select all
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectionStartLine = 0;
            selectionStartCol = 0;
            cursorLine = lines.size() - 1;
            cursorColumn = lines.get(cursorLine).length();
            return true;
        }

        // Ctrl+C - Copy
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection() && this.client != null) {
                this.client.keyboard.setClipboard(getSelectedText());
            }
            return true;
        }

        // Ctrl+X - Cut
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            if (hasSelection() && this.client != null) {
                this.client.keyboard.setClipboard(getSelectedText());
                deleteSelection();
            }
            return true;
        }

        // Ctrl+V - Paste
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            if (this.client != null) {
                String clipboard = this.client.keyboard.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    if (hasSelection()) {
                        deleteSelection();
                    }
                    // Insert clipboard text
                    String[] pasteLines = clipboard.split("\n", -1);
                    if (pasteLines.length == 1) {
                        insertText(pasteLines[0]);
                    } else {
                        String currentLine = getCurrentLine();
                        String before = currentLine.substring(0, cursorColumn);
                        String after = currentLine.substring(cursorColumn);

                        lines.set(cursorLine, before + pasteLines[0]);
                        for (int i = 1; i < pasteLines.length - 1; i++) {
                            lines.add(cursorLine + i, pasteLines[i]);
                        }
                        lines.add(cursorLine + pasteLines.length - 1, pasteLines[pasteLines.length - 1] + after);

                        cursorLine += pasteLines.length - 1;
                        cursorColumn = pasteLines[pasteLines.length - 1].length();
                        hasChanges = true;
                    }
                }
            }
            return true;
        }

        // Navigation with optional shift for selection
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (shift) startSelection();
                else clearSelection();
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorColumn = Math.min(cursorColumn, getCurrentLine().length());
                    ensureCursorVisible();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (shift) startSelection();
                else clearSelection();
                if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorColumn = Math.min(cursorColumn, getCurrentLine().length());
                    ensureCursorVisible();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) startSelection();
                else clearSelection();
                if (cursorColumn > 0) {
                    cursorColumn--;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorColumn = getCurrentLine().length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) startSelection();
                else clearSelection();
                if (cursorColumn < getCurrentLine().length()) {
                    cursorColumn++;
                } else if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorColumn = 0;
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shift) startSelection();
                else clearSelection();
                cursorColumn = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (shift) startSelection();
                else clearSelection();
                cursorColumn = getCurrentLine().length();
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (shift) startSelection();
                else clearSelection();
                cursorLine = Math.max(0, cursorLine - maxVisibleLines);
                cursorColumn = Math.min(cursorColumn, getCurrentLine().length());
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                if (shift) startSelection();
                else clearSelection();
                cursorLine = Math.min(lines.size() - 1, cursorLine + maxVisibleLines);
                cursorColumn = Math.min(cursorColumn, getCurrentLine().length());
                ensureCursorVisible();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (hasSelection()) deleteSelection();
                insertNewLine();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else {
                    deleteBackward();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else {
                    deleteForward();
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (hasSelection()) deleteSelection();
                insertText("    ");
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int codepoint = input.codepoint();
        if (codepoint >= 32 && codepoint != 127) {
            if (hasSelection()) deleteSelection();
            insertText(Character.toString(codepoint));
            return true;
        }
        return super.charTyped(input);
    }

    private void insertText(String text) {
        String line = getCurrentLine();
        String newLine = line.substring(0, cursorColumn) + text + line.substring(cursorColumn);
        lines.set(cursorLine, newLine);
        cursorColumn += text.length();
        hasChanges = true;
    }

    private void insertNewLine() {
        String line = getCurrentLine();
        String before = line.substring(0, cursorColumn);
        String after = line.substring(cursorColumn);

        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, after);

        cursorLine++;
        cursorColumn = 0;
        ensureCursorVisible();
        hasChanges = true;
    }

    private void deleteBackward() {
        if (cursorColumn > 0) {
            String line = getCurrentLine();
            String newLine = line.substring(0, cursorColumn - 1) + line.substring(cursorColumn);
            lines.set(cursorLine, newLine);
            cursorColumn--;
            hasChanges = true;
        } else if (cursorLine > 0) {
            String currentLine = getCurrentLine();
            cursorLine--;
            cursorColumn = getCurrentLine().length();
            lines.set(cursorLine, getCurrentLine() + currentLine);
            lines.remove(cursorLine + 1);
            hasChanges = true;
        }
    }

    private void deleteForward() {
        String line = getCurrentLine();
        if (cursorColumn < line.length()) {
            String newLine = line.substring(0, cursorColumn) + line.substring(cursorColumn + 1);
            lines.set(cursorLine, newLine);
            hasChanges = true;
        } else if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, line + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
            hasChanges = true;
        }
    }

    private String getCurrentLine() {
        if (cursorLine >= 0 && cursorLine < lines.size()) {
            return lines.get(cursorLine);
        }
        return "";
    }

    private void ensureCursorVisible() {
        if (cursorLine < scrollOffset) {
            scrollOffset = cursorLine;
        } else if (cursorLine >= scrollOffset + maxVisibleLines) {
            scrollOffset = cursorLine - maxVisibleLines + 1;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) verticalAmount * 3;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, lines.size() - maxVisibleLines)));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mouseX = click.x();
        double mouseY = click.y();

        int editorLeft = EDITOR_PADDING + 40;
        int editorRight = this.width - EDITOR_PADDING;
        int editorBottom = this.height - 60;

        if (mouseX >= editorLeft && mouseX <= editorRight &&
            mouseY >= EDITOR_TOP && mouseY <= editorBottom) {

            int clickedLine = scrollOffset + (int) ((mouseY - EDITOR_TOP) / getLineHeight());
            clickedLine = Math.max(0, Math.min(clickedLine, lines.size() - 1));

            String line = lines.get(clickedLine);
            int clickedCol = getColumnAtX(line, (int) mouseX - editorLeft);

            // Start selection on click
            clearSelection();
            selectionStartLine = clickedLine;
            selectionStartCol = Math.min(clickedCol, line.length());
            isSelecting = true;

            cursorLine = clickedLine;
            cursorColumn = Math.min(clickedCol, line.length());
            return true;
        }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (isSelecting) {
            int editorLeft = EDITOR_PADDING + 40;
            int editorBottom = this.height - 60;

            int draggedLine = scrollOffset + (int) ((mouseY - EDITOR_TOP) / getLineHeight());
            draggedLine = Math.max(0, Math.min(draggedLine, lines.size() - 1));

            String line = lines.get(draggedLine);
            int draggedCol = getColumnAtX(line, (int) mouseX - editorLeft);

            cursorLine = draggedLine;
            cursorColumn = Math.min(draggedCol, line.length());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            isSelecting = false;
            // If selection start equals cursor, clear selection (just a click)
            if (selectionStartLine == cursorLine && selectionStartCol == cursorColumn) {
                clearSelection();
            }
        }
        return super.mouseReleased(click);
    }

    private int getColumnAtX(String line, int xOffset) {
        if (xOffset <= 0 || line.isEmpty()) return 0;

        int totalWidth = 0;
        for (int i = 0; i < line.length(); i++) {
            int charWidth = this.textRenderer.getWidth(String.valueOf(line.charAt(i)));
            if (totalWidth + charWidth / 2 > xOffset) {
                return i;
            }
            totalWidth += charWidth;
        }
        return line.length();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int lineHeight = getLineHeight();

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Modified indicator
        if (hasChanges) {
            context.drawTextWithShadow(this.textRenderer, "*", this.width / 2 + this.textRenderer.getWidth(this.title.getString()) / 2 + 5, 10, 0xFFFF5555);
        }

        // Editor background
        int editorLeft = EDITOR_PADDING;
        int editorRight = this.width - EDITOR_PADDING;
        int editorBottom = this.height - 60;

        context.fill(editorLeft, EDITOR_TOP, editorRight, editorBottom, 0xFF1A1A1A);

        // Line number background
        context.fill(editorLeft, EDITOR_TOP, editorLeft + 35, editorBottom, 0xFF252525);

        // Normalize selection for rendering
        int selStartLine = -1, selEndLine = -1, selStartCol = -1, selEndCol = -1;
        if (hasSelection()) {
            if (selectionStartLine < cursorLine || (selectionStartLine == cursorLine && selectionStartCol < cursorColumn)) {
                selStartLine = selectionStartLine;
                selStartCol = selectionStartCol;
                selEndLine = cursorLine;
                selEndCol = cursorColumn;
            } else {
                selStartLine = cursorLine;
                selStartCol = cursorColumn;
                selEndLine = selectionStartLine;
                selEndCol = selectionStartCol;
            }
        }

        // Render lines
        int y = EDITOR_TOP + 2;
        int textX = editorLeft + 40;

        int maxWidth = editorRight - textX - 10;

        for (int i = scrollOffset; i < Math.min(lines.size(), scrollOffset + maxVisibleLines); i++) {
            // Line number
            String lineNum = String.valueOf(i + 1);
            int lineNumX = editorLeft + 30 - this.textRenderer.getWidth(lineNum);
            context.drawTextWithShadow(this.textRenderer, lineNum, lineNumX, y, 0xFF666666);

            // Current line highlight (if no selection on this line)
            if (i == cursorLine && !hasSelection()) {
                context.fill(editorLeft + 35, y - 1, editorRight - 2, y + lineHeight - 1, 0xFF2A2A40);
            }

            // Line text
            String line = lines.get(i);

            // Draw selection highlight
            if (hasSelection() && i >= selStartLine && i <= selEndLine) {
                int highlightStartX = textX;
                int highlightEndX = textX + this.textRenderer.getWidth(line);

                if (i == selStartLine && i == selEndLine) {
                    String before = line.substring(0, Math.min(selStartCol, line.length()));
                    String selected = line.substring(Math.min(selStartCol, line.length()), Math.min(selEndCol, line.length()));
                    highlightStartX = textX + this.textRenderer.getWidth(before);
                    highlightEndX = highlightStartX + this.textRenderer.getWidth(selected);
                } else if (i == selStartLine) {
                    String before = line.substring(0, Math.min(selStartCol, line.length()));
                    highlightStartX = textX + this.textRenderer.getWidth(before);
                } else if (i == selEndLine) {
                    String selected = line.substring(0, Math.min(selEndCol, line.length()));
                    highlightEndX = textX + this.textRenderer.getWidth(selected);
                }

                context.fill(highlightStartX, y - 1, highlightEndX, y + lineHeight - 1, 0xFF3355AA);
            }

            // Simple syntax highlighting
            int textColor = getLineColor(line);

            // Truncate if too long
            String displayLine = line;
            if (this.textRenderer.getWidth(displayLine) > maxWidth) {
                while (this.textRenderer.getWidth(displayLine + "...") > maxWidth && displayLine.length() > 0) {
                    displayLine = displayLine.substring(0, displayLine.length() - 1);
                }
                displayLine += "...";
            }

            context.drawTextWithShadow(this.textRenderer, displayLine, textX, y, textColor);

            // Cursor
            if (i == cursorLine) {
                int cursorX = textX + this.textRenderer.getWidth(line.substring(0, Math.min(cursorColumn, line.length())));
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    context.fill(cursorX, y, cursorX + 1, y + lineHeight - 2, 0xFFFFFFFF);
                }
            }

            y += lineHeight;
        }

        // Scrollbar
        if (lines.size() > maxVisibleLines) {
            int scrollbarHeight = editorBottom - EDITOR_TOP - 4;
            int thumbHeight = Math.max(20, scrollbarHeight * maxVisibleLines / lines.size());
            int maxScroll = lines.size() - maxVisibleLines;
            int thumbY = EDITOR_TOP + 2 + (scrollbarHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);

            context.fill(editorRight - 6, EDITOR_TOP + 2, editorRight - 2, editorBottom - 2, 0xFF333333);
            context.fill(editorRight - 6, thumbY, editorRight - 2, thumbY + thumbHeight, 0xFF666666);
        }

        // Status bar
        String status = "Line " + (cursorLine + 1) + ", Col " + (cursorColumn + 1);
        if (hasSelection()) {
            status += " | Selected";
        }
        context.drawTextWithShadow(this.textRenderer, status, EDITOR_PADDING, this.height - 25, 0xFF888888);
    }

    private int getLineColor(String line) {
        String trimmed = line.trim();

        if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
            return 0xFF6A9955;
        }
        if (trimmed.contains(":") && !trimmed.startsWith("-")) {
            return 0xFF9CDCFE;
        }
        if (line.contains("=")) {
            return 0xFF9CDCFE;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("}") ||
            trimmed.startsWith("[") || trimmed.startsWith("]")) {
            return 0xFFD4D4D4;
        }

        return 0xFFCCCCCC;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
