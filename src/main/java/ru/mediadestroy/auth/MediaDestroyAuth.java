package ru.mediadestroy.auth;

import org.bukkit.plugin.java.JavaPlugin;
import ru.mediadestroy.auth.commands.AdminCommand;
import ru.mediadestroy.auth.commands.ChangePasswordCommand;
import ru.mediadestroy.auth.commands.LoginCommand;
import ru.mediadestroy.auth.listeners.JoinListener;
import ru.mediadestroy.auth.listeners.RestrictionListener;
import ru.mediadestroy.auth.storage.PlayerDataStore;

public class MediaDestroyAuth extends JavaPlugin {

    private PlayerDataStore dataStore;
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        dataStore = new PlayerDataStore(getDataFolder(), getLogger());
        dataStore.load();

        authManager = new AuthManager(this, dataStore);

        getServer().getPluginManager().registerEvents(new JoinListener(this, authManager), this);
        getServer().getPluginManager().registerEvents(new RestrictionListener(authManager), this);

        getCommand("l").setExecutor(new LoginCommand(authManager));
        getCommand("mdauth").setExecutor(new AdminCommand(this));
        ChangePasswordCommand changePassword = new ChangePasswordCommand(this, authManager);
        getCommand("changepassword").setExecutor(changePassword);
        getCommand("changepassword").setTabCompleter(changePassword);

        getLogger().info("MediaDestroyAuth включён.");
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            authManager.shutdown();
        }
        getLogger().info("MediaDestroyAuth выключен, данные сохранены.");
    }

    /** Обновляет старые конфиги на сервере: config-version 2 - пароль при регистрации вводится один раз. */
    private void migrateConfig() {
        if (getConfig().getInt("config-version", 1) < 2) {
            getConfig().set("register.confirm-password", false);
            getConfig().set("config-version", 2);
            saveConfig();
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
    }

    public AuthManager getAuthManager() {
        return authManager;
    }
}
