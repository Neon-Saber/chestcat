package net.chestcat;

import net.minecraft.SharedConstants;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * What ChestCat is running on, and whether that is what it was built for. Nothing here is typed in by hand:
 * the supported Minecraft / NeoForge ranges are read from ChestCat's own mod metadata (the dependency ranges
 * declared in the build), the installed versions from the loader, and the Java version ChestCat needs from the
 * class file version of this very class. So the About page always matches the jar that is actually running.
 */
public final class CompatInfo {

    private CompatInfo() {}

    /** One thing ChestCat depends on: what it needs, what is installed, and whether they match. */
    public record Requirement(String name, String supported, String installed, boolean ok) {}

    public record Report(String chestcatVersion, String minecraftVersion, String loaderName, String loaderVersion,
                         String javaRunning, int javaRequired, String os, int installedMods,
                         List<Requirement> requirements, List<String> problems) {
        public boolean compatible() {
            return problems.isEmpty();
        }
    }

    private static Report cached;

    /** Computed once; the environment cannot change while the game is running. */
    public static synchronized Report get() {
        if (cached == null) cached = compute();
        return cached;
    }

    private static Report compute() {
        List<Requirement> reqs = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        String chestcat = "unknown";
        ModList mods = ModList.get();
        String mc = safeMinecraftVersion();
        String loaderVersion = "unknown";

        ModContainer self = mods == null ? null : mods.getModContainerById("chestcat").orElse(null);
        if (self != null) {
            chestcat = self.getModInfo().getVersion().toString();
            for (IModInfo.ModVersion dep : self.getModInfo().getDependencies()) {
                String id = dep.getModId();
                if (!id.equals("minecraft") && !id.equals("neoforge")) continue;
                ArtifactVersion installed = mods.getModContainerById(id).map(c -> c.getModInfo().getVersion()).orElse(null);
                boolean ok = installed != null && dep.getVersionRange().containsVersion(installed);
                String name = id.equals("minecraft") ? "Minecraft" : "NeoForge";
                String installedText = installed == null ? "not found" : installed.toString();
                if (id.equals("minecraft") && installed != null) mc = installedText;
                if (id.equals("neoforge") && installed != null) loaderVersion = installedText;
                String supported = friendlyRange(dep.getVersionRange().toString());
                reqs.add(new Requirement(name, supported, installedText, ok));
                if (!ok) {
                    problems.add("Unsupported " + name + " version: ChestCat supports " + supported
                            + ", but you're running " + installedText + ". Some features may not work correctly.");
                }
            }
        } else {
            problems.add("ChestCat could not read its own mod information, so compatibility could not be checked.");
        }

        int required = compiledJava();
        int running = Runtime.version().feature();
        if (required > 0 && running < required) {
            problems.add("Unsupported Java version: ChestCat needs Java " + required + " or newer, but you're running Java "
                    + running + ".");
        }

        String os = System.getProperty("os.name", "unknown") + " (" + System.getProperty("os.arch", "?") + ")";
        int count = mods == null ? 0 : mods.getMods().size();
        return new Report(chestcat, mc, "NeoForge", loaderVersion, System.getProperty("java.version", "unknown"),
                required, os, count, List.copyOf(reqs), List.copyOf(problems));
    }

    private static String safeMinecraftVersion() {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** The Java version this class was compiled for (read from its class file header), or -1 if unreadable. */
    private static int compiledJava() {
        try (InputStream in = CompatInfo.class.getResourceAsStream("CompatInfo.class")) {
            if (in == null) return -1;
            DataInputStream data = new DataInputStream(in);
            data.readInt();          // magic
            data.readUnsignedShort(); // minor
            return data.readUnsignedShort() - 44; // major 65 = Java 21
        } catch (Exception e) {
            return -1;
        }
    }

    /** "[1.21.1,1.22)" -> "1.21.1 up to (not including) 1.22"; "[21.1.0,)" -> "21.1.0 or newer". Anything else is shown as-is. */
    static String friendlyRange(String range) {
        String r = range.strip();
        if (r.length() > 2 && r.startsWith("[") && r.endsWith(")")) {
            String[] parts = r.substring(1, r.length() - 1).split(",", -1);
            if (parts.length == 2 && !parts[0].isBlank()) {
                return parts[1].isBlank() ? parts[0] + " or newer" : parts[0] + " up to (not including) " + parts[1];
            }
        }
        return r;
    }

    /** Plain-text block for bug reports. Contains no user name, paths or account details. */
    public static String diagnostics() {
        Report r = get();
        StringBuilder sb = new StringBuilder();
        sb.append("ChestCat: ").append(r.chestcatVersion()).append('\n');
        sb.append("Minecraft: ").append(r.minecraftVersion()).append('\n');
        sb.append("Loader: ").append(r.loaderName()).append(' ').append(r.loaderVersion()).append('\n');
        sb.append("Java: ").append(r.javaRunning()).append(" (needs ")
                .append(r.javaRequired() > 0 ? r.javaRequired() + "+" : "unknown").append(")\n");
        sb.append("OS: ").append(r.os()).append('\n');
        sb.append("Mods: ").append(r.installedMods()).append('\n');
        for (Requirement q : r.requirements()) {
            sb.append(q.name()).append(" supported: ").append(q.supported()).append(q.ok() ? " (ok)" : " (MISMATCH)").append('\n');
        }
        sb.append("Status: ").append(r.compatible() ? "Compatible" : String.join(" | ", r.problems()))
                .append('\n');
        return sb.toString();
    }
}
