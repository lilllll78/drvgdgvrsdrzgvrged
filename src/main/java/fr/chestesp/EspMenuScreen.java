package fr.chestesp;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu RoseClient : onglets en haut, recherche, cartes de modules (interrupteur + reglages depliables). */
public class EspMenuScreen extends Screen {

    private static final String[] CATS = {"Visuals", "PvP", "Farm", "Build", "Thème", "Aide"};
    private static final int[] PRESETS = {0xA855F7, 0xFB7185, 0x22D3EE, 0x4ADE80, 0xFB923C, 0xFACC15, 0xEF4444, 0xE5E7EB};
    private static final Map<String, Boolean> OPEN = new HashMap<>();
    private static int lastCat = 0;
    private static final int HEADER = 28;

    private final Screen parent;
    private final List<Card> cards = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private TextFieldWidget search;
    private int cat = lastCat;
    private int scroll = 0;
    private int contentH = 0;
    private Draggable dragging = null;
    private final int[] tabX = new int[CATS.length];
    private final int[] tabW = new int[CATS.length];

    private int wx, wy, ww, wh, bx, by, bw, bh;

    public EspMenuScreen() {
        this(null);
    }

    public EspMenuScreen(Screen parent) {
        super(Text.literal("RoseClient"));
        this.parent = parent;
    }

    // ================================================================ lignes

    private interface Draggable {
        void drag(double mx, double my);
    }

