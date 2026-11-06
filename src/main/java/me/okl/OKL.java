package me.okl;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class OKL extends JavaPlugin implements Listener {

    private File logFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        logFile = new File(getDataFolder(), "log.txt");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private boolean isTrainee(String name) {
        for (String n : getConfig().getStringList("trainees")) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private synchronized void log(String line) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (PrintWriter w = new PrintWriter(new FileWriter(logFile, true))) {
            w.println("[" + ts + "] " + line);
        } catch (IOException ex) {
            getLogger().warning("Could not write log: " + ex.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // list management: console or existing ops who are NOT trainees
        if (args.length >= 1 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("list"))) {
            boolean admin = !(sender instanceof Player p) || (p.isOp() && !isTrainee(p.getName()));
            if (!admin) {
                sender.sendMessage("§cYou can't manage the list.");
                return true;
            }
            List<String> list = getConfig().getStringList("trainees");
            if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage("§eTrainees: " + String.join(", ", list));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /okl " + args[0] + " <name>");
                return true;
            }
            if (args[0].equalsIgnoreCase("add")) {
                if (list.stream().noneMatch(n -> n.equalsIgnoreCase(args[1]))) list.add(args[1]);
            } else {
                list.removeIf(n -> n.equalsIgnoreCase(args[1]));
            }
            getConfig().set("trainees", list);
            saveConfig();
            log(sender.getName() + " ran /okl " + args[0] + " " + args[1]);
            sender.sendMessage("§aDone.");
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Use /okl add|remove|list <name> from console.");
            return true;
        }
        if (!isTrainee(p.getName())) {
            // look like an unknown command so the word isn't advertised
            p.sendMessage("§cUnknown or incomplete command.");
            log("DENIED /okl from " + p.getName());
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("off")) {
            p.setOp(false);
            log(p.getName() + " removed their own operator");
            p.sendMessage("§eOperator removed.");
            return true;
        }

        p.setOp(true);
        log(p.getName() + " GOT operator via /okl");
        p.sendMessage("§aYou now have operator. Everything you do is logged.");
        return true;
    }

    // log every command run by a listed trainee
    @EventHandler
    public void onCommandUsed(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (isTrainee(p.getName())) {
            log(p.getName() + " ran " + e.getMessage());
        }
    }

    // trainees lose op when they leave, so it never stays by accident
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isTrainee(p.getName()) && p.isOp()) {
            p.setOp(false);
            log(p.getName() + " operator reset on join");
        }
    }
}
