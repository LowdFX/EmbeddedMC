package com.embeddedmc.client.gui;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.config.ServerInstance;
import com.embeddedmc.server.EmbeddedServer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConsoleScreen extends Screen {
    private final Screen parent;
    private final ServerInstance instance;

    private final List<String> consoleLines = new ArrayList<>();
    private Consumer<String> consoleListener;
    private TextFieldWidget commandInput;

    private int scrollOffset = 0;
    private int maxVisibleLines = 20;

    // Text selection (character-based)
    private int selectionStartLine = -1;
    private int selectionStartCol = -1;
    private int selectionEndLine = -1;
    private int selectionEndCol = -1;
    private boolean isSelecting = false;

    public ConsoleScreen(Screen parent, ServerInstance instance) {
        super(Text.literal("Console: " + instance.getName()));
        this.parent = parent;
        this.instance = instance;
    }

    @Override
    protected void init() {
        // Calculate visible lines based on screen height
        int consoleTop = 30;
        int consoleBottom = this.height - 60;
        int lineHeight = 10;
        maxVisibleLines = (consoleBottom - consoleTop) / lineHeight;

        // Command input field
        this.commandInput = new TextFieldWidget(
            this.textRenderer,
            10, this.height - 50, this.width - 130, 20,
            Text.literal("Command")
        );
        this.commandInput.setMaxLength(256);
        this.commandInput.setChangedListener(s -> {});
        this.addSelectableChild(this.commandInput);

        // Send button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Send"),
            button -> sendCommand()
        ).dimensions(this.width - 115, this.height - 50, 50, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("embeddedmc.button.back"),
            button -> this.client.setScreen(parent)
        ).dimensions(this.width - 60, this.height - 50, 50, 20).build());

        // Load existing console buffer
        EmbeddedServer server = EmbeddedMC.getInstance().getServerManager().getServer(instance.getId());
        if (server != null) {
            consoleLines.addAll(server.getConsoleBuffer());

            // Register listener for new lines
            consoleListener = line -> {
                if (this.client != null) {
                    this.client.execute(() -> {
                        consoleLines.add(line);
                        // Auto-scroll if at bottom
                        int maxScroll = Math.max(0, consoleLines.size() - maxVisibleLines);
                        if (scrollOffset >= maxScroll - 1) {
                            scrollOffset = maxScroll;
                        }
                    });
                }
            };
            server.addConsoleListener(consoleListener);
        }

        // Scroll to bottom
        scrollOffset = Math.max(0, consoleLines.size() - maxVisibleLines);

        // Focus on command input
        this.setInitialFocus(this.commandInput);
    }

    private void sendCommand() {
        String command = commandInput.getText().trim();
        if (command.isEmpty()) return;

        EmbeddedServer server = EmbeddedMC.getInstance().getServerManager().getServer(instance.getId());
        if (server != null) {
            server.sendCommand(command);
        }

        commandInput.setText("");
    }

    private boolean enterWasPressed = false;

    @Override
    public void tick() {
        super.tick();

        // Check for Enter key press while text field is focused
        if (this.client != null && commandInput.isFocused()) {
            boolean enterPressed = GLFW.glfwGetKey(this.client.getWindow().getHandle(), GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
                                   GLFW.glfwGetKey(this.client.getWindow().getHandle(), GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
            if (enterPressed && !enterWasPressed) {
                sendCommand();
            }
            enterWasPressed = enterPressed;
        } else {
            enterWasPressed = false;
        }
    }

    public void scroll(int amount) {
        int maxScroll = Math.max(0, consoleLines.size() - maxVisibleLines);
        scrollOffset += amount;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    /**
     * Sanitize text by removing characters that Minecraft can't render.
     * Only allows ASCII printable chars, German umlauts, and MC formatting codes.
     */
    private String sanitizeText(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            // ASCII printable characters (space to tilde)
            if (c >= 32 && c < 127) {
                sb.append(c);
            }
            // Minecraft formatting code prefix (§)
            else if (c == '\u00A7') {
                sb.append(c);
            }
            // German umlauts and common Latin-1 supplement
            else if (c == '\u00E4' || c == '\u00F6' || c == '\u00FC' ||  // ä ö ü
                     c == '\u00C4' || c == '\u00D6' || c == '\u00DC' ||  // Ä Ö Ü
                     c == '\u00DF') {                                      // ß
                sb.append(c);
            }
            // Tab becomes spaces
            else if (c == '\t') {
                sb.append("    ");
            }
            // Skip all other characters (box drawing, control chars, etc.)
            // Don't add space to avoid stretching the line
        }
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll((int) -verticalAmount * 3);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // Check if click is in console area
        int consoleTop = 30;
        int consoleBottom = this.height - 60;
        int consoleLeft = 10;
        int consoleRight = this.width - 10;

        if (button == 0 && mouseX >= consoleLeft && mouseX <= consoleRight &&
            mouseY >= consoleTop && mouseY <= consoleBottom) {
            // Start selection
            int lineHeight = 10;
            int clickedLine = scrollOffset + (int) ((mouseY - consoleTop - 5) / lineHeight);
            if (clickedLine >= 0 && clickedLine < consoleLines.size()) {
                selectionStartLine = clickedLine;
                selectionEndLine = clickedLine;
                // Calculate column based on mouse X position
                String line = sanitizeText(consoleLines.get(clickedLine));
                int col = getColumnAtX(line, (int) mouseX - consoleLeft - 5);
                selectionStartCol = col;
                selectionEndCol = col;
                isSelecting = true;
            }
            return true;
        }
        return super.mouseClicked(click, handled);
    }

    /**
     * Calculate which character column is at the given X pixel offset.
     */
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
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (isSelecting && button == 0) {
            int consoleTop = 30;
            int consoleLeft = 10;
            int lineHeight = 10;
            int draggedLine = scrollOffset + (int) ((mouseY - consoleTop - 5) / lineHeight);
            draggedLine = Math.max(0, Math.min(draggedLine, consoleLines.size() - 1));
            selectionEndLine = draggedLine;

            // Calculate end column
            if (draggedLine >= 0 && draggedLine < consoleLines.size()) {
                String line = sanitizeText(consoleLines.get(draggedLine));
                selectionEndCol = getColumnAtX(line, (int) mouseX - consoleLeft - 5);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            isSelecting = false;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int modifiers = input.modifiers();

        // Ctrl+C to copy console selection - works even with input focused
        if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (selectionStartLine >= 0 && selectionEndLine >= 0) {
                copySelection();
                return true;
            }
        }

        // If input field is focused, let it handle other shortcuts
        if (commandInput.isFocused()) {
            return super.keyPressed(input);
        }
        // Ctrl+A to select all console lines (only when input not focused)
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (!consoleLines.isEmpty()) {
                selectionStartLine = 0;
                selectionStartCol = 0;
                selectionEndLine = consoleLines.size() - 1;
                String lastLine = consoleLines.get(selectionEndLine);
                selectionEndCol = lastLine.length();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    private void copySelection() {
        if (selectionStartLine < 0 || selectionEndLine < 0) return;

        // Normalize selection direction (start should be before end)
        int startLine, endLine, startCol, endCol;
        if (selectionStartLine < selectionEndLine ||
            (selectionStartLine == selectionEndLine && selectionStartCol <= selectionEndCol)) {
            startLine = selectionStartLine;
            endLine = selectionEndLine;
            startCol = selectionStartCol;
            endCol = selectionEndCol;
        } else {
            startLine = selectionEndLine;
            endLine = selectionStartLine;
            startCol = selectionEndCol;
            endCol = selectionStartCol;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= endLine && i < consoleLines.size(); i++) {
            if (sb.length() > 0) sb.append("\n");
            String line = consoleLines.get(i);

            if (startLine == endLine) {
                // Single line selection - use both columns
                int from = Math.max(0, Math.min(startCol, line.length()));
                int to = Math.max(0, Math.min(endCol, line.length()));
                sb.append(line.substring(from, to));
            } else if (i == startLine) {
                // First line - from startCol to end
                int from = Math.max(0, Math.min(startCol, line.length()));
                sb.append(line.substring(from));
            } else if (i == endLine) {
                // Last line - from start to endCol
                int to = Math.max(0, Math.min(endCol, line.length()));
                sb.append(line.substring(0, to));
            } else {
                // Middle lines - full line
                sb.append(line);
            }
        }

        if (this.client != null && sb.length() > 0) {
            this.client.keyboard.setClipboard(sb.toString());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // Console background
        int consoleLeft = 10;
        int consoleTop = 30;
        int consoleRight = this.width - 10;
        int consoleBottom = this.height - 60;

        context.fill(consoleLeft, consoleTop, consoleRight, consoleBottom, 0xFF1A1A1A);

        // Console lines
        int lineHeight = 10;
        int y = consoleTop + 5;
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(consoleLines.size(), scrollOffset + maxVisibleLines);

        for (int i = visibleStart; i < visibleEnd; i++) {
            String line = sanitizeText(consoleLines.get(i));

            // Draw selection highlight (character-based)
            if (selectionStartLine >= 0 && selectionEndLine >= 0) {
                // Normalize selection direction
                int startLine, endLine, startCol, endCol;
                if (selectionStartLine < selectionEndLine ||
                    (selectionStartLine == selectionEndLine && selectionStartCol <= selectionEndCol)) {
                    startLine = selectionStartLine;
                    endLine = selectionEndLine;
                    startCol = selectionStartCol;
                    endCol = selectionEndCol;
                } else {
                    startLine = selectionEndLine;
                    endLine = selectionStartLine;
                    startCol = selectionEndCol;
                    endCol = selectionStartCol;
                }

                if (i >= startLine && i <= endLine) {
                    int highlightStartX = consoleLeft + 5;
                    int highlightEndX = consoleLeft + 5 + this.textRenderer.getWidth(line);

                    if (i == startLine && i == endLine) {
                        // Single line - highlight from startCol to endCol
                        String before = line.substring(0, Math.min(startCol, line.length()));
                        String selected = line.substring(Math.min(startCol, line.length()), Math.min(endCol, line.length()));
                        highlightStartX = consoleLeft + 5 + this.textRenderer.getWidth(before);
                        highlightEndX = highlightStartX + this.textRenderer.getWidth(selected);
                    } else if (i == startLine) {
                        // First line - highlight from startCol to end
                        String before = line.substring(0, Math.min(startCol, line.length()));
                        highlightStartX = consoleLeft + 5 + this.textRenderer.getWidth(before);
                    } else if (i == endLine) {
                        // Last line - highlight from start to endCol
                        String selected = line.substring(0, Math.min(endCol, line.length()));
                        highlightEndX = consoleLeft + 5 + this.textRenderer.getWidth(selected);
                    }
                    // Middle lines use full line width (default values)

                    context.fill(highlightStartX, y - 1, highlightEndX, y + lineHeight - 1, 0xFF3355AA);
                }
            }

            // Color based on content
            int color = 0xFFCCCCCC;
            if (line.startsWith(">")) {
                color = 0xFF88FF88; // Commands in green
            } else if (line.contains("ERROR") || line.contains("Exception")) {
                color = 0xFFFF8888; // Errors in red
            } else if (line.contains("WARN")) {
                color = 0xFFFFFF88; // Warnings in yellow
            } else if (line.contains("INFO")) {
                color = 0xFFAAAAAA;
            }

            // Truncate long lines
            String displayLine = line;
            int maxWidth = consoleRight - consoleLeft - 15;
            if (this.textRenderer.getWidth(displayLine) > maxWidth) {
                while (this.textRenderer.getWidth(displayLine + "...") > maxWidth && displayLine.length() > 0) {
                    displayLine = displayLine.substring(0, displayLine.length() - 1);
                }
                displayLine += "...";
            }

            context.drawTextWithShadow(this.textRenderer, displayLine, consoleLeft + 5, y, color);
            y += lineHeight;
        }

        // Scrollbar
        if (consoleLines.size() > maxVisibleLines) {
            int scrollbarHeight = consoleBottom - consoleTop - 4;
            int thumbHeight = Math.max(20, scrollbarHeight * maxVisibleLines / consoleLines.size());
            int maxScroll = consoleLines.size() - maxVisibleLines;
            int thumbY = consoleTop + 2 + (scrollbarHeight - thumbHeight) * scrollOffset / maxScroll;

            context.fill(consoleRight - 6, consoleTop + 2, consoleRight - 2, consoleBottom - 2, 0xFF333333);
            context.fill(consoleRight - 6, thumbY, consoleRight - 2, thumbY + thumbHeight, 0xFF666666);
        }

        // Command input
        this.commandInput.render(context, mouseX, mouseY, delta);

        // Status
        EmbeddedServer server = EmbeddedMC.getInstance().getServerManager().getServer(instance.getId());
        String status = server != null && server.isRunning() ? "Server running" : "Server not running";
        int statusColor = server != null && server.isRunning() ? 0xFF88FF88 : 0xFFFF8888;
        context.drawTextWithShadow(this.textRenderer, status, 10, this.height - 25, statusColor);
    }

    @Override
    public void close() {
        // Remove listener
        if (consoleListener != null) {
            EmbeddedServer server = EmbeddedMC.getInstance().getServerManager().getServer(instance.getId());
            if (server != null) {
                server.removeConsoleListener(consoleListener);
            }
        }
        this.client.setScreen(parent);
    }
}
