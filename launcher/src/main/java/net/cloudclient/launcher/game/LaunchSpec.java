package net.cloudclient.launcher.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.json.Json;

/**
 * Turns a Minecraft version JSON (plus its parent, for Forge) into a launch
 * spec: classpath entries, native jars, main class and substituted arguments.
 *
 * <p>Pure data transformation, so it can be tested against the real 1.8.9 JSON
 * without launching anything.
 */
public final class LaunchSpec {

    /** One library to download. */
    public static final class Library {
        public final String name;
        public final String path;
        public final String url;
        public final boolean nativeJar;

        Library(String name, String path, String url, boolean nativeJar) {
            this.name = name;
            this.path = path;
            this.url = url;
            this.nativeJar = nativeJar;
        }
    }

    public String mainClass;
    public String assetIndex;
    public String assetsUrl;
    public String minecraftArguments;
    public final List<Library> libraries = new ArrayList<Library>();
    public final Map<String, String> placeholders = new LinkedHashMap<String, String>();

    /** Maven coordinates "group:artifact:version[:classifier]" -> relative path. */
    public static String mavenPath(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length < 3) return coordinates;
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version
                + classifier + ".jar";
    }

    /**
     * Builds a spec from a version object, resolving {@code inheritsFrom} against
     * the supplied parent.
     */
    public static LaunchSpec from(Map<String, Object> version, Map<String, Object> parent) {
        LaunchSpec spec = new LaunchSpec();

        Map<String, Object> base = parent != null ? parent : version;
        spec.mainClass = Json.getString(version, "mainClass", Json.getString(base, "mainClass", ""));
        spec.minecraftArguments = Json.getString(version, "minecraftArguments",
                Json.getString(base, "minecraftArguments", ""));

        Map<String, Object> assetIndex = Json.child(version, "assetIndex");
        if (assetIndex == null) assetIndex = Json.child(base, "assetIndex");
        if (assetIndex != null) {
            spec.assetIndex = Json.getString(assetIndex, "id", "1.8");
            spec.assetsUrl = Json.getString(assetIndex, "url", "");
        }

        // Parent libraries first, then the child's (Forge overrides on conflict).
        addLibraries(spec, base);
        if (version != base) addLibraries(spec, version);
        return spec;
    }

    private static void addLibraries(LaunchSpec spec, Map<String, Object> version) {
        List<Object> libraries = Json.children(version, "libraries");
        if (libraries == null) return;

        String osName = osName();
        String arch = System.getProperty("os.arch", "x86_64").contains("64") ? "64" : "32";

        for (Object entry : libraries) {
            if (!(entry instanceof Map)) continue;
            Map<String, Object> library = Json.asObject(entry);
            if (!allowed(library, osName)) continue;

            String name = Json.getString(library, "name", "");
            String baseUrl = Json.getString(library, "url", "https://libraries.minecraft.net/");
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://libraries.minecraft.net/";
            }
            if (!baseUrl.endsWith("/")) baseUrl = baseUrl + "/";

            Map<String, Object> natives = Json.child(library, "natives");
            if (natives != null) {
                String classifier = Json.getString(natives, osName, null);
                if (classifier == null) continue;
                classifier = classifier.replace("${arch}", arch);
                String coordinates = name + ":" + classifier;
                spec.libraries.add(new Library(coordinates, mavenPath(coordinates),
                        baseUrl + mavenPath(coordinates), true));
            } else {
                spec.libraries.add(new Library(name, mavenPath(name),
                        baseUrl + mavenPath(name), false));
            }
        }
    }

    /**
     * Evaluates vanilla's rule list for the current OS.
     *
     * <p>Vanilla semantics: no rules means allowed; the last matching rule wins.
     */
    public static boolean allowed(Map<String, Object> library, String osName) {
        List<Object> rules = Json.children(library, "rules");
        if (rules == null || rules.isEmpty()) return true;

        boolean allowed = false;
        for (Object ruleObject : rules) {
            if (!(ruleObject instanceof Map)) continue;
            Map<String, Object> rule = Json.asObject(ruleObject);
            String action = Json.getString(rule, "action", "allow");
            Map<String, Object> os = Json.child(rule, "os");
            boolean matches = true;
            if (os != null) {
                String name = Json.getString(os, "name", null);
                if (name != null && !name.equals(osName)) matches = false;
                String arch = Json.getString(os, "arch", null);
                if (matches && arch != null
                        && !arch.equals(System.getProperty("os.arch", ""))) {
                    matches = false;
                }
            }
            if (matches) allowed = "allow".equals(action);
        }
        return allowed;
    }

    /** Minecraft's OS names, not Java's. */
    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    /** Substitutes ${...} placeholders, leaving unknown ones untouched. */
    public static String substitute(String template, Map<String, String> values) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** Splits an argument string on spaces, respecting double quotes. */
    public static List<String> splitArguments(String arguments) {
        List<String> out = new ArrayList<String>();
        if (arguments == null || arguments.trim().isEmpty()) return out;
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ' ' && !quoted) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    public List<String> classpath() {
        List<String> paths = new ArrayList<String>();
        for (Library library : libraries) {
            if (!library.nativeJar) paths.add(library.path);
        }
        return paths;
    }
}
