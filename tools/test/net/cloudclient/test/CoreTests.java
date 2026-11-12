package net.cloudclient.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.cloudclient.core.Event;
import net.cloudclient.core.EventBus;
import net.cloudclient.core.Log;
import net.cloudclient.core.Module;
import net.cloudclient.core.ModuleContext;
import net.cloudclient.core.ModuleManager;
import net.cloudclient.core.config.CloudConfig;
import net.cloudclient.core.json.Json;
import net.cloudclient.cosmetic.AnchorSolver;
import net.cloudclient.cosmetic.AttachmentPoint;
import net.cloudclient.cosmetic.CosmeticProfile;
import net.cloudclient.cosmetic.CosmeticType;
import net.cloudclient.cosmetic.cape.CapeParams;
import net.cloudclient.cosmetic.cape.CapeSimulation;
import net.cloudclient.cosmetic.cape.CapeState;
import net.cloudclient.cosmetic.halo.Halo;
import net.cloudclient.cosmetic.horns.HornGeometry;
import net.cloudclient.cosmetic.horns.HornSet;
import net.cloudclient.cosmetic.pet.Pet;
import net.cloudclient.cosmetic.pet.PetFlock;
import net.cloudclient.cosmetic.pet.PetType;
import net.cloudclient.cosmetic.render.Lod;
import net.cloudclient.cosmetic.wings.WingRig;
import net.cloudclient.cosmetic.wings.WingState;
import net.cloudclient.math.FastMath;
import net.cloudclient.math.Mat4;
import net.cloudclient.math.Vec3;
import net.cloudclient.pack.CloudPack;
import net.cloudclient.pack.PackLoader;
import net.cloudclient.pack.PackRegistry;
import net.cloudclient.pack.PackValidation;

/**
 * Standalone test harness (no JUnit, so it runs against a bare JDK).
 *
 * <p>Run with {@code java -cp <out> net.cloudclient.test.CoreTests}. Exits
 * non-zero on failure so CI and the shell stay honest.
 */
public final class CoreTests {

    private static int passed;
    private static final List<String> failures = new ArrayList<String>();
    private static String scope = "";

