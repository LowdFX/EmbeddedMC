package com.embeddedmc.client.mixin;

import com.embeddedmc.EmbeddedMC;
import com.embeddedmc.client.EmbeddedMCClient;
import com.embeddedmc.client.gui.ConsoleScreen;
import com.embeddedmc.client.gui.FileListScreen;
import com.embeddedmc.config.ServerInstance;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addConsoleButton(CallbackInfo ci) {
        // Only add buttons if an EmbeddedMC server is running
        if (EmbeddedMC.getInstance() != null &&
            EmbeddedMC.getInstance().getServerManager() != null &&
            EmbeddedMC.getInstance().getServerManager().hasRunningServer()) {

            ServerInstance instance = EmbeddedMC.getInstance().getServerManager().getAnyRunningInstance();
            if (instance == null) return;

            int buttonWidth = 120;
            int buttonHeight = 20;

            // Console button with keybind: "Server Konsole (K)"
            KeyBinding keyBinding = EmbeddedMCClient.getConsoleKeyBinding();
            Text consoleText = Text.translatable("embeddedmc.button.server_console")
                    .append(" (")
                    .append(keyBinding != null ? keyBinding.getBoundKeyLocalizedText() : Text.literal("?"))
                    .append(")");

            this.addDrawableChild(ButtonWidget.builder(
                    consoleText,
                    button -> {
                        if (this.client != null) {
                            this.client.setScreen(new ConsoleScreen(this, instance));
                        }
                    }
            ).dimensions(5, 5, buttonWidth, buttonHeight).build());

            // Files button
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("embeddedmc.button.files"),
                    button -> {
                        if (this.client != null) {
                            this.client.setScreen(new FileListScreen(this, instance));
                        }
                    }
            ).dimensions(5, 28, buttonWidth, buttonHeight).build());
        }
    }
}
