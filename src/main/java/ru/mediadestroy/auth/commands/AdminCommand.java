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
        sender.sendMessage(ChatColor.YELLOW + "/mdauth reload");
        return true;
    }
}
