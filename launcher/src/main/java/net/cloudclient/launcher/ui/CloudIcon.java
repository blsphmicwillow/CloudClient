package net.cloudclient.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Painted artwork for the launcher: the cloud mark, the account badge and the
 * play button. Everything is drawn with Java2D so the launcher stays a single
 * dependency-free jar with no image assets to lose.
 */
public final class CloudIcon {

    public static final Color ACCENT = new Color(0x5AC8FA);
    public static final Color ACCENT_DARK = new Color(0x2E9BD6);
    public static final Color GREEN = new Color(0x3DD68C);
    public static final Color INK = new Color(0x0E1116);
    public static final Color PANEL = new Color(0x161B22);
    public static final Color PANEL_HI = new Color(0x1E2530);
    public static final Color TEXT = new Color(0xE8EDF3);
    public static final Color MUTED = new Color(0x8A94A6);

    private CloudIcon() {
    }

    public static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * The Cloud Client mark: a rounded app tile with a three-lobe cloud, a
     * highlight sweep and a small orbit spark. Designed in a 64-unit box so it
     * stays sharp from a 16px taskbar icon to a 1024px promotional render.
     */
    public static void logo(Graphics2D g, float x, float y, float size) {
        Graphics2D c = (Graphics2D) g.create();
        antialias(c);
        c.translate(x, y);
        float s = size / 64.0F;
        c.scale(s, s);

        // Tile with a deep vertical gradient.
        RoundRectangle2D.Float tile = new RoundRectangle2D.Float(0, 0, 64, 64, 19, 19);
        c.setPaint(new GradientPaint(0, 0, new Color(0x63D3FF), 0, 64,
                new Color(0x2A5FD8)));
        c.fill(tile);

        // Top-left sheen, then a darker foot so the tile reads as solid.
        c.setPaint(new RadialGradientPaint(20.0F, 6.0F, 54.0F,
                new float[]{0.0F, 1.0F},
                new Color[]{new Color(255, 255, 255, 90), new Color(255, 255, 255, 0)}));
        c.fill(tile);
        c.setPaint(new GradientPaint(0, 40, new Color(0, 0, 0, 0), 0, 64,
                new Color(6, 22, 60, 90)));
        c.fill(tile);

        // Cloud: union of the lobes. A plain Path2D punches holes where subpaths
        // wind in opposite directions, so the shapes are merged as an Area.
        java.awt.geom.Area cloud = new java.awt.geom.Area(
                new Ellipse2D.Float(11.0F, 26.0F, 21.0F, 21.0F));
        cloud.add(new java.awt.geom.Area(new Ellipse2D.Float(21.0F, 17.0F, 24.0F, 24.0F)));
        cloud.add(new java.awt.geom.Area(new Ellipse2D.Float(33.0F, 25.0F, 19.0F, 19.0F)));
        cloud.add(new java.awt.geom.Area(
                new RoundRectangle2D.Float(11.0F, 33.0F, 41.0F, 14.0F, 14.0F, 14.0F)));

        c.setColor(new Color(8, 30, 70, 70));
        c.translate(0.0F, 1.5F);
        c.fill(cloud);
        c.translate(0.0F, -1.5F);

        c.setPaint(new GradientPaint(0, 16, Color.WHITE, 0, 50, new Color(0xDCEFFF)));
        c.fill(cloud);

        // Small spark at the cloud's shoulder, tying the mark to the "cloud" name.
        c.setColor(new Color(0xFFE9A8));
        Path2D.Float spark = new Path2D.Float();
        spark.moveTo(47.0F, 16.0F);
        spark.lineTo(49.2F, 21.4F);
        spark.lineTo(54.6F, 23.6F);
        spark.lineTo(49.2F, 25.8F);
        spark.lineTo(47.0F, 31.2F);
        spark.lineTo(44.8F, 25.8F);
        spark.lineTo(39.4F, 23.6F);
        spark.lineTo(44.8F, 21.4F);
        spark.closePath();
        c.fill(spark);

        c.dispose();
    }

