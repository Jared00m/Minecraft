package com.example.jailplugin;

import java.util.UUID;

public class JailEntry {
    private final UUID playerId;
    private final long jailUntil;
    private final String reason;
    private final boolean permanentLoop;
    private final int offenseCount;

    public JailEntry(UUID playerId, long jailUntil, String reason, boolean permanentLoop, int offenseCount) {
        this.playerId = playerId;
        this.jailUntil = jailUntil;
        this.reason = reason;
        this.permanentLoop = permanentLoop;
        this.offenseCount = offenseCount;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getJailUntil() {
        return jailUntil;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPermanentLoop() {
        return permanentLoop;
    }

    public int getOffenseCount() {
        return offenseCount;
    }
}
