package ru.mediadestroy.auth.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.mediadestroy.auth.AuthManager;
import ru.mediadestroy.auth.MediaDestroyAuth;

public class JoinListener implements Listener {

    private final MediaDestroyAuth plugin;
    private final AuthManager authManager;

    public JoinListener(MediaDestroyAuth plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        var player = event.getPlayer();

        if (authManager.hasValidSession(player)) {
            // Сессия ещё активна (менее часа с последнего входа, тот же IP) — пароль не нужен
            authManager.finishAuthSuccess(player, AuthManager.Entry.SESSION);
            return;
        }

        authManager.startAuth(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        authManager.cancelAuth(event.getPlayer().getUniqueId());
    }
}
