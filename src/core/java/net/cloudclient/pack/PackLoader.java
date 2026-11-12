package net.cloudclient.pack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.cloudclient.core.json.Json;

/**
 * Reads a {@code .cloudpack} archive into memory and validates it.
 *
 * <p>Threat model: archives come from the internet. The loader therefore
 * <ul>
 *   <li>rejects absolute paths, drive letters, {@code ..} segments and
 *       backslashes (zip-slip), even though this client never auto-extracts;</li>
 *   <li>caps entry count, per-entry size and total size while reading, so a
 *       zip bomb is stopped before it allocates;</li>
 *   <li>parses JSON with the strict parser;</li>
 *   <li>validates PNG headers for dimensions and frame divisibility;</li>
 *   <li>refuses packs that ship scripts, because the Lua sandbox is not
 *       implemented yet - refusing is safer than silently ignoring behaviour a
 *       creator thinks will run.</li>
 * </ul>
 */
public final class PackLoader {

    public static final String MANIFEST_NAME = "manifest.json";

    private PackLoader() {
    }

    /** Result of a load attempt. {@code pack} is null when validation failed. */
    public static final class Loaded {
        public final CloudPack pack;
        public final PackValidation validation;
        public final String source;

        Loaded(CloudPack pack, PackValidation validation, String source) {
            this.pack = pack;
            this.validation = validation;
            this.source = source;
        }

        public boolean ok() {
            return pack != null && validation.ok();
        }
    }

    public static Loaded load(byte[] archive, String source) {
        PackValidation validation = new PackValidation();
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();

        if (archive == null || archive.length == 0) {
            validation.error("Empty archive");
            return new Loaded(null, validation, source);
        }
        if (archive.length > PackValidation.MAX_TOTAL_BYTES) {
            validation.error("Archive exceeds " + PackValidation.MAX_TOTAL_BYTES + " bytes");
            return new Loaded(null, validation, source);
        }

        try {
            readEntries(archive, files, validation);
        } catch (IOException e) {
            validation.error("Unreadable archive: " + e.getMessage());
            return new Loaded(null, validation, source);
        }

        byte[] manifestBytes = files.get(MANIFEST_NAME);
        if (manifestBytes == null) {
            validation.error("Missing " + MANIFEST_NAME + " at archive root");
            return new Loaded(null, validation, source);
        }

        PackManifest manifest;
        try {
            String text = new String(manifestBytes, java.nio.charset.StandardCharsets.UTF_8);
            manifest = PackManifest.parse(Json.asObject(Json.parse(text)));
        } catch (Json.JsonException e) {
            validation.error("Malformed manifest.json: " + e.getMessage());
            return new Loaded(null, validation, source);
        } catch (PackFormatException e) {
            validation.error(e.getMessage());
            return new Loaded(null, validation, source);
        }

        validate(manifest, files, validation);
        if (!validation.ok()) {
            return new Loaded(null, validation, source);
        }
        return new Loaded(new CloudPack(manifest, files, source), validation, source);
    }

