package org.vennv.utils;

import org.vennv.ByteBufferUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents an item with name, custom name, and item stack data
 */
public class Item {
    private String name;
    private String customName;
    private ItemStack itemStack;

    public Item(String name, String customName, ItemStack itemStack) {
        this.name = name;
        this.customName = customName;
        this.itemStack = itemStack;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    /**
     * Encode the item to a byte array output stream
     */
    public void encode(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putString(out, name == null ? "" : name);
        ByteBufferUtil.putString(out, customName == null ? "" : customName);

        // Write item stack
        itemStack.encode(out);
    }

    /**
     * Decode an item from a byte buffer
     */
    public static Item decode(ByteBuffer buf) {
        String name = readString(buf);
        String customName = readString(buf);

        // Read item stack
        ItemStack itemStack = ItemStack.decode(buf);

        return new Item(name, customName, itemStack);
    }

    private static String readString(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Item{" +
                "name='" + name + '\'' +
                ", customName='" + customName + '\'' +
                ", itemStack=" + itemStack +
                '}';
    }
}
