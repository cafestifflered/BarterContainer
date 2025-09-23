package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.*;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.time.Instant;
import java.time.format.*;

import java.util.*;
import java.util.Base64;

/**
 * Manages owner-facing purchase logs for each {@link com.stifflered.bartercontainer.store.BarterStore}.

 * Storage format:
 * - Per-store text file at: <plugin-data>/purchase_transactions/<storeUUID>.txt
 * - Each line is either:
 *     1) A serialized TransactionRecord:
 *          v1 → "TIMESTAMP:PLAYER_UUID:PLAYER_NAME:ITEM_TYPE:AMOUNT"
 *          v2 → "TIMESTAMP:PLAYER_UUID:PLAYER_NAME:ITEM_TYPE:AMOUNT:PRICE_BASE64"  // price item
 *          v3 → "TIMESTAMP:PLAYER_UUID:PLAYER_NAME:ITEM_TYPE:AMOUNT:PRICE_BASE64:PURCHASED_BASE64"  // NEW: full purchased stack
 *        where PRICE_BASE64 and PURCHASED_BASE64 are single-line Base64 of Bukkit-serialized {@link ItemStack}s.
 *     2) The literal line "ACK" which acts as a bookmark (all lines after this are "new")

 * Key capabilities:
 * - addLog(...)          : Append a new purchase record line to a store's file.
 * - ackRecords(...)      : Deduplicate "new" vs "seen" lines by rewriting file and appending "ACK".
 * - getPreAckedEntries() : Return only the entries that appeared after the last ACK.
 * - listAllEntries()     : Return all entries (except ACK lines), oldest to newest.
 * - notifyNewPurchases() : On player join, summarizes number of post-ACK entries across their shops.

 * MiniMessage formatting:
 * - Title/hover strings are configurable via {@link BarterContainerConfiguration#getTransactionLogConfiguration()}.
 * - {@link TransactionRecord#formatted()} turns a record into a display-ready Component.
 */
public class BarterShopOwnerLogManager {

    // Directory to store the transaction logs
    private static final String DIRECTORY_NAME = "purchase_transactions";

    // Formatting for the book/list UI; comes from config.yml "transactions.*"
    // Fetch lazily to avoid static-init ordering issues on server boot.
    private static BarterContainerConfiguration.TransactionLogConfiguration txCfg() {
        return BarterContainer.INSTANCE.getConfiguration().getTransactionLogConfiguration();
    }

    // Use TimeUtil for safe, reloadable date/time formatting (UTC, with config fallback).
    // NOTE: DateTimeFormatter is thread-safe (unlike SimpleDateFormat), so this remains safe if formatted()
    // is ever called off the main thread in the future.
    private static DateTimeFormatter formatter() {
        return TimeUtil.absoluteFormatter();
    }

    /**
     * When a player joins, asynchronously check all shops they own and
     * notify them if there are any post-ACK (unacknowledged) purchase entries.
     */
    public static void notifyNewPurchases(Player player) {
        // OPTIONAL: Warm the owner’s own Bedrock skin (if applicable) so their head is ready in any GUIs.
        try {
            var hs = BarterContainer.INSTANCE.getHeadService();
            if (hs != null) {
                hs.ensureCachedBedrock(player.getUniqueId());
            }
        } catch (Throwable ignored) {
            // Best effort only; never break notifications
        }

        BarterManager.INSTANCE.getOwnedShops(player).thenAccept((shops) -> {
            int purchases = 0;
            for (BarterStore store : shops) {
                try {
                    purchases += getPreAckedEntries(store.getKey()).size();
                } catch (Exception e) {
                    BarterContainer.INSTANCE.getLogger().warning(
                            "Failed to read pre-ACKed entries for store " + store.getKey() + ": " + e.getMessage()
                    );
                }
            }

            if (purchases > 0) {
                // Routed through messages.yml (MiniMessage) for styling/localization
                player.sendMessage(Messages.mm(
                        "notifications.owner_new_purchases",
                        "count", Integer.toString(purchases)
                ));
            }
        });
    }

