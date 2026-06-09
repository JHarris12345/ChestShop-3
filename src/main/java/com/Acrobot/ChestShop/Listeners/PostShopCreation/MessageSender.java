package com.Acrobot.ChestShop.Listeners.PostShopCreation;

import com.Acrobot.ChestShop.Configuration.Messages;
import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Utils.Utils;
import de.themoep.minedown.adventure.MineDown;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * @author Acrobot
 */
public class MessageSender implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public static void onShopCreation(ShopCreatedEvent event) {
        boolean buy = event.getSignLines()[0].contains("buy");
        boolean money = event.getSignLines()[3].contains("$");

        //Messages.SHOP_CREATED.sendWithPrefix(event.getPlayer());
        if (buy) {
            event.getPlayer().sendMessage(Utils.colour("&a[Shop] &fShop successfully created! Players will &abuy &fthis item from you and give you &a" + ((money) ? "money" : "gc")));
        } else {
            event.getPlayer().sendMessage(Utils.colour("&a[Shop] &fShop successfully created! Players will &asell &fthis item to you and take your &a" + ((money) ? "money" : "gc")));
        }
    }
}
