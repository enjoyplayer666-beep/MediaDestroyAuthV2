package ru.mediadestroy.auth.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerDataStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, PlayerRecord> records = new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerDataStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "players.yml");
        this.logger = logger;
    }

    public void load() {
        records.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.isConfigurationSection("players")) {
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String hash = yaml.getString("players." + key + ".hash");
                    String salt = yaml.getString("players." + key + ".salt");
                    long lastLogin = yaml.getLong("players." + key + ".lastLogin", 0L);
                    String lastIp = yaml.getString("players." + key + ".lastIp");
                    records.put(uuid, new PlayerRecord(hash, salt, lastLogin, lastIp));
                } catch (IllegalArgumentException ex) {
                    logger.warning("Некорректный UUID в players.yml: " + key);
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerRecord> entry : records.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".hash", entry.getValue().getHash());
            yaml.set(path + ".salt", entry.getValue().getSalt());
            yaml.set(path + ".lastLogin", entry.getValue().getLastLogin());
            yaml.set(path + ".lastIp", entry.getValue().getLastIp());
        }
        // сначала во временный файл, потом подмена - пароли не пропадут, даже если сервер упадёт во время записи
        File tmp = new File(file.getParentFile(), "players.yml.tmp");
        try {
            file.getParentFile().mkdirs();
            yaml.save(tmp);
            if (file.exists()) {
                java.nio.file.Files.copy(file.toPath(), new File(file.getParentFile(), "players.yml.bak").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Не удалось сохранить players.yml", e);
        }
    }

    public PlayerRecord get(UUID uuid) {
        return records.get(uuid);
    }

    public boolean isRegistered(UUID uuid) {
        return records.containsKey(uuid);
    }

    public boolean remove(UUID uuid) {
        return records.remove(uuid) != null;
    }

    public void put(UUID uuid, PlayerRecord record) {
        records.put(uuid, record);
    }
}
