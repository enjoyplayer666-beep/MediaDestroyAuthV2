package ru.mediadestroy.auth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.mediadestroy.auth.AuthManager;
import ru.mediadestroy.auth.MediaDestroyAuth;

import java.util.List;

/** /changepassword <старый пароль> <новый пароль> */
public class ChangePasswordCommand implements CommandExecutor, TabCompleter {

    private final MediaDestroyAuth plugin;
    private final AuthManager authManager;

    public ChangePasswordCommand(MediaDestroyAuth plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (authManager.isAuthenticating(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Сначала войдите: /l <пароль>");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("change-password.usage-message",
                            "&cИспользование: /changepassword <старый пароль> <новый пароль>")));
            return true;
        }
        authManager.changePassword(player, args[0], args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of(); // пароли не подсказываем
    }
}
