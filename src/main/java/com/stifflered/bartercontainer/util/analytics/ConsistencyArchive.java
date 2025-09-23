package com.stifflered.bartercontainer.util.analytics;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;

/**
 * Maintains a monthly CSV at: data/consistency/YYYY-MM.csv
 * Columns: Player,Week1,Week2,Week3,Week4,Grand,Month

 * - "Grand" is recomputed = average of non-empty week scores (or your custom blend).
 * - Rows are kept sorted by Player (case-insensitive).
 * - Idempotent upsert: update the (Player, WeekIndex) cell.
 */
public final class ConsistencyArchive {

    private final Path baseDir;

    public ConsistencyArchive(Plugin plugin) {
        this.baseDir = plugin.getDataFolder().toPath().resolve("consistency");
    }

    public Path monthFile(YearMonth ym) {
        return baseDir.resolve(ym.toString() + ".csv"); // e.g., 2025-09.csv
    }

    public void recordWeeklyScore(String playerName, YearMonth ym, int weekIndex, double weeklyScore) throws IOException {
        if (weekIndex < 1 || weekIndex > 4) throw new IllegalArgumentException("weekIndex must be 1..4");
        Files.createDirectories(baseDir);

        Path file = monthFile(ym);
        Map<String, Row> rows = Files.exists(file) ? read(file) : new LinkedHashMap<>();

        Row r = rows.computeIfAbsent(playerName, Row::new);
        r.setWeek(weekIndex, weeklyScore);
        r.recomputeGrand();

        // Maintain Month field for all rows.
        for (Row row : rows.values()) row.month = ym.toString();

        // Sort by Player (case-insensitive) before writing
        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparing(a -> a.player.toLowerCase(Locale.ROOT)));

        write(file, sorted);
    }

    /* -------------------- CSV internals -------------------- */

    private static final String HEADER = "Player,Week1,Week2,Week3,Week4,Grand,Month";

    private Map<String, Row> read(Path file) throws IOException {
        Map<String, Row> map = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 7) continue;
                Row r = new Row(p[0]);
                r.w1 = parse(p[1]);
                r.w2 = parse(p[2]);
                r.w3 = parse(p[3]);
                r.w4 = parse(p[4]);
                r.grand = parse(p[5]);
                r.month = p[6];
                map.put(r.player, r);
            }
        }
        return map;
    }

    private void write(Path file, List<Row> rows) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            bw.write(HEADER);
            bw.newLine();
            for (Row r : rows) {
                bw.write(String.join(",",
                        esc(r.player),
                        fmt(r.w1),
                        fmt(r.w2),
                        fmt(r.w3),
                        fmt(r.w4),
                        fmt(r.grand),
                        esc(r.month)
                ));
                bw.newLine();
            }
        }
    }

    private static String esc(String s) { return s == null ? "" : s; }
    private static String fmt(Double v) { return v == null ? "" : String.format(Locale.US, "%.4f", v); }
    private static Double parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    /* -------------------- Row model -------------------- */

    private static final class Row {
        final String player;
        Double w1, w2, w3, w4;
        Double grand;
        String month;

        Row(String player) { this.player = player; }

        void setWeek(int idx, double v) {
            switch (idx) {
                case 1 -> w1 = v;
                case 2 -> w2 = v;
                case 3 -> w3 = v;
                case 4 -> w4 = v;
            }
        }

        void recomputeGrand() {
            // GRAND = simple average of the non-null week scores (change if you want weights)
            double sum = 0.0; int cnt = 0;
            if (w1 != null) { sum += w1; cnt++; }
            if (w2 != null) { sum += w2; cnt++; }
            if (w3 != null) { sum += w3; cnt++; }
            if (w4 != null) { sum += w4; cnt++; }
            grand = (cnt == 0) ? null : (sum / cnt);
        }
    }
}
