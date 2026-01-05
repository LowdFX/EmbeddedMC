package com.embeddedmc.client.mixin;

import com.embeddedmc.client.gui.ServerSelectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addPluginButton(CallbackInfo ci) {
        // Add "Singleplayer with Plugins" button above the normal singleplayer button
        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int y = this.height / 4 + 48 - 24; // Above "Singleplayer" button

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("embeddedmc.button.singleplayer_plugins"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new ServerSelectScreen(this));
                    }
                }
        ).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
    }
}
