package com.Acrobot.ChestShop;

import com.Acrobot.Breeze.Configuration.Configuration;
import com.Acrobot.ChestShop.Commands.*;
import com.Acrobot.ChestShop.Configuration.Messages;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Database.Migrations;
import com.Acrobot.ChestShop.Listeners.Block.BlockPlace;
import com.Acrobot.ChestShop.Listeners.Block.Break.ChestBreak;
import com.Acrobot.ChestShop.Listeners.Block.Break.SignBreak;
import com.Acrobot.ChestShop.Listeners.Block.SignCreate;
import com.Acrobot.ChestShop.Listeners.Economy.ServerAccountCorrector;
import com.Acrobot.ChestShop.Listeners.Economy.TaxModule;
import com.Acrobot.ChestShop.Listeners.GarbageTextListener;
import com.Acrobot.ChestShop.Listeners.Item.ItemMoveListener;
import com.Acrobot.ChestShop.Listeners.Item.ItemStringListener;
import com.Acrobot.ChestShop.Listeners.ItemInfoListener;
import com.Acrobot.ChestShop.Listeners.Modules.ItemAliasModule;
import com.Acrobot.ChestShop.Listeners.Modules.MetricsModule;
import com.Acrobot.ChestShop.Listeners.Modules.StockCounterModule;
import com.Acrobot.ChestShop.Listeners.ShopInfoListener;
import com.Acrobot.ChestShop.Listeners.SignParseListener;
import com.Acrobot.ChestShop.Listeners.Modules.DiscountModule;
import com.Acrobot.ChestShop.Listeners.Modules.PriceRestrictionModule;
import com.Acrobot.ChestShop.Listeners.Player.*;
import com.Acrobot.ChestShop.Listeners.PreShopCreation.CreationFeeGetter;
import com.Acrobot.ChestShop.Listeners.PostShopCreation.MessageSender;
import com.Acrobot.ChestShop.Listeners.PostShopCreation.ShopCreationLogger;
import com.Acrobot.ChestShop.Listeners.PostShopCreation.SignSticker;
import com.Acrobot.ChestShop.Listeners.PostTransaction.*;
import com.Acrobot.ChestShop.Listeners.PreShopCreation.*;
import com.Acrobot.ChestShop.Listeners.PreTransaction.*;
import com.Acrobot.ChestShop.Listeners.PreTransaction.ErrorMessageSender;
import com.Acrobot.ChestShop.Listeners.PreTransaction.PermissionChecker;
import com.Acrobot.ChestShop.Listeners.ShopRemoval.ShopRefundListener;
import com.Acrobot.ChestShop.Listeners.ShopRemoval.ShopRemovalLogger;
import com.Acrobot.ChestShop.Metadata.ItemDatabase;
import com.Acrobot.ChestShop.Signs.RestrictedSign;
import com.Acrobot.ChestShop.UUIDs.NameManager;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Main file of the plugin
 *
 * @author Acrobot
 */
public class ChestShop extends JavaPlugin {
    private static ChestShop plugin;
    private static Server server;
    private static PluginDescriptionFile description;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private static BukkitAudiences audiences;

    private static File dataFolder;
    private static ItemDatabase itemDatabase;

    private List<PluginCommand> commands = new ArrayList<>();

    public ChestShop() {
        dataFolder = getDataFolder();
        description = getDescription();
        server = getServer();
        plugin = this;
    }

    @Override
    public void onLoad() {
        Dependencies.initializePlugins();
    }

