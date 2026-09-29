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

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AuthManager {

    private static final String CENTER_TAG = "<center>";

    /** Как игрок попал на сервер - от этого зависит приветствие в чате. */
    public enum Entry {
        /** первая регистрация - полное приветствие с "Ваш аккаунт уязвим" */
        REGISTER,
        /** вход по паролю */
        LOGIN,
        /** вход без пароля по сохранённой сессии */
        SESSION
    }

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

    /**
     * Сессия ещё активна: с последнего входа по паролю прошло меньше session-duration-minutes
     * и игрок заходит с того же IP (иначе на сервере без лицензии любой мог бы зайти под чужим
     * ником без пароля).
     */
    public boolean hasValidSession(Player player) {
        PlayerRecord record = dataStore.get(player.getUniqueId());
        if (record == null) {
            return false;
        }
        long minutes = cfg().getLong("timing.session-duration-minutes", 60);
        long validUntil = record.getLastLogin() + Duration.ofMinutes(minutes).toMillis();
        if (System.currentTimeMillis() >= validUntil) {
            return false;
        }
        return record.getLastIp() != null && record.getLastIp().equals(ip(player));
    }

    public boolean isRegistered(UUID uuid) {
        return dataStore.isRegistered(uuid);
    }

    private static String ip(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    // ---------- Запуск процесса авторизации ----------

    public void startAuth(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            return;
        }
        frozenAt.put(uuid, player.getLocation());

        boolean registering = !dataStore.isRegistered(uuid);
        int timeout = cfg().getInt("timing.auth-timeout-seconds", 120);
        int cycleTicks = cfg().getInt("timing.cycle-interval-seconds", 5) * 20;

        AuthSession session = new AuthSession(null, timeout, registering);
        BossBar bar = plugin.getServer().createBossBar(
                buildBossBarText(session, timeout), barColor(), barStyle());
        bar.addPlayer(player);
        bar.setProgress(1.0);
        session.bar = bar;
        sessions.put(uuid, session);

        showScreenCommand(player, session);

        BukkitTask cycleTask = new BukkitRunnable() {
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
                bar.setTitle(buildBossBarText(session, session.secondsLeft));

                // пока ждём повтор пароля - экран "Повторите пароль" не переключаем
                if (session.pendingPassword == null && elapsedTicks % cycleTicks == 0) {
                    showingCommandScreen = !showingCommandScreen;
                    if (showingCommandScreen) {
                        showScreenCommand(player, session);
                    } else {
                        showScreenChat(player, session);
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
            if (session.bar != null) {
                session.bar.removeAll();
            }
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
        AuthSession session = sessions.get(uuid);
        if (session == null) {
            return false;
        }
        String password = rawPassword.trim();
        if (password.isEmpty()) {
            return true;
        }

        // регистрация: проверка длины сразу, без экрана "Проверка пароля"
        if (session.registering && session.pendingPassword == null) {
            int min = cfg().getInt("register.min-length", 4);
            int max = cfg().getInt("register.max-length", 32);
            if (password.length() < min || password.length() > max) {
                player.sendMessage(c("register.bad-length-message")
                        .replace("{min}", String.valueOf(min)).replace("{max}", String.valueOf(max)));
                return true;
            }
            if (cfg().getBoolean("register.confirm-password", false)) {
                session.pendingPassword = password;
                sendTitle(player, cfg().getString("screen-register-confirm.title", ""),
                        cfg().getString("screen-register-confirm.subtitle", ""));
                session.bar.setTitle(buildBossBarText(session, session.secondsLeft));
                return true;
            }
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
        AuthSession session = sessions.get(uuid);
        if (session == null) {
            return;
        }

        if (!dataStore.isRegistered(uuid)) {
            // повтор пароля при регистрации
            if (session.pendingPassword != null && !session.pendingPassword.equals(password)) {
                session.pendingPassword = null;
                player.sendMessage(c("register.mismatch-message"));
                session.bar.setTitle(buildBossBarText(session, session.secondsLeft));
                showScreenCommand(player, session);
                return;
            }
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash(password, salt);
            PlayerRecord record = new PlayerRecord(hash, salt, System.currentTimeMillis(), ip(player));
            dataStore.put(uuid, record);
            dataStore.save();
            player.sendMessage(c("registered-message"));
            finishAuthSuccess(player, Entry.REGISTER);
            return;
        }

        PlayerRecord record = dataStore.get(uuid);
        if (PasswordUtil.matches(password, record)) {
            record.setLastLogin(System.currentTimeMillis());
            record.setLastIp(ip(player));
            dataStore.save();
            finishAuthSuccess(player, Entry.LOGIN);
        } else {
            player.sendMessage(c("wrong-password-message"));
            showScreenCommand(player, session);
        }
    }

    public void finishAuthSuccess(Player player, Entry entry) {
        cancelAuth(player.getUniqueId());
        clearTitle(player);

        if (entry == Entry.SESSION) {
            showWelcomeBackTitle(player);
        }
        sendChatWelcome(player, entry);
    }

    // ---------- Смена пароля ----------

    /** /changepassword <старый> <новый>. Сообщения берутся из change-password.* в конфиге. */
    public void changePassword(Player player, String oldPassword, String newPassword) {
        PlayerRecord record = dataStore.get(player.getUniqueId());
        if (record == null) {
            player.sendMessage(c("change-password.not-registered-message"));
            return;
        }
        if (!PasswordUtil.matches(oldPassword, record)) {
            player.sendMessage(c("change-password.wrong-old-message"));
            return;
        }
        int min = cfg().getInt("register.min-length", 4);
        int max = cfg().getInt("register.max-length", 32);
        if (newPassword.length() < min || newPassword.length() > max) {
            player.sendMessage(c("register.bad-length-message")
                    .replace("{min}", String.valueOf(min)).replace("{max}", String.valueOf(max)));
            return;
        }
        if (Objects.equals(oldPassword, newPassword)) {
            player.sendMessage(c("change-password.same-message"));
            return;
        }
        String salt = PasswordUtil.generateSalt();
        record.setPassword(PasswordUtil.hash(newPassword, salt), salt);
        record.setLastIp(ip(player));
        dataStore.save();
        player.sendMessage(c("change-password.success-message"));
    }

    // ---------- Экраны ----------

    private void showScreenCommand(Player player, AuthSession session) {
        String key = session.registering ? "screen-register-command" : "screen-command";
        sendTitle(player, cfg().getString(key + ".title", ""), cfg().getString(key + ".subtitle", ""));
    }

    private void showScreenChat(Player player, AuthSession session) {
        String key = session.registering ? "screen-register-chat" : "screen-chat";
        sendTitle(player, cfg().getString(key + ".title", ""), cfg().getString(key + ".subtitle", ""));
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
     * Приветствие в чат: после регистрации - welcome.register (с "Ваш аккаунт уязвим"),
     * при обычном входе и по сессии - только welcome.login.
     */
    private void sendChatWelcome(Player player, Entry entry) {
        String key = entry == Entry.REGISTER ? "welcome.register" : "welcome.login";
        List<String> lines = cfg().getStringList(key);
        for (String line : lines) {
            player.sendMessage(renderChatLine(line));
        }
    }

    private String buildBossBarText(AuthSession session, int secondsLeft) {
        String path = session.pendingPassword != null ? "bossbar.text-confirm"
                : session.registering ? "bossbar.text-register" : "bossbar.text";
        String template = cfg().getString(path, cfg().getString("bossbar.text", "{time}"));
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
            if (session.bar != null) {
                session.bar.removeAll();
            }
        }
        sessions.clear();
        frozenAt.clear();
        dataStore.save();
    }

    private static class AuthSession {
        BossBar bar;
        int secondsLeft;
        BukkitTask task;
        /** игрок ещё не зарегистрирован - экраны регистрации */
        final boolean registering;
        /** первый ввод пароля при регистрации, ждём повтор */
        String pendingPassword;

        AuthSession(BossBar bar, int secondsLeft, boolean registering) {
            this.bar = bar;
            this.secondsLeft = secondsLeft;
            this.registering = registering;
        }
    }
}
