package net.cloudclient.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A validated pack held in memory. Files stay as byte arrays: cosmetics are
 * small, and keeping the archive off disk means an uninstall is a single
 * reference drop with no temp-file cleanup or partial-write states.
 */
public final class CloudPack {

    private final PackManifest manifest;
    private final Map<String, byte[]> files;
    private final String source;

    CloudPack(PackManifest manifest, Map<String, byte[]> files, String source) {
        this.manifest = manifest;
        this.files = Collections.unmodifiableMap(new LinkedHashMap<String, byte[]>(files));
        this.source = source;
    }

    public PackManifest manifest() {
        return manifest;
    }

    public String source() {
        return source;
    }

    public byte[] file(String path) {
        return files.get(path);
    }

    public boolean has(String path) {
        return files.containsKey(path);
    }

    public Map<String, byte[]> files() {
        return files;
    }

    /** Human-readable label for the hub and locker UI. */
    public String label() {
        return manifest.name + " " + manifest.version + " by " + manifest.author;
    }
}
