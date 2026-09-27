package ru.mediadestroy.auth;

import org.bukkit.plugin.java.JavaPlugin;
import ru.mediadestroy.auth.commands.AdminCommand;
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

        dataStore = new PlayerDataStore(getDataFolder(), getLogger());
        dataStore.load();

        authManager = new AuthManager(this, dataStore);

        getServer().getPluginManager().registerEvents(new JoinListener(this, authManager), this);
        getServer().getPluginManager().registerEvents(new RestrictionListener(authManager), this);

        getCommand("l").setExecutor(new LoginCommand(authManager));
        getCommand("mdauth").setExecutor(new AdminCommand(this));

        getLogger().info("MediaDestroyAuth включён.");
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            authManager.shutdown();
        }
        getLogger().info("MediaDestroyAuth выключен, данные сохранены.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
    }

    public AuthManager getAuthManager() {
        return authManager;
    }
}
