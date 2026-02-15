package me.mtynnn.valerinutils.core;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private boolean tiktokClaimed;
    private int kills;
    private int deaths;
    private int dailyKills; // This is actually daily rewards count for KillRewards
    private long lastDailyReset; // Timestamp of the last day tracked
    private boolean menuDisabled;
    private boolean royalPayDisabled;
    private boolean deathMessagesDisabled;
    private boolean starterKitReceived;
    private String nickname;

    private boolean dirty = false; // If true, needs saving

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isMenuDisabled() {
        return menuDisabled;
    }

    public void setMenuDisabled(boolean menuDisabled) {
        this.menuDisabled = menuDisabled;
        this.dirty = true;
    }

    public boolean isRoyalPayDisabled() {
        return royalPayDisabled;
    }

    public void setRoyalPayDisabled(boolean royalPayDisabled) {
        this.royalPayDisabled = royalPayDisabled;
        this.dirty = true;
    }

    public boolean isDeathMessagesDisabled() {
        return deathMessagesDisabled;
    }

    public void setDeathMessagesDisabled(boolean deathMessagesDisabled) {
        this.deathMessagesDisabled = deathMessagesDisabled;
        this.dirty = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isTikTokClaimed() {
        return tiktokClaimed;
    }

    public void setTikTokClaimed(boolean tiktokClaimed) {
        this.tiktokClaimed = tiktokClaimed;
        this.dirty = true;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
        this.dirty = true;
    }

    public void addKill() {
        this.kills++;
        this.dirty = true;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
        this.dirty = true;
    }

    public void addDeath() {
        this.deaths++;
        this.dirty = true;
    }

    public int getDailyRewardsCount() {
        return dailyKills; // Reuse column for simplicity
    }

    public void setDailyRewardsCount(int count) {
        this.dailyKills = count;
        this.dirty = true;
    }

    public long getLastDailyReset() {
        return lastDailyReset;
    }

    public void setLastDailyReset(long lastDailyReset) {
        this.lastDailyReset = lastDailyReset;
        this.dirty = true;
    }

    public boolean isStarterKitReceived() {
        return starterKitReceived;
    }

    public void setStarterKitReceived(boolean starterKitReceived) {
        this.starterKitReceived = starterKitReceived;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.dirty = true;
    }
}
