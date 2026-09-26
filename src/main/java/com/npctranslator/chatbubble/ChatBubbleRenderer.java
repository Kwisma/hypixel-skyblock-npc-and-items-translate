package com.npctranslator.chatbubble;

import com.npctranslator.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;

public class ChatBubbleRenderer {

    public static void render(DrawContext context, TextRenderer textRenderer) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.enableChatBubbles) return;

        Collection<ChatBubble> bubbles = ChatBubbleManager.getActiveBubbles();
        if (bubbles.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        Window window = client.getWindow();
        if (window == null) return;

        int screenWidth = window.getScaledWidth();
        int screenHeight = window.getScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return;

        double maxDist = config.chatBubbleMaxDistance;
        double maxDistSq = maxDist * maxDist;
        Vec3d camPos = camera.getCameraPos();

        for (ChatBubble bubble : bubbles) {
            float alpha = bubble.getAlpha();
            if (alpha <= 0f) continue;

            // Follow entity if tracked
            if (bubble.entityId != -1) {
                Entity e = client.world.getEntityById(bubble.entityId);
                if (e != null) {
                    bubble.x = e.getX();
                    bubble.y = (e instanceof ArmorStandEntity)
                            ? e.getY() + 0.35
                            : e.getY() + e.getStandingEyeHeight() + 0.45;
                    bubble.z = e.getZ();
                }
            }

            double distSq = client.player.squaredDistanceTo(bubble.x, bubble.y, bubble.z);
            if (distSq > maxDistSq) continue;

            // Project world coordinates (bubble.x, bubble.y, bubble.z) to screen coordinates
            float dx = (float) (bubble.x - camPos.x);
            float dy = (float) (bubble.y - camPos.y);
            float dz = (float) (bubble.z - camPos.z);

            Vector3f rel = new Vector3f(dx, dy, dz);
            Quaternionf rot = new Quaternionf(camera.getRotation()).conjugate();
            rot.transform(rel);

            // In front of camera check (OpenGL view space: -Z is forward)
            if (rel.z >= -0.5f) continue;

            float depth = -rel.z;
            float fov = (float) client.options.getFov().getValue();
            if (fov <= 0f) fov = 70f;
            double tanHalfFov = Math.tan(Math.toRadians(fov) / 2.0);
            double aspect = (double) screenWidth / (double) screenHeight;

            float projX = (float) (rel.x / (depth * tanHalfFov * aspect));
            float projY = (float) (rel.y / (depth * tanHalfFov));

            int screenX = (int) ((screenWidth / 2.0f) * (1.0f + projX));
            int screenY = (int) ((screenHeight / 2.0f) * (1.0f - projY));

            if (screenX < -200 || screenX > screenWidth + 200 || screenY < -200 || screenY > screenHeight + 200) {
                continue;
            }

            // Prepare text and lines
            String title = (bubble.isNpc ? "[NPC] " : "") + bubble.speakerName;
            List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(bubble.text), 160);
            if (lines.isEmpty()) continue;

            // Measure box bounds
            int titleWidth = textRenderer.getWidth(title);
            int maxLineWidth = titleWidth;
            int maxLinesToRender = Math.min(lines.size(), 7);
            for (int i = 0; i < maxLinesToRender; i++) {
                maxLineWidth = Math.max(maxLineWidth, textRenderer.getWidth(lines.get(i)));
            }

            int bubbleWidth = maxLineWidth + 16;
            int bubbleHeight = (maxLinesToRender + 1) * 10 + 6;

            int x1 = screenX - bubbleWidth / 2;
            int y1 = screenY - bubbleHeight - 8;
            int x2 = screenX + bubbleWidth / 2;
            int y2 = screenY - 8;

            int bgAlpha = (int) (alpha * 215) & 0xFF;
            int borderAlpha = (int) (alpha * 240) & 0xFF;
            int textAlpha = (int) (alpha * 255) & 0xFF;

            int bgColor = (bgAlpha << 24) | 0x0E141D;
            int borderColor = (borderAlpha << 24) | 0x38BDF8;
            int titleColor = (textAlpha << 24) | 0xFBBF24;
            int textColor = (textAlpha << 24) | 0xF8FAFC;

            // 1. Box Background
            context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, bgColor);

            // 2. Box Border (1px outline with soft corners)
            context.fill(x1 + 1, y1, x2 - 1, y1 + 1, borderColor);
            context.fill(x1 + 1, y2 - 1, x2 - 1, y2, borderColor);
            context.fill(x1, y1 + 1, x1 + 1, y2 - 1, borderColor);
            context.fill(x2 - 1, y1 + 1, x2, y2 - 1, borderColor);

            // 3. Downward Pointer Tail
            context.fill(screenX - 2, y2, screenX + 3, y2 + 1, borderColor);
            context.fill(screenX - 1, y2 + 1, screenX + 2, y2 + 2, borderColor);
            context.fill(screenX, y2 + 2, screenX + 1, y2 + 3, borderColor);

            // 4. Title (Speaker Name)
            context.drawText(textRenderer, title, x1 + 8, y1 + 4, titleColor, true);

            // 5. Dialogue lines
            int curY = y1 + 15;
            for (int i = 0; i < maxLinesToRender; i++) {
                context.drawText(textRenderer, lines.get(i), x1 + 8, curY, textColor, true);
                curY += 10;
            }
        }
    }
}
