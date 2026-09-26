package com.npctranslator.chatbubble;

import com.npctranslator.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ChatBubbleManager {

    private static final Map<String, List<ChatBubble>> BUBBLES_BY_SPEAKER = new ConcurrentHashMap<>();
    private static final Map<String, ChatBubble> BUBBLES_BY_MSG_ID = new ConcurrentHashMap<>();
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9a-fk-or]");

    public static Collection<List<ChatBubble>> getActiveBubbleStacks() {
        // Clean up expired bubbles from each speaker's stack
        BUBBLES_BY_SPEAKER.entrySet().removeIf(entry -> {
            List<ChatBubble> list = entry.getValue();
            list.removeIf(b -> b.getAlpha() <= 0f);
            return list.isEmpty();
        });
        BUBBLES_BY_MSG_ID.values().removeIf(b -> b.getAlpha() <= 0f);
        return BUBBLES_BY_SPEAKER.values();
    }

    public static void onChatMessage(String msgId, String rawText, String translatedDialogue) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || !config.enableChatBubbles) return;

        String clean = stripColor(rawText).trim();
        if (clean.length() < 3) return;

        boolean isNpc = clean.contains("[NPC] ") || clean.contains("[NPC]");
        if (config.chatBubbleNpcOnly && !isNpc) return;

        String speaker = "";
        String dialogue = "";

        if (isNpc) {
            int npcIdx = clean.indexOf("[NPC]");
            int afterNpc = npcIdx + "[NPC]".length();
            int colonIdx = clean.indexOf(':', afterNpc);
            if (colonIdx != -1) {
                speaker = clean.substring(afterNpc, colonIdx).trim();
                dialogue = clean.substring(colonIdx + 1).trim();
            }
        } else {
            int colonIdx = clean.indexOf(':');
            if (colonIdx > 0 && colonIdx < 32) {
                speaker = clean.substring(0, colonIdx).trim();
                dialogue = clean.substring(colonIdx + 1).trim();
            }
        }

        if (speaker.isEmpty() || dialogue.isEmpty()) return;

        String bubbleText = (translatedDialogue != null && !translatedDialogue.isEmpty()) ? translatedDialogue : dialogue;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        double maxDist = config.chatBubbleMaxDistance;
        double maxDistSq = maxDist * maxDist;
        Entity matchedEntity = null;
        double closestDistSq = Double.MAX_VALUE;

        try {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player && isNpc) continue;
                double distSq = client.player.distanceToSqr(entity);
                if (distSq > maxDistSq) continue;

                String entName = stripColor(entity.getName().getString());
                String custName = entity.getCustomName() != null ? stripColor(entity.getCustomName().getString()) : "";

                if (matchesSpeaker(entName, speaker) || matchesSpeaker(custName, speaker)) {
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        matchedEntity = entity;
                    }
                }
            }
        } catch (Exception ignored) {}

        double bx, by, bz;
        int entityId = -1;

        if (matchedEntity != null) {
            entityId = matchedEntity.getId();
            bx = matchedEntity.getX();
            by = (matchedEntity instanceof ArmorStand)
                    ? matchedEntity.getY() + 0.35
                    : matchedEntity.getY() + matchedEntity.getEyeHeight() + 0.45;
            bz = matchedEntity.getZ();
        } else {
            if (config.chatBubbleNpcOnly) return;
            var look = client.player.getLookAngle();
            var eye = client.player.getEyePosition();
            bx = eye.x + look.x * 2.5;
            by = eye.y + look.y * 2.5;
            bz = eye.z + look.z * 2.5;
        }

        // Dynamic duration: Give enough time to read based on text length (65ms/char)
        long baseDuration = config.chatBubbleDuration * 1000L;
        long dynamicDuration = 4000L + (long) (bubbleText.length() * 65L);
        long durationMs = Math.min(22000L, Math.max(baseDuration, dynamicDuration));

        ChatBubble bubble = new ChatBubble(speaker, bubbleText, durationMs, bx, by, bz, entityId, isNpc, msgId);

        String speakerKey = speaker.toLowerCase();
        List<ChatBubble> speakerBubbles = BUBBLES_BY_SPEAKER.computeIfAbsent(speakerKey, k -> new CopyOnWriteArrayList<>());

        // Limit maximum stacked bubbles per speaker to 3 so older ones gracefully fade
        while (speakerBubbles.size() >= 3) {
            ChatBubble oldest = speakerBubbles.get(0);
            oldest.fastFade();
            speakerBubbles.remove(0);
        }

        speakerBubbles.add(bubble);

        if (msgId != null && !msgId.isEmpty()) {
            BUBBLES_BY_MSG_ID.put(msgId, bubble);
        }
    }

    public static void onTranslationComplete(String msgId, String translatedFullText) {
        if (msgId == null) return;
        ChatBubble bubble = BUBBLES_BY_MSG_ID.get(msgId);
        if (bubble != null) {
            String clean = stripColor(translatedFullText);
            int colonIdx = clean.indexOf(':');
            if (colonIdx != -1) {
                bubble.text = clean.substring(colonIdx + 1).trim();
            } else {
                bubble.text = clean.trim();
            }
            // Ensure at least 4.5 seconds to read the newly translated message
            bubble.extendDuration(4500L);
        }
    }

    private static boolean matchesSpeaker(String name, String speaker) {
        if (name == null || name.isEmpty() || speaker == null || speaker.isEmpty()) return false;
        String s1 = name.toLowerCase().trim();
        String s2 = speaker.toLowerCase().trim();
        return s1.equals(s2) || s1.contains(s2) || s2.contains(s1);
    }

    public static String stripColor(String str) {
        if (str == null) return "";
        return STRIP_COLOR_PATTERN.matcher(str).replaceAll("");
    }
}
