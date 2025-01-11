package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.*;
import com.stifflered.bartercontainer.barter.BarterManager;
import com.stifflered.bartercontainer.store.BarterStore;
import com.stifflered.bartercontainer.store.BarterStoreKey;
import com.stifflered.bartercontainer.util.source.impl.BarterStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.*;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class BarterShopOwnerLogManager {

    // Directory to store the transaction logs
    private static final String DIRECTORY_NAME = "purchase_transactions";
    private static final BarterContainerConfiguration.TransactionLogConfiguration TRANSACTION_LOG_CONFIGURATION = BarterContainer.INSTANCE.getConfiguration().getTransactionLogConfiguration();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(TRANSACTION_LOG_CONFIGURATION.timeFormat());


    public static void notifyNewPurchases(Player player) {
        BarterManager.INSTANCE.getOwnedShops(player).thenAccept((shops) -> {
            int purchases = 0;
            for (BarterStore store : shops) {
                try {
                    purchases += getPreAckedEntries(store.getKey()).size();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (purchases > 0) {
                player.sendMessage(Component.text("Check your shops! %s new purchases have been made since you've been gone!".formatted(purchases), NamedTextColor.GREEN));
            }
        });
    }

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

        Files.delete(file);
        Files.move(tempFile, file);
    }

    public static List<TransactionRecord> getPreAckedEntries(BarterStoreKey key) throws IOException {
        Path file = getFile(key);

        if (Files.notExists(file)) {
            // No file -> no entries
            return List.of();
        }

        // We'll store the lines that come after "ACK" (in reverse order at first).
        List<String> postAckReversed = new ArrayList<>();
        boolean foundAck = false;

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
            if (!foundAck && !sb.isEmpty()) {
                String line = sb.reverse().toString().trim();
                if (!line.equals("ACK")) {
                    postAckReversed.add(line);
                }
            }
        }

        return postAckReversed.stream().filter(s -> !s.isBlank()).map(TransactionRecord::deserialize).toList();
    }


    public static List<TransactionRecord> listAllEntries(BarterStoreKey key) throws IOException {
        Path file = getFile(key);
        if (Files.notExists(file)) {
            return List.of();
        }

        List<TransactionRecord> records = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            // Skip ACK lines
            if (line.equals("ACK")) {
                continue;
            }

            try {
                records.add(TransactionRecord.deserialize(line));
            } catch (Exception e) {
            }
        }
        return records;
    }


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

    public record TransactionRecord(long timestamp, UUID purchaserUuid, String purchaserName, Material itemType,
                                    int amount) {
        /**
         * Serialize a TransactionRecord to a single line.
         * Format: TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT
         */
        public static String serialize(TransactionRecord record) {
            return String.format(
                    "%d:%s:%s:%s:%d",
                    record.timestamp,
                    record.purchaserUuid.toString(),
                    record.purchaserName,
                    record.itemType.name(),
                    record.amount
            );
        }

        /**
         * Deserialize a line into a TransactionRecord.
         * Format: TIMESTAMP:PURCHASER_UUID:PURCHASER_NAME:ITEM_TYPE:AMOUNT
         */
        public static TransactionRecord deserialize(String line) throws IllegalArgumentException {
            String[] parts = line.split(":");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Invalid line format: " + line);
            }

            long timestamp = Long.parseLong(parts[0]);
            UUID purchaserUuid = UUID.fromString(parts[1]);
            String purchaserName = parts[2];
            String itemType = parts[3];
            int amount = Integer.parseInt(parts[4]);

            return new TransactionRecord(timestamp, purchaserUuid, purchaserName, Material.matchMaterial(itemType), amount);
        }

        public Component formatted() {
            Component name = MiniMessage.miniMessage().deserialize(TRANSACTION_LOG_CONFIGURATION.title(), TagResolver.builder()
                    .resolvers(
                            Placeholder.component("amount", Component.text(this.amount)),
                            Placeholder.component("itemtype", Component.text(this.itemType.name()))
                    ).build());

            Component hover = MiniMessage.miniMessage().deserialize(TRANSACTION_LOG_CONFIGURATION.hover(), TagResolver.builder()
                    .resolvers(
                            Placeholder.component("purchaser", Component.text(this.purchaserName)),
                            Placeholder.component("timestamp", Component.text(FORMATTER.format(Instant.ofEpochMilli(this.timestamp))))
                    ).build());


            return name.hoverEvent(hover);
        }
    }
}
