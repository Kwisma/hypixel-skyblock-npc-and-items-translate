package com.npctranslator.mixin;

import com.npctranslator.chatbubble.ChatBubbleRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderState(GuiGraphicsExtractor context, Font font, int currentTick, int mouseX, int mouseY, ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        ChatBubbleRenderer.render(context, font);
    }
}