    /**
     * Append a serialized {@link TransactionRecord} to the store's log file.
     * Creates the directory and file if missing.
     *
     * @param key    Store identifier (UUID wrapper)
     * @param record Structured transaction info to persist
     */
    public static void addLog(BarterStoreKey key,
                              TransactionRecord record) throws IOException {
        Path file = getFile(key);

        // Ensure directory exists
        Files.createDirectories(file.getParent());

        // Append line + newline to file
        Files.write(
                file,
                Collections.singletonList(TransactionRecord.serialize(record)),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    // ---------------------------------------------------------------------------------------------
    //      Convenience overload for callers that have the player + the *actual* purchased stack.
    //      This writes a v3 line with both PRICE_BASE64 and PURCHASED_BASE64 populated where possible.
    //      The price stack is optional (pass null to skip). The purchased stack is what the player
    //      actually received (with full meta/NBT), and will be encoded to keep one-transaction-per-line.
    // ---------------------------------------------------------------------------------------------
    public static void addLog(BarterStoreKey key,
                              long timestamp,
                              java.util.UUID purchaserUuid,
                              String purchaserName,
                              org.bukkit.inventory.ItemStack purchasedStack,
                              org.bukkit.inventory.ItemStack priceStack) throws java.io.IOException {
        org.bukkit.Material itemType = (purchasedStack == null ? org.bukkit.Material.AIR : purchasedStack.getType());
        int amount = (purchasedStack == null ? 0 : Math.max(0, purchasedStack.getAmount()));

        String purchasedB64 = "";
        try {
            // Use the private helper in this class to serialize a full ItemStack to Base64 (single line)
            purchasedB64 = encodeItemStack(purchasedStack);
        } catch (java.io.IOException e) {
            BarterContainer.INSTANCE.getLogger().warning("Failed to serialize purchased ItemStack: " + e.getMessage());
        }

        TransactionRecord rec = new TransactionRecord(
                timestamp,
                purchaserUuid,
                purchaserName,
                itemType,
                amount,
                priceStack,   // may be null; serializer tolerates it
                purchasedB64  // full purchased stack (meta/NBT preserved)
        );

        addLog(key, rec);
    }

    /**
     * Marks all current records as "seen" by:
     * 1) Copying existing lines except any prior "ACK" lines into a temp file,
     * 2) Appending a fresh "ACK" at the end,
     * 3) Replacing the original file atomically (without ever leaving a gap).

     * After this, "new" entries are those that appear after the final ACK line.

     * Optimization:
     * - Removed the explicit Files.delete(file).
     * - Now we let Files.move(..., REPLACE_EXISTING) handle the replacement in one step.
     * - Safer: the old file remains in place until the new file is successfully moved.
     */
    public static void ackRecords(BarterStoreKey key) throws IOException {
        Path file = getFile(key);

        if (Files.notExists(file)) {
            // No file -> no entries
            return;
        }

        Path tempFile = file.getParent().resolve(file.getFileName() + ".tmp");

        try (
                BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                BufferedWriter bw = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                // Skip lines that are exactly "ACK"
                if (!line.trim().equals("ACK")) {
                    bw.write(line);
                    bw.newLine();
                }
            }

            // Append a fresh "ACK" at the end
            bw.write("ACK");
            bw.newLine();
        }

        // Safer: replace the file in a single move step
        try {
            Files.move(
                    tempFile,
                    file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            // Fallback for filesystems that do not support atomic moves.
            Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Returns all records that were written *after* the last "ACK" marker line.
     * Implementation scans the file from the end backwards until "ACK" is found,
     * collecting lines in reverse order, then reverses them on return.

     * If the file has no "ACK", all records are considered "new".
     */
    public static List<TransactionRecord> getPreAckedEntries(BarterStoreKey key) throws IOException {
        Path file = getFile(key);

        if (Files.notExists(file)) {
            // No file -> no entries
            return List.of();
        }

        // We'll store the lines that come after "ACK" (in reverse order at first).
        List<String> postAckReversed = new ArrayList<>();
        boolean foundAck = false;

        // RandomAccessFile lets us seek from the end to find the last ACK efficiently
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long pointer = raf.length() - 1;  // Start from the last byte

            // A buffer to accumulate characters in reverse
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0) {
                raf.seek(pointer);
                int readByte = raf.readByte();
                char c = (char) readByte;

                if (c == '\n') {
                    // We have a line in reverse order in sb
                    String line = sb.reverse().toString().trim();
                    sb.setLength(0);

                    if (line.equals("ACK")) {
                        foundAck = true;
                        break;
                    } else {
                        postAckReversed.add(line);
                    }
                } else {
                    // Accumulate characters in reverse
                    sb.append(c);
                }

                pointer--;
            }

            // Edge case: if there's no trailing newline at the end,
            // we might have a leftover line in sb:
            if (!foundAck && sb.length() > 0) { // FIX: StringBuilder has no isEmpty()
                String line = sb.reverse().toString().trim();
                if (!line.equals("ACK")) {
                    postAckReversed.add(line);
                }
            }
        }

        // We collected in reverse (newest-first). Restore to original order (oldest → newest).
        Collections.reverse(postAckReversed); // FIX: ensure chronological order

        // Filter blanks and convert to TransactionRecord objects (now in correct order)
        return postAckReversed.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("ACK"))
                .map(TransactionRecord::deserialize)
                .toList();
    }

    /**
     * Returns all persisted records (oldest → newest), skipping any "ACK" lines.
     * If parsing any given line fails, that line is silently ignored.
     */
    public static List<TransactionRecord> listAllEntries(BarterStoreKey key) throws IOException {
        Path file = getFile(key);
        if (Files.notExists(file)) {
            return List.of();
        }

        List<TransactionRecord> records = new ArrayList<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();                      // handle stray whitespace lines
            // Skip ACK lines (they are just markers, not transactions)
            if (line.isEmpty() || line.equals("ACK")) {    // also skip empty lines
                continue;
            }

            try {
                records.add(TransactionRecord.deserialize(line));
            } catch (Exception e) {
                // Ignore malformed lines to avoid breaking the whole file read
            }
        }
        return records;
    }

