package com.jellypudding.simpleBounty.utils;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ItemSerialiser {

    private ItemSerialiser() {}

    // Serialise a single ItemStack to bytes. Returns null for null/air.
    public static byte[] serialise(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.serializeAsBytes();
    }

    // Deserialise a single ItemStack from bytes. Returns null on failure.
    public static ItemStack deserialise(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            return ItemStack.deserializeBytes(data);
        } catch (Exception e) {
            return null;
        }
    }

    // Serialises a list of ItemStacks (null entries allowed) into a single byte array. 
    // Useful when storing an entire bounty's contents as one BLOB.
    public static byte[] serialiseList(List<ItemStack> items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(items.size());
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    dos.writeInt(0);
                    continue;
                }
                byte[] bytes = item.serializeAsBytes();
                dos.writeInt(bytes.length);
                dos.write(bytes);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialise item list", e);
        }
    }

    public static List<ItemStack> deserialiseList(byte[] data) {
        List<ItemStack> result = new ArrayList<>();
        if (data == null || data.length == 0) return result;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                int len = dis.readInt();
                if (len == 0) {
                    result.add(null);
                    continue;
                }
                byte[] buf = new byte[len];
                dis.readFully(buf);
                result.add(ItemStack.deserializeBytes(buf));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialise item list", e);
        }
        return result;
    }
}
