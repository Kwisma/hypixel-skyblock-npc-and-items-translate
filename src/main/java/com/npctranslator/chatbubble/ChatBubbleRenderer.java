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

        Collection<List<ChatBubble>> stacks = ChatBubbleManager.getActiveBubbleStacks();
        if (stacks.isEmpty()) return;

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

        for (List<ChatBubble> stack : stacks) {
            if (stack.isEmpty()) continue;

            // Use the latest bubble's location
            ChatBubble baseBubble = stack.get(stack.size() - 1);

            // Follow entity if tracked
            if (baseBubble.entityId != -1) {
                Entity e = client.world.getEntityById(baseBubble.entityId);
                if (e != null) {
                    baseBubble.x = e.getX();
                    baseBubble.y = (e instanceof ArmorStandEntity)
                            ? e.getY() + 0.35
                            : e.getY() + e.getStandingEyeHeight() + 0.45;
                    baseBubble.z = e.getZ();
                }
            }

            double distSq = client.player.squaredDistanceTo(baseBubble.x, baseBubble.y, baseBubble.z);
            if (distSq > maxDistSq) continue;

            // Project world coordinates (baseBubble.x, baseBubble.y, baseBubble.z) to screen
            float dx = (float) (baseBubble.x - camPos.x);
            float dy = (float) (baseBubble.y - camPos.y);
            float dz = (float) (baseBubble.z - camPos.z);

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

            // Stack bubbles vertically from bottom (newest) to top (oldest)
            // stack is ordered: index 0 is oldest, index (size - 1) is newest.
            int currentBottomY = screenY - 10;

            for (int i = stack.size() - 1; i >= 0; i--) {
                ChatBubble bubble = stack.get(i);
                float alpha = bubble.getAlpha();
                if (alpha <= 0f) continue;

                boolean isTopBubble = (i == 0);
                boolean isBottomBubble = (i == stack.size() - 1);

                String title = (bubble.isNpc ? "[NPC] " : "") + bubble.speakerName;
                List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(bubble.text), 165);
                if (lines.isEmpty()) continue;

                int maxLineWidth = isTopBubble ? textRenderer.getWidth(title) : 0;
                int maxLinesToRender = Math.min(lines.size(), 6);
                for (int l = 0; l < maxLinesToRender; l++) {
                    maxLineWidth = Math.max(maxLineWidth, textRenderer.getWidth(lines.get(l)));
                }

                int bubbleWidth = maxLineWidth + 18;
                int bubbleHeight = (isTopBubble ? 13 : 0) + (maxLinesToRender * 10) + 7;

                int y2 = currentBottomY;
                int y1 = y2 - bubbleHeight;
                int x1 = screenX - bubbleWidth / 2;
                int x2 = screenX + bubbleWidth / 2;

                // Older bubbles have slightly softer alpha to focus on the active dialogue
                float renderAlpha = isBottomBubble ? alpha : alpha * 0.90f;

                int bgAlpha = (int) (renderAlpha * 220) & 0xFF;
                int borderAlpha = (int) (renderAlpha * 245) & 0xFF;
                int textAlpha = (int) (renderAlpha * 255) & 0xFF;

                int bgColor = (bgAlpha << 24) | 0x10172A;     // Deep slate background
                int borderColor = (borderAlpha << 24) | 0x38BDF8; // Sky blue border
                int titleColor = (textAlpha << 24) | 0xFBBF24;    // Warm gold title
                int textColor = (textAlpha << 24) | 0xF8FAFC;     // Soft white text

                // 1. Natural Rounded Bubble Body & Outline
                drawRoundedBubble(context, x1, y1, x2, y2, bgColor, borderColor);

                // 2. Downward Pointer Tail on the bottom bubble (pointing to NPC head)
                if (isBottomBubble) {
                    drawTail(context, screenX, y2, bgColor, borderColor);
                }

                // 3. Title (Speaker Name) on the top-most bubble
                int textY = y1 + 5;
                if (isTopBubble) {
                    context.drawText(textRenderer, title, x1 + 9, textY, titleColor, true);
                    textY += 12;
                }

                // 4. Dialogue lines
                for (int l = 0; l < maxLinesToRender; l++) {
                    context.drawText(textRenderer, lines.get(l), x1 + 9, textY, textColor, true);
                    textY += 10;
                }

                // Move bottom anchor up for the next stacked bubble with a 5px gap
                currentBottomY = y1 - 5;
            }
        }
    }

    private static void drawRoundedBubble(DrawContext context, int x1, int y1, int x2, int y2, int bgColor, int borderColor) {
        // Main center fill
        context.fill(x1 + 3, y1, x2 - 3, y2, bgColor);
        context.fill(x1, y1 + 3, x1 + 3, y2 - 3, bgColor);
        context.fill(x2 - 3, y1 + 3, x2, y2 - 3, bgColor);

        // Soft corner fills
        context.fill(x1 + 1, y1 + 2, x1 + 3, y1 + 3, bgColor);
        context.fill(x2 - 3, y1 + 2, x2 - 1, y1 + 3, bgColor);
        context.fill(x1 + 1, y2 - 3, x1 + 3, y2 - 2, bgColor);
        context.fill(x2 - 3, y2 - 3, x2 - 1, y2 - 2, bgColor);
        context.fill(x1 + 2, y1 + 1, x1 + 3, y1 + 2, bgColor);
        context.fill(x2 - 3, y1 + 1, x2 - 2, y1 + 2, bgColor);
        context.fill(x1 + 2, y2 - 2, x1 + 3, y2 - 1, bgColor);
        context.fill(x2 - 3, y2 - 2, x2 - 2, y2 - 1, bgColor);

        // Borders - horizontal lines
        context.fill(x1 + 3, y1, x2 - 3, y1 + 1, borderColor);
        context.fill(x1 + 3, y2 - 1, x2 - 3, y2, borderColor);

        // Borders - vertical lines
        context.fill(x1, y1 + 3, x1 + 1, y2 - 3, borderColor);
        context.fill(x2 - 1, y1 + 3, x2, y2 - 3, borderColor);

        // Rounded corner border pixels
        // Top-left
        context.fill(x1 + 1, y1 + 2, x1 + 2, y1 + 3, borderColor);
        context.fill(x1 + 2, y1 + 1, x1 + 3, y1 + 2, borderColor);
        // Top-right
        context.fill(x2 - 2, y1 + 2, x2 - 1, y1 + 3, borderColor);
        context.fill(x2 - 3, y1 + 1, x2 - 2, y1 + 2, borderColor);
        // Bottom-left
        context.fill(x1 + 1, y2 - 3, x1 + 2, y2 - 2, borderColor);
        context.fill(x1 + 2, y2 - 2, x1 + 3, y2 - 1, borderColor);
        // Bottom-right
        context.fill(x2 - 2, y2 - 3, x2 - 1, y2 - 2, borderColor);
        context.fill(x2 - 3, y2 - 2, x2 - 2, y2 - 1, borderColor);
    }

    private static void drawTail(DrawContext context, int screenX, int y2, int bgColor, int borderColor) {
        // Triangular pointer tail pointing towards NPC head
        context.fill(screenX - 3, y2, screenX + 4, y2 + 1, borderColor);
        context.fill(screenX - 2, y2 + 1, screenX + 3, y2 + 2, borderColor);
        context.fill(screenX - 1, y2 + 2, screenX + 2, y2 + 3, borderColor);
        context.fill(screenX, y2 + 3, screenX + 1, y2 + 4, borderColor);

        // Inner tail fill
        context.fill(screenX - 2, y2, screenX + 3, y2 + 1, bgColor);
        context.fill(screenX - 1, y2 + 1, screenX + 2, y2 + 2, bgColor);
        context.fill(screenX, y2 + 2, screenX + 1, y2 + 3, bgColor);
    }
}
