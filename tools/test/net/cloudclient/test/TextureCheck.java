package net.cloudclient.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.cloudclient.pack.PngHeader;

/**
 * Verifies the generated cosmetic textures are real PNGs and that the cape sheet
 * satisfies the animation rule enforced by {@code PackLoader}: the sheet height
 * must divide evenly by the frame count.
 *
 * <p>Run from the project root: {@code java -cp <out> ...TextureCheck}
 */
public final class TextureCheck {

    private static final String[] NAMES = {
            "cape_cloud.png",
            "wings_ember.png",
            "halo_gold.png",
            "horns_obsidian.png",
            "pet_spark.png"
    };

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get("src", "main", "resources", "assets", "cloudclient",
                "textures", "cosmetics");
        int failures = 0;

        for (String name : NAMES) {
            Path file = dir.resolve(name);
            if (!Files.isRegularFile(file)) {
                System.out.println("MISSING " + name);
                failures++;
                continue;
            }
            byte[] bytes = Files.readAllBytes(file);
            PngHeader header = PngHeader.read(bytes);
            boolean alpha = header.hasAlpha();
            System.out.printf("%-22s %4dx%-4d alpha=%s %d bytes%n",
                    name, header.width, header.height, alpha, bytes.length);
            if (header.width > 2048 || header.height > 2048) {
                System.out.println("  ! exceeds texture edge limit");
                failures++;
            }
            if (!alpha) {
                System.out.println("  ! expected an alpha channel");
                failures++;
            }
        }

        Path cape = dir.resolve("cape_cloud.png");
        if (Files.isRegularFile(cape)) {
            PngHeader header = PngHeader.read(Files.readAllBytes(cape));
            int frames = 4;
            if (header.height % frames != 0) {
                System.out.println("cape_cloud.png height " + header.height
                        + " not divisible by " + frames);
                failures++;
            } else {
                System.out.println("cape frames ok: " + frames + " x "
                        + (header.height / frames) + "px");
            }
        }

        if (failures > 0) {
            System.out.println("texture check FAILED (" + failures + ")");
            System.exit(1);
        }
        System.out.println("texture check passed");
    }

    private TextureCheck() {
    }
}
