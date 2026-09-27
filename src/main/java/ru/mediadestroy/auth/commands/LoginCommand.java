package ru.mediadestroy.auth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.mediadestroy.auth.AuthManager;

public class LoginCommand implements CommandExecutor {

    private final AuthManager authManager;

    public LoginCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (!authManager.isAuthenticating(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "Вы уже авторизованы.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Использование: /l <пароль>");
            return true;
        }
        String password = String.join(" ", args);
        authManager.handlePasswordAttempt(player, password);
        return true;
    }
}
