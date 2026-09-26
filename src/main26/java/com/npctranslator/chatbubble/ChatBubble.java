package com.npctranslator.chatbubble;

public class ChatBubble {
    public final String speakerName;
    public String text;
    public final long createdAt;
    public final long durationMs;
    public double x, y, z;
    public int entityId = -1;
    public boolean isNpc = true;
    public String msgId = "";

    public ChatBubble(String speakerName, String text, long durationMs, double x, double y, double z, int entityId, boolean isNpc, String msgId) {
        this.speakerName = speakerName;
        this.text = text;
        this.createdAt = System.currentTimeMillis();
        this.durationMs = durationMs;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = entityId;
        this.isNpc = isNpc;
        this.msgId = msgId;
    }

    public float getAlpha() {
        long elapsed = System.currentTimeMillis() - createdAt;
        if (elapsed > durationMs) return 0f;
        long remaining = durationMs - elapsed;
        if (remaining < 800) {
            return Math.max(0f, (float) remaining / 800f);
        }
        if (elapsed < 200) {
            return Math.min(1.0f, (float) elapsed / 200f);
        }
        return 1.0f;
    }
}
