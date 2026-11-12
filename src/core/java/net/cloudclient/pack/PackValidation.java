package net.cloudclient.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of validating a pack archive. Errors block install; warnings do not. */
public final class PackValidation {

    // Hard limits. These are deliberately generous for real cosmetic packs and
    // strict enough that a malicious archive cannot exhaust memory or VRAM.
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_ENTRY_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_TOTAL_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_COSMETICS = 64;
    public static final int MAX_TRIANGLES = 20000;
    public static final int MAX_TEXTURE_EDGE = 2048;
    public static final int MAX_ANIMATION_FRAMES = 64;

    private final List<String> errors = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();

    public void error(String message) {
        errors.add(message);
    }

    public void warn(String message) {
        warnings.add(message);
    }

    public boolean ok() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public String summary() {
        if (ok() && warnings.isEmpty()) return "valid";
        StringBuilder sb = new StringBuilder();
        if (!ok()) {
            sb.append(errors.size()).append(" error(s)");
            for (String e : errors) sb.append("\n  - ").append(e);
        } else {
            sb.append("valid with warnings");
        }
        for (String w : warnings) sb.append("\n  ! ").append(w);
        return sb.toString();
    }
}
