package ru.mediadestroy.auth.storage;

public class PlayerRecord {

    private String hash;
    private String salt;
    private long lastLogin;
    /** IP последнего входа по паролю - сессия без пароля только с него. */
    private String lastIp;

    public PlayerRecord(String hash, String salt, long lastLogin) {
        this(hash, salt, lastLogin, null);
    }

    public PlayerRecord(String hash, String salt, long lastLogin, String lastIp) {
        this.hash = hash;
        this.salt = salt;
        this.lastLogin = lastLogin;
        this.lastIp = lastIp;
    }

    public String getHash() {
        return hash;
    }

    public String getSalt() {
        return salt;
    }

    /** Новый пароль (/changepassword): хеш и соль меняются вместе. */
    public void setPassword(String hash, String salt) {
        this.hash = hash;
        this.salt = salt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getLastIp() {
        return lastIp;
    }

    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }
}
