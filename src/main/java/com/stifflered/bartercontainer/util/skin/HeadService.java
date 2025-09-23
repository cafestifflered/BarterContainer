package com.stifflered.bartercontainer.util.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.stifflered.bartercontainer.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * HeadService

 * Goals:
 *  - Guarantee that head textures render for BOTH Java and Bedrock viewers.
 *  - Java: use Mojang UUID (client fetches skin from textures.minecraft.net automatically).
 *  - Bedrock (Geyser/Floodgate): upload the Bedrock skin PNG to MineSkin to obtain a
 *    textures.minecraft.net-compatible "textures" property (value/signature), cache it,
 *    and set it on the skull profile.

 * Design:
 *  - Synchronous code only touches local cache and builds a placeholder head (never blocks).
 *  - Network work happens ASYNC. When ready, we schedule a SYNC inventory slot update.
 *  - Cache file: plugins/<YourPlugin>/skins.json

 * Reliability upgrades in this version:
 *  - Stale-while-revalidate: show cached textures immediately (even if TTL-expired), then refresh in background.
 *  - Cache-first application happens BEFORE we branch on Java/Bedrock (so even if detection is flaky, cached
 *    textures still render).
 *  - Floodgate compatibility: support multiple skin getters (PNG bytes, base64 PNG, raw pixels + PNG encoding, URL).
 *  - Linked Java fast-path: if Floodgate exposes a linked Java account, use that UUID (skip MineSkin entirely).
 *  - MineSkin retry/backoff with 429 (rate limit) handling.
 *  - ProfileId alignment: set the skull's profile UUID to the one embedded in the textures "value" so
 *    clients don't ignore the texture due to a UUID mismatch.
 *  - More careful connection cleanup and thread handoff.

 * Requirements:
 *  - Paper API (PlayerProfile).
 *  - Optional: Floodgate API present at runtime (we reflectively detect it).

 * Notes:
 *  - MineSkin endpoints are rate-limited. We back off and cache for 7 days by default.
 *  - If Floodgate isn't installed, we treat players as Java (safe fallback).
 */
public final class HeadService {

    /* ---------------------------- Configurable knobs ---------------------------- */

    /** How long a cached Bedrock skin stays "fresh" before we try to refresh. */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /** MineSkin upload endpoint (no API key required for public tier). */
    private static final String MINESKIN_UPLOAD_URL = "https://api.mineskin.org/generate/upload";

    /** User agent for HTTP requests. */
    private static final String USER_AGENT = "BarterContainer-HeadService/1.2 (+https://example.invalid)";

    /** Async retry policy for MineSkin uploads. */
    private static final int MINESKIN_MAX_ATTEMPTS = 3;
    private static final long MINESKIN_INITIAL_BACKOFF_MS = 2000L;

    /* ---------------------------- State & wiring ---------------------------- */

    private final Plugin plugin;
    private final File cacheFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Cache keyed by the Bedrock UUID (Floodgate UUID).
     * Each entry stores the Base64 "textures" value/signature and lastUpdated timestamp.
     */
    private final Map<UUID, SkinEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Optional Floodgate classes resolved via reflection so we don't hard-depend. */
    private final Object floodgateApi; // org.geysermc.floodgate.api.FloodgateApi instance or null