    public static void main(String[] args) throws Exception {
        eventBusTests();
        jsonTests();
        configTests();
        moduleTests();
        anchorTests();
        fastMathTests();
        matrixTests();
        capeTests();
        wingTests();
        petTests();
        haloHornTests();
        lodTests();
        profileTests();
        animatedTextureTests();
        packTests();

        System.out.println();
        System.out.println("passed: " + passed + "  failed: " + failures.size());
        if (!failures.isEmpty()) {
            for (String f : failures) System.out.println("FAIL " + f);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- harness

    private static void section(String name) {
        scope = name;
        System.out.println("- " + name);
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
        } else {
            failures.add(scope + " / " + name + (detail == null ? "" : " :: " + detail));
        }
    }

    private static void checkNear(String name, float actual, float expected, float tolerance) {
        check(name, Math.abs(actual - expected) <= tolerance,
                "expected " + expected + " +/- " + tolerance + " but was " + actual);
    }

    private static void checkFinite(String name, float[] data) {
        for (float v : data) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                check(name, false, "non-finite value in buffer");
                return;
            }
        }
        check(name, true, null);
    }

    // ------------------------------------------------------------- events

    private static final class Ping extends Event {
        final List<String> order = new ArrayList<String>();
    }

    private static final class CancelablePing extends Event {
        final List<String> order = new ArrayList<String>();

        @Override
        public boolean isCancellable() {
            return true;
        }
    }

    private static void eventBusTests() {
        section("event bus");
        EventBus bus = new EventBus();
        final Object ownerA = new Object();
        final Object ownerB = new Object();
        final Ping ping = new Ping();

        bus.subscribe(Ping.class, EventBus.Priority.LOWEST, ownerA,
                new EventBus.Listener<Ping>() {
                    @Override
                    public void onEvent(Ping e) {
                        e.order.add("lowest");
                    }
                });
        bus.subscribe(Ping.class, EventBus.Priority.HIGHEST, ownerB,
                new EventBus.Listener<Ping>() {
                    @Override
                    public void onEvent(Ping e) {
                        e.order.add("highest");
                    }
                });
        bus.subscribe(Ping.class, EventBus.Priority.NORMAL, ownerA,
                new EventBus.Listener<Ping>() {
                    @Override
                    public void onEvent(Ping e) {
                        e.order.add("normal");
                    }
                });
        bus.post(ping);
        check("priority ordering", ping.order.equals(
                java.util.Arrays.asList("highest", "normal", "lowest")), ping.order.toString());
        check("listener count", bus.listenerCount(Ping.class) == 3,
                "was " + bus.listenerCount(Ping.class));

        final CancelablePing cancel = new CancelablePing();
        bus.subscribe(CancelablePing.class, EventBus.Priority.HIGHEST, ownerA,
                new EventBus.Listener<CancelablePing>() {
                    @Override
                    public void onEvent(CancelablePing e) {
                        e.order.add("first");
                        e.cancel();
                    }
                });
        bus.subscribe(CancelablePing.class, EventBus.Priority.LOWEST, ownerA,
                new EventBus.Listener<CancelablePing>() {
                    @Override
                    public void onEvent(CancelablePing e) {
                        e.order.add("after-cancel");
                    }
                });
        bus.post(cancel);
        check("cancel stops lower priority listeners", cancel.order.equals(
                java.util.Arrays.asList("first")), cancel.order.toString());

        bus.unsubscribe(ownerB);
        check("unsubscribe removes listeners", bus.listenerCount(Ping.class) == 2,
                "was " + bus.listenerCount(Ping.class));

        boolean threw = false;
        try {
            ping.cancel();
        } catch (IllegalStateException expected) {
            threw = true;
        }
        check("cancel on non-cancellable throws", threw, null);
    }

    // --------------------------------------------------------------- json

    private static void jsonTests() {
        section("json");
        String source = "{\"a\":[1,2.5,true,null],\"b\":{\"c\":\"x\\ny\"},\"d\":-3}";
        Map<String, Object> parsed = Json.asObject(Json.parse(source));
        check("object keys", parsed.size() == 3, "was " + parsed.size());
        List<Object> a = Json.asArray(parsed.get("a"));
        check("array size", a.size() == 4, "was " + a.size());
        check("number parse", ((Number) a.get(1)).doubleValue() == 2.5D, String.valueOf(a.get(1)));
        check("string unescape", "x\ny".equals(Json.getString(
                Json.asObject(parsed.get("b")), "c", "")), null);

        String round = Json.write(parsed);
        Map<String, Object> again = Json.asObject(Json.parse(round));
        check("round trip", Json.write(again).equals(round), round);

        check("rejects trailing garbage", throwsJson("{\"a\":1} junk"), null);
        check("rejects NaN", throwsJson("{\"a\":NaN}"), null);
        check("rejects trailing comma", throwsJson("{\"a\":1,}"), null);
        check("rejects unterminated", throwsJson("{\"a\":[1,2"), null);
        check("rejects bad escape", throwsJson("{\"a\":\"\\q\"}"), null);
        check("accepts empty object", !throwsJson("{}"), null);
        check("pretty print parses", !throwsJson(Json.writePretty(parsed)), null);
    }

    private static boolean throwsJson(String text) {
        try {
            Json.parse(text);
            return false;
        } catch (Json.JsonException e) {
            return true;
        }
    }

    // -------------------------------------------------------------- config

    private static void configTests() throws Exception {
        section("config");
        File dir = Files.createTempDirectory("cloud-config-test").toFile();
        File file = new File(dir, "cloud.json");
        CloudConfig config = new CloudConfig(file);
        config.set("module.cape.physics.stiffness", 0.87D);
        config.set("module.cape.enabled", Boolean.TRUE);
        config.set("hud.fps.x", 4);
        check("int clamp writes back", config.getIntClamped("hud.fps.x", 0, 10, 100) == 10,
                "value was " + config.raw("hud.fps.x"));
        config.save();

        CloudConfig reloaded = new CloudConfig(file);
        reloaded.load();
        check("bool persisted", reloaded.getBool("module.cape.enabled", false), null);
        checkNear("double persisted",
                (float) reloaded.getDouble("module.cape.physics.stiffness", 0.0D), 0.87F, 0.001F);
        check("clamped int persisted", reloaded.getInt("hud.fps.x", -1) == 10,
                String.valueOf(reloaded.getInt("hud.fps.x", -1)));
        check("default for missing", reloaded.getString("nope", "fallback").equals("fallback"), null);

        File broken = new File(dir, "broken.json");
        Files.write(broken.toPath(), "{oops".getBytes(StandardCharsets.UTF_8));
        CloudConfig bad = new CloudConfig(broken);
        boolean threw = false;
        try {
            bad.load();
        } catch (Json.JsonException e) {
            threw = true;
        }
        check("broken config reports instead of silently resetting", threw, null);
        deleteTree(dir);
    }

    private static final class CountingModule extends Module {
        int enables;
        int disables;

        @Override
        public String id() {
            return "test.counting";
        }

        @Override
        protected void onEnable() {
            enables++;
        }

        @Override
        protected void onDisable() {
            disables++;
        }
    }

    private static final class BrokenModule extends Module {
        @Override
        public String id() {
            return "test.broken";
        }

        @Override
        public int order() {
            return 10;
        }

        @Override
        protected void onEnable() {
            throw new IllegalStateException("boom");
        }
    }

    private static void moduleTests() {
        section("modules");
        EventBus bus = new EventBus();
        CloudConfig config = new CloudConfig();
        ModuleManager manager = new ModuleManager(bus, config, Log.CONSOLE);
        CountingModule counting = manager.register(new CountingModule());
        manager.register(new BrokenModule());
        manager.initAll();

        check("default enabled", counting.isEnabled(), null);
        check("enable hook ran once", counting.enables == 1, "was " + counting.enables);
        check("throwing module does not abort init", manager.modules().size() == 2, null);

        manager.setEnabled("test.counting", false);
        check("disable hook", counting.disables == 1 && !counting.isEnabled(), null);
        check("state persisted", !config.getBool("module.test.counting.enabled", true), null);
        manager.setEnabled("test.counting", true);
        check("re-enable", counting.enables == 2 && counting.isEnabled(), null);
        check("unknown module", !manager.setEnabled("nope", true), null);
    }

    // ------------------------------------------------------------- anchors

    private static void anchorTests() {
        section("attachment anchors");
        AnchorSolver solver = new AnchorSolver();
        Vec3 pos = new Vec3(100.0F, 64.0F, -200.0F);

        Vec3 right0 = solver.solve(pos, 0.0F, 0.0F, 0.0F, AttachmentPoint.HEAD_SIDE_RIGHT);
        check("facing south, right is west (-x)", right0.x < pos.x,
                "x=" + right0.x);
        checkNear("anchor height", right0.y, pos.y + 1.62F, 0.001F);

        Vec3 right180 = solver.solve(pos, 180.0F, 180.0F, 0.0F, AttachmentPoint.HEAD_SIDE_RIGHT);
        check("facing north, right is east (+x)", right180.x > pos.x, "x=" + right180.x);

        Vec3 back0 = solver.solve(pos, 0.0F, 0.0F, 0.0F, AttachmentPoint.BACK);
        check("back is behind player", back0.z < pos.z, "z=" + back0.z);

        Vec3 topLevel = solver.solve(pos, 0.0F, 0.0F, 0.0F, AttachmentPoint.HEAD_TOP);
        Vec3 topUp = solver.solve(pos, 0.0F, 0.0F, -80.0F, AttachmentPoint.HEAD_TOP);
        check("head pitch moves head anchors",
                topLevel.distanceTo(topUp) > 0.05F,
                "distance=" + topLevel.distanceTo(topUp));

        Vec3 backLevel = solver.solve(pos, 0.0F, 0.0F, 0.0F, AttachmentPoint.BACK);
        Vec3 backPitched = solver.solve(pos, 0.0F, 0.0F, -80.0F, AttachmentPoint.BACK);
        check("torso anchors ignore head pitch",
                backLevel.distanceTo(backPitched) < 1.0E-4F,
                "distance=" + backLevel.distanceTo(backPitched));

        Vec3 sideLevel = solver.solve(pos, 0.0F, 0.0F, 0.0F, AttachmentPoint.HEAD_SIDE_RIGHT);
        Vec3 sideTurn = solver.solve(pos, 0.0F, 45.0F, 0.0F, AttachmentPoint.HEAD_SIDE_RIGHT);
        check("head yaw turns off-axis head anchors", sideTurn.distanceTo(sideLevel) > 0.01F,
                "distance=" + sideTurn.distanceTo(sideLevel));

        // HEAD_TOP sits on the yaw axis and must *not* move when the head turns.
        Vec3 topTurn = solver.solve(pos, 0.0F, 45.0F, 0.0F, AttachmentPoint.HEAD_TOP);
        check("head yaw leaves top-centre anchor fixed",
                topTurn.distanceTo(topLevel) < 1.0E-4F,
                "distance=" + topTurn.distanceTo(topLevel));
        check("anchors finite", topUp.isFinite() && topTurn.isFinite(), null);
    }

    // ------------------------------------------------------------ math

    private static void fastMathTests() {
        section("fast math");
        float maxErr = 0.0F;
        for (int i = 0; i < 20000; i++) {
            float radians = (float) ((i - 10000) * 0.0013D);
            float expected = (float) Math.sin(radians);
            maxErr = Math.max(maxErr, Math.abs(FastMath.sin(radians) - expected));
        }
        check("sin error bounded", maxErr < 1.0E-4F, "max error " + maxErr);
        checkNear("cos", FastMath.cos(0.0F), 1.0F, 1.0E-4F);
        checkNear("sinDeg 90", FastMath.sinDeg(90.0F), 1.0F, 1.0E-4F);
        checkNear("wrap negative", FastMath.wrapDegrees(-90.0F), 270.0F, 1.0E-4F);
        checkNear("angle delta shortest", FastMath.angleDelta(350.0F, 10.0F), 20.0F, 1.0E-3F);
        checkNear("damp half life", FastMath.damp(0.0F, 1.0F, 0.5F, 0.5F), 0.5F, 1.0E-3F);
    }

    private static void matrixTests() {
        section("matrices");
        Vec3 out = new Vec3();
        new Mat4().rotateY(90.0F).transformPoint(1.0F, 0.0F, 0.0F, out);
        checkNear("yaw 90 maps +x to -z", out.z, -1.0F, 1.0E-4F);
        new Mat4().translate(1.0F, 2.0F, 3.0F).transformPoint(0.0F, 0.0F, 0.0F, out);
        check("translation", out.x == 1.0F && out.y == 2.0F && out.z == 3.0F, out.toString());

        Mat4 a = new Mat4().rotateY(90.0F);
        Mat4 b = new Mat4().translate(0.0F, 0.0F, 1.0F);
        Vec3 sequential = new Vec3();
        b.transformPoint(1.0F, 0.0F, 0.0F, sequential);
        a.transformPoint(sequential.x, sequential.y, sequential.z, sequential);
        a.multiply(b);
        a.transformPoint(1.0F, 0.0F, 0.0F, out);
        check("multiply applies right-hand first", sequential.distanceTo(out) < 1.0E-4F,
                sequential + " vs " + out);
    }

    // -------------------------------------------------------------- cape

    private static void capeTests() {
        section("cape simulation");
        CapeSimulation cape = new CapeSimulation();
        Vec3 anchorL = new Vec3(-0.31F, 65.42F, 10.13F);
        Vec3 anchorR = new Vec3(0.31F, 65.42F, 10.13F);
        Vec3 wind = new Vec3(0.4F, 0.0F, -1.2F);
        Vec3 vel = new Vec3();
        Vec3 body = new Vec3(0.0F, 64.0F, 10.0F);

        cape.reset(anchorL, anchorR);
        for (int i = 0; i < 400; i++) {
            cape.update(1.0F / 60.0F, anchorL, anchorR, wind, vel, body, CapeParams.idle());
        }
        check("vertices finite", cape.isFinite(), null);
        checkFinite("position buffer", cape.positions());
        check("vertex count", cape.positions().length == CapeSimulation.POINT_COUNT * 3,
                "was " + cape.positions().length);
        check("uv count", cape.uvs().length == CapeSimulation.POINT_COUNT * 2, null);

        Vec3 topMean = new Vec3();
        cape.meanRow(0, topMean);
        checkNear("top row pinned x", topMean.x, 0.0F, 1.0E-3F);
        checkNear("top row pinned y", topMean.y, 65.42F, 1.0E-3F);

        Vec3 bottomMean = new Vec3();
        cape.meanRow(CapeSimulation.ROWS, bottomMean);
        check("cape hangs below anchors", bottomMean.y < topMean.y - 0.3F,
                "bottom y=" + bottomMean.y);

        // Rest length preservation on the vertical spine.
        float restH = cape.length() / CapeSimulation.ROWS;
        Vec3 a = new Vec3();
        Vec3 b = new Vec3();
        float worst = 0.0F;
        for (int col = 0; col <= CapeSimulation.COLS; col++) {
            for (int row = 0; row < CapeSimulation.ROWS; row++) {
                cape.point(col, row, a);
                cape.point(col, row + 1, b);
                worst = Math.max(worst, Math.abs(a.distanceTo(b) - restH));
            }
        }
        check("segment length preserved", worst < restH * 0.45F,
                "worst deviation " + worst + " (rest " + restH + ")");

        // Sprint trailing: with velocity along +x the hem must lag toward -x.
        CapeSimulation sprint = new CapeSimulation();
        sprint.reset(anchorL, anchorR);
        Vec3 sprintVel = new Vec3(0.28F, 0.0F, 0.0F);
        for (int i = 0; i < 300; i++) {
            sprint.update(1.0F / 60.0F, anchorL, anchorR, new Vec3(), sprintVel, body,
                    CapeParams.sprinting());
        }
        Vec3 sTop = new Vec3();
        Vec3 sBottom = new Vec3();
        sprint.meanRow(0, sTop);
        sprint.meanRow(CapeSimulation.ROWS, sBottom);
        check("sprint trails behind travel", sBottom.x < sTop.x - 0.05F,
                "top x=" + sTop.x + " bottom x=" + sBottom.x);
        check("sprint stays finite", sprint.isFinite(), null);

        // Teleport robustness: anchors jump 40 blocks, cape must not explode.
        CapeSimulation jump = new CapeSimulation();
        jump.reset(anchorL, anchorR);
        for (int i = 0; i < 900; i++) {
            Vec3 l = anchorL;
            Vec3 r = anchorR;
            if (i == 200 || i == 500) {
                l = new Vec3(anchorL.x + 40.0F, anchorL.y, anchorL.z - 40.0F);
                r = new Vec3(anchorR.x + 40.0F, anchorR.y, anchorR.z - 40.0F);
            }
            jump.update(1.0F / 60.0F, l, r, wind, vel, body, CapeParams.falling());
        }
        check("teleport does not destabilise", jump.isFinite(), null);

        // Extreme frame times must be clamped, not integrated raw.
        CapeSimulation lag = new CapeSimulation();
        lag.reset(anchorL, anchorR);
        for (int i = 0; i < 50; i++) {
            lag.update(2.5F, anchorL, anchorR, wind, vel, body, CapeParams.idle());
        }
        check("lag spike does not destabilise", lag.isFinite(), null);

        check("state derive idle", CapeState.derive(0.0F, true, false, false) == CapeState.IDLE, null);
        check("state derive sprint", CapeState.derive(0.2F, true, false, false) == CapeState.SPRINTING, null);
        check("state derive fall", CapeState.derive(0.0F, false, true, false) == CapeState.FALLING, null);
        check("elytra hides cape", CapeState.derive(0.0F, false, false, true).hidesCape(), null);
    }

    // ------------------------------------------------------------- wings

    private static void wingTests() {
        section("wings");
        WingRig rig = new WingRig();
        Vec3 anchor = new Vec3(0.0F, 65.42F, 10.13F);
        Vec3 tip = new Vec3();

        rig.setState(WingState.SPRINTING);
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        rig.update(0.05F, anchor, 0.0F);
        for (int i = 0; i < 400; i++) {
            rig.update(1.0F / 60.0F, anchor, 0.0F);
            rig.point(WingRig.POINTS - 1, tip);
            if (!tip.isFinite()) {
                check("tip finite during flap", false, tip.toString());
                break;
            }
            minY = Math.min(minY, tip.y);
            maxY = Math.max(maxY, tip.y);
        }
        check("sprint tip flaps", (maxY - minY) > 0.02F, "range=" + (maxY - minY));
        check("tip stays near body", Math.abs(tip.x - anchor.x) < 1.6F, "x=" + tip.x);

        Vec3 mirrored = new Vec3();
        rig.point(WingRig.POINTS - 1, tip);
        rig.mirroredPoint(WingRig.POINTS - 1, anchor, 0.0F, mirrored);
        checkNear("mirror flips x", mirrored.x, -tip.x, 1.0E-3F);
        checkNear("mirror keeps y", mirrored.y, tip.y, 1.0E-3F);

        WingRig idle = new WingRig();
        idle.setState(WingState.IDLE);
        idle.update(0.05F, anchor, 0.0F);
        float idleMin = Float.MAX_VALUE;
        float idleMax = -Float.MAX_VALUE;
        for (int i = 0; i < 400; i++) {
            idle.update(1.0F / 60.0F, anchor, 0.0F);
            idle.point(WingRig.POINTS - 1, tip);
            idleMin = Math.min(idleMin, tip.y);
            idleMax = Math.max(idleMax, tip.y);
        }
        check("idle flaps less than sprint", (idleMax - idleMin) < (maxY - minY),
                "idle=" + (idleMax - idleMin) + " sprint=" + (maxY - minY));

        WingRig.TipEmitter emitter = new WingRig.TipEmitter();
        int emitted = 0;
        for (int i = 0; i < 20; i++) {
            if (emitter.tryEmit(7)) emitted++;
        }
        check("particle budget enforced", emitted == WingRig.TipEmitter.BUDGET_PER_TICK,
                "emitted " + emitted);
        check("budget resets next tick", emitter.tryEmit(8), null);
    }

    // -------------------------------------------------------------- pets

    private static void petTests() {
        section("pets");
        PetFlock flock = new PetFlock();
        Pet orb = flock.add(PetType.ORB, "Spark");
        Pet cube = flock.add(PetType.CUBE_COMPANION, "Box");
        Vec3 owner = new Vec3(12.0F, 70.0F, -4.0F);

        for (int i = 0; i < 600; i++) {
            flock.update(0.05F, owner, 90.0F, false);
        }
        check("pet settles near owner", orb.position().distanceTo(owner) < 2.0F,
                "distance=" + orb.position().distanceTo(owner));
        check("pets separated", orb.position().distanceTo(cube.position()) > 0.30F,
                "distance=" + orb.position().distanceTo(cube.position()));
        check("pets finite", orb.position().isFinite() && cube.position().isFinite(), null);

        orb.setShoulderRiding(true);
        for (int i = 0; i < 200; i++) {
            flock.update(0.05F, owner, 90.0F, false);
        }
        check("shoulder pet sits near head height",
                orb.position().y > owner.y + 1.0F && orb.position().y < owner.y + 1.8F,
                "y=" + orb.position().y);

        check("rename trims", "Fluffy".equals(orb.rename("  Fluffy  ").name()), orb.name());
        check("blank rename falls back", "orb".equals(orb.rename("   ").name()), orb.name());
        check("bob is bounded", Math.abs(flock.bob(cube)) < 0.1F, String.valueOf(flock.bob(cube)));

        boolean limited = false;
        try {
            flock.add(PetType.CAT, "a");
            flock.add(PetType.WOLF, "b");
            flock.add(PetType.PARROT, "c");
        } catch (IllegalStateException e) {
            limited = true;
        }
        check("pet cap enforced", limited, null);
    }

    // ----------------------------------------------------------- halo/horn

    private static void haloHornTests() {
        section("halo and horns");
        Halo halo = new Halo().configure(90.0F, 0.5F, 0.2F, 0.05F, 0.3F);
        float minB = 1.0F;
        float maxB = 0.0F;
        for (int i = 0; i < 300; i++) {
            halo.update(1.0F / 60.0F);
            minB = Math.min(minB, halo.brightness());
            maxB = Math.max(maxB, halo.brightness());
            if (halo.brightness() < 0.0F || halo.brightness() > 1.0F) {
                check("brightness in range", false, String.valueOf(halo.brightness()));
                break;
            }
        }
        check("brightness stays in 0..1", minB >= 0.0F && maxB <= 1.0F, null);
        check("brightness pulses", (maxB - minB) > 0.02F, "range=" + (maxB - minB));
        check("halo spins", halo.angleDegrees() >= 0.0F && halo.angleDegrees() < 360.0F,
                String.valueOf(halo.angleDegrees()));

        Halo.Mesh ring = Halo.buildRing(0.10F, 0.16F, 24);
        check("ring vertex count", ring.vertexCount() == 25 * 2, "was " + ring.vertexCount());
        check("ring triangle count", ring.triangleCount() == 24 * 2, "was " + ring.triangleCount());
        boolean inRange = true;
        for (int index : ring.indices) {
            if (index < 0 || index >= ring.vertexCount()) inRange = false;
        }
        check("ring indices in range", inRange, null);

        HornSet horns = new HornSet().style(HornSet.Style.SOFT)
                .shape(40.0F, 0.05F, 0.30F, 0.006F);
        HornGeometry.Mesh mesh = horns.mesh(true);
        check("horn vertex count", mesh.vertexCount() == 9 * 8, "was " + mesh.vertexCount());
        checkFinite("horn positions", mesh.positions);
        checkFinite("horn normals", mesh.normals);

        // Tube radius = half the max chord within a ring (the bent centreline
        // moves, so distance-from-origin would be wrong).
        float baseRadius = ringRadius(mesh, 0);
        float tipRadius = ringRadius(mesh, (mesh.vertexCount() / 8) - 1);
        check("horn tapers", baseRadius > tipRadius * 2.0F,
                "base=" + baseRadius + " tip=" + tipRadius);
        checkNear("horn base radius matches request", baseRadius, 0.05F, 1.0E-3F);

        Mat4 mount = new Mat4();
        horns.mountMatrix(true, mount);
        Vec3 p = new Vec3();
        mount.transformPoint(0.0F, 0.0F, 0.0F, p);
        check("left horn mounts left of centre", p.x < 0.0F, "x=" + p.x);
        horns.mountMatrix(false, mount);
        mount.transformPoint(0.0F, 0.0F, 0.0F, p);
        check("right horn mounts right of centre", p.x > 0.0F, "x=" + p.x);
    }

    // --------------------------------------------------------------- lod

    private static void lodTests() {
        section("lod");
        float previous = -1.0F;
        boolean monotonic = true;
        for (float d = 0.0F; d <= 100.0F; d += 0.5F) {
            float b = Lod.blend(d);
            if (b < previous - 1.0E-6F) monotonic = false;
            previous = b;
        }
        check("blend monotonic", monotonic, null);
        checkNear("blend near", Lod.blend(0.0F), 0.0F, 1.0E-5F);
        checkNear("blend far", Lod.blend(100.0F), 2.0F, 1.0E-5F);

        boolean sums = true;
        for (float d = 20.0F; d <= 80.0F; d += 0.25F) {
            float sum = Lod.alphaFor(0, d) + Lod.alphaFor(1, d) + Lod.alphaFor(2, d);
            if (Math.abs(sum - 1.0F) > 1.0E-4F) sums = false;
        }
        check("alphas sum to one (no popping)", sums, null);
        check("full detail close", Lod.alphaFor(0, 10.0F) == 1.0F, null);
        check("proxy far", Lod.alphaFor(2, 90.0F) == 1.0F, null);
        check("dominant level mid", Lod.dominantLevel(48.0F) == 1, null);
    }

    // ----------------------------------------------------------- profile

    private static void profileTests() {
        section("cosmetic profile");
        CosmeticProfile profile = CosmeticProfile.starter();
        check("starter has cape", profile.isEquipped(CosmeticType.CAPE), null);
        profile.equip(CosmeticType.WINGS, "demo", "ember_wings").b = 0.25F;
        profile.equip(CosmeticType.HALO, "demo", "gold_halo");

        String json = Json.write(profile.toJson());
        CosmeticProfile parsed = CosmeticProfile.fromJson(Json.asObject(Json.parse(json)));
        check("profile round trip", parsed.isEquipped(CosmeticType.WINGS)
                && parsed.get(CosmeticType.WINGS).itemId.equals("ember_wings"), json);
        checkNear("colour preserved", parsed.get(CosmeticType.WINGS).b, 0.25F, 1.0E-4F);

        parsed.unequip(CosmeticType.HALO);
        check("unequip", !parsed.isEquipped(CosmeticType.HALO), null);
        check("cape tessellation is 8x4", CosmeticProfile.capeVertexCount() == 45,
                String.valueOf(CosmeticProfile.capeVertexCount()));
    }

    // --------------------------------------------------------------- packs

    private static void animatedTextureTests() {
        section("animated textures");
        net.cloudclient.cosmetic.asset.AnimatedTexture still =
                new net.cloudclient.cosmetic.asset.AnimatedTexture(1, 12.0F);
        check("static texture is frame 0", still.frameAt(99.0F) == 0 && !still.isAnimated(), null);

        net.cloudclient.cosmetic.asset.AnimatedTexture sheet =
                new net.cloudclient.cosmetic.asset.AnimatedTexture(4, 10.0F);
        check("frame advances", sheet.frameAt(0.15F) == 1, "was " + sheet.frameAt(0.15F));
        check("frame wraps", sheet.frameAt(0.45F) == 0, "was " + sheet.frameAt(0.45F));
        checkNear("frame height", sheet.frameHeight(), 0.25F, 1.0E-6F);
        checkNear("frame v0", sheet.v0(2), 0.5F, 1.0E-6F);
        checkNear("frame v1", sheet.v1(3), 1.0F, 1.0E-6F);
        check("v0 stays in range", sheet.v0(7) >= 0.0F && sheet.v0(7) < 1.0F, null);
    }

    private static final byte[] TINY_PNG = png(64, 64);

    private static void packTests() throws Exception {
        section("cloudpack");

        Map<String, byte[]> good = new LinkedHashMap<String, byte[]>();
        good.put("manifest.json", utf8(validManifest(1)));
        good.put("textures/cape.png", TINY_PNG);
        PackLoader.Loaded loaded = PackLoader.load(zip(good), "test:good");
        check("valid pack loads", loaded.ok(), loaded.validation.summary());
        check("cosmetic resolved", loaded.pack.manifest().find("cloud_cape") != null, null);

        Map<String, byte[]> slip = new LinkedHashMap<String, byte[]>(good);
        slip.put("../../evil.txt", utf8("pwn"));
        PackLoader.Loaded slipResult = PackLoader.load(zip(slip), "test:slip");
        check("zip slip rejected", !slipResult.ok(), slipResult.validation.summary());

        Map<String, byte[]> absolute = new LinkedHashMap<String, byte[]>(good);
        absolute.put("/etc/passwd", utf8("pwn"));
        check("absolute path rejected", !PackLoader.load(zip(absolute), "test:abs").ok(), null);

        Map<String, byte[]> scripts = new LinkedHashMap<String, byte[]>(good);
        scripts.put("scripts/main.lua", utf8("os.execute('rm -rf /')"));
        PackLoader.Loaded scriptResult = PackLoader.load(zip(scripts), "test:script");
        check("script packs rejected", !scriptResult.ok(), scriptResult.validation.summary());

        Map<String, byte[]> badFrames = new LinkedHashMap<String, byte[]>();
        badFrames.put("manifest.json", utf8(validManifest(5)));
        badFrames.put("textures/cape.png", TINY_PNG);
        PackLoader.Loaded frameResult = PackLoader.load(zip(badFrames), "test:frames");
        check("non-divisible frames rejected", !frameResult.ok(),
                frameResult.validation.summary());

        Map<String, byte[]> poly = new LinkedHashMap<String, byte[]>();
        poly.put("manifest.json", utf8(validManifest(1).replace("\"triangles\": 900",
                "\"triangles\": 999999")));
        poly.put("textures/cape.png", TINY_PNG);
        PackLoader.Loaded polyResult = PackLoader.load(zip(poly), "test:poly");
        check("poly budget enforced", !polyResult.ok(), polyResult.validation.summary());

        Map<String, byte[]> unknownType = new LinkedHashMap<String, byte[]>();
        unknownType.put("manifest.json", utf8(validManifest(1).replace("\"cape\"", "\"jetpack\"")));
        unknownType.put("textures/cape.png", TINY_PNG);
        check("unknown type rejected", !PackLoader.load(zip(unknownType), "test:type").ok(), null);

        Map<String, byte[]> noManifest = new LinkedHashMap<String, byte[]>();
        noManifest.put("textures/cape.png", TINY_PNG);
        check("missing manifest rejected",
                !PackLoader.load(zip(noManifest), "test:nomanifest").ok(), null);

        check("bad api rejected", !PackLoader.load(zip(
                withManifest(validManifest(1).replace("\"api\": 1", "\"api\": 7"))), "test:api").ok(),
                null);

        PackRegistry registry = new PackRegistry();
        registry.install(zip(good), "test:good");
        check("registry installed", registry.size() == 1, String.valueOf(registry.size()));
        check("registry lookup", registry.find("demo", "cloud_cape") != null, null);
        check("registry by type",
                registry.byType(CosmeticType.CAPE).size() == 1,
                String.valueOf(registry.byType(CosmeticType.CAPE).size()));
        check("registry uninstall", registry.uninstall("demo")
                && registry.find("demo", "cloud_cape") == null, null);

        check("limits documented", PackValidation.MAX_TRIANGLES == 20000, null);
        check("png header read", net.cloudclient.pack.PngHeader.read(TINY_PNG).width == 64, null);
    }

    private static float ringRadius(HornGeometry.Mesh mesh, int ring) {
        int sides = 8;
        int start = ring * sides;
        float max = 0.0F;
        for (int i = 0; i < sides; i++) {
            for (int j = i + 1; j < sides; j++) {
                float dx = mesh.positions[(start + i) * 3] - mesh.positions[(start + j) * 3];
                float dy = mesh.positions[(start + i) * 3 + 1] - mesh.positions[(start + j) * 3 + 1];
                float dz = mesh.positions[(start + i) * 3 + 2] - mesh.positions[(start + j) * 3 + 2];
                max = Math.max(max, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        return max * 0.5F;
    }

    private static Map<String, byte[]> withManifest(String manifest) {
        Map<String, byte[]> map = new LinkedHashMap<String, byte[]>();
        map.put("manifest.json", utf8(manifest));
        map.put("textures/cape.png", TINY_PNG);
        return map;
    }

    private static String validManifest(int frames) {
        return "{\n"
                + "  \"id\": \"demo\",\n"
                + "  \"name\": \"Demo Pack\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"author\": \"tester\",\n"
                + "  \"api\": 1,\n"
                + "  \"cosmetics\": [\n"
                + "    {\"id\": \"cloud_cape\", \"name\": \"Cloud Cape\", \"type\": \"cape\",\n"
                + "     \"texture\": \"textures/cape.png\",\n"
                + "     \"triangles\": 900,\n"
                + "     \"animation\": {\"frames\": " + frames + ", \"fps\": 12}}\n"
                + "  ]\n"
                + "}";
    }

    /** Builds a minimal but structurally valid PNG header block. */
    private static byte[] png(int width, int height) {
        byte[] data = new byte[33];
        byte[] sig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, data, 0, 8);
        data[11] = 13; // IHDR length
        data[12] = 'I';
        data[13] = 'H';
        data[14] = 'D';
        data[15] = 'R';
        writeInt(data, 16, width);
        writeInt(data, 20, height);
        data[24] = 8;  // bit depth
        data[25] = 6;  // RGBA
        return data;
    }

    private static void writeInt(byte[] d, int offset, int value) {
        d[offset] = (byte) (value >>> 24);
        d[offset + 1] = (byte) (value >>> 16);
        d[offset + 2] = (byte) (value >>> 8);
        d[offset + 3] = (byte) value;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(bos);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            zos.putNextEntry(new ZipEntry(e.getKey()));
            zos.write(e.getValue());
            zos.closeEntry();
        }
        zos.close();
        return bos.toByteArray();
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private CoreTests() {
    }
}
