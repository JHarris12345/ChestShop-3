package com.Acrobot.ChestShop.Listeners.Item;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stops a copper golem carrying items in or out of a shop that is not its owner's.
 *
 * A copper golem moves items by itself, with no player involved, so none of the usual shop checks
 * ever see it: left alone it will quietly empty another player's stock for free, or stuff junk into
 * their buy shop. Paper asks whether an item transporting entity may use a block, which is what this
 * answers. The question carries no direction, so one check covers the golem both taking stock out
 * and putting items in.
 *
 * Ownership is InsanityCore's, not ours. Its CopperGolemOwners module records who built or
 * reanimated each golem in the entity's persistent data, and GriefPrevention reads that very same
 * tag to protect claims, so a golem has one owner across the whole server rather than one per
 * plugin. Nothing is written here, only read.
 *
 * A golem we cannot attribute is refused rather than allowed, which is deliberately stricter than
 * GriefPrevention, where an untagged golem is simply ignored. A shop holds someone else's goods, so
 * an unattributable golem at a shop is exactly the case worth refusing: no tag, no access. That also
 * covers a server with no InsanityCore, where no golem has an owner at all, and admin shops, whose
 * sign name matches no player account. It is the same stance ChestShop already takes with hoppers in
 * {@link ItemMoveListener}, which may never pull out of a shop no matter who owns it.
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

    /** InsanityCore's CopperGolemOwners tag: the UUID of whoever built or reanimated the golem. */
    private static final String OWNER_PLUGIN = "InsanityCore";
    private static final String OWNER_KEY = "copperGolemOwner";

    private static Method isAllowed;
    private static Method setAllowed;
    private static Method getBlock;

    private static NamespacedKey ownerKey;
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
            if (!(event instanceof EntityEvent entityEvent)) {
                return;
            }
            if (!((Boolean) isAllowed.invoke(event))) {
                return;   // already refused, by GriefPrevention's claim check or anything else
            }

            Block block = (Block) getBlock.invoke(event);
            if (!uBlock.couldBeShopContainer(block)) {
                return;
            }

            Sign sign = uBlock.getConnectedSign(block);
            if (sign == null) {
                return;   // a container, but nobody's shop
            }

            if (!isOwnShop(sign, entityEvent.getEntity())) {
                setAllowed.invoke(event, false);
            }
        } catch (Throwable t) {
            // Complain once and leave golems alone, rather than spamming the console every tick
            standDown = true;
            ChestShop.getBukkitLogger().log(Level.WARNING,
                    "Could not check a copper golem against a shop, so golem protection is off until the next restart:", t);
        }
    }

    /** Whether this shop sign belongs to the player who made this golem. No, unless it clearly does. */
    private static boolean isOwnShop(Sign sign, Entity golem) {
        UUID golemOwner = getGolemOwner(golem);
        if (golemOwner == null) {
            return false;
        }

        Account account = NameManager.getAccount(golemOwner);
        if (account == null) {
            return false;   // the golem's owner has never had a shop account, so no shop is theirs
        }

        String shopOwner = ChestShopSign.getOwner(sign);
        if (shopOwner == null || shopOwner.isEmpty()) {
            return false;
        }

        // A sign only has room for 15 characters of a name, so the short name has to count too
        return shopOwner.equalsIgnoreCase(account.getName())
                || shopOwner.equalsIgnoreCase(account.getShortName());
    }

    /** Who built or reanimated this golem, as InsanityCore recorded it, or null if nobody did. */
    private static UUID getGolemOwner(Entity golem) {
        NamespacedKey key = ownerKey();
        if (key == null) {
            return null;
        }

        String owner = golem.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }

        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * InsanityCore's key, built the way InsanityCore and GriefPrevention both build it so that all
     * three agree on the namespace. Looked up on first use rather than at startup, so the load order
     * cannot matter.
     */
    private static NamespacedKey ownerKey() {
        if (ownerKey == null) {
            Plugin iCore = Bukkit.getPluginManager().getPlugin(OWNER_PLUGIN);
            if (iCore == null) {
                return null;
            }
            ownerKey = new NamespacedKey(iCore, OWNER_KEY);
        }
        return ownerKey;
    }
}
