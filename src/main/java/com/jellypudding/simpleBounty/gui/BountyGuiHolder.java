package com.jellypudding.simpleBounty.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BountyGuiHolder implements InventoryHolder {

    public enum Type {
        LIST,
        ON_ME,
        MINE,
        DETAIL,
        PLACE
    }

    private final Type type;
    private final Map<String, Object> data = new HashMap<>();
    private Inventory inventory;

    public BountyGuiHolder(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean getBoolean(String key) {
        Object v = data.get(key);
        return v instanceof Boolean b && b;
    }

    public int getInt(String key, int def) {
        Object v = data.get(key);
        return v instanceof Integer i ? i : def;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
