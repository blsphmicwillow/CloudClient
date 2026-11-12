package net.cloudclient.launcher;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.cloudclient.core.json.Json;
import net.cloudclient.launcher.auth.Account;
import net.cloudclient.launcher.auth.MicrosoftAuth;
import net.cloudclient.launcher.auth.MinecraftAuth;
import net.cloudclient.launcher.game.Downloader;
import net.cloudclient.launcher.game.LaunchSpec;
import net.cloudclient.launcher.net.Http;
import net.cloudclient.launcher.ui.LauncherWindow;

/**
 * Cloud Client's own launcher.
 *
 * <p>It does not wrap or drive Mojang's launcher: it authenticates, installs
 * 1.8.9 + Forge into its own directory, builds the classpath and spawns the game
 * itself. The same code backs both the window and the terminal:
 *
 * <pre>
 *   java -jar cloud-launcher.jar                 # the window (default)
 *   java -jar cloud-launcher.jar login           # device-code sign in
 *   java -jar cloud-launcher.jar offline Steve   # offline profile
 *   java -jar cloud-launcher.jar launch          # install + boot, no window
 *   java -jar cloud-launcher.jar --screenshot f  # render the window to a PNG
 * </pre>
 */
public final class LauncherMain {

    /** Receives human-readable progress from a launch or sign-in. */
    public interface Log {
        void line(String message);

        void progress(float fraction);
    }

    private static final String VERSION_MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FORGE_MAVEN =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String FORGE_VERSION = "1.8.9-11.15.1.2318-1.8.9";
    private static final String MC_VERSION = "1.8.9";

    private final File gameDir;
    private String clientId;
    private boolean dryRun;
    private boolean exitWhenDone = true;
    private Log log = new ConsoleLog();

    public LauncherMain(File gameDir) {
        this.gameDir = gameDir;
    }

    public File gameDir() {
        return gameDir;
    }

    public void setLog(Log log) {
        this.log = log == null ? new ConsoleLog() : log;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void setExitWhenDone(boolean exitWhenDone) {
        this.exitWhenDone = exitWhenDone;
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            if (System.getenv("CLOUDCLIENT_DEBUG") != null) e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        File gameDir = new File(System.getProperty("user.home"), ".cloudclient");
        String clientId = null;
        boolean dryRun = false;
        String command = null;
        List<String> rest = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            if ("--game-dir".equals(args[i]) && i + 1 < args.length) {
                gameDir = new File(args[++i]);
            } else if ("--client-id".equals(args[i]) && i + 1 < args.length) {
                clientId = args[++i];
            } else if ("--dry-run".equals(args[i])) {
                dryRun = true;
            } else if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                LauncherWindow.renderPreview(new File(args[++i]));
                return;
            } else if (command == null && ("--help".equals(args[i]) || "-h".equals(args[i]))) {
                command = args[i];
            } else if (command == null && !args[i].startsWith("-")) {
                command = args[i];
            } else {
                rest.add(args[i]);
            }
        }

        if ("--help".equals(command) || "-h".equals(command)) {
            usage();
            return;
        }

        LauncherMain launcher = new LauncherMain(gameDir);
        launcher.clientId = clientId;
        launcher.dryRun = dryRun;

