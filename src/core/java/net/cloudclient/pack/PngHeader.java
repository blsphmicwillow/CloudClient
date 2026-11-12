package net.cloudclient.pack;

/**
 * Reads just enough PNG to validate a cosmetic texture before the client ever
 * hands it to the GPU.
 *
 * <p>Full decoding is not needed and would be an attack surface: dimensions and
 * the color-type byte are enough to reject oversized atlases and malformed
 * files up front.
 */
public final class PngHeader {

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    public final int width;
    public final int height;
    /** 0 = grayscale, 2 = RGB, 3 = palette, 4 = grayscale+alpha, 6 = RGBA. */
    public final int colorType;

    private PngHeader(int width, int height, int colorType) {
        this.width = width;
        this.height = height;
        this.colorType = colorType;
    }

    public static PngHeader read(byte[] data) throws PackFormatException {
        if (data == null || data.length < 33) {
            throw new PackFormatException("PNG too short (" + (data == null ? 0 : data.length) + " bytes)");
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) throw new PackFormatException("Not a PNG file");
        }
        // IHDR must be the first chunk: length(4) type(4) data...
        int chunkLength = readInt(data, 8);
        String type = new String(new char[]{
                (char) data[12], (char) data[13], (char) data[14], (char) data[15]});
        if (chunkLength != 13 || !"IHDR".equals(type)) {
            throw new PackFormatException("Malformed PNG: missing IHDR");
        }
        int width = readInt(data, 16);
        int height = readInt(data, 20);
        int colorType = data[25] & 0xFF;
        if (width <= 0 || height <= 0) {
            throw new PackFormatException("Malformed PNG: non-positive dimensions");
        }
        return new PngHeader(width, height, colorType);
    }

    public boolean hasAlpha() {
        return colorType == 4 || colorType == 6 || colorType == 3;
    }

    private static int readInt(byte[] d, int offset) {
        return ((d[offset] & 0xFF) << 24)
                | ((d[offset + 1] & 0xFF) << 16)
                | ((d[offset + 2] & 0xFF) << 8)
                | (d[offset + 3] & 0xFF);
    }
}
