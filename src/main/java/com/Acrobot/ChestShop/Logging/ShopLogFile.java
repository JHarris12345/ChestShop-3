package com.Acrobot.ChestShop.Logging;

import com.Acrobot.ChestShop.ChestShop;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ShopLogFile {
    private static File file;
    private static ChestShop plugin = ChestShop.getPlugin();

    public static void log (String logMessage) {
        Date now = new Date();
        SimpleDateFormat time = new SimpleDateFormat("[dd-MMM-yyyy HH:mm:ss] ");
        String fileName = getFileName(now);

        try {
            file = new File(plugin.getDataFolder(), "logs/" + fileName + ".yml");

            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file, true);
            PrintWriter pw = new PrintWriter(fw);

            pw.println(time.format(now) + ChatColor.stripColor(logMessage));
            pw.flush();
            pw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getFileName(Date now) {
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM");
        return date.format(now);
    }
}
