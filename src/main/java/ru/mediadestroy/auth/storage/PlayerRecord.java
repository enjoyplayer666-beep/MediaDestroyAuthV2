package ru.mediadestroy.auth.storage;

public class PlayerRecord {

    private String hash;
    private String salt;
    private long lastLogin;

    public PlayerRecord(String hash, String salt, long lastLogin) {
        this.hash = hash;
        this.salt = salt;
        this.lastLogin = lastLogin;
    }

    public String getHash() {
        return hash;
    }

    public String getSalt() {
        return salt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }
}
