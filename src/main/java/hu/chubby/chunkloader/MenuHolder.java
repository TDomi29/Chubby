package hu.chubby.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class MenuHolder implements InventoryHolder {
    public enum View { DASHBOARD, LIST, INFO, MAP, MANAGE, GROUP, GROUP_LIST, DIAGNOSTICS, HELP, CONFIRM_DELETE }

    private final Inventory inventory;
    private final View view;
    private final int page;
    private final String filter;
    private final ChunkKey subject;
    private final Map<Integer, ChunkKey> chunkSlots = new HashMap<>();
    private final Map<Integer, String> groupSlots = new HashMap<>();

    public MenuHolder(View view, int page, String filter, ChunkKey subject, String title, int size) {
        this.view = view;
        this.page = page;
        this.filter = filter;
        this.subject = subject;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override public @NotNull Inventory getInventory() { return inventory; }
    public View view() { return view; }
    public int page() { return page; }
    public String filter() { return filter; }
    public ChunkKey subject() { return subject; }
    public Map<Integer, ChunkKey> chunkSlots() { return chunkSlots; }
    public Map<Integer, String> groupSlots() { return groupSlots; }
}