    public HeadService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.cacheFile = new File(plugin.getDataFolder(), "skins.json");
        this.floodgateApi = tryGetFloodgateApi();
        loadCache();
    }

    /* ---------------------------- Public API ---------------------------- */

    /**
     * Build a skull for the given player UUID+name and update it later if we can resolve a Bedrock skin.

     * Usage pattern in GUIs:
     *   1) Put the returned ItemStack in the inventory immediately (placeholder shows up).
     *   2) Pass a Consumer<ItemStack> that swaps the slot with the updated head (we call it on the main thread when ready).

     * Robustness changes:
     *   - If Floodgate exposes a linked Java account, we use that UUID (client resolves Mojang textures automatically).
     *   - If we have cached textures (even expired), we apply them immediately (stale-while-revalidate).
     *   - Then we refresh asynchronously if the cache is missing/expired; when done we call onReadyMainThread with the updated head.
     *
     * @param viewerOrOwnerUuid UUID of the player whose avatar we want to show
     * @param nameOrNull        Optional display name (Skull tooltip/title is up to the GUI)
     * @param onReadyMainThread Called on the server thread with the refreshed head ItemStack when available
     * @return a PLAYER_HEAD item you can place right away (may be updated later)
     */
    public ItemStack buildHeadAsync(UUID viewerOrOwnerUuid, @Nullable String nameOrNull,
                                    Consumer<ItemStack> onReadyMainThread) {
        // 0) If the player is ONLINE right now, copy their live PlayerProfile.
        //    This avoids any timing/race with Floodgate/skin forwarding and works even
        //    if Floodgate is only partially involved. We also cache what we see.
        var online = Bukkit.getPlayer(viewerOrOwnerUuid);
        if (online != null) {
            ItemStack liveHead = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im = liveHead.getItemMeta();
            if (im instanceof SkullMeta sm) {
                PlayerProfile live = online.getPlayerProfile(); // should already include "textures"
                sm.setPlayerProfile(live);
                liveHead.setItemMeta(sm);

                // Cache the "textures" for later/offline GUI rendering.
                ProfileProperty tex = live.getProperties().stream()
                        .filter(p -> "textures".equals(p.getName()))
                        .findFirst().orElse(null);
                if (tex != null) {
                    SkinEntry se = new SkinEntry();
                    se.value = tex.getValue();
                    se.signature = tex.getSignature();
                    se.lastUpdated = System.currentTimeMillis();
                    cache.put(viewerOrOwnerUuid, se);
                    saveCache();
                    log("used live profile and cached texture for " + viewerOrOwnerUuid);
                } else {
                    log("live profile had no textures for " + viewerOrOwnerUuid);
                }
            }
            // We are DONE for the online case—no async or callback required.
            return liveHead;
        }

        // 1) Linked-Java fast path (works when Floodgate exposes a linked Java UUID).
        UUID linkedJava = tryGetLinkedJavaUuid(viewerOrOwnerUuid);
        if (linkedJava != null) {
            log("using linked Java UUID for " + viewerOrOwnerUuid + " -> " + linkedJava);
            return immediateJavaHead(linkedJava, nameOrNull);
        }

        // 2) Start with a generic skull bound to the given UUID (never blocks).
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta im = skull.getItemMeta();
        if (im instanceof SkullMeta sm) {
            PlayerProfile profile = Bukkit.createProfile(viewerOrOwnerUuid, nameOrNull);
            sm.setPlayerProfile(profile);
            skull.setItemMeta(sm);
        }

        // === Apply cached textures FIRST, regardless of Bedrock/Java detection ===
        SkinEntry cached = cache.get(viewerOrOwnerUuid);
        boolean appliedCache;
        if (cached != null && cached.value != null && !cached.value.isBlank()) {
            appliedCache = applyTextures(skull, cached.value, cached.signature);
            if (appliedCache && plugin.isEnabled()) {
                // Deliver the best we have now immediately to the GUI
                Bukkit.getScheduler().runTask(plugin, () -> onReadyMainThread.accept(skull));
            }
            // If cache is still fresh, we are done (stale-while-revalidate complete).
            if (cached.isFresh()) {
                return skull;
            }
        }

        // Java players: we're done — the client resolves Mojang skin.
        // If we're here, cache was missing or stale. For Java, there is nothing else we can do.
        if (isJavaOrNoFloodgate(viewerOrOwnerUuid)) {
            return skull;
        }

        // Bedrock players (or unknown-but-likely-Bedrock): fetch/refresh in the background.
        fetchBedrockSkinAsync(viewerOrOwnerUuid).thenAccept(opt -> {
            if (opt.isEmpty()) return;
            SkinEntry entry = opt.get();

            // Update cache (mem + disk)
            cache.put(viewerOrOwnerUuid, entry);
            saveCache();

            // Build a new ItemStack carrying the updated textures
            ItemStack updated = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im2 = updated.getItemMeta();
            if (im2 instanceof SkullMeta sm2) {
                PlayerProfile prof = Bukkit.createProfile(viewerOrOwnerUuid, nameOrNull);
                prof.setProperty(new ProfileProperty("textures", entry.value, entry.signature));
                sm2.setPlayerProfile(prof);
                updated.setItemMeta(sm2);
            }

            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> onReadyMainThread.accept(updated));
            }
        });

        return skull;
    }

    /* ---------------------------- Internals ---------------------------- */

    /** Create a skull for the given Java UUID (client resolves textures automatically). */
    private ItemStack immediateJavaHead(UUID javaUuid, @Nullable String nameOrNull) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta im = skull.getItemMeta();
        if (im instanceof SkullMeta sm) {
            PlayerProfile profile = Bukkit.createProfile(javaUuid, nameOrNull);
            sm.setPlayerProfile(profile);
            skull.setItemMeta(sm);
        }
        return skull;
    }

    /**
     * True iff this UUID should be treated as Java (or Floodgate is absent).
     * Bedrock returns false.

     * (Positive predicate to avoid "BooleanMethodIsAlwaysInverted" warnings at call sites.)
     */
    private boolean isJavaOrNoFloodgate(UUID uuid) {
        if (floodgateApi == null) return true; // no Floodgate => treat as Java
        try {
            // FloodgateApi.isFloodgateId(uuid) -> true when it's a Bedrock UUID
            boolean isBedrock = (boolean) floodgateApi.getClass()
                    .getMethod("isFloodgateId", UUID.class)
                    .invoke(floodgateApi, uuid);
            return !isBedrock;
        } catch (Throwable ignored) {
            return true; // safest fallback is Java behavior
        }
    }

    /**
     * If available, resolve the linked Java UUID for a Floodgate Bedrock player.
     * This lets us skip MineSkin entirely and rely on Mojang textures.
     */
    private @Nullable UUID tryGetLinkedJavaUuid(UUID bedrockUuid) {
        if (floodgateApi == null) return null;
        try {
            Object fgPlayer = floodgateApi.getClass().getMethod("getPlayer", UUID.class).invoke(floodgateApi, bedrockUuid);
            if (fgPlayer == null) return null;

            // Floodgate variations:
            //  - getLinkedPlayer() / getLinkedAccount() => FloodgateLinkedPlayer
            //  - FloodgateLinkedPlayer.getJavaUniqueId()
            Object linked = firstOf(
                    () -> tryCallNoArgs(fgPlayer, "getLinkedPlayer"),
                    () -> tryCallNoArgs(fgPlayer, "getLinkedAccount")
            );
            if (linked == null) return null;

            Object javaUuid = firstOf(
                    () -> tryCallNoArgs(linked, "getJavaUniqueId"),
                    () -> tryCallNoArgs(linked, "getJavaUuid"),
                    () -> tryCallNoArgs(linked, "getUuid")
            );
            if (javaUuid instanceof UUID id) return id;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Fetch or generate a textures property for a Bedrock player's current skin.
     *  1) Pull raw PNG bytes from Floodgate (reflective calls, many variants supported).
     *  2) POST to MineSkin /generate/upload (multipart/form-data).
     *  3) Parse { value, signature }.

     * Includes limited retries with exponential backoff on IO failures / 429.
     */
    private CompletableFuture<Optional<SkinEntry>> fetchBedrockSkinAsync(UUID bedrockUuid) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] rawPng = getBedrockSkinPng(bedrockUuid);
            if (rawPng == null || rawPng.length == 0) {
                log("no PNG from Floodgate for " + bedrockUuid);
                return Optional.empty();
            }

            // Normalize to 64x64 if needed and pick a sensible variant (default classic).
            NormalizedPng norm = normalizeForJava(rawPng);
            log("uploading Bedrock skin to MineSkin: " + bedrockUuid + " variant=" + norm.variant);

            int attempts = 0;
            long backoff = MINESKIN_INITIAL_BACKOFF_MS;

            while (attempts++ < MINESKIN_MAX_ATTEMPTS) {
                try {
                    SkinEntry entry = uploadToMineSkin(norm.png, norm.variant);
                    if (entry != null) return Optional.of(entry);
                    // Non-200, non-429 etc.: give up immediately (no tight loop).
                    log("MineSkin returned non-200/no-body for " + bedrockUuid);
                    break;
                } catch (IOException ioe) {
                    log("MineSkin attempt " + attempts + " failed for " + bedrockUuid + " : " + ioe.getMessage());
                    try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                    backoff *= 2;
                } catch (Throwable t) {
                    log("MineSkin unexpected error for " + bedrockUuid + " : " + t.getMessage());
                    break;
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Obtain the Bedrock player's skin as PNG bytes using Floodgate (reflectively).

     * We support several Floodgate API shapes:
     *   - skin.getData() -> byte[] PNG
     *   - skin.getEncodedData() -> base64 PNG string
     *   - skin.getSkinData() / getRawSkin() / getPixels() -> raw pixel arrays + getWidth()/getHeight(), which we encode to PNG
     *   - skin.getTextureUrl() -> HTTP URL to download (last resort)
     */
    private byte[] getBedrockSkinPng(UUID bedrockUuid) {
        if (floodgateApi == null) return null;
        try {
            Object fgPlayer = floodgateApi.getClass().getMethod("getPlayer", UUID.class)
                    .invoke(floodgateApi, bedrockUuid);
            if (fgPlayer == null) return null;

            Object skin = tryCallNoArgs(fgPlayer, "getSkin");
            if (skin == null) return null;

            // 1) Direct PNG bytes (common in newer APIs)
            Object data = tryCallNoArgs(skin, "getData");
            if (data instanceof byte[] png && png.length > 0) {
                return png;
            }

            // 2) Base64-encoded PNG string
            Object encoded = tryCallNoArgs(skin, "getEncodedData");
            if (encoded instanceof String b64 && !b64.isBlank()) {
                try {
                    return Base64.getDecoder().decode(b64);
                } catch (IllegalArgumentException ignored) {
                    // keep trying other paths
                }
            }

            // 3) Raw pixel data -> encode to PNG
            Object raw = firstOf(
                    () -> tryCallNoArgs(skin, "getSkinData"),
                    () -> tryCallNoArgs(skin, "getRawSkin"),
                    () -> tryCallNoArgs(skin, "getPixels")
            );
            Integer width  = (Integer) firstOf(() -> tryCallNoArgs(skin, "getWidth"),  () -> 64);
            Integer height = (Integer) firstOf(() -> tryCallNoArgs(skin, "getHeight"), () -> 64);
            if (raw != null && width != null && height != null) {
                byte[] png = encodeToPng(raw, width, height);
                if (png != null && png.length > 0) return png;
            }

            // 4) URL fallback
            Object urlObj = tryCallNoArgs(skin, "getTextureUrl");
            if (urlObj instanceof String url && !url.isBlank()) {
                return httpGetBytes(url);
            }

            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Helper: attempt calling a no-arg method reflectively; return null on failure. */
    private static Object tryCallNoArgs(Object target, String method) {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    /** Helper: try a sequence of suppliers, returning the first non-null result. */
    @SafeVarargs
    private static <T> T firstOf(Supplier<T>... attempts) {
        for (Supplier<T> s : attempts) {
            try {
                T v = s.get();
                if (v != null) return v;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Encode raw pixel buffers to PNG.
     * Supports:
     *   - byte[] length >= width*height*4 in RGBA order
     *   - int[] length >= width*height in ARGB ints
     */
    private static byte[] encodeToPng(Object raw, int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] argb = new int[width * height];

            if (raw instanceof byte[] b && (b.length >= width * height * 4)) {
                // Assume RGBA order
                for (int i = 0, p = 0; i < width * height; i++) {
                    int r = b[p++] & 0xFF;
                    int g = b[p++] & 0xFF;
                    int bl = b[p++] & 0xFF;
                    int a = b[p++] & 0xFF;
                    argb[i] = ((a & 0xFF) << 24) | (r << 16) | (g << 8) | bl;
                }
            } else if (raw instanceof int[] ints && ints.length >= width * height) {
                // Assume ARGB ints
                System.arraycopy(ints, 0, argb, 0, width * height);
            } else {
                return null;
            }

            img.setRGB(0, 0, width, height, argb, 0, width);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(img, "png", baos);
                return baos.toByteArray();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] httpGetBytes(String urlStr) throws IOException {
        HttpURLConnection con = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        try (InputStream in = con.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toByteArray();
        } finally {
            con.disconnect(); // ensure connection is released
        }
    }

    /** Toggle verbose logs for HeadService (set true while debugging). */
    private static final boolean VERBOSE_LOGS = false;

    /** Minimal logger for quick tracing. Only logs when VERBOSE_LOGS is true. */
    private void log(String msg) {
        if (!VERBOSE_LOGS) return;
        plugin.getLogger().info("[HeadService] " + msg);
    }

    /**
     * Container for normalized PNG plus the chosen model variant.
     *
     * @param variant "classic" or "slim"
     */
    private record NormalizedPng(byte[] png, String variant) {
        private NormalizedPng(byte[] png, String variant) {
            this.png = png;
            this.variant = (variant == null || variant.isBlank()) ? "classic" : variant;
        }
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    /** Warm/refresh cached texture for a Bedrock UUID in the background. */
    public void ensureCachedBedrock(UUID uuid) {
        // Only do Bedrock work; Java clients resolve via Mojang.
        if (isJavaOrNoFloodgate(uuid)) return;
        SkinEntry s = cache.get(uuid);
        // If we already have a fresh entry, nothing to do.
        if (s != null && s.isFresh()) return;
        // Otherwise warm/refresh asynchronously.
        fetchBedrockSkinAsync(uuid).thenAccept(opt -> opt.ifPresent(se -> {
            cache.put(uuid, se);
            saveCache();
            log("warmed cache for " + uuid);
        }));
    }

    /**
     * Normalize Floodgate PNGs (often 128x128) down to Java's expected 64x64 and try to
     * pick a sane variant. We default to "classic" because variant detection differs per Floodgate build.
     */
    private static NormalizedPng normalizeForJava(byte[] pngBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(pngBytes)) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                // If we can't parse, ship the original and let MineSkin try; default to "classic".
                return new NormalizedPng(pngBytes, "classic");
            }

            int w = src.getWidth();
            int h = src.getHeight();

            // Heuristic: many Bedrock skins are 128x128. Scale to 64x64 for MineSkin reliability.
            if (w == 64 && h == 64) {
                return new NormalizedPng(pngBytes, "classic"); // safe default
            }

            BufferedImage dst = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            // Nearest-neighbor downscale to preserve pixel art look
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int sx = x * w / 64;
                    int sy = y * h / 64;
                    dst.setRGB(x, y, src.getRGB(sx, sy));
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(dst, "png", out);
                return new NormalizedPng(out.toByteArray(), "classic"); // variant default
            }
        } catch (IOException e) {
            // Fall back to raw bytes if normalization fails.
            return new NormalizedPng(pngBytes, "classic");
        }
    }

    /**
     * POST the PNG to MineSkin and parse the returned "textures" value/signature.

     * Response shape we care about (simplified):
     * {
     *   "data": {
     *     "texture": {
     *       "value": "<base64>",
     *       "signature": "<sig>" // optional
     *     }
     *   }
     * }

     * Special handling:
     *   - 429 -> throw IOException so caller can backoff/retry.
     *   - Other non-200 -> return null (no retry, unless caller decides otherwise).
     */
    // Accepts the normalized PNG and an explicit "variant" ("classic" or "slim").
    private @Nullable SkinEntry uploadToMineSkin(byte[] png, String variant) throws IOException {
        String boundary = "----BarterContainer" + System.currentTimeMillis();
        HttpURLConnection con = (HttpURLConnection) URI.create(MINESKIN_UPLOAD_URL).toURL().openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(30000);

        try (OutputStream out = con.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {

            // 1) file part: "file"
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
            writer.append("Content-Type: image/png\r\n\r\n").flush();
            out.write(png);
            out.flush();
            writer.append("\r\n");

            // 2) explicit model variant helps MineSkin handle 128x128 and slim models correctly
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
            writer.append(variant == null ? "classic" : variant).append("\r\n");

            // close
            writer.append("--").append(boundary).append("--\r\n").flush();
        }

        int code = con.getResponseCode();
        if (code == 429) {
            // Rate limited — drain error and signal caller to retry
            try (InputStream err = con.getErrorStream()) {
                if (err != null) err.transferTo(OutputStream.nullOutputStream());
            } finally {
                con.disconnect();
            }
            throw new IOException("MineSkin rate limited (429)");
        }
        if (code != 200) {
            // Other failure — drain and return null (no retry by default)
            try (InputStream err = con.getErrorStream()) {
                if (err != null) err.transferTo(OutputStream.nullOutputStream());
            } finally {
                con.disconnect();
            }
            return null;
        }

        // Parse JSON using JsonObject and always disconnect after reading
        try (InputStream in = con.getInputStream();
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root == null) return null;

            JsonObject data = root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : null;
            if (data == null) return null;

            JsonObject texture = data.has("texture") && data.get("texture").isJsonObject()
                    ? data.getAsJsonObject("texture") : null;
            if (texture == null) return null;

            String value = texture.has("value") && !texture.get("value").isJsonNull()
                    ? texture.get("value").getAsString() : null;
            String sig = texture.has("signature") && !texture.get("signature").isJsonNull()
                    ? texture.get("signature").getAsString() : null;

            if (value == null || value.isBlank()) return null;

            SkinEntry entry = new SkinEntry();
            entry.value = value;
            entry.signature = sig; // MineSkin may omit signature
            entry.lastUpdated = Instant.now().toEpochMilli();
            return entry;
        } finally {
            con.disconnect();
        }
    }

    /**
     * Parse dashed UUID from a 32-hex character string (no dashes).
     * Returns null if the string is not 32 hex chars or cannot be parsed.
     */
    private static @Nullable UUID dashedUuidFromUndashed(@Nullable String undashed) {
        if (undashed == null) return null;
        String s = undashed.trim().toLowerCase(Locale.ROOT).replace("-", "");
        if (s.length() != 32) return null;
        try {
            return UUID.fromString(
                    s.substring(0, 8) + "-" +
                            s.substring(8, 12) + "-" +
                            s.substring(12, 16) + "-" +
                            s.substring(16, 20) + "-" +
                            s.substring(20)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extract the "profileId" (undashed hex) from a MineSkin/Yggdrasil textures "value" (Base64-encoded JSON).
     * Returns a dashed UUID if present/parseable; otherwise null.
     * The "value" structure (decoded) typically includes:
     * {
     *   "timestamp": ...,
     *   "profileId": "21e367d725cf4e3bb2692c4a300a4deb",
     *   "profileName": "...",
     *   "signatureRequired": true,
     *   "textures": { "SKIN": { "url": "<a href="http://textures.minecraft.net/texture/">...</a>..." } }
     * }
     */
    private static @Nullable UUID extractProfileIdFromTexturesValue(String base64Value) {
        try {
            byte[] jsonBytes = Base64.getDecoder().decode(base64Value);
            try (InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(jsonBytes), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root == null) return null;
                if (root.has("profileId") && !root.get("profileId").isJsonNull()) {
                    String undashed = root.get("profileId").getAsString();
                    return dashedUuidFromUndashed(undashed);
                }
                return null;
            }
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * Apply a textures property to a skull ItemStack (returns true if applied).

     * IMPORTANT: We set the skull's PlayerProfile UUID to match the "profileId" embedded
     * inside the textures "value" when available. Some clients/proxies ignore the texture if
     * the skull profile UUID does not match the signed value's profileId, leading to Steve heads.
     */
    private static boolean applyTextures(ItemStack skull, String value, @Nullable String signature) {
        // Prefer to bind the profile UUID to the one the texture was signed for.
        UUID signedFor = extractProfileIdFromTexturesValue(value);

        ItemMeta im = skull.getItemMeta();
        if (!(im instanceof SkullMeta sm)) return false;

        // Build or reuse a profile whose UUID matches the textures' profileId when possible.
        PlayerProfile profile;
        if (signedFor != null) {
            profile = Bukkit.createProfile(signedFor, null);
        } else {
            profile = sm.getPlayerProfile();
            if (profile == null || profile.getId() == null) {
                // fallback profile (never null after this)
                OfflinePlayer dummy = Bukkit.getOfflinePlayer(UUID.randomUUID());
                profile = Bukkit.createProfile(dummy.getUniqueId(), dummy.getName());
            }
        }

        profile.setProperty(new ProfileProperty("textures", value, signature));
        sm.setPlayerProfile(profile);
        skull.setItemMeta(sm);
        return true;
    }

    /* ---------------------------- Cache ---------------------------- */

    private void loadCache() {
        if (!cacheFile.exists()) return;
        try (FileReader fr = new FileReader(cacheFile, StandardCharsets.UTF_8)) {
            // Deserialize as Map<?,?> then coerce entries.
            Map<?, ?> tmp = gson.fromJson(fr, Map.class);
            if (tmp != null && !tmp.isEmpty()) {
                for (Map.Entry<?, ?> e : tmp.entrySet()) {
                    try {
                        UUID id = UUID.fromString(String.valueOf(e.getKey()));
                        JsonObject obj = gson.toJsonTree(e.getValue()).getAsJsonObject();
                        SkinEntry se = new SkinEntry();
                        se.value = obj.has("value") && !obj.get("value").isJsonNull() ? obj.get("value").getAsString() : null;
                        se.signature = obj.has("signature") && !obj.get("signature").isJsonNull() ? obj.get("signature").getAsString() : null;
                        se.lastUpdated = obj.has("lastUpdated") && !obj.get("lastUpdated").isJsonNull() ? obj.get("lastUpdated").getAsLong() : 0L;
                        if (se.value != null) cache.put(id, se);
                    } catch (Exception ignore) { /* skip malformed entries */ }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveCache() {
        // Make sure plugin dir exists
        File dir = cacheFile.getParentFile();
        if (dir != null && !dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok && !dir.exists()) {
                plugin.getLogger().warning(Messages.fmt(
                        "skins.head_service.cache_dir_failed",
                        "path", dir.getAbsolutePath()
                ));
            }
        }
        try (FileWriter fw = new FileWriter(cacheFile, StandardCharsets.UTF_8)) {
            gson.toJson(cache, fw);
        } catch (Throwable ignored) {
        }
    }

    /* ---------------------------- Floodgate detection ---------------------------- */

    private static Object tryGetFloodgateApi() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return apiClass.getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------------------- Data model ---------------------------- */

    private static final class SkinEntry {
        String value;
        String signature; // optional
        long lastUpdated;

        /**
         * Returns true when the cache entry is still within TTL (fresh),
         * false when stale (age >= TTL).

         * (Positive predicate to avoid "always inverted" warnings at call sites.)
         */
        boolean isFresh() {
            long ageMs = Math.max(0, System.currentTimeMillis() - lastUpdated);
            return ageMs < CACHE_TTL.toMillis();
        }
    }
}