    /**
     * Deletes the per-store log file if it exists.
     * (Used on cleanup or store deletion.)
     */
    public static void deleteFile(BarterStoreKey key) {
        Path file = getFile(key);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper method to get the File object for a given key.
    private static Path getFile(BarterStoreKey key) {
        return BarterContainer.INSTANCE.getDataPath().resolve(DIRECTORY_NAME).resolve(key.key().toString() + ".txt");
    }

    // -------------------------------------------------------------------------------------------------------------
    // Helpers to (de)serialize an ItemStack to a single Base64 token so we can keep "one transaction per line".
    // -------------------------------------------------------------------------------------------------------------

    /**
     * Serialize a Bukkit ItemStack into a single-line Base64 string.

     * Strategy (1.21+ safe):
     *  - Prefer stable Map-based config serialization: ItemStack#serialize() -> Map<String,Object>
     *  - Write that Map using standard Java ObjectOutputStream (no BukkitObject*).
     *  - Base64 the bytes to keep "one transaction per line".

     * Back-compat:
     *  - Older logs written with BukkitObjectOutputStream will still be read by
     *    {@link #decodeItemStack(String)} via a fallback path.
     */
    private static String encodeItemStack(ItemStack stack) throws IOException {
        if (stack == null) return "";

        // Map<String, Object> containing only primitives, strings, lists, and nested maps
        Map<String, Object> map = stack.serialize();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(map);
            oos.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }

    /**
     * Deserialize a Base64 string produced by {@link #encodeItemStack(ItemStack)}.
     * Returns null if the string is empty.

     * Back-compat:
     *  - First try Map-based deserialization (new, preferred).
     *  - If that fails, attempt to read legacy bytes that were written with
     *    BukkitObjectOutputStream (pre-1.21 code). This keeps old log lines valid.
     */
    @SuppressWarnings("unchecked")
    private static ItemStack decodeItemStack(String base64) throws IOException, ClassNotFoundException {
        if (base64 == null || base64.isEmpty()) return null;

        byte[] data = Base64.getDecoder().decode(base64);

        // --- Preferred: Map-based path (1.21+ safe) ---
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis)) {

            Object obj = ois.readObject();
            if (obj instanceof Map<?, ?> raw) {
                // Cast is safe as Bukkit's serialize() always returns Map<String, Object>
                Map<String, Object> map = (Map<String, Object>) raw;
                return ItemStack.deserialize(map);
            }
            // If it wasn't a Map, let it fall through to legacy fallback below.
        } catch (Exception ignored) {
            // Fall through to legacy path
        }

