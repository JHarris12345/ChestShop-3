package com.Acrobot.ChestShop.Utils;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.Set;

/**
 * Helpers for inspecting the contents of container items (shulker boxes) and
 * for recognising the item types that have to be kept out of shop containers.
 *
 * <p>Both of the problems this class exists for come from the same place: the
 * shop's item transfer is a plain Bukkit inventory write
 * ({@code Inventory#addItem} with an {@code Inventory#setItem} fallback, see
 * {@link com.Acrobot.Breeze.Utils.InventoryUtil#add(ItemStack, Inventory)}).
 * That write does not go through the vanilla
 * {@code ShulkerBoxBlockEntity#canPlaceItem} restriction and fires no
 * inventory event, so nothing else stops a shulker box being placed inside a
 * shulker box that way.</p>
 *
 * @author Claude
 */
public final class ContainerItemUtil {

    /**
     * How many levels of nested container items are walked when counting.
     * Shop items are not supposed to nest at all (that is what the shulker
     * checks exist to keep true), so this is only a safety valve against
     * spending unbounded time on an already deeply nested item.
     */
    public static final int MAX_NESTING_DEPTH = 6;

    private static final Set<Material> SHULKER_BOXES;
    private static final Set<Material> BOOKS;

    static {
        Set<Material> shulkerBoxes = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            // Matches SHULKER_BOX and all 16 coloured variants, but not
            // SHULKER_SHELL or SHULKER_SPAWN_EGG.
            if (material.name().endsWith("SHULKER_BOX")) {
                shulkerBoxes.add(material);
            }
        }
        SHULKER_BOXES = shulkerBoxes;

        Set<Material> books = EnumSet.noneOf(Material.class);
        // The two types ExploitFixer's book-limit module counts: written books
        // and books-and-quills. Plain books and enchanted books hold no pages
        // and so carry no meaningful amount of NBT.
        addIfPresent(books, "WRITTEN_BOOK");
        addIfPresent(books, "WRITABLE_BOOK");
        BOOKS = books;
    }

    private ContainerItemUtil() {
    }

    private static void addIfPresent(Set<Material> set, String name) {
        try {
            set.add(Material.valueOf(name));
        } catch (IllegalArgumentException missingOnThisVersion) {
            // Nothing to do; the material simply does not exist here.
        }
    }

    /**
     * @param material The material to check
     * @return Whether the material is a shulker box (any colour)
     */
    public static boolean isShulkerBox(Material material) {
        return material != null && SHULKER_BOXES.contains(material);
    }

    /**
     * @param item The item to check
     * @return Whether the item is a shulker box (any colour)
     */
    public static boolean isShulkerBox(ItemStack item) {
        return item != null && isShulkerBox(item.getType());
    }

    /**
     * Works out whether an inventory belongs to a shulker box.
     *
     * <p>Null safe, and safe for the virtual
     * {@link com.Acrobot.ChestShop.Containers.AdminInventory}, whose
     * {@code getType()} and {@code getHolder()} both return null.</p>
     *
     * @param inventory The inventory to check
     * @return Whether the inventory is the inventory of a shulker box
     */
    public static boolean isShulkerBoxInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        try {
            if (inventory.getType() == InventoryType.SHULKER_BOX) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Custom Inventory implementations are allowed to be awkward here.
        }

        InventoryHolder holder;
        try {
            holder = inventory.getHolder();
        } catch (RuntimeException ignored) {
            return false;
        }

        if (holder instanceof ShulkerBox) {
            return true;
        }

        return holder instanceof BlockState && isShulkerBox(((BlockState) holder).getType());
    }

    /**
     * @param material The material to check
     * @return Whether the material is a written book or a book and quill
     */
    public static boolean isBook(Material material) {
        return material != null && BOOKS.contains(material);
    }

    /**
     * Counts the books in an item stack, including the books held inside it if
     * it is a shulker box (recursively, up to {@link #MAX_NESTING_DEPTH}).
     *
     * @param item The item to count the books of
     * @return Number of books this item would add to a container
     */
    public static int countBooks(ItemStack item) {
        return countBooks(item, 0);
    }

    private static int countBooks(ItemStack item, int depth) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            return 0;
        }

        if (isBook(item.getType())) {
            return item.getAmount();
        }

        if (!isShulkerBox(item.getType()) || depth >= MAX_NESTING_DEPTH) {
            return 0;
        }

        int booksInside = 0;
        for (ItemStack nested : getContents(item)) {
            booksInside += countBooks(nested, depth + 1);
        }

        // Every copy of the stack carries the same contents.
        return booksInside * item.getAmount();
    }

    /**
     * Counts the books held in an inventory, including the books inside any
     * shulker boxes stored in it.
     *
     * @param inventory The inventory to count the books of
     * @return Number of books in the inventory
     */
    public static int countBooks(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int books = 0;
        for (ItemStack item : inventory.getContents()) {
            books += countBooks(item);
        }

        return books;
    }

    /**
     * Reads the items stored inside a container item.
     *
     * @param item The container item (a shulker box)
     * @return Its contents, or an empty array if it holds nothing or cannot be read
     */
    public static ItemStack[] getContents(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new ItemStack[0];
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof BlockStateMeta)) {
                return new ItemStack[0];
            }

            BlockStateMeta blockStateMeta = (BlockStateMeta) meta;
            if (!blockStateMeta.hasBlockState()) {
                return new ItemStack[0];
            }

            BlockState state = blockStateMeta.getBlockState();
            if (!(state instanceof ShulkerBox)) {
                return new ItemStack[0];
            }

            return ((ShulkerBox) state).getInventory().getContents();
        } catch (RuntimeException couldNotRead) {
            return new ItemStack[0];
        }
    }
}
