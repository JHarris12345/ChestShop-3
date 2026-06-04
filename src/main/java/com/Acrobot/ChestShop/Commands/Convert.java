package com.Acrobot.ChestShop.Commands;

import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Bulk-migrates old-format shop signs in all currently loaded chunks to the
 * new layout.
 *
 * @author Acrobot
 */
public class Convert implements CommandExecutor {

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int converted = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Sign && ChestShopSign.convertIfLegacy((Sign) state)) {
                        converted++;
                    }
                }
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Converted " + converted + " old shop sign(s) in loaded chunks to the new format.");
        return true;
    }
}
