package com.Acrobot.ChestShop.Listeners.Block;

import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Lazily migrates old-format shop signs to the new layout as chunks are loaded.
 *
 * @author Acrobot
 */
public class ShopConverter implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Sign) {
                ChestShopSign.convertIfLegacy((Sign) state);
            }
        }
    }
}
