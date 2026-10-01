package ru.mediadestroy.auth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.mediadestroy.auth.MediaDestroyAuth;

public class AdminCommand implements CommandExecutor {

    private final MediaDestroyAuth plugin;

    public AdminCommand(MediaDestroyAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "MediaDestroyAuth: конфиг перезагружен.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (target == null) target = org.bukkit.Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок " + args[1] + " не найден.");
                return true;
            }
            if (plugin.getAuthManager().resetPassword(target.getUniqueId())) {
                sender.sendMessage(ChatColor.GREEN + "Пароль игрока " + args[1] + " сброшен - при входе он зарегистрируется заново.");
            } else {
                sender.sendMessage(ChatColor.GRAY + "Игрок " + args[1] + " не зарегистрирован.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/mdauth reload | /mdauth reset <ник>");
        return true;
    }
}
