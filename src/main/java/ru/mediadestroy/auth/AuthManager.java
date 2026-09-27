package ru.mediadestroy.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.mediadestroy.auth.storage.PasswordUtil;
import ru.mediadestroy.auth.storage.PlayerDataStore;
import ru.mediadestroy.auth.storage.PlayerRecord;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthManager {

    private static final String CENTER_TAG = "<center>";

    private final MediaDestroyAuth plugin;
    private final PlayerDataStore dataStore;
    private final Map<UUID, AuthSession> sessions = new HashMap<>();
    private final Map<UUID, Location> frozenAt = new HashMap<>();

    public AuthManager(MediaDestroyAuth plugin, PlayerDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private String c(String path) {
        return ChatColor.translateAlternateColorCodes('&', cfg().getString(path, ""));
    }

    // ---------- Состояние ----------

    public boolean isAuthenticating(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean hasValidSession(UUID uuid) {
        PlayerRecord record = dataStore.get(uuid);
        if (record == null) {
            return false;
        }
        long minutes = cfg().getLong("timing.session-duration-minutes", 60);
        long validUntil = record.getLastLogin() + Duration.ofMinutes(minutes).toMillis();
        return System.currentTimeMillis() < validUntil;
    }

    public boolean isRegistered(UUID uuid) {
        return dataStore.isRegistered(uuid);
    }

    // ---------- Запуск процесса авторизации ----------

    public void startAuth(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            return;
        }
        frozenAt.put(uuid, player.getLocation());

        int timeout = cfg().getInt("timing.auth-timeout-seconds", 120);
        int cycleTicks = cfg().getInt("timing.cycle-interval-seconds", 5) * 20;

        BossBar bar = plugin.getServer().createBossBar(
                buildBossBarText(timeout), barColor(), barStyle());
        bar.addPlayer(player);
        bar.setProgress(1.0);

        AuthSession session = new AuthSession(bar, timeout);
        sessions.put(uuid, session);

        showScreenCommand(player);

        BukkitRunnable cycleTask = new BukkitRunnable() {
            int elapsedTicks = 0;
            boolean showingCommandScreen = true;

            @Override
            public void run() {
                if (!player.isOnline() || !sessions.containsKey(uuid)) {
                    cancel();
                    return;
                }
                elapsedTicks += 20;
                session.secondsLeft--;

                bar.setProgress(Math.max(0.0, (double) session.secondsLeft / timeout));
                bar.setTitle(buildBossBarText(session.secondsLeft));

                if (elapsedTicks % cycleTicks == 0) {
                    showingCommandScreen = !showingCommandScreen;
                    if (showingCommandScreen) {
                        showScreenCommand(player);
                    } else {
                        showScreenChat(player);
                    }
                }

                if (session.secondsLeft <= 0) {
                    cancel();
                    kickForTimeout(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        session.task = cycleTask;
    }

    public void cancelAuth(UUID uuid) {
        AuthSession session = sessions.remove(uuid);
        if (session != null) {
            if (session.task != null) {
                session.task.cancel();
            }
            session.bar.removeAll();
        }
        frozenAt.remove(uuid);
    }

    private void kickForTimeout(Player player) {
        cancelAuth(player.getUniqueId());
        player.kickPlayer(c("kick-message"));
    }

    // ---------- Обработка попытки ввода пароля ----------

    /**
     * @return true если сообщение было обработано как попытка пароля (и должно быть скрыто из чата)
     */
    public boolean handlePasswordAttempt(Player player, String rawPassword) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) {
            return false;
        }
        String password = rawPassword.trim();
        if (password.isEmpty()) {
            return true;
        }

        showScreenChecking(player);

        int delayTicks = cfg().getInt("timing.checking-title-ticks", 30);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            resolveAttempt(player, password);
        }, delayTicks);

        return true;
    }

    private void resolveAttempt(Player player, String password) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) {
            return;
        }

        if (!dataStore.isRegistered(uuid)) {
            // Регистрация первым введённым паролем
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash(password, salt);
            PlayerRecord record = new PlayerRecord(hash, salt, System.currentTimeMillis());
            dataStore.put(uuid, record);
            dataStore.save();
            player.sendMessage(c("registered-message"));
            finishAuthSuccess(player, false);
            return;
        }

        PlayerRecord record = dataStore.get(uuid);
        if (PasswordUtil.matches(password, record)) {
            record.setLastLogin(System.currentTimeMillis());
            dataStore.save();
            finishAuthSuccess(player, false);
        } else {
            player.sendMessage(c("wrong-password-message"));
            // Возвращаем зацикленные экраны
            showScreenCommand(player);
        }
    }

    /**
     * @param cachedSession true если пароль вообще не спрашивался (сессия ещё активна)
     */
    public void finishAuthSuccess(Player player, boolean cachedSession) {
        cancelAuth(player.getUniqueId());
        clearTitle(player);

        if (cachedSession) {
            showWelcomeBackTitle(player);
        }
        sendChatWelcome(player, cachedSession);
    }

    // ---------- Экраны ----------

    private void showScreenCommand(Player player) {
        sendTitle(player, cfg().getString("screen-command.title", ""),
                cfg().getString("screen-command.subtitle", ""));
    }

    private void showScreenChat(Player player) {
        sendTitle(player, cfg().getString("screen-chat.title", ""),
                cfg().getString("screen-chat.subtitle", ""));
    }

    private void showScreenChecking(Player player) {
        sendTitle(player, cfg().getString("screen-checking.title", ""),
                cfg().getString("screen-checking.subtitle", ""));
    }

    private void showWelcomeBackTitle(Player player) {
        sendTitle(player, cfg().getString("screen-welcome-back.title", ""),
                cfg().getString("screen-welcome-back.subtitle", ""));
    }

    private void sendTitle(Player player, String rawTitle, String rawSubtitle) {
        Component titleComp = parseComponent(rawTitle);
        Component subtitleComp = parseComponent(rawSubtitle);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofMillis(200));
        player.showTitle(Title.title(titleComp, subtitleComp, times));
    }

    private void clearTitle(Player player) {
        player.clearTitle();
    }

    /**
     * Парсит строку из конфига. Поддерживает MiniMessage (градиенты, теги вида
     * &lt;gradient:...&gt;) если в строке есть угловые скобки, иначе — обычный
     * legacy-формат Minecraft (&amp;-коды).
     */
    private Component parseComponent(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        if (raw.contains("<") && raw.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(raw);
            } catch (Exception ignored) {
                // упадёт в legacy ниже
            }
        }
        String legacy = ChatColor.translateAlternateColorCodes('&', raw);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(legacy);
    }

    /**
     * Рендерит одну строку приветственного сообщения: снимает служебный тег
     * "<center>" (если есть) и центрирует итоговый текст пробелами по
     * пиксельной ширине, затем парсит оставшийся MiniMessage/legacy текст
     * (включая градиенты).
     */
    private Component renderChatLine(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        boolean center = false;
        String content = raw;
        if (content.startsWith(CENTER_TAG)) {
            center = true;
            content = content.substring(CENTER_TAG.length());
        }

        Component parsed = parseComponent(content);

        if (!center) {
            return parsed;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        String pad = ChatUtils.centerPad(plain);
        if (pad.isEmpty()) {
            return parsed;
        }
        return Component.text(pad).append(parsed);
    }

    /**
     * Отправляет приветственное сообщение в чат.
     * Если пароль реально запрашивался (обычный логин/регистрация) — используется
     * "chat-welcome-login" (с предупреждением про уязвимость аккаунта).
     * Если игрок вошёл автоматически по активной сессии — "chat-welcome-session"
     * (без предупреждения, т.к. пароль не вводился).
     * Если новых ключей нет в конфиге — используется старый "chat-welcome" для
     * обратной совместимости.
     */
    private void sendChatWelcome(Player player, boolean cachedSession) {
        String key = cachedSession ? "chat-welcome-session" : "chat-welcome-login";
        List<String> lines = cfg().getStringList(key);
        if (lines.isEmpty()) {
            lines = cfg().getStringList("chat-welcome");
        }
        for (String line : lines) {
            player.sendMessage(renderChatLine(line));
        }
    }

    private String buildBossBarText(int secondsLeft) {
        String template = cfg().getString("bossbar.text", "{time}");
        String replaced = template.replace("{time}", String.valueOf(Math.max(secondsLeft, 0)));
        return ChatColor.translateAlternateColorCodes('&', replaced);
    }

    private BarColor barColor() {
        try {
            return BarColor.valueOf(cfg().getString("bossbar.color", "YELLOW").toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle barStyle() {
        try {
            return BarStyle.valueOf(cfg().getString("bossbar.style", "SOLID").toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    public Location getFreezeLocation(UUID uuid) {
        return frozenAt.get(uuid);
    }

    public void shutdown() {
        for (AuthSession session : sessions.values()) {
            if (session.task != null) {
                session.task.cancel();
            }
            session.bar.removeAll();
        }
        sessions.clear();
        frozenAt.clear();
        dataStore.save();
    }

    private static class AuthSession {
        final BossBar bar;
        int secondsLeft;
        BukkitTask task;

        AuthSession(BossBar bar, int secondsLeft) {
            this.bar = bar;
            this.secondsLeft = secondsLeft;
        }
    }
}
