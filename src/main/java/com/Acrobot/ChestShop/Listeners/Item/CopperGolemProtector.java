package com.Acrobot.ChestShop.Listeners.Item;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Stops a copper golem carrying items in or out of a chest shop, whoever owns either.
 *
 * A copper golem moves items by itself, with no player involved, so none of the usual shop checks
 * ever see it: left alone it will quietly empty a shop's stock for free, or stuff junk into a buy
 * shop. Paper asks whether an item transporting entity may use a block, which is what this answers.
 * The question carries no direction, so one check covers the golem both taking stock out and putting
 * items in.
 *
 * <b>Ownership is not consulted.</b> A shop is a shop, so no golem may touch one, not even the
 * shop owner's own golem. That is the same stance ChestShop already takes with hoppers in
 * {@link ItemMoveListener}, which may never pull out of a shop no matter who placed them: a shop's
 * stock is the thing being sold, and automating it away from the sign empties the shop without a
 * single transaction being recorded. It also means this works identically on a realm with no
 * InsanityCore to attribute golems, and on admin shops, whose sign name matches no player account.
 * Only containers with a shop sign are refused; a golem may still use ordinary chests and barrels.
 *
 * <b>The event is never named at compile time, on purpose.</b> It arrived in 1.21.11, and the
 * Skyblock realms run 1.21.8, where the class does not exist (nor do copper golems). Registering a
 * listener whose method signature mentions a missing class throws NoClassDefFoundError and would
 * take ChestShop down with it, and a version guard in front of the call does not help, because the
 * JVM resolves the class at the instruction that touches it. So the event is looked up by name and
 * dispatched through an executor, and {@link #register(Plugin)} does nothing at all where the class
 * is absent. That is also why ChestShop can keep building against the oldest API the fleet runs.
 */
public final class CopperGolemProtector {

    /** Paper's "may this entity use this block?" event. Present from 1.21.11 on. */
    private static final String EVENT_CLASS = "io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent";

    private static Method isAllowed;
    private static Method setAllowed;
    private static Method getBlock;

    private static boolean standDown = false;

    private CopperGolemProtector() {
    }

    /**
     * Listens for golems validating a block, where the server is new enough to ask.
     *
     * @return whether shops are now protected from copper golems
     */
    @SuppressWarnings("unchecked")
    public static boolean register(Plugin plugin) {
        try {
            Class<?> eventClass = Class.forName(EVENT_CLASS);
            isAllowed = eventClass.getMethod("isAllowed");
            setAllowed = eventClass.getMethod("setAllowed", boolean.class);
            getBlock = eventClass.getMethod("getBlock");

            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) eventClass, new Listener() {
            }, EventPriority.HIGH, (listener, event) -> handle(event), plugin, false);
            return true;
        } catch (ClassNotFoundException e) {
            return false;   // older than 1.21.11, so there are no copper golems to keep out
        } catch (Throwable t) {
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Could not watch copper golems, so shops are not protected from them:", t);
            return false;
        }
    }

    /** Cheapest checks first: most of these events are a golem eyeing a block that is not a shop. */
    private static void handle(Event event) {
        if (standDown) {
            return;
        }

        try {
            if (!((Boolean) isAllowed.invoke(event))) {
                return;   // already refused, by GriefPrevention's claim check or anything else
            }

            Block block = (Block) getBlock.invoke(event);
            if (!uBlock.couldBeShopContainer(block)) {
                return;
            }

            if (uBlock.getConnectedSign(block) != null) {
                setAllowed.invoke(event, false);
            }
        } catch (Throwable t) {
            // Complain once and leave golems alone, rather than spamming the console every tick
            standDown = true;
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Could not check a copper golem against a shop, so golem protection is off until the next restart:", t);
        }
    }
}
