package com.npctranslator.chatbubble;

public class ChatBubble {
    public final String speakerName;
    public String text;
    public final long createdAt;
    public long durationMs;
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

    public void extendDuration(long minRemainingMs) {
        long elapsed = System.currentTimeMillis() - createdAt;
        long remaining = durationMs - elapsed;
        if (remaining < minRemainingMs) {
            this.durationMs = elapsed + minRemainingMs;
        }
    }

    public void fastFade() {
        long elapsed = System.currentTimeMillis() - createdAt;
        this.durationMs = Math.min(this.durationMs, elapsed + 400L);
    }

    public float getAlpha() {
        long elapsed = System.currentTimeMillis() - createdAt;
        if (elapsed > durationMs) return 0f;
        long remaining = durationMs - elapsed;
        if (remaining < 600) {
            return Math.max(0f, (float) remaining / 600f);
        }
        if (elapsed < 200) {
            return Math.min(1.0f, (float) elapsed / 200f);
        }
        return 1.0f;
    }
}