    private static void readEntries(byte[] archive, Map<String, byte[]> files,
                                    PackValidation validation) throws IOException {
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archive));
        try {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            int entries = 0;
            long total = 0L;
            Set<String> seen = new HashSet<String>();

            while ((entry = zis.getNextEntry()) != null) {
                String name = normalise(entry.getName());
                if (name == null) {
                    validation.error("Illegal archive path: " + entry.getName());
                    continue;
                }
                if (entry.isDirectory()) continue;
                if (name.isEmpty()) continue;
                if (++entries > PackValidation.MAX_ENTRIES) {
                    validation.error("Archive has more than " + PackValidation.MAX_ENTRIES + " entries");
                    return;
                }
                if (!seen.add(name)) {
                    validation.warn("Duplicate entry ignored: " + name);
                    continue;
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                long size = 0L;
                int read;
                boolean tooBig = false;
                while ((read = zis.read(buffer)) > 0) {
                    size += read;
                    if (size > PackValidation.MAX_ENTRY_BYTES) {
                        validation.error("Entry exceeds " + PackValidation.MAX_ENTRY_BYTES
                                + " bytes: " + name);
                        tooBig = true;
                        break;
                    }
                    out.write(buffer, 0, read);
                }
                if (tooBig) continue;
                total += size;
                if (total > PackValidation.MAX_TOTAL_BYTES) {
                    validation.error("Uncompressed contents exceed "
                            + PackValidation.MAX_TOTAL_BYTES + " bytes");
                    return;
                }
                files.put(name, out.toByteArray());
            }
        } finally {
            zis.close();
        }
    }

    /** Returns a safe relative path, or null when the entry must be rejected. */
    static String normalise(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/').trim();
        if (name.isEmpty()) return null;
        if (name.startsWith("/")) return null;
        if (name.length() > 1 && name.charAt(1) == ':') return null;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) < 0x20) return null;
        }
        while (name.startsWith("./")) name = name.substring(2);
        if (name.equals("..") || name.startsWith("../") || name.contains("/../")) return null;
        if (name.endsWith("/")) return null;
        return name;
    }

    private static void validate(PackManifest manifest, Map<String, byte[]> files,
                                 PackValidation v) {
        if (manifest.api != PackManifest.API_VERSION) {
            v.error("Unsupported pack API " + manifest.api + " (this client supports "
                    + PackManifest.API_VERSION + ")");
            return;
        }
        if (!manifest.id.matches("[a-z0-9][a-z0-9_.-]{0,63}")) {
            v.error("Invalid pack id '" + manifest.id + "'");
        }
        if (manifest.name.trim().isEmpty()) v.error("Pack name is empty");
        if (manifest.name.length() > 64) v.error("Pack name longer than 64 characters");
        if (!manifest.version.matches("\\d+\\.\\d+\\.\\d+([-+][A-Za-z0-9.]+)?")) {
            v.error("Version must be semver-like (e.g. 1.0.0), got '" + manifest.version + "'");
        }
        if (manifest.author.length() > 64) v.error("Author name longer than 64 characters");

        for (String path : files.keySet()) {
            if (path.startsWith("scripts/") || path.endsWith(".lua")) {
                v.error("Pack ships scripts (" + path + "); scripting is not enabled in API "
                        + PackManifest.API_VERSION);
            }
        }

        if (manifest.cosmetics.isEmpty()) {
            v.error("Pack declares no cosmetics");
        }
        if (manifest.cosmetics.size() > PackValidation.MAX_COSMETICS) {
            v.error("Pack declares more than " + PackValidation.MAX_COSMETICS + " cosmetics");
        }

        Set<String> ids = new HashSet<String>();
        for (CosmeticDefinition c : manifest.cosmetics) {
            if (!ids.add(c.id)) {
                v.error("Duplicate cosmetic id '" + c.id + "'");
            }
            validateCosmetic(c, files, v);
        }
    }

    private static void validateCosmetic(CosmeticDefinition c, Map<String, byte[]> files,
                                         PackValidation v) {
        if (c.texture == null || c.texture.isEmpty()) {
            v.error("Cosmetic '" + c.id + "' has no texture");
        } else if (!c.texture.toLowerCase().endsWith(".png")) {
            v.error("Cosmetic '" + c.id + "' texture must be a .png");
        } else {
            byte[] png = files.get(c.texture);
            if (png == null) {
                v.error("Cosmetic '" + c.id + "' texture not found: " + c.texture);
            } else {
                try {
                    PngHeader header = PngHeader.read(png);
                    if (header.width > PackValidation.MAX_TEXTURE_EDGE
                            || header.height > PackValidation.MAX_TEXTURE_EDGE) {
                        v.error("Cosmetic '" + c.id + "' texture is "
                                + header.width + "x" + header.height
                                + "; max edge is " + PackValidation.MAX_TEXTURE_EDGE);
                    }
                    if (c.animationFrames < 1
                            || c.animationFrames > PackValidation.MAX_ANIMATION_FRAMES) {
                        v.error("Cosmetic '" + c.id + "' has invalid frame count "
                                + c.animationFrames);
                    } else if (c.animationFrames > 1 && header.height % c.animationFrames != 0) {
                        v.error("Cosmetic '" + c.id + "' texture height " + header.height
                                + " is not divisible by " + c.animationFrames + " frames");
                    }
                    if (!header.hasAlpha()) {
                        v.warn("Cosmetic '" + c.id + "' texture has no alpha channel");
                    }
                } catch (PackFormatException e) {
                    v.error("Cosmetic '" + c.id + "': " + e.getMessage());
                }
            }
        }

        if (c.model != null) {
            String lower = c.model.toLowerCase();
            if (!lower.endsWith(".json") && !lower.endsWith(".obj")) {
                v.error("Cosmetic '" + c.id + "' model must be .json or .obj");
            } else if (!files.containsKey(c.model)) {
                v.error("Cosmetic '" + c.id + "' model not found: " + c.model);
            }
        }

        if (c.declaredTriangles > PackValidation.MAX_TRIANGLES) {
            v.error("Cosmetic '" + c.id + "' declares " + c.declaredTriangles
                    + " triangles; limit is " + PackValidation.MAX_TRIANGLES);
        }
        if (c.animationFps <= 0.0F || c.animationFps > 60.0F) {
            v.error("Cosmetic '" + c.id + "' animation fps must be within 0-60");
        }
        if (c.scale <= 0.05F || c.scale > 4.0F) {
            v.error("Cosmetic '" + c.id + "' scale must be within 0.05-4.0");
        }
    }

    /** Validates and returns just the messages; useful for the hub UI. */
    public static List<String> explain(byte[] archive) {
        return load(archive, "preview").validation.errors();
    }
}
