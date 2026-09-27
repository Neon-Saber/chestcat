package net.chestcat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional AI helper for item types ChestCat can't place by itself (mostly modded items).
 * It never sits in the sort loop: when a sort meets unknown item types, one background request
 * asks an OpenAI-compatible endpoint (Groq / Google AI Studio free tiers work) to pick a label for
 * each item id. Answers are cached in config/chestcat/ai_cache.json, so every item type is only
 * ever asked about once and later sorts are instant and identical.
 */
public final class AiClassifier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BATCH_SIZE = 50;
    private static final long RETRY_COOLDOWN_MS = 60_000L;

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile boolean loaded = false;
    private static volatile long retryAfterMs = 0L;

    private AiClassifier() {}

    private static Path cacheFile() {
        return CustomGroups.configDir().resolve("ai_cache.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = cacheFile();
        try {
            if (!Files.exists(file)) return;
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getValue().isJsonPrimitive()) CACHE.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            LOGGER.warn("[ChestCat] Could not read AI cache {}: {}", file, e.toString());
        }
    }

    /** The AI's label for this item id ("TOOLS", "CUSTOM:Mob Drops", ...) or null if never asked. */
    public static String cachedLabel(String itemId) {
        ensureLoaded();
        return CACHE.get(itemId);
    }

    public static boolean isEnabled() {
        CustomGroups.AiSettings s = CustomGroups.ai();
        return s.enabled() && !s.apiKey().isBlank();
    }

    /** Starts one background request for these item ids. Returns false if nothing was started. */
    public static boolean classifyAsync(Collection<String> itemIds, ServerPlayer player) {
        if (!isEnabled() || itemIds.isEmpty() || System.currentTimeMillis() < retryAfterMs) return false;
        if (!RUNNING.compareAndSet(false, true)) return false;

        List<String> ids = new ArrayList<>(itemIds);
        CustomGroups.AiSettings settings = CustomGroups.ai();
        Map<String, String> labels = allowedLabels();
        MinecraftServer server = player.getServer();

        Thread worker = new Thread(() -> run(ids, settings, labels, server, player), "chestcat-ai");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /** label -> short description shown to the AI: every built-in category plus every custom group. */
    private static Map<String, String> allowedLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (ItemCategory c : ItemCategory.values()) {
            if (c != ItemCategory.CUSTOM) labels.put(c.name(), c.getDisplayName());
        }
        for (CustomGroups.Group g : CustomGroups.groups()) {
            labels.put("CUSTOM:" + g.name(), g.description().isBlank() ? g.name() : g.description());
        }
        return labels;
    }

    private static void run(List<String> ids, CustomGroups.AiSettings settings, Map<String, String> labels,
                            MinecraftServer server, ServerPlayer player) {
        int classified = 0;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
                List<String> batch = ids.subList(from, Math.min(ids.size(), from + BATCH_SIZE));
                classified += classifyBatch(client, settings, labels, batch);
            }
            saveCache();
            tell(server, player, "ChestCat AI classified " + classified + " item type(s). Sort again to apply.",
                    ChatFormatting.AQUA);
        } catch (Exception e) {
            retryAfterMs = System.currentTimeMillis() + RETRY_COOLDOWN_MS;
            LOGGER.warn("[ChestCat] AI classification failed", e);
            if (classified > 0) saveCache();
            tell(server, player, "ChestCat AI failed: " + e.getMessage(), ChatFormatting.RED);
        } finally {
            RUNNING.set(false);
        }
    }

    private static int classifyBatch(HttpClient client, CustomGroups.AiSettings settings,
                                     Map<String, String> labels, List<String> batch) throws Exception {
        StringBuilder user = new StringBuilder("Allowed labels:\n");
        for (Map.Entry<String, String> e : labels.entrySet()) {
            user.append("- ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        user.append("\nItem ids:\n");
        for (String id : batch) user.append(id).append('\n');

        JsonArray messages = new JsonArray();
        messages.add(message("system", "You sort Minecraft items (vanilla and modded) into storage groups. "
                + "Reply with ONLY a JSON object that maps every given item id to exactly one allowed label. "
                + "Use the label whose meaning fits the item best; use MISC only if nothing fits. No explanations."));
        messages.add(message("user", user.toString()));

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("temperature", 0);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.endpoint()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            String text = response.body();
            throw new IllegalStateException("HTTP " + response.statusCode() + " "
                    + (text.length() > 200 ? text.substring(0, 200) : text));
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalStateException("the AI reply contained no JSON");

        JsonObject answers = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
        Set<String> asked = Set.copyOf(batch);
        int accepted = 0;
        for (Map.Entry<String, JsonElement> e : answers.entrySet()) {
            if (!asked.contains(e.getKey()) || !e.getValue().isJsonPrimitive()) continue;
            String label = e.getValue().getAsString().trim();
            if (labels.containsKey(label)) {
                CACHE.put(e.getKey(), label);
                accepted++;
            }
        }
        return accepted;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static synchronized void saveCache() {
        try {
            Files.createDirectories(cacheFile().getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<String, String> e : new TreeMap<>(CACHE).entrySet()) {
                root.addProperty(e.getKey(), e.getValue());
            }
            Files.writeString(cacheFile(), root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[ChestCat] Could not write AI cache: {}", e.toString());
        }
    }

    private static void tell(MinecraftServer server, ServerPlayer player, String text, ChatFormatting color) {
        if (server == null) return;
        server.execute(() -> player.sendSystemMessage(Component.literal(text).withStyle(color)));
    }
}