    /** Square icon image for the window/taskbar. */
    public static java.awt.image.BufferedImage icon(int size) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            logo(g, 0.0F, 0.0F, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * A stylised cloud built from three lobes over a rounded base. Drawn rather
     * than font-based so it renders identically with no glyph fallback.
     */
    public static void cloud(Graphics2D g, float x, float y, float size, Color colour) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        copy.translate(x, y);
        float s = size / 32.0F;
        copy.scale(s, s);

        java.awt.geom.Area shape = new java.awt.geom.Area(
                new Ellipse2D.Float(3.0F, 12.0F, 13.0F, 13.0F));
        shape.add(new java.awt.geom.Area(new Ellipse2D.Float(12.0F, 7.0F, 15.0F, 15.0F)));
        shape.add(new java.awt.geom.Area(new Ellipse2D.Float(20.0F, 12.0F, 10.0F, 11.0F)));
        shape.add(new java.awt.geom.Area(
                new RoundRectangle2D.Float(3.0F, 17.0F, 27.0F, 9.0F, 9.0F, 9.0F)));

        copy.setColor(colour);
        copy.fill(shape);
        copy.dispose();
    }

    /** Cloud outline used at small sizes, where a filled mark turns to mush. */
    public static void cloudOutline(Graphics2D g, float x, float y, float size, Color colour) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        copy.translate(x, y);
        float s = size / 32.0F;
        copy.scale(s, s);
        copy.setColor(colour);
        copy.setStroke(new BasicStroke(2.6F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(6.0F, 26.0F);
        shape.curveTo(2.0F, 26.0F, 1.0F, 20.0F, 5.0F, 18.0F);
        shape.curveTo(5.0F, 11.0F, 14.0F, 9.0F, 16.0F, 15.0F);
        shape.curveTo(20.0F, 9.0F, 29.0F, 12.0F, 28.0F, 19.0F);
        shape.curveTo(31.0F, 20.0F, 31.0F, 26.0F, 27.0F, 26.0F);
        shape.closePath();
        copy.draw(shape);
        copy.dispose();
    }

    /** Rounded chip: the account name sits on this, with the badge inside it. */
    public static void chip(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        copy.setColor(fill);
        copy.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        copy.setColor(border);
        copy.setStroke(new BasicStroke(1.0F));
        copy.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F,
                h, h));
        copy.dispose();
    }

    /** The cloud badge shown next to the account name. */
    public static void accountBadge(Graphics2D g, int x, int y, int size, boolean online) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        copy.setColor(online ? new Color(0x1B3A52) : new Color(0x222A36));
        copy.fill(new Ellipse2D.Float(x, y, size, size));
        copy.setColor(online ? ACCENT : MUTED);
        copy.setStroke(new BasicStroke(1.2F));
        copy.draw(new Ellipse2D.Float(x + 0.5F, y + 0.5F, size - 1.0F, size - 1.0F));
        cloud(copy, x + size * 0.18F, y + size * 0.22F, size * 0.64F,
                online ? ACCENT : MUTED);
        copy.dispose();
    }

    public static void playButton(Graphics2D g, int x, int y, int w, int h,
                                  boolean hover, boolean enabled) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, w, h, 10.0F, 10.0F);
        if (!enabled) {
            copy.setColor(new Color(0x232A34));
            copy.fill(shape);
        } else {
            copy.setPaint(new GradientPaint(x, y, hover ? new Color(0x7BD8FF) : ACCENT,
                    x, y + h, hover ? ACCENT : ACCENT_DARK));
            copy.fill(shape);
        }
        copy.dispose();
    }

    public static void progress(Graphics2D g, int x, int y, int w, int h, float fraction) {
        Graphics2D copy = (Graphics2D) g.create();
        antialias(copy);
        copy.setColor(new Color(0x1F2733));
        copy.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        float clamped = Math.max(0.0F, Math.min(1.0F, fraction));
        if (clamped > 0.0F) {
            copy.setColor(ACCENT);
            copy.fill(new RoundRectangle2D.Float(x, y, Math.max(h, w * clamped), h, h, h));
        }
        copy.dispose();
    }
}