        if (command == null) {
            if (GraphicsEnvironment.isHeadless()) {
                usage();
                return;
            }
            LauncherWindow.show(gameDir);
        } else if ("login".equals(command)) {
            launcher.signIn(clientId);
        } else if ("offline".equals(command)) {
            String name = rest.isEmpty() ? System.getProperty("user.name", "Player") : rest.get(0);
            launcher.saveAccount(Account.offline(name));
            launcher.log.line("Using offline profile " + name
                    + " (single-player and offline-mode servers only).");
        } else if ("launch".equals(command)) {
            launcher.launchGame();
        } else if ("assets".equals(command)) {
            launcher.downloadAssetsOnly();
        } else {
            usage();
        }
    }

    private static void usage() {
        System.out.println("Cloud Client launcher\n");
        System.out.println("  (no arguments)           open the launcher window");
        System.out.println("  login [--client-id ID]   sign in with a Microsoft account");
        System.out.println("  offline <name>           create an offline profile");
        System.out.println("  launch [--dry-run]       install 1.8.9 + Forge if needed, then boot");
        System.out.println("  assets                   download/repair the 1.8.9 asset objects");
        System.out.println("  --screenshot PATH        render the window to a PNG\n");
        System.out.println("Options: --game-dir DIR (default ~/.cloudclient)");
        System.out.println("Forge: " + FORGE_VERSION);
    }

    // ------------------------------------------------------------------- auth

    /** Device-code sign in. Blocking; call from a worker thread. */
    public Account signIn(String clientIdOverride) throws IOException {
        String id = MicrosoftAuth.clientId(clientIdOverride != null ? clientIdOverride : clientId);
        MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode(id);
        log.line("Open " + code.verificationUri + " and enter " + code.userCode);
        log.line("Waiting for sign-in...");
        log.progress(0.2F);
        String token = MicrosoftAuth.pollForToken(id, code);
        Account account = MinecraftAuth.login(token);
        saveAccount(account);
        log.line("Signed in as " + account);
        log.progress(1.0F);
        return account;
    }

    private File accountFile() {
        return new File(gameDir, "account.json");
    }

    public void saveAccount(Account account) throws IOException {
        if (!gameDir.isDirectory() && !gameDir.mkdirs()) {
            throw new IOException("Cannot create " + gameDir);
        }
        Map<String, Object> json = new java.util.LinkedHashMap<String, Object>();
        json.put("username", account.username);
        json.put("uuid", account.uuid);
        json.put("accessToken", account.accessToken);
        json.put("userType", account.userType);
        json.put("offline", Boolean.valueOf(account.offline));

        File target = accountFile();
        FileOutputStream out = new FileOutputStream(target);
        try {
            out.write(Json.writePretty(json).getBytes("UTF-8"));
        } finally {
            out.close();
        }
        restrictPermissions(target);
    }

    public Account loadAccount() throws IOException {
        File file = accountFile();
        if (!file.isFile()) {
            throw new IOException("No saved account. Run `login` or `offline <name>` first.");
        }
        String text = readFile(file);
        Map<String, Object> json = Json.asObject(Json.parse(text));
        return new Account(
                Json.getString(json, "username", "Player"),
                Json.getString(json, "uuid", ""),
                Json.getString(json, "accessToken", "0"),
                null,
                Json.getString(json, "userType", "legacy"),
                Json.getBool(json, "offline", true));
    }

    private static void restrictPermissions(File file) {
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> permissions =
                    java.util.EnumSet.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), permissions);
        } catch (Exception ignored) {
            // Non-POSIX filesystems (Windows) have no equivalent; tokens there
            // rely on the user profile's ACL.
        }
    }

    // ----------------------------------------------------------------- launch

    /** Installs what is missing and starts the game. Returns when it exits. */
    public void launchGame() throws Exception {
        Account account = loadAccount();
        log.line("Account: " + account);

        File versionsDir = new File(gameDir, "versions");
        File librariesDir = new File(gameDir, "libraries");
        File assetsDir = new File(gameDir, "assets");
        File nativesDir = new File(gameDir, "natives");

        log.progress(0.03F);
        log.line("Reading the version manifest...");
        Map<String, Object> versionJson = fetchJson(VERSION_MANIFEST);
        String versionUrl = null;
        for (Object entry : Json.children(versionJson, "versions")) {
            Map<String, Object> version = Json.asObject(entry);
            if (MC_VERSION.equals(Json.getString(version, "id", ""))) {
                versionUrl = Json.getString(version, "url", null);
                break;
            }
        }
        if (versionUrl == null) throw new IOException("1.8.9 not in the version manifest");

        File baseJsonFile = new File(new File(versionsDir, MC_VERSION), MC_VERSION + ".json");
        if (!dryRun && (!baseJsonFile.isFile() || baseJsonFile.length() == 0)) {
            log.line("Downloading 1.8.9 metadata...");
            Downloader.fetch(versionUrl, baseJsonFile, null);
        }
        Map<String, Object> baseJson = baseJsonFile.isFile()
                ? Json.asObject(Json.parse(readFile(baseJsonFile)))
                : Json.asObject(Json.parse(Http.get(versionUrl).body));

        log.progress(0.1F);
        File forgeJsonFile = dryRun ? findForgeJson(versionsDir) : installForge(baseJsonFile);
        Map<String, Object> forgeJson = null;
        String versionName = MC_VERSION;
        if (forgeJsonFile != null && forgeJsonFile.isFile()) {
            forgeJson = Json.asObject(Json.parse(readFile(forgeJsonFile)));
            versionName = stripJson(forgeJsonFile.getName());
        }

        LaunchSpec spec = LaunchSpec.from(forgeJson != null ? forgeJson : baseJson, baseJson);

        File clientJar = new File(new File(versionsDir, MC_VERSION), MC_VERSION + ".jar");
        if (!dryRun) {
            Map<String, Object> downloads = Json.child(baseJson, "downloads");
            Map<String, Object> client = downloads == null ? null : Json.child(downloads, "client");
            if (client != null && (!clientJar.isFile() || clientJar.length() == 0)) {
                log.line("Downloading the 1.8.9 client...");
                Downloader.fetch(Json.getString(client, "url", ""), clientJar,
                        Json.getString(client, "sha1", null));
            }
            log.progress(0.25F);
            downloadLibraries(spec, librariesDir, nativesDir);
            downloadAssets(spec, assetsDir);
            installMod();
        }

        List<String> command = buildCommand(spec, account, versionName, clientJar,
                librariesDir, nativesDir, assetsDir);
        if (dryRun) {
            log.line("Resolved launch command (" + command.size() + " args):");
            StringBuilder sb = new StringBuilder();
            for (String part : command) {
                String safe = part.equals(account.accessToken) ? "<access-token>" : part;
                if (safe.indexOf(' ') >= 0) sb.append('"').append(safe).append("\" ");
                else sb.append(safe).append(' ');
            }
            log.line(sb.toString().trim());
            return;
        }

        log.progress(1.0F);
        log.line("Launching Minecraft 1.8.9 with Forge...");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(gameDir);
        builder.inheritIO();
        Process process = builder.start();
        int status = process.waitFor();
        log.line("Minecraft exited with status " + status + ".");
        if (exitWhenDone) System.exit(status);
    }

    private List<String> buildCommand(LaunchSpec spec, Account account, String versionName,
                                      File clientJar, File librariesDir, File nativesDir,
                                      File assetsDir) {
        List<String> command = new ArrayList<String>();
        command.add(javaBinary());
        command.add("-Xmx2G");
        command.add("-XX:+UseG1GC");
        command.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        command.add("-Dfml.ignoreInvalidMinecraftCertificates=true");

        StringBuilder classpath = new StringBuilder();
        classpath.append(clientJar.getAbsolutePath());
        for (String path : spec.classpath()) {
            classpath.append(File.pathSeparator).append(new File(librariesDir, path).getAbsolutePath());
        }
        command.add("-cp");
        command.add(classpath.toString());
        command.add(spec.mainClass);

        Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        values.put("auth_player_name", account.username);
        values.put("version_name", versionName);
        values.put("game_directory", gameDir.getAbsolutePath());
        values.put("assets_root", assetsDir.getAbsolutePath());
        values.put("assets_index_name", spec.assetIndex == null ? "1.8" : spec.assetIndex);
        values.put("auth_uuid", account.uuid);
        values.put("auth_access_token", account.accessToken);
        values.put("user_type", account.userType);
        values.put("user_properties", "{}");
        values.put("clientid", "");
        values.put("auth_xuid", "");

        for (String argument : LaunchSpec.splitArguments(spec.minecraftArguments)) {
            command.add(LaunchSpec.substitute(argument, values));
        }
        return command;
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        String exe = LaunchSpec.osName().equals("windows") ? "java.exe" : "java";
        return new File(new File(home, "bin"), exe).getAbsolutePath();
    }

    // -------------------------------------------------------------- game files

    private void downloadLibraries(LaunchSpec spec, File librariesDir, File nativesDir)
            throws IOException {
        int index = 0;
        for (LaunchSpec.Library library : spec.libraries) {
            File target = new File(librariesDir, library.path);
            Downloader.fetch(library.url, target, null);
            if (library.nativeJar) extractNatives(target, nativesDir);
            index++;
            if (index % 4 == 0) {
                log.progress(0.25F + 0.3F * (index / (float) spec.libraries.size()));
            }
        }
        log.progress(0.55F);
    }

    /**
     * Downloads the asset index and every object it references.
     *
     * <p>A standalone launcher cannot borrow Mojang's asset cache, and 1.8.9
     * still reads sounds and language files from {@code assets/objects}.
     * Objects are content-addressed: an existing file with the expected size is
     * trusted, and anything downloaded is verified against its SHA-1.
     */
    private void downloadAssets(LaunchSpec spec, File assetsDir) throws IOException {
        if (spec.assetsUrl == null || spec.assetsUrl.isEmpty()) {
            log.line("No asset index in the version metadata; skipping assets.");
            return;
        }
        File indexFile = new File(new File(assetsDir, "indexes"), spec.assetIndex + ".json");
        Downloader.fetch(spec.assetsUrl, indexFile, null);

        Map<String, Object> index = Json.asObject(Json.parse(readFile(indexFile)));
        Map<String, Object> objects = Json.child(index, "objects");
        if (objects == null || objects.isEmpty()) {
            log.line("Asset index is empty; nothing to download.");
            return;
        }

        log.line("Checking " + objects.size() + " game assets...");
        File objectsDir = new File(assetsDir, "objects");
        int checked = 0;
        int downloaded = 0;
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Map<String, Object> object = Json.asObject(entry.getValue());
            String hash = Json.getString(object, "hash", null);
            if (hash == null || hash.length() < 4) continue;
            int size = Json.getInt(object, "size", -1);

            String prefix = hash.substring(0, 2);
            File target = new File(new File(objectsDir, prefix), hash);
            if (!target.isFile() || (size >= 0 && target.length() != size)) {
                Downloader.fetch("https://resources.download.minecraft.net/" + prefix
                        + "/" + hash, target, hash);
                downloaded++;
            }

            checked++;
            if (checked % 200 == 0) {
                log.progress(0.55F + 0.30F * (checked / (float) objects.size()));
            }
        }
        log.line("Assets ready: " + objects.size() + " objects, " + downloaded
                + " downloaded.");
        log.progress(0.9F);
    }

    /** Downloads the asset index and objects only, for repairing an install. */
    public void downloadAssetsOnly() throws IOException {
        File versionsDir = new File(gameDir, "versions");
        Map<String, Object> manifest = fetchJson(VERSION_MANIFEST);
        String versionUrl = null;
        for (Object entry : Json.children(manifest, "versions")) {
            Map<String, Object> version = Json.asObject(entry);
            if (MC_VERSION.equals(Json.getString(version, "id", ""))) {
                versionUrl = Json.getString(version, "url", null);
                break;
            }
        }
        if (versionUrl == null) throw new IOException("1.8.9 not in the version manifest");

        File baseJsonFile = new File(new File(versionsDir, MC_VERSION), MC_VERSION + ".json");
        if (!baseJsonFile.isFile() || baseJsonFile.length() == 0) {
            Downloader.fetch(versionUrl, baseJsonFile, null);
        }
        Map<String, Object> baseJson = Json.asObject(Json.parse(readFile(baseJsonFile)));
        LaunchSpec spec = LaunchSpec.from(baseJson, baseJson);
        downloadAssets(spec, new File(gameDir, "assets"));
    }

    private void extractNatives(File jar, File nativesDir) throws IOException {
        if (!nativesDir.isDirectory() && !nativesDir.mkdirs()) {
            throw new IOException("Cannot create " + nativesDir);
        }
        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar);
        try {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (!(name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib"))) {
                    continue;
                }
                File out = new File(nativesDir, new File(name).getName());
                InputStream in = zip.getInputStream(entry);
                FileOutputStream stream = new FileOutputStream(out);
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) stream.write(buffer, 0, read);
                } finally {
                    try {
                        in.close();
                    } finally {
                        stream.close();
                    }
                }
            }
        } finally {
            zip.close();
        }
    }

    /**
     * Runs the official Forge installer, then locates the generated version JSON.
     * Scanning for the result is deliberate: Forge's version folder naming has
     * changed across releases, so trusting one hardcoded name is brittle.
     */
    private File installForge(File baseJsonFile) throws IOException {
        File existing = findForgeJson(new File(gameDir, "versions"));
        if (existing != null) return existing;

        File installer = new File(gameDir, "forge-installer.jar");
        String url = FORGE_MAVEN + FORGE_VERSION + "/forge-" + FORGE_VERSION + "-installer.jar";
        log.line("Downloading Forge " + FORGE_VERSION + "...");
        Downloader.fetch(url, installer, null);

        log.line("Installing Forge (this only happens once)...");
        List<String> command = new ArrayList<String>();
        command.add(javaBinary());
        command.add("-jar");
        command.add(installer.getAbsolutePath());
        command.add("--installClient");
        command.add(gameDir.getAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO();
        Process process = builder.start();
        try {
            int status = process.waitFor();
            if (status != 0) {
                throw new IOException("Forge installer exited with " + status
                        + ". Check that 1.8.9 is available in the launcher once, then retry.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while installing Forge", e);
        }

        File installed = findForgeJson(new File(gameDir, "versions"));
        if (installed == null) {
            throw new IOException("Forge installed but no version JSON was found under "
                    + new File(gameDir, "versions"));
        }
        return installed;
    }

    private static File findForgeJson(File versionsDir) {
        File[] children = versionsDir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().startsWith(MC_VERSION + "-forge")) {
                continue;
            }
            File json = new File(child, child.getName() + ".json");
            if (json.isFile()) return json;
        }
        return null;
    }

    /** Copies the built mod jar into mods/ so the client actually loads. */
    private void installMod() throws IOException {
        File mods = new File(gameDir, "mods");
        if (!mods.isDirectory() && !mods.mkdirs()) {
            throw new IOException("Cannot create " + mods);
        }
        File built = locateBuiltMod();
        if (built == null) {
            log.line("Note: no built cloud-client jar found next to the launcher; "
                    + "the game will start without the mod.");
            return;
        }
        File target = new File(mods, built.getName());
        copy(built, target);
        log.line("Installed " + target.getName());
    }

    private static File locateBuiltMod() {
        File[] candidates = {
                new File("build/libs"),
                new File("../build/libs"),
                new File("../../build/libs"),
                new File("cloud-client/build/libs"),
                new File("../cloud-client/build/libs")
        };
        for (File dir : candidates) {
            File[] jars = dir.listFiles();
            if (jars == null) continue;
            for (File jar : jars) {
                String name = jar.getName();
                if (name.startsWith("cloud-client-") && name.endsWith(".jar")
                        && !name.contains("-sources") && !name.contains("-dev")) {
                    return jar;
                }
            }
        }
        return null;
    }

    private static void copy(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } finally {
            try {
                in.close();
            } finally {
                out.close();
            }
        }
    }

    private static Map<String, Object> fetchJson(String url) throws IOException {
        Http.Response response = Http.get(url);
        if (!response.ok()) throw new IOException("HTTP " + response.status + " for " + url);
        return Json.asObject(Json.parse(response.body));
    }

    private static String readFile(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String stripJson(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    /** Default sink: prints to stdout so the CLI behaves like a normal tool. */
    private static final class ConsoleLog implements Log {
        private float last = -1.0F;

        @Override
        public void line(String message) {
            System.out.println(message);
        }

        @Override
        public void progress(float fraction) {
            int percent = Math.round(fraction * 100.0F);
            if (percent != Math.round(last * 100.0F)) {
                last = fraction;
                if (percent < 100) System.out.println("  " + percent + "%");
            }
        }
    }
}