        // --- Legacy fallback: decode entries written with BukkitObjectOutputStream ---
        // This block is isolated and deprecation-suppressed so the rest of the class is warning-free.
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            @SuppressWarnings("deprecation")
            org.bukkit.util.io.BukkitObjectInputStream legacy =
                    new org.bukkit.util.io.BukkitObjectInputStream(bis);
            try (legacy) {
                Object obj = legacy.readObject();
                return (ItemStack) obj;
            }
        }
    }

    /**
     * Simple immutable record for purchases.
     * Serialized as colon-separated fields on a single line.
     * v2 adds {@code ItemStack price} as the sixth field.
     * v3 adds {@code PURCHASED_BASE64} as the seventh field (full purchased stack with meta/NBT).
     * Legacy lines (v1/v2) still parse.
     */
    public record TransactionRecord(long timestamp,
                                    UUID purchaserUuid,
                                    String purchaserName,
                                    Material itemType,
                                    int amount,
                                    ItemStack price,
                                    String purchasedB64) { // <-- NEW: v3 field

        /** Legacy convenience constructor (v1/v2): preserves source compatibility. */
        public TransactionRecord(long timestamp,
                                 UUID purchaserUuid,
                                 String purchaserName,
                                 Material itemType,
                                 int amount,
                                 ItemStack price) {
            this(timestamp, purchaserUuid, purchaserName, itemType, amount, price, null);
        }

        /**
         * Serialize a TransactionRecord to a single line.
         * Format (v3): TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT:PRICE_BASE64:PURCHASED_BASE64

         * BACKWARDS COMPATIBLE: We write v3 now. v1/v2 lines are still accepted on read (see deserialize).
         */
        public static String serialize(TransactionRecord record) {
            String priceB64 = "";
            try {
                priceB64 = encodeItemStack(record.price);
            } catch (IOException ioe) {
                BarterContainer.INSTANCE.getLogger().warning("Failed to serialize price ItemStack: " + ioe.getMessage());
            }

            String purchasedB64 = record.purchasedB64 == null ? "" : record.purchasedB64;

            return String.format(
                    "%d:%s:%s:%s:%d:%s:%s",
                    record.timestamp,
                    record.purchaserUuid.toString(),
                    record.purchaserName,
                    record.itemType.name(),
                    record.amount,
                    priceB64,
                    purchasedB64
            );
        }

        /**
         * Deserialize a line into a TransactionRecord.

         * Accepted formats:
         *  - v1: TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT
         *  - v2: TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT:PRICE_BASE64
         *  - v3: TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT:PRICE_BASE64:PURCHASED_BASE64
         */
        public static TransactionRecord deserialize(String line) throws IllegalArgumentException {
            String[] parts = line.split(":");
            if (parts.length != 5 && parts.length != 6 && parts.length != 7) {
                throw new IllegalArgumentException("Invalid line format: " + line);
            }

            long timestamp = Long.parseLong(parts[0]);
            UUID purchaserUuid = UUID.fromString(parts[1]);
            String purchaserName = parts[2];
            String itemType = parts[3];
            int amount = Integer.parseInt(parts[4]);

            Material mat = Material.matchMaterial(itemType);
            if (mat == null) mat = Material.AIR;

            ItemStack price = null;
            if (parts.length >= 6) {
                try {
                    price = decodeItemStack(parts[5]);
                } catch (Exception e) {
                    BarterContainer.INSTANCE.getLogger().warning("Failed to deserialize price ItemStack: " + e.getMessage());
                }
            }

            String purchasedB64 = (parts.length == 7) ? parts[6] : null;

            return new TransactionRecord(timestamp, purchaserUuid, purchaserName, mat, amount, price, purchasedB64);
        }

        /**
         * Pretty-print the record for in-game display (book/hover).
         * Uses the configurable title/hover templates from config.yml (MiniMessage).
         */
        public Component formatted() {
            Component name = MiniMessage.miniMessage().deserialize(
                    txCfg().title(),
                    TagResolver.builder()
                            .resolvers(
                                    Placeholder.component("amount", Component.text(this.amount)),
                                    Placeholder.component("itemtype", Component.text(this.itemType.name()))
                            ).build()
            );

            Component hover = MiniMessage.miniMessage().deserialize(
                    txCfg().hover(),
                    TagResolver.builder()
                            .resolvers(
                                    Placeholder.component("purchaser", Component.text(this.purchaserName)),
                                    Placeholder.component("time", Component.text(formatter().format(Instant.ofEpochMilli(this.timestamp))))
                            ).build()
            );

            return name.hoverEvent(hover);
        }

        // -------------------- Surface the full purchased stack to GUIs --------------------

        /**
         * Full purchased ItemStack with meta/NBT (enchants, shulker contents, bundles, etc.).
         * GUIs discover this via reflection (resolvePurchasedStack tries method names like "purchased").
         */
        public ItemStack purchased() {
            if (purchasedB64 == null || purchasedB64.isBlank()) return null;
            try {
                return decodeItemStack(purchasedB64);
            } catch (Exception e) {
                BarterContainer.INSTANCE.getLogger().warning("Failed to decode purchased ItemStack: " + e.getMessage());
                return null;
            }
        }
    }
}