    @Override
    public void onEnable() {
        audiences = BukkitAudiences.create(this);
        turnOffDatabaseLogging();
        if (!handleMigrations()) {
            return;
        }

        registerCommand("iteminfo", new ItemInfo(), Permission.ITEMINFO);
        registerCommand("shopinfo", new ShopInfo(), Permission.SHOPINFO);
        registerCommand("csVersion", new Version(), Permission.ADMIN);
        registerCommand("csMetrics", new com.Acrobot.ChestShop.Commands.Metrics(), Permission.ADMIN);
        registerCommand("csGive", new Give(), Permission.ADMIN);
        registerCommand("cstoggle", new Toggle(), Permission.NOTIFY_TOGGLE);
        registerCommand("csaccess", new AccessToggle(), Permission.ACCESS_TOGGLE);
        registerCommand("csstats", new Stats(), Permission.STATS);
        registerCommand("csstatsother", new StatsOther(), Permission.STATS_OTHER);
        registerCommand("csglobalstats", new GlobalStats(), Permission.GLOBAL_STATS);

        loadConfig();

        itemDatabase = new ItemDatabase();

        if (!Dependencies.loadPlugins()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerEvents();

        registerPluginMessagingChannels();
    }

    private void registerCommand(String name, CommandExecutor executor, Permission permission) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setPermission(permission.toString());
            commands.add(command);
        }
    }

    public void loadConfig() {
        Configuration.pairFileAndClass(loadFile("config.yml"), Properties.class, getBukkitLogger());

        Messages.load();

        NameManager.load();

        commands.forEach(c -> c.setPermissionMessage(Messages.ACCESS_DENIED.getTextWithPrefix(null)));
    }

    private void turnOffDatabaseLogging() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("");

        loggerConfig.addFilter(new AbstractFilter() {
            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, String msg, Object... params) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Object msg, Throwable t) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Message msg, Throwable t) {
                return filter(logger.getName(), level);
            }

            @Override
            public Result filter(LogEvent event) {
                return filter(event.getLoggerName(), event.getLevel());
            }

            private Result filter(String classname, Level level) {
                if (level.intLevel() <= Level.ERROR.intLevel() && !classname.contains("SqliteDatabaseType")) {
                    return Result.NEUTRAL;
                }

                if (classname.contains("SqliteDatabaseType") || classname.contains("TableUtils")) {
                    return Result.DENY;
                } else {
                    return Result.NEUTRAL;
                }
            }
        });
    }

    private boolean handleMigrations() {
        File versionFile = loadFile("version");
        YamlConfiguration previousVersion = YamlConfiguration.loadConfiguration(versionFile);

        if (previousVersion.get("version") == null) {
            previousVersion.set("version", Migrations.CURRENT_DATABASE_VERSION);

            try {
                previousVersion.save(versionFile);
            } catch (IOException e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Unable to save new database version " + Migrations.CURRENT_DATABASE_VERSION, e);
            }
        }

        int lastVersion = previousVersion.getInt("version");
        int newVersion = Migrations.migrate(lastVersion);

        if (newVersion == -1) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error while migrating! ChestShop can not run with a broken/outdated database...");
            plugin.getServer().getPluginManager().disablePlugin(this);
            return false;
        } else if (lastVersion != newVersion) {
            previousVersion.set("version", newVersion);

            try {
                previousVersion.save(versionFile);
            } catch (IOException e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Unable to save new database version " + newVersion, e);
            }
        }
        return true;
    }

    public static File loadFile(String string) {
        File file = new File(dataFolder, string);

        return loadFile(file);
    }

    private static File loadFile(File file) {
        if (!file.exists()) {
            try {
                if (file.getParent() != null) {
                    file.getParentFile().mkdirs();
                }

                file.createNewFile();
            } catch (IOException e) {
                getBukkitLogger().log(java.util.logging.Level.SEVERE, "Unable to load file " + file.getName(), e);
            }
        }

        return file;
    }

    public void onDisable() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    //////////////////    REGISTER EVENTS, SCHEDULER & STATS    ///////////////////////////
    private void registerEvents() {
        registerEvent(new com.Acrobot.ChestShop.Plugins.ChestShop()); //Chest protection

        registerEvent(new Dependencies());

        registerEvent(new NameManager());

        registerPreShopCreationEvents();
        registerPreTransactionEvents();
        registerPostShopCreationEvents();
        registerPostTransactionEvents();
        registerShopRemovalEvents();

        registerModules();

        registerEvent(new SignBreak());
        registerEvent(new SignCreate());
        registerEvent(new ChestBreak());

        registerEvent(new BlockPlace());
        registerEvent(new PlayerConnect());
        registerEvent(new PlayerInteract());
        registerEvent(new PlayerInventory());
        registerEvent(new PlayerTeleport());

        registerEvent(new SignParseListener());
        registerEvent(new ItemStringListener());
        registerEvent(new ItemInfoListener());
        registerEvent(new ShopInfoListener());
        registerEvent(new GarbageTextListener());

        registerEvent(new RestrictedSign());

        if (!Properties.TURN_OFF_HOPPER_PROTECTION) {
            registerEvent(new ItemMoveListener());
        }
    }

    private void registerShopRemovalEvents() {
        registerEvent(new ShopRefundListener());
        registerEvent(new ShopRemovalLogger());
    }

    private void registerPreShopCreationEvents() {
        if (Properties.BLOCK_SHOPS_WITH_SELL_PRICE_HIGHER_THAN_BUY_PRICE) {
            registerEvent(new PriceRatioChecker());
        }

        registerEvent(new ChestChecker());
        registerEvent(new ItemChecker());
        registerEvent(new MoneyChecker());
        registerEvent(new NameChecker());
        registerEvent(new com.Acrobot.ChestShop.Listeners.PreShopCreation.PermissionChecker());
        registerEvent(new com.Acrobot.ChestShop.Listeners.PreShopCreation.ErrorMessageSender());
        registerEvent(new PriceChecker());
        registerEvent(new QuantityChecker());
        registerEvent(new TerrainChecker());
    }

    private void registerPostShopCreationEvents() {
        registerEvent(new CreationFeeGetter());
        registerEvent(new MessageSender());
        registerEvent(new SignSticker());
        registerEvent(new ShopCreationLogger());
    }

    private void registerPreTransactionEvents() {
        if (Properties.ALLOW_PARTIAL_TRANSACTIONS) {
            registerEvent(new PartialTransactionModule());
        } else {
            registerEvent(new AmountAndPriceChecker());
        }

        registerEvent(new InvalidNameIgnorer());
        registerEvent(new CreativeModeIgnorer());
        registerEvent(new ErrorMessageSender());
        registerEvent(new PermissionChecker());
        registerEvent(new PriceValidator());
        registerEvent(new ShopValidator());
        registerEvent(new SpamClickProtector());
        registerEvent(new StockFittingChecker());
    }

    private void registerPostTransactionEvents() {
        registerEvent(new EconomicModule());
        registerEvent(new EmptyShopDeleter());
        registerEvent(new ItemManager());
        registerEvent(new TransactionLogger());
        registerEvent(new TransactionMessageSender());
    }

    private void registerModules() {
        registerEvent(new ItemAliasModule());
        registerEvent(new DiscountModule());
        registerEvent(new MetricsModule());
        registerEvent(new PriceRestrictionModule());
        registerEvent(new StockCounterModule());

        registerEconomicalModules();
    }

    private void registerEconomicalModules() {
        registerEvent(new ServerAccountCorrector());
        registerEvent(new TaxModule());
    }

    private void registerPluginMessagingChannels() {
        if (Properties.BUNGEECORD_MESSAGES) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }
    }

    public void registerEvent(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private int[] getChartArray(boolean value) {
        return new int[]{!value ? 1 : 0, value ? 0 : 1};
    }

    private static final int PROJECT_BUKKITDEV_ID = 31263;

    ///////////////////////////////////////////////////////////////////////////////

    public static ItemDatabase getItemDatabase() {
        return itemDatabase;
    }

    public static File getFolder() {
        return dataFolder;
    }

    public static Logger getShopLogger() {
        return plugin.getLogger();
    }

    public static Logger getBukkitLogger() {
        return plugin.getLogger();
    }

    public static void logDebug(String message) {
        if (Properties.DEBUG) {
            getBukkitLogger().info("[DEBUG] " + message);
        }
    }

    public static Server getBukkitServer() {
        return server;
    }

    public static String getVersion() {
        return description.getVersion();
    }

    public static String getPluginName() {
        return description.getName();
    }

    public static List<String> getDependencies() {
        return description.getSoftDepend();
    }

    public static ChestShop getPlugin() {
        return plugin;
    }

    public static BukkitAudiences getAudiences() {
        return audiences;
    }

    public static void registerListener(Listener listener) {
        plugin.registerEvent(listener);
    }

    public static <E extends Event> E callEvent(E event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    public static void sendBungeeMessage(String playerName, Messages.Message message, Map<String, String> replacementMap, String... replacements) {
        sendBungeeMessage(playerName, message.getComponent(null, true, replacementMap, replacements));
    }

    public static void sendBungeeMessage(String playerName, String message) {
        sendBungeeMessage(playerName, "Message", message);
    }

    public static void sendBungeeMessage(String playerName, BaseComponent[] message) {
        sendBungeeMessage(playerName, "MessageRaw", ComponentSerializer.toString(message));
    }

    public static void sendBungeeMessage(String playerName, Component message) {
        sendBungeeMessage(playerName, "MessageRaw", GsonComponentSerializer.gson().serialize(message));
    }

    private static void sendBungeeMessage(String playerName, String channel, String message) {
        if (Properties.BUNGEECORD_MESSAGES && !Bukkit.getOnlinePlayers().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(channel);
            out.writeUTF(playerName);
            out.writeUTF(message);

            Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }

    public static void runInAsyncThread(Runnable runnable) {
        executorService.submit(runnable);
    }
}
