package com.embeddedmc.client;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.client.gui.ConsoleScreen;
import com.embeddedmc.config.ServerInstance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class EmbeddedMCClient implements ClientModInitializer {

    private static EmbeddedMCClient instance;
    private static KeyBinding consoleKeyBinding;

    // Custom keybind category for EmbeddedMC (appears at top of keybinds list)
    private static final KeyBinding.Category EMBEDDEDMC_CATEGORY =
            KeyBinding.Category.create(Identifier.of("embeddedmc", "category"));

    @Override
    public void onInitializeClient() {
        instance = this;
        EmbeddedMC.LOGGER.info("EmbeddedMC client initialized!");

        // Register console keybinding (K key) in our custom category
        consoleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.embeddedmc.console",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                EMBEDDEDMC_CATEGORY
        ));

        // Register tick handler for keybinding
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (consoleKeyBinding.wasPressed()) {
                openConsole(client);
            }
        });
    }

    private void openConsole(MinecraftClient client) {
        // Only open if a server is running and we're in-game
        if (client.player == null) return;

        if (EmbeddedMC.getInstance() != null &&
            EmbeddedMC.getInstance().getServerManager() != null &&
            EmbeddedMC.getInstance().getServerManager().hasRunningServer()) {

            ServerInstance instance = EmbeddedMC.getInstance().getServerManager().getAnyRunningInstance();
            if (instance != null) {
                client.setScreen(new ConsoleScreen(null, instance));
            }
        }
    }

    public static EmbeddedMCClient getInstance() {
        return instance;
    }

    public static KeyBinding getConsoleKeyBinding() {
        return consoleKeyBinding;
    }
}
