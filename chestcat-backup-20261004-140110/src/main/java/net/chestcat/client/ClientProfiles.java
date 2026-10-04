package net.chestcat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sort profiles kept in a file on this computer (config/chestcat/profiles.json), so they work in every world
 * and on every server. Each profile is a snapshot of all sorting settings - modes, chain, favorites behaviour,
 * hotbar options, category / mod order, ignore rules and category splitting - but not the interface look.
 * The world-bound profiles that live on the server still exist (Saved Profiles); this is the portable kind.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientProfiles {

    private ClientProfiles() {}

    public static final int MAX_PROFILES = 30;
    private static final int MAX_NAME = 32;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    private static Map<String, String> profiles = null;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("chestcat").resolve("profiles.json");
    }

    private static synchronized Map<String, String> map() {
        if (profiles != null) return profiles;
        profiles = new LinkedHashMap<>();
        Path path = file();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) profiles.putAll(loaded);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("ChestCat: could not read {} - starting with no client profiles", path, e);
            }
        }
        return profiles;
    }

    private static void write() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(map(), MAP_TYPE));
        } catch (IOException e) {
            LOGGER.warn("ChestCat: could not write {}", path, e);
        }
    }

    public static synchronized List<String> names() {
        List<String> list = new ArrayList<>(map().keySet());
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    /** Saves the current sorting settings under this name. @return false when the profile limit is reached. */
    public static synchronized boolean save(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) n = "Unnamed";
        if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
        if (!map().containsKey(n) && map().size() >= MAX_PROFILES) return false;
        map().put(n, ClientPrefs.snapshotSort());
        write();
        return true;
    }

    /** Loads a profile into the live settings (and sends them to the server). */
    public static synchronized boolean load(String name) {
        String json = map().get(name);
        return json != null && ClientPrefs.applySortSnapshot(json);
    }

    public static synchronized void delete(String name) {
        if (map().remove(name) != null) write();
    }
}