    private interface Row {
        int height();
        void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel);
        default boolean click(double mx, double my, int x, int y, int w) { return false; }
        default void activate() {}
        default void adjust(int dir) {}
        default boolean selectable() { return true; }
        default String hint() { return null; }
    }

    private static final class HeaderRow implements Row {
        private final String text;
        HeaderRow(String text) { this.text = text; }
        public int height() { return 18; }
        public boolean selectable() { return false; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            c.drawText(tr, text.toUpperCase(), x + 2, y + 8, Ui.ACCENT2, true);
            c.fill(x, y + 17, x + w, y + 18, Ui.BORDER);
        }
    }

    private static final class InfoRow implements Row {
        private final Supplier<String> text;
        private final int color;
        InfoRow(Supplier<String> text, int color) { this.text = text; this.color = color; }
        public int height() { return 14; }
        public boolean selectable() { return false; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            c.drawText(tr, text.get(), x + 4, y + 3, color, true);
        }
    }

    private static final class ToggleRow implements Row {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private String hint;
        private float anim = -1f;
        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label; this.get = get; this.set = set;
        }
        ToggleRow h(String hint) { this.hint = hint; return this; }
        public String hint() { return hint; }
        public int height() { return 22; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            Ui.rowBg(c, x, y, w, 22, hover, sel);
            boolean on = get.getAsBoolean();
            if (anim < 0) anim = on ? 1f : 0f;
            anim += ((on ? 1f : 0f) - anim) * 0.35f;
            c.drawText(tr, label, x + 8, y + 7, Ui.TEXT, true);
            int sw = 30, sh = 12, sx = x + w - sw - 8, sy = y + 5;
            c.fill(sx, sy, sx + sw, sy + sh, Ui.lerp(Ui.OFF, Ui.ACCENT, anim));
            int kx = sx + 1 + Math.round(anim * (sw - sh));
            c.fill(kx, sy + 1, kx + sh - 2, sy + sh - 1, 0xFFFFFFFF);
            String st = on ? "ON" : "OFF";
            c.drawText(tr, st, sx - tr.getWidth(st) - 6, y + 7, on ? Ui.GREEN : Ui.MUTED, true);
        }
        public boolean click(double mx, double my, int x, int y, int w) { activate(); return true; }
        public void activate() { set.accept(!get.getAsBoolean()); }
        public void adjust(int dir) { set.accept(dir > 0); }
    }

    private static final class SliderRow implements Row, Draggable {
        private final String label, unit;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final double min, max, step;
        private String hint;
        private int lastX, lastW;
        SliderRow(String label, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, String unit) {
            this.label = label; this.get = get; this.set = set; this.min = min; this.max = max; this.step = step; this.unit = unit;
        }
        SliderRow h(String hint) { this.hint = hint; return this; }
        public String hint() { return hint; }
        public int height() { return 32; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            Ui.rowBg(c, x, y, w, 32, hover, sel);
            double v = get.getAsDouble();
            float t = (float) ((v - min) / (max - min));
            c.drawText(tr, label, x + 8, y + 5, Ui.TEXT, true);
            String vs = ((int) Math.round(v)) + unit;
            c.drawText(tr, vs, x + w - 8 - tr.getWidth(vs), y + 5, Ui.ACCENT2, true);
            int tx = x + 8, tw = w - 16, ty = y + 21;
            lastX = tx; lastW = tw;
            c.fill(tx, ty, tx + tw, ty + 4, Ui.OFF);
            c.fill(tx, ty, tx + (int) (tw * t), ty + 4, Ui.ACCENT);
            int kx = tx + (int) (tw * t);
            c.fill(kx - 3, ty - 3, kx + 3, ty + 7, 0xFFFFFFFF);
        }
        void setFromMouse(double mx) {
            if (lastW <= 0) return;
            double t = Math.max(0.0, Math.min(1.0, (mx - lastX) / lastW));
            double v = min + t * (max - min);
            v = Math.round(v / step) * step;
            set.accept(Math.max(min, Math.min(max, v)));
        }
        public void drag(double mx, double my) { setFromMouse(mx); }
        public boolean click(double mx, double my, int x, int y, int w) { setFromMouse(mx); return true; }
        public void adjust(int dir) { set.accept(Math.max(min, Math.min(max, get.getAsDouble() + dir * step))); }
    }

    private static final class ButtonRow implements Row {
        private final Supplier<String> label;
        private final Runnable action;
        private final int color;
        private String hint;
        ButtonRow(Supplier<String> label, int color, Runnable action) {
            this.label = label; this.color = color; this.action = action;
        }
        ButtonRow h(String hint) { this.hint = hint; return this; }
        public String hint() { return hint; }
        public int height() { return 22; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            Ui.rowBg(c, x, y, w, 22, hover, sel);
            c.fill(x, y, x + 2, y + 22, color);
            c.drawText(tr, label.get(), x + 10, y + 7, Ui.TEXT, true);
            c.drawText(tr, ">", x + w - 14, y + 7, color, true);
        }
        public boolean click(double mx, double my, int x, int y, int w) { action.run(); return true; }
        public void activate() { action.run(); }
    }

    private static final class StepRow implements Row {
        private final String label;
        private final Runnable minus, plus;
        StepRow(String label, Runnable minus, Runnable plus) { this.label = label; this.minus = minus; this.plus = plus; }
        public int height() { return 22; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            Ui.rowBg(c, x, y, w, 22, hover, sel);
            c.drawText(tr, label, x + 8, y + 7, Ui.TEXT, true);
            c.fill(x + w - 54, y + 4, x + w - 32, y + 18, Ui.OFF);
            c.fill(x + w - 28, y + 4, x + w - 6, y + 18, Ui.ACCENT);
            c.drawText(tr, "-", x + w - 46, y + 7, 0xFFFFFFFF, true);
            c.drawText(tr, "+", x + w - 20, y + 7, 0xFFFFFFFF, true);
        }
        public boolean click(double mx, double my, int x, int y, int w) {
            if (mx >= x + w - 54 && mx <= x + w - 32) { minus.run(); return true; }
            if (mx >= x + w - 28 && mx <= x + w - 6) { plus.run(); return true; }
            return false;
        }
        public void adjust(int dir) { if (dir > 0) plus.run(); else minus.run(); }
    }

    /** Pastilles de couleurs toutes faites pour l'accent. */
    private static final class PresetRow implements Row {
        public int height() { return 28; }
        public String hint() { return "Couleurs toutes faites pour le menu, le HUD et la rose"; }
        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            Ui.rowBg(c, x, y, w, 28, hover, sel);
            for (int i = 0; i < PRESETS.length; i++) {
                int sx = x + 8 + i * 26, sy = y + 6;
                c.fill(sx, sy, sx + 20, sy + 16, 0xFF000000 | PRESETS[i]);
                if ((Config.accent & 0xFFFFFF) == PRESETS[i]) c.drawBorder(sx - 2, sy - 2, 24, 20, 0xFFFFFFFF);
            }
        }
        public boolean click(double mx, double my, int x, int y, int w) {
            int i = (int) ((mx - (x + 8)) / 26);
            if (i >= 0 && i < PRESETS.length) {
                Config.accent = PRESETS[i];
                Ui.applyTheme();
                return true;
            }
            return false;
        }
        public void adjust(int dir) {
            int cur = 0;
            for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == (Config.accent & 0xFFFFFF)) cur = i;
            Config.accent = PRESETS[(cur + dir + PRESETS.length) % PRESETS.length];
            Ui.applyTheme();
        }
    }

    /** Choix d'une couleur : teinte, saturation, luminosite. */
    private static final class ColorRow implements Row, Draggable {
        private final String label;
        private final IntSupplier get;
        private final IntConsumer set;
        private final Runnable after;
        private String hint;
        private int lastRgb = -1;
        private float hh, ss, vv;
        private int lastX, lastW, bar = 0;
        ColorRow(String label, IntSupplier get, IntConsumer set, Runnable after) {
            this.label = label; this.get = get; this.set = set; this.after = after;
        }
        ColorRow h(String hint) { this.hint = hint; return this; }
        public String hint() { return hint; }
        public int height() { return 62; }

        private void sync() {
            int cur = get.getAsInt() & 0xFFFFFF;
            if (cur != lastRgb) {
                float[] hsv = Ui.toHsv(cur);
                hh = hsv[0]; ss = hsv[1]; vv = hsv[2];
                lastRgb = cur;
            }
        }

        public void render(DrawContext c, TextRenderer tr, int x, int y, int w, boolean hover, boolean sel) {
            sync();
            Ui.rowBg(c, x, y, w, 62, hover, sel);
            c.drawText(tr, label, x + 8, y + 5, Ui.TEXT, true);
            String hex = String.format("#%06X", lastRgb);
            int hw = tr.getWidth(hex);
            c.drawText(tr, hex, x + w - 8 - hw, y + 5, Ui.MUTED, true);
            c.fill(x + w - 8 - hw - 22, y + 4, x + w - 8 - hw - 6, y + 14, 0xFF000000 | lastRgb);
            c.drawBorder(x + w - 8 - hw - 22, y + 4, 16, 10, 0xFFFFFFFF);

            int tx = x + 8, tw = w - 16;
            lastX = tx; lastW = tw;
            int[] ys = {y + 20, y + 33, y + 46};
            for (int i = 0; i < tw; i += 2) {
                float t = i / (float) tw;
                c.fill(tx + i, ys[0], tx + i + 2, ys[0] + 8, Ui.hsv(t, 1f, 1f));
                c.fill(tx + i, ys[1], tx + i + 2, ys[1] + 8, Ui.hsv(hh, t, vv));
                c.fill(tx + i, ys[2], tx + i + 2, ys[2] + 8, Ui.hsv(hh, ss, t));
            }
            float[] pos = {hh, ss, vv};
            for (int b = 0; b < 3; b++) {
                int mxp = tx + (int) (tw * pos[b]);
                c.fill(mxp - 1, ys[b] - 2, mxp + 1, ys[b] + 10, 0xFFFFFFFF);
            }
        }

        private void setFromMouse(double mx) {
            if (lastW <= 0) return;
            sync();
            float t = (float) Math.max(0.0, Math.min(1.0, (mx - lastX) / lastW));
            if (bar == 0) hh = Math.min(t, 0.9999f);
            else if (bar == 1) ss = t;
            else vv = t;
            int rgb = Ui.hsv(hh, ss, vv) & 0xFFFFFF;
            lastRgb = rgb;
            set.accept(rgb);
            if (after != null) after.run();
        }

        public void drag(double mx, double my) { setFromMouse(mx); }
        public boolean click(double mx, double my, int x, int y, int w) {
            double dy = my - y;
            bar = dy < 32 ? 0 : (dy < 45 ? 1 : 2);
            setFromMouse(mx);
            return true;
        }
        public void adjust(int dir) {
            sync();
            hh = (hh + dir * 0.02f + 1f) % 1f;
            int rgb = Ui.hsv(hh, ss, vv) & 0xFFFFFF;
            lastRgb = rgb;
            set.accept(rgb);
            if (after != null) after.run();
        }
    }

    // =============================================================== cartes

    private record Placed(Card card, int x, int y, int w, int h) {}

    private static final class Card {
        final String id, title, cat, desc;
        final BooleanSupplier on;
        final Consumer<Boolean> setOn;
        final List<Row> rows = new ArrayList<>();
        float anim = -1f;
        Card(String cat, String title, String desc, BooleanSupplier on, Consumer<Boolean> setOn) {
            this.id = cat + "/" + title; this.cat = cat; this.title = title; this.desc = desc;
            this.on = on; this.setOn = setOn;
        }
        Card add(Row r) { rows.add(r); return this; }
        boolean open() { return OPEN.getOrDefault(id, false); }
        int height() {
            int h = HEADER;
            if (open() && !rows.isEmpty()) {
                h += 4;
                for (Row r : rows) h += r.height() + 3;
                h += 3;
            }
            return h;
        }
    }

    private Card mod(String cat, String title, String desc, BooleanSupplier on, Consumer<Boolean> set) {
        Card c = new Card(cat, title, desc, on, set);
        cards.add(c);
        return c;
    }

    private Card section(String cat, String title, String desc) {
        return mod(cat, title, desc, null, null);
    }

    private static InfoRow note(String text) {
        return new InfoRow(() -> text, Ui.MUTED);
    }

    private static ToggleRow hudToggle(String label, BooleanSupplier g, Consumer<Boolean> s) {
        return new ToggleRow(label, g, s);
    }

    private BlockPos aimedAdjacent() {
        if (this.client.crosshairTarget instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            return b.getBlockPos().offset(b.getSide());
        }
        return this.client.player != null ? this.client.player.getBlockPos() : null;
    }

    private void nudge(int dx, int dy, int dz) {
        if (BuildState.origin != null) BuildState.origin = BuildState.origin.add(dx, dy, dz);
    }

    private void startAndClose(BuildBot.Mode m) {
        if (BuildBot.start(this.client, m)) this.client.setScreen(null);
    }

    @Override
    protected void init() {
        ww = Math.min(this.width - 24, 780);
        wh = Math.min(this.height - 24, 470);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;
        bx = wx + 12;
        by = wy + 68;
        bw = ww - 24;
        bh = wh - 68 - 24;

        String previous = search != null ? search.getText() : "";
        search = new TextFieldWidget(this.textRenderer, wx + ww / 2 - 100, wy + 8, 200, 18, Text.literal("Recherche"));
        search.setPlaceholder(Text.literal("Rechercher un module..."));
        search.setMaxLength(40);
        search.setText(previous);
        search.setChangedListener(s -> scroll = 0);
        this.addDrawableChild(search);

        int tx = bx;
        for (int i = 0; i < CATS.length; i++) {
            tabX[i] = tx;
            tabW[i] = this.textRenderer.getWidth(CATS[i]) + 26;
            tx += tabW[i] + 4;
        }

        cards.clear();

        // ------------------------------------------------------------ Visuals
        mod("Visuals", "Chest ESP", "Contour des coffres, tonneaux et ender chests à travers les blocs",
                () -> Config.chestEsp, v -> Config.chestEsp = v)
                .add(new ColorRow("Couleur des coffres", () -> Config.chestColor, v -> Config.chestColor = v, null));

        mod("Visuals", "Custom ESP", "Contour des blocs que tu choisis (ancres, minerais, spawners...)",
                () -> Config.customEsp, v -> {
                    Config.customEsp = v;
                    if (v && CustomEsp.count() == 0) this.client.setScreen(new BlockPickerScreen(this));
                })
                .add(new ButtonRow(() -> "Choisir les blocs...", Ui.CYAN,
                        () -> this.client.setScreen(new BlockPickerScreen(this)))
                        .h("Menu avec tous les blocs et une barre de recherche"))
                .add(new InfoRow(() -> "Blocs : " + CustomEsp.summary(), Ui.MUTED))
                .add(new InfoRow(() -> "Analyse : " + CustomEsp.scannedChunks + "/" + CustomEsp.totalChunks
                        + " chunks, " + CustomEsp.found + " trouvés", Ui.MUTED))
                .add(new InfoRow(() -> "Couches reçues : " + CustomEsp.deepSections + " sections sous Y=0", Ui.MUTED))
                .add(new InfoRow(() -> CustomEsp.found == 0 ? "Hauteur des blocs trouvés : aucun"
                        : "Hauteur des blocs trouvés : Y " + CustomEsp.minY + " à " + CustomEsp.maxY, Ui.MUTED))
                .add(note("Test : choisis \"Bedrock\" dans la liste"))
                .add(note("S'il s'affiche au fond, l'ESP marche"))
                .add(new ColorRow("Couleur", () -> Config.customColor, v -> Config.customColor = v, null));

        mod("Visuals", "Player ESP", "Contour des autres joueurs (plus clair quand ils sont accroupis)",
                () -> Config.playerEsp, v -> Config.playerEsp = v)
                .add(new ColorRow("Couleur des joueurs", () -> Config.playerColor, v -> Config.playerColor = v, null));

        section("Visuals", "Portée et lignes", "Réglages communs à tous les ESP")
                .add(new SliderRow("Portée de l'ESP", () -> Config.espRange, v -> Config.espRange = v, 32, 384, 8, " blocs")
                        .h("Distance max à laquelle les contours sont affichés"))
                .add(new ToggleRow("Voir jusqu'à la bedrock", () -> Config.espUnlimitedY, v -> Config.espUnlimitedY = v)
                        .h("La portée ne compte que l'horizontale : tu vois tout jusqu'au fond"))
                .add(new ToggleRow("Lignes (tracers)", () -> Config.tracers, v -> Config.tracers = v)
                        .h("Trace une ligne de toi vers chaque cible"));

        // ---------------------------------------------------------------- PvP
        mod("PvP", "Infos (FPS / ping)", "Pastille avec le ping et les images par seconde",
                () -> Config.hudFps, v -> Config.hudFps = v);
        mod("PvP", "CPS", "Clics par seconde (gauche | droit)", () -> Config.hudCps, v -> Config.hudCps = v);
        mod("PvP", "Coordonnées", "Position XYZ et direction", () -> Config.hudCoords, v -> Config.hudCoords = v);
        mod("PvP", "Touches", "Affiche WASD, espace et les clics de souris", () -> Config.hudKeystrokes, v -> Config.hudKeystrokes = v);
        mod("PvP", "Armure", "Pièces d'armure et durabilité sur le côté", () -> Config.hudArmor, v -> Config.hudArmor = v);
        mod("PvP", "Liste des modules", "Liste des modules actifs en haut à gauche", () -> Config.hudModules, v -> Config.hudModules = v);
        mod("PvP", "Boussole", "Barre de cap en haut de l'écran", () -> Config.hudCompass, v -> Config.hudCompass = v);
        mod("PvP", "Effets", "Effets de potion actifs avec leur durée", () -> Config.hudEffects, v -> Config.hudEffects = v);
        mod("PvP", "Inventaire", "Aperçu de l'inventaire en bas à droite", () -> Config.hudInventory, v -> Config.hudInventory = v);
        mod("PvP", "Carte joueur", "Ton nom et ta barre de vie", () -> Config.hudPlayerCard, v -> Config.hudPlayerCard = v);
        mod("PvP", "Raccourcis", "Liste des touches de RoseClient", () -> Config.hudKeybinds, v -> Config.hudKeybinds = v);
        mod("PvP", "Sprint automatique", "Tu cours dès que tu avances, sans maintenir la touche",
                () -> Config.toggleSprint, v -> Config.toggleSprint = v);
        mod("PvP", "Zoom", "Zoom tant que tu maintiens la touche C", () -> Config.zoom, v -> Config.zoom = v);

        // --------------------------------------------------------------- Farm
        mod("Farm", "Anchor AutoFarm", "Va vers l'ancre la plus proche, la mine et la ramasse",
                () -> Config.anchorFarm, v -> {
                    Config.anchorFarm = v;
                    if (v) BuildBot.stop(null);
                })
                .add(new ToggleRow("Mode humain", () -> Config.human, v -> Config.human = v)
                        .h("Rotations fluides, délais de réaction, petites pauses"))
                .add(new SliderRow("Portée de recherche", () -> Config.farmRange, v -> Config.farmRange = v, 32, 160, 8, " blocs")
                        .h("Distance max pour aller chercher une ancre"))
                .add(new InfoRow(() -> "Ancres détectées : " + ChestEspClient.anchors.size(), Ui.TEXT))
                .add(new InfoRow(() -> "Ancres minées (session) : " + AnchorFarm.mined, Ui.TEXT));
        mod("Farm", "Ramassage auto", "Marche jusqu'à l'ancre cassée qui tombe près de toi",
                () -> Config.autoPickup, v -> Config.autoPickup = v);
        mod("Farm", "Auto /rtp", "Envoie /rtp quand il n'y a plus d'ancre (1 fois par minute max)",
                () -> Config.autoRtp, v -> Config.autoRtp = v);
        mod("Farm", "Auto /home 1", "Envoie /home 1 et stoppe : vie basse, feu, lave, joueurs ou monstres proches",
                () -> Config.autoHome, v -> Config.autoHome = v)
                .add(new SliderRow("Seuil de vie", () -> Config.homeHp / 2.0, v -> Config.homeHp = (float) (v * 2.0), 1, 10, 1, " coeurs")
                        .h("Sous cette vie, /home 1 est envoyé tout de suite"));

        // -------------------------------------------------------------- Build
        section("Build", "Schéma", "Charge un schéma et pose-le avec le bot")
                .add(new InfoRow(() -> BuildState.schem == null ? "Aucun schéma chargé"
                        : BuildState.schemName + "  (" + BuildState.schem.sx + "x" + BuildState.schem.sy + "x" + BuildState.schem.sz + ")", Ui.MUTED))
                .add(new InfoRow(() -> BuildState.origin == null ? "Origine : non définie"
                        : "Origine : " + BuildState.origin.getX() + " " + BuildState.origin.getY() + " " + BuildState.origin.getZ(), Ui.MUTED))
                .add(new ButtonRow(() -> "Charger un schéma...", Ui.ACCENT,
                        () -> this.client.setScreen(new SchematicListScreen(this)))
                        .h("Fichiers .litematic / .schem / .nbt du dossier schematics"))
                .add(new ButtonRow(() -> "Origine : bloc visé", Ui.ACCENT, () -> {
                    BlockPos p = aimedAdjacent();
                    if (p != null) BuildState.origin = p;
                }).h("Place le schéma sur le bloc que tu vises"))
                .add(new ButtonRow(() -> "Origine : ma position", Ui.ACCENT, () -> {
                    if (this.client.player != null) BuildState.origin = this.client.player.getBlockPos();
                }))
                .add(new StepRow("Origine X", () -> nudge(-1, 0, 0), () -> nudge(1, 0, 0)))
                .add(new StepRow("Origine Y", () -> nudge(0, -1, 0), () -> nudge(0, 1, 0)))
                .add(new StepRow("Origine Z", () -> nudge(0, 0, -1), () -> nudge(0, 0, 1)))
                .add(new ButtonRow(() -> "Rotation : " + BuildState.rot * 90 + "°", Ui.ACCENT,
                        () -> BuildState.setRotation(BuildState.rot + 1)))
                .add(new ToggleRow("Plan fantôme", () -> BuildState.showGhost, v -> BuildState.showGhost = v)
                        .h("Blanc : emprise, vert : bloc manquant, rouge : mauvais bloc"))
                .add(new ButtonRow(() -> "Construire le schéma", Ui.GREEN, () -> startAndClose(BuildBot.Mode.SCHEM))
                        .h("Le bot pose les blocs de bas en haut (solo / créatif)"));

        section("Build", "Sélection", "Remplir ou vider une zone (touches H et J pour les coins)")
                .add(new InfoRow(() -> {
                    int[] sb = BuildState.selBounds();
                    return sb == null ? "Définis 2 coins (touches H et J)"
                            : "Zone : " + (sb[3] - sb[0] + 1) + " x " + (sb[4] - sb[1] + 1) + " x " + (sb[5] - sb[2] + 1);
                }, Ui.MUTED))
                .add(new ButtonRow(() -> "Coin 1 : bloc visé", Ui.CYAN, () -> BuildState.setCorner(this.client, 1)))
                .add(new ButtonRow(() -> "Coin 2 : bloc visé", Ui.CYAN, () -> BuildState.setCorner(this.client, 2)))
                .add(new ButtonRow(() -> "Remplir (bloc en main)", Ui.GREEN, () -> startAndClose(BuildBot.Mode.FILL))
                        .h("Pose le bloc que tu tiens dans tous les trous de la zone"))
                .add(new ButtonRow(() -> "Vider la sélection", Ui.RED, () -> startAndClose(BuildBot.Mode.CLEAR))
                        .h("Casse tout ce qui est dans la zone, de haut en bas"))
                .add(new ButtonRow(() -> "Effacer la sélection", Ui.MUTED, () -> {
                    BuildState.pos1 = null;
                    BuildState.pos2 = null;
                }));

        section("Build", "Contrôle", "État du bot de construction")
                .add(new InfoRow(() -> "Build : " + (BuildBot.mode == BuildBot.Mode.NONE ? "arrêté" : BuildBot.mode.toString()), Ui.TEXT))
                .add(new ButtonRow(() -> "STOP build", Ui.RED, () -> BuildBot.stop("Build arrêté")));

        // -------------------------------------------------------------- Theme
        section("Thème", "Couleurs", "Couleur principale du menu, du HUD et de la rose")
                .add(new PresetRow())
                .add(new ColorRow("Accent", () -> Config.accent, v -> Config.accent = v, Ui::applyTheme)
                        .h("Teinte / saturation / luminosité : n'importe quelle couleur"))
                .add(new SliderRow("Opacité du fond", () -> Config.uiOpacity, v -> {
                    Config.uiOpacity = v;
                    Ui.applyTheme();
                }, 40, 100, 5, " %").h("Transparence de la fenêtre RoseClient"))
                .add(new ToggleRow("Halo animé", () -> Config.glow, v -> Config.glow = v)
                        .h("Bordures et titre qui pulsent doucement"));
        section("Thème", "Décor", "Ambiance des menus de Minecraft")
                .add(new ToggleRow("Décor sakura", () -> Config.sakuraDecor, v -> Config.sakuraDecor = v)
                        .h("Lune, cerisiers et pétales sur l'écran titre et le menu pause"));
        section("Thème", "Réinitialiser", "Remet le violet d'origine")
                .add(new ButtonRow(() -> "Réinitialiser les couleurs", Ui.MUTED, () -> {
                    Config.resetTheme();
                    Ui.applyTheme();
                }));

        // --------------------------------------------------------------- Aide
        section("Aide", "Touches", "Raccourcis clavier")
                .add(note("G : ouvrir / fermer ce menu"))
                .add(note("H / J : coin 1 / coin 2 (vise un bloc)"))
                .add(note("C (maintenu) : zoom"))
                .add(note("Tab : catégorie suivante"));
        section("Aide", "Menu", "Utilisation de ce menu")
                .add(note("Clique l'interrupteur pour activer un module"))
                .add(note("Clique le nom pour déplier ses réglages"))
                .add(note("La recherche cherche dans tous les modules"))
                .add(note("Tout est sauvegardé automatiquement"));
        section("Aide", "Schémas", "Dossier des schémas")
                .add(note("Dossier : .minecraft/schematics"))
                .add(note(".litematic  .schem  .nbt"));
        section("Aide", "Couleurs de Minecraft", "Boutons et menus du jeu")
                .add(note("Packs RoseClient-Theme-*.zip dans resourcepacks"))
                .add(note("Options > Packs de ressources"));
    }

    // ================================================================ rendu

    private List<Card> visibleCards() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            if (q.isEmpty() ? c.cat.equals(CATS[cat]) : c.title.toLowerCase(Locale.ROOT).contains(q)) out.add(c);
        }
        return out;
    }

    private void layout() {
        placed.clear();
        List<Card> vis = visibleCards();
        int n = bw >= 560 ? 3 : (bw >= 360 ? 2 : 1);
        int gap = 8;
        int cw = (bw - gap * (n - 1)) / n;
        int[] colH = new int[n];
        for (Card c : vis) {
            int col = 0;
            for (int i = 1; i < n; i++) if (colH[i] < colH[col]) col = i;
            int h = c.height();
            placed.add(new Placed(c, bx + col * (cw + gap), by + colH[col] - scroll, cw, h));
            colH[col] += h + gap;
        }
        int m = 0;
        for (int h : colH) m = Math.max(m, h);
        contentH = Math.max(0, m - gap);
        int max = Math.max(0, contentH - bh);
        if (scroll > max) {
            scroll = max;
            layout();
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        TextRenderer tr = this.textRenderer;
        c.fill(0, 0, this.width, this.height, Ui.DIM);
        Ui.window(c, wx, wy, ww, wh);

        // en-tete
        c.fillGradient(wx, wy, wx + ww, wy + 34, Ui.lerp(Ui.BG | 0xFF000000, Ui.ACCENT, 0.16f), Ui.BG);
        c.fill(wx, wy, wx + ww, wy + 2, Ui.lerp(Ui.ACCENT, Ui.CYAN, Ui.pulse()));
        RoseLogo.draw(c, wx + 12, wy + 8, 1);
        c.drawText(tr, Text.literal("ROSE").formatted(Formatting.BOLD), wx + 32, wy + 8, Ui.lerp(Ui.ACCENT2, 0xFFFFFFFF, Ui.pulse() * 0.4f), true);
        c.drawText(tr, "client", wx + 32, wy + 19, Ui.MUTED, false);
        search.render(c, mx, my, delta);
        boolean xHover = mx >= wx + ww - 24 && mx <= wx + ww - 8 && my >= wy + 8 && my <= wy + 24;
        c.drawText(tr, "x", wx + ww - 18, wy + 12, xHover ? Ui.RED : Ui.MUTED, true);
        String state = Config.anchorFarm ? "AutoFarm actif" : "";
        if (!state.isEmpty()) c.drawText(tr, state, wx + ww - 30 - tr.getWidth(state), wy + 12, Ui.GREEN, true);

        // onglets
        boolean searching = !search.getText().trim().isEmpty();
        for (int i = 0; i < CATS.length; i++) {
            boolean sel = i == cat && !searching;
            boolean hover = mx >= tabX[i] && mx <= tabX[i] + tabW[i] && my >= wy + 40 && my <= wy + 62;
            int col = sel ? Ui.TEXT : (hover ? Ui.ACCENT2 : Ui.MUTED);
            c.drawText(tr, CATS[i], tabX[i] + 13, wy + 46, col, true);
            if (sel) c.fill(tabX[i] + 6, wy + 59, tabX[i] + tabW[i] - 6, wy + 61, Ui.ACCENT);
        }
        c.fill(bx, wy + 62, bx + bw, wy + 63, Ui.BORDER);

        // corps
        layout();
        String hint = null;
        c.enableScissor(bx, by, bx + bw, by + bh);
        for (Placed p : placed) {
            if (p.y() + p.h() < by || p.y() > by + bh) continue;
            Card card = p.card();
            int x = p.x(), y = p.y(), w = p.w(), h = p.h();
            boolean hoverCard = mx >= x && mx <= x + w && my >= Math.max(y, by) && my <= Math.min(y + h, by + bh);
            c.fill(x, y, x + w, y + h, 0x80100C18);
            c.drawBorder(x, y, w, h, hoverCard ? Ui.BORDER : ((Ui.BORDER & 0x00FFFFFF) | 0x80000000));

            boolean enabled = card.on != null && card.on.getAsBoolean();
            boolean headerHover = hoverCard && my <= y + HEADER;
            if (headerHover) c.fill(x + 1, y + 1, x + w - 1, y + HEADER, (Ui.ACCENT & 0x00FFFFFF) | 0x18000000);
            if (enabled) c.fill(x, y, x + 2, y + HEADER, Ui.ACCENT);
            if (!card.rows.isEmpty()) c.drawText(tr, card.open() ? "v" : ">", x + 8, y + 10, Ui.MUTED, true);
            c.drawText(tr, card.title, x + (card.rows.isEmpty() ? 10 : 20), y + 10, enabled || card.on == null ? Ui.TEXT : Ui.MUTED, true);

            if (card.on != null) {
                if (card.anim < 0) card.anim = enabled ? 1f : 0f;
                card.anim += ((enabled ? 1f : 0f) - card.anim) * 0.35f;
                int sw = 28, sh = 12, sx = x + w - sw - 8, sy = y + 8;
                c.fill(sx, sy, sx + sw, sy + sh, Ui.lerp(Ui.OFF, Ui.ACCENT, card.anim));
                int kx = sx + 1 + Math.round(card.anim * (sw - sh));
                c.fill(kx, sy + 1, kx + sh - 2, sy + sh - 1, 0xFFFFFFFF);
            }
            if (headerHover) hint = card.desc;

            if (card.open()) {
                int ry = y + HEADER + 4;
                for (Row r : card.rows) {
                    int rh = r.height();
                    boolean rowHover = r.selectable() && hoverCard && my >= Math.max(ry, by) && my <= Math.min(ry + rh, by + bh)
                            && my > y + HEADER;
                    if (ry + rh >= by && ry <= by + bh) r.render(c, tr, x + 6, ry, w - 12, rowHover, false);
                    if (rowHover && r.hint() != null) hint = r.hint();
                    ry += rh + 3;
                }
            }
        }
        c.disableScissor();

        if (placed.isEmpty()) {
            c.drawCenteredTextWithShadow(tr, Text.literal("Aucun module ne correspond"), this.width / 2, by + 24, Ui.MUTED);
        }

        // defilement
        if (contentH > bh) {
            int barH = Math.max(20, bh * bh / contentH);
            int barY = by + (int) ((long) (bh - barH) * scroll / Math.max(1, contentH - bh));
            c.fill(bx + bw + 3, by, bx + bw + 5, by + bh, Ui.OFF);
            c.fill(bx + bw + 3, barY, bx + bw + 5, barY + barH, Ui.ACCENT);
        }

        // pied de page
        c.fill(wx + 1, wy + wh - 22, wx + ww - 1, wy + wh - 1, Ui.SIDE);
        if (hint != null) c.drawText(tr, hint, wx + 12, wy + wh - 15, Ui.MUTED, true);
        String tabHint = "Tab : catégorie suivante";
        c.drawText(tr, tabHint, wx + ww - 12 - tr.getWidth(tabHint), wy + wh - 15, Ui.OFF, false);
    }

    // ========================================================= interactions

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (mx >= search.getX() && mx < search.getX() + search.getWidth()
                && my >= search.getY() && my < search.getY() + search.getHeight()) {
            return super.mouseClicked(mx, my, button);
        }
        search.setFocused(false);

        if (mx >= wx + ww - 24 && mx <= wx + ww - 8 && my >= wy + 8 && my <= wy + 24) {
            this.close();
            return true;
        }
        for (int i = 0; i < CATS.length; i++) {
            if (mx >= tabX[i] && mx <= tabX[i] + tabW[i] && my >= wy + 40 && my <= wy + 62) {
                cat = i;
                lastCat = i;
                scroll = 0;
                search.setText("");
                return true;
            }
        }
        if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
            layout();
            for (Placed p : placed) {
                if (mx < p.x() || mx > p.x() + p.w() || my < p.y() || my > p.y() + p.h()) continue;
                Card card = p.card();
                if (my <= p.y() + HEADER) {
                    if (card.on != null && mx >= p.x() + p.w() - 44) card.setOn.accept(!card.on.getAsBoolean());
                    else if (!card.rows.isEmpty()) OPEN.put(card.id, !card.open());
                    return true;
                }
                if (card.open()) {
                    int ry = p.y() + HEADER + 4;
                    for (Row r : card.rows) {
                        int rh = r.height();
                        if (my >= ry && my <= ry + rh) {
                            if (r.selectable()) {
                                if (r instanceof Draggable d) dragging = d;
                                r.click(mx, my, p.x() + 6, ry, p.w() - 12);
                            }
                            return true;
                        }
                        ry += rh + 3;
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) (vertical * 24));
        layout();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!search.isFocused()) {
            if (ChestEspClient.menuKey != null && ChestEspClient.menuKey.matchesKey(keyCode, scanCode)) {
                this.close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                cat = (cat + 1) % CATS.length;
                lastCat = cat;
                scroll = 0;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
