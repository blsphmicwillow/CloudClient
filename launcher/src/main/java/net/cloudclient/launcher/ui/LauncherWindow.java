package net.cloudclient.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.cloudclient.launcher.LauncherMain;
import net.cloudclient.launcher.auth.Account;

/**
 * The Cloud Client launcher window.
 *
 * <p>This is the client's own front end: it is not a wrapper around the vanilla
 * launcher and never asks Minecraft's launcher to start the game. It signs in,
 * installs 1.8.9 + Forge into its own directory, and spawns the game itself.
 *
 * <p>Deliberately plain Swing: the launcher has to build with nothing but a JDK,
 * so there are no UI dependencies and no image files. The cloud mark, the
 * account badge and the buttons are all painted in {@link CloudIcon}.
 */
public final class LauncherWindow {

    private static final int WIDTH = 920;
    private static final int HEIGHT = 560;

    private final LauncherMain launcher;
    private final CloudPanel panel;
    private JFrame frame;

    public LauncherWindow(File gameDir) {
        this.launcher = new LauncherMain(gameDir);
        this.panel = new CloudPanel();
        launcher.setExitWhenDone(false);
        launcher.setLog(panel);
    }

    public static void show(final File gameDir) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    LauncherWindow window = new LauncherWindow(gameDir);
                    window.open();
                } catch (Throwable t) {
                    System.err.println("Could not open the launcher window: " + t);
                    t.printStackTrace();
                }
            }
        });
    }

    /** Headless render of the full window, used for review and documentation. */
    public static void renderPreview(File out) throws IOException {
        CloudPanel panel = new LauncherWindow(new File(System.getProperty("user.home"),
                ".cloudclient")).panel;
        panel.previewMode = true;
        panel.setSize(WIDTH, HEIGHT);
        panel.doLayout();

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    private void open() {
        frame = new JFrame("Cloud Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(panel);
        frame.setSize(WIDTH, HEIGHT);
        frame.setMinimumSize(new java.awt.Dimension(760, 480));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.refreshAccount();
    }

    // ------------------------------------------------------------------- panel

    private final class CloudPanel extends JPanel implements LauncherMain.Log {
        private static final long serialVersionUID = 1L;

        private final JTextArea console = new JTextArea();
        private final FlatButton play = new FlatButton("PLAY", true);
        private final FlatButton signIn = new FlatButton("Sign in with Microsoft", false);
        private final FlatButton offline = new FlatButton("Offline profile", false);

        private Account account;
        private String status = "Checking your install...";
        private float progress;
        private boolean busy;
        private boolean previewMode;
        private String clientId;

        CloudPanel() {
            setLayout(null);
            setBackground(CloudIcon.INK);

            console.setEditable(false);
            console.setLineWrap(true);
            console.setWrapStyleWord(true);
            console.setFont(new Font("Monospaced", Font.PLAIN, 12));
            console.setForeground(new Color(0xB8C2D0));
            console.setBackground(new Color(0x11161D));
            console.setCaretColor(CloudIcon.ACCENT);

            JScrollPane scroll = new JScrollPane(console);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setBackground(new Color(0x11161D));
            add(scroll);
            scrollRef = scroll;

            add(play);
            add(signIn);
            add(offline);

            play.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    startGame();
                }
            });
            signIn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    signIn();
                }
            });
            offline.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    useOffline();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (chipBounds().contains(e.getPoint())) signIn();
                }
            });
            setCursor(Cursor.getDefaultCursor());
            loadClientId();
        }

        private JScrollPane scrollRef;

        @Override
        public void doLayout() {
            play.setBounds(44, 268, 210, 50);
            signIn.setBounds(268, 268, 214, 50);
            offline.setBounds(496, 268, 168, 50);
            if (scrollRef != null) scrollRef.setBounds(44, 348, WIDTH - 88, 168);
        }

        // --------------------------------------------------------- painting

        @Override
        protected void paintComponent(Graphics graphics) {
            // Fill the panel background first; painting before this call would be
            // wiped by the opaque background fill.
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            CloudIcon.antialias(g);

            g.setPaint(new GradientPaint(0, 0, new Color(0x0B0F14), 0, HEIGHT,
                    new Color(0x121924)));
            g.fillRect(0, 0, getWidth(), getHeight());

            float[] fractions = {0.0F, 1.0F};
            Color[] colours = {new Color(0x2A7FB8, true), new Color(0x2A7FB8, false)};
            g.setPaint(new RadialGradientPaint(180.0F, 60.0F, 420.0F, fractions, colours));
            g.fillRect(0, 0, getWidth(), getHeight());

            // Watermark: a large, very faint cloud behind the hero card.
            Graphics2D faint = (Graphics2D) g.create();
            faint.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.05F));
            CloudIcon.cloud(faint, getWidth() - 290.0F, 150.0F, 300.0F, Color.WHITE);
            faint.dispose();

            drawHeader(g);
            drawAccount(g);
            drawHero(g);
            drawFooter(g);
            g.dispose();
        }

        private void drawHeader(Graphics2D g) {
            CloudIcon.cloud(g, 40.0F, 26.0F, 44.0F, CloudIcon.ACCENT);
            CloudIcon.cloud(g, 46.0F, 32.0F, 32.0F, new Color(0xEAF6FF));

            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            g.setColor(CloudIcon.TEXT);
            g.drawString("CLOUD CLIENT", 100, 50);

            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(CloudIcon.MUTED);
            g.drawString("Minecraft 1.8.9  ·  Forge  ·  own launcher", 100, 68);
        }

        private java.awt.Rectangle chipBounds() {
            int width = 268;
            return new java.awt.Rectangle(getWidth() - width - 40, 26, width, 46);
        }

        /** Account chip: cloud badge immediately left of the nickname. */
        private void drawAccount(Graphics2D g) {
            java.awt.Rectangle chip = chipBounds();
            String name = account == null ? "Not signed in" : account.username;
            String sub = account == null ? "Click to connect an account"
                    : (account.offline ? "Offline profile" : "Microsoft account");

            int textWidth = Math.max(g.getFontMetrics(new Font("SansSerif", Font.BOLD, 14))
                    .stringWidth(name), g.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11))
                    .stringWidth(sub));
            int width = Math.min(chip.width, Math.max(150, 62 + textWidth + 18));
            chip.width = width;

            CloudIcon.chip(g, chip.x, chip.y, chip.width, chip.height,
                    new Color(0x1A212B), new Color(0x2B3542));

            boolean online = account != null && !account.offline;
            CloudIcon.accountBadge(g, chip.x + 9, chip.y + 9, 28, online);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(account == null ? CloudIcon.MUTED : CloudIcon.TEXT);
            g.drawString(name, chip.x + 46, chip.y + 21);

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(online ? CloudIcon.GREEN : CloudIcon.MUTED);
            g.drawString(sub, chip.x + 46, chip.y + 36);
        }

        private void drawHero(Graphics2D g) {
            int x = 28;
            int y = 108;
            int w = getWidth() - 56;
            int h = 214;

            g.setColor(new Color(0x990F141B, true));
            g.fill(new RoundRectangle2D.Float(x, y, w, h, 18.0F, 18.0F));
            g.setColor(new Color(0x2A3543));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F,
                    18.0F, 18.0F));

            g.setFont(new Font("SansSerif", Font.BOLD, 27));
            g.setColor(CloudIcon.TEXT);
            g.drawString("Ready to fly.", 44, 168);

            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(CloudIcon.MUTED);
            g.drawString(status, 44, 192);

            // Version + capability chips, drawn rather than component-per-chip.
            pill(g, 44, 210, "1.8.9", CloudIcon.ACCENT);
            pill(g, 100, 210, "Forge 11.15.1.2318", CloudIcon.ACCENT);
            pill(g, 250, 210, "no Mixin", CloudIcon.GREEN);

            if (busy) {
                CloudIcon.progress(g, 44, 306, getWidth() - 88, 6, progress);
            }
        }

        private void pill(Graphics2D g, int x, int y, String label, Color colour) {
            int width = g.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11))
                    .stringWidth(label) + 18;
            int height = 20;
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 26));
            g.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
            g.setColor(colour);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(label, x + 9, y + 14);
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 90));
            g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, width - 1.0F, height - 1.0F,
                    height, height));
        }

        private void drawFooter(Graphics2D g) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(0x5A6472));
            g.drawString("Cloud Client 0.1.0  ·  cosmetics are local to your game  ·  "
                    + "no packets, no reach, no hitbox changes", 44, getHeight() - 20);
        }

        // --------------------------------------------------------- behaviour

        void refreshAccount() {
            try {
                account = launcher.loadAccount();
            } catch (IOException e) {
                account = null;
            }
            play.setEnabled(!busy);
            repaint();
        }

        private void startGame() {
            if (busy) return;
            if (account == null) {
                useOffline();
                if (account == null) return;
            }
            busy = true;
            play.setEnabled(false);
            status = "Preparing Cloud Client...";
            progress = 0.02F;
            repaint();

            new Thread("cloudclient-launch") {
                @Override
                public void run() {
                    try {
                        launcher.launchGame();
                        status = "Game closed. Ready when you are.";
                    } catch (final Throwable t) {
                        line("error: " + messageOf(t));
                        status = "Launch failed — see the log below.";
                    } finally {
                        busy = false;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                play.setEnabled(true);
                                doLayout();
                                repaint();
                            }
                        });
                    }
                }
            }.start();
        }

        private void signIn() {
            if (busy) return;
            String id = JOptionPane.showInputDialog(this,
                    "Microsoft client id (Entra app registration with XboxLive.signin):\n"
                            + "Leave blank to use CLOUDCLIENT_MSA_CLIENT_ID.",
                    clientId == null ? "" : clientId);
            if (id == null) return;
            id = id.trim();
            if (!id.isEmpty()) saveClientId(id);

            final String useId = id.isEmpty() ? null : id;
            busy = true;
            status = "Waiting for Microsoft sign-in...";
            repaint();
            new Thread("cloudclient-login") {
                @Override
                public void run() {
                    try {
                        final Account signedIn = launcher.signIn(useId);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                account = signedIn;
                                status = "Signed in as " + signedIn.username + ".";
                                refreshAccount();
                            }
                        });
                    } catch (final Throwable t) {
                        line("error: " + messageOf(t));
                        final String hint = messageOf(t);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                status = "Sign-in failed.";
                                if (previewMode) return;
                                JOptionPane.showMessageDialog(CloudPanel.this, hint,
                                        "Cloud Client", JOptionPane.WARNING_MESSAGE);
                            }
                        });
                    } finally {
                        busy = false;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                play.setEnabled(true);
                                repaint();
                            }
                        });
                    }
                }
            }.start();
        }

        private void useOffline() {
            if (busy) return;
            String name = JOptionPane.showInputDialog(this,
                    "Offline username (single-player and offline-mode servers only):",
                    account == null ? "Player" : account.username);
            if (name == null) return;
            name = name.trim();
            if (name.length() < 3 || name.length() > 16) {
                JOptionPane.showMessageDialog(this,
                        "Username must be 3-16 characters.", "Cloud Client",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                launcher.saveAccount(Account.offline(name));
                status = "Offline profile ready.";
                refreshAccount();
            } catch (IOException e) {
                line("error: " + e.getMessage());
            }
        }

        private void loadClientId() {
            Properties properties = new Properties();
            File file = new File(launcher.gameDir(), "launcher.properties");
            if (!file.isFile()) return;
            FileInputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
                clientId = properties.getProperty("microsoft.client_id");
            } catch (IOException ignored) {
                // A missing or unreadable settings file just means no default id.
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // best effort
                    }
                }
            }
        }

        private void saveClientId(String id) {
            clientId = id;
            Properties properties = new Properties();
            properties.setProperty("microsoft.client_id", id);
            File file = new File(launcher.gameDir(), "launcher.properties");
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                properties.store(out, "Cloud Client launcher settings");
            } catch (IOException e) {
                line("warning: could not save the client id: " + e.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                        // best effort
                    }
                }
            }
        }

        private String messageOf(Throwable t) {
            String message = t.getMessage();
            return message == null || message.isEmpty() ? t.toString() : message;
        }

        // ------------------------------------------------- LauncherMain.Log

        @Override
        public void line(final String message) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    console.append(message);
                    console.append("\n");
                    console.setCaretPosition(console.getDocument().getLength());
                    status = message;
                    repaint();
                }
            });
        }

        @Override
        public void progress(final float fraction) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progress = fraction;
                    repaint();
                }
            });
        }
    }

    /** Flat, painted button so the window has no native-looking chrome. */
    private static final class FlatButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean primary;
        private boolean hover;

        FlatButton(String label, boolean primary) {
            super(label);
            this.primary = primary;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setForeground(primary ? new Color(0x0B1117) : CloudIcon.TEXT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            CloudIcon.antialias(g);
            int w = getWidth();
            int h = getHeight();

            if (primary) {
                CloudIcon.playButton(g, 0, 0, w, h, hover, isEnabled());
                g.setFont(getFont());
                g.setColor(isEnabled() ? new Color(0x08131C) : new Color(0x6B7686));
                int textWidth = g.getFontMetrics().stringWidth(getText());
                g.drawString(getText(), (w - textWidth) / 2, (h + 10) / 2 - 4);
            } else {
                g.setColor(hover ? new Color(0x232C38) : new Color(0x1A212B));
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, 10.0F, 10.0F));
                g.setColor(new Color(0x2E3948));
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, w - 1.0F, h - 1.0F,
                        10.0F, 10.0F));
                g.setColor(hover ? CloudIcon.TEXT : new Color(0xC7D2E0));
                g.setFont(getFont());
                int textWidth = g.getFontMetrics().stringWidth(getText());
                g.drawString(getText(), (w - textWidth) / 2, (h + 10) / 2 - 4);
            }
            g.dispose();
        }
    }
}
