package com.stifflered.bartercontainer.util;

import com.stifflered.bartercontainer.BarterContainer;
import com.stifflered.bartercontainer.store.BarterStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarterContainerLogger {

    private static final ExecutorService SERVICE = Executors.newSingleThreadExecutor();
    private Path path;
    private static final Logger LOGGER = LogManager.getLogger();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public BarterContainerLogger() {
        this.updateLogFile();
    }


    public void logTransaction(Player player, ItemStack bought, BarterStore store) {
        String log = "%s bought one %s from %s".formatted(player.getName(), bought, store.getKey());
        LOGGER.info(log);
        this.logToFile(log);
        SERVICE.execute(() -> {
            try {
                BarterShopOwnerLogManager.addLog(store.getKey(), new BarterShopOwnerLogManager.TransactionRecord(System.currentTimeMillis(), player.getUniqueId(), player.getName(), bought.getType(), bought.getAmount()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }


    private void logToFile(String log) {
        SERVICE.execute(() -> {
            this.updateLogFile();
            try {
                try (FileWriter writer = new FileWriter(this.path.toFile(), true)) {
                    writer.write(Instant.now() + ": " + log + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    private void updateLogFile() {
        this.path = new File(BarterContainer.INSTANCE.getDataFolder(), "logs").toPath()
                .resolve(dateFormat.format(new Date()) + ".txt");

        try {
            Files.createDirectories(this.path.getParent());
            if (Files.notExists(this.path)) {
                Files.createFile(this.path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
