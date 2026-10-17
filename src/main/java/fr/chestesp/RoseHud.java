package fr.chestesp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/** HUD minimaliste : pastille infos, CPS, coordonnees, modules actifs, touches, boussole, effets, inventaire... */
public final class RoseHud {

    private static final Deque<Long> LMB = new ArrayDeque<>();
    private static final Deque<Long> RMB = new ArrayDeque<>();
    private static boolean lWas = false, rWas = false;
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private RoseHud() {}

    public static void render(DrawContext c, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        pollMouse(mc);
        if (mc.currentScreen != null) return;

        TextRenderer tr = mc.textRenderer;
        ClientPlayerEntity p = mc.player;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = 6, y = 6;

        if (Config.hudFps) y = chip(c, tr, x, y, infoText(mc, p));
        if (Config.hudCps) y = chip(c, tr, x, y, "CPS " + cps(LMB) + " | " + cps(RMB));
        if (Config.hudCoords) {
            y = chip(c, tr, x, y, "XYZ " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
            y = chip(c, tr, x, y, p.getHorizontalFacing().asString());
        }
        if (Config.hudModules) y = moduleList(c, tr, x, y + 2);
        if (Config.hudKeystrokes) keystrokes(c, tr, mc, x, y + 3);
        if (Config.hudKeybinds) keybinds(c, tr, x, sh / 2 + 20);
        if (Config.hudArmor) armor(c, tr, mc, p);
        if (Config.hudCompass) compass(c, tr, p, sw);
        if (Config.hudEffects) effects(c, tr, p, sw);
        if (Config.hudPlayerCard) playerCard(c, tr, p, sw);
        if (Config.hudInventory) inventory(c, tr, p, sw, sh);
    }

    // ------------------------------------------------------------------ pieces

    private static String infoText(MinecraftClient mc, ClientPlayerEntity p) {
        String s = "ROSE  " + mc.getCurrentFps() + "fps";
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) s += "  " + e.getLatency() + "ms";
        }
        return s;
    }

    private static int chip(DrawContext c, TextRenderer tr, int x, int y, String text) {
        int w = tr.getWidth(text) + 12;
        c.fill(x, y, x + w, y + 14, 0xB00D0A14);
        c.fill(x, y, x + 2, y + 14, Ui.ACCENT);
        c.drawText(tr, text, x + 7, y + 3, Ui.TEXT, true);
        return y + 17;
    }

    private static List<String> activeModules() {
        List<String> l = new ArrayList<>();
        if (Config.chestEsp) l.add("Chest ESP");
        if (Config.customEsp) l.add("Custom ESP");
        if (Config.playerEsp) l.add("Player ESP");
        if (Config.anchorFarm) l.add("AutoFarm");
        if (Config.autoPickup) l.add("Ramassage");
        if (Config.toggleSprint) l.add("Sprint auto");
        if (BuildBot.mode != BuildBot.Mode.NONE) l.add("Build");
        return l;
    }

    private static int moduleList(DrawContext c, TextRenderer tr, int x, int y) {
        for (String name : activeModules()) {
            int w = tr.getWidth(name) + 26;
            c.fill(x, y, x + w, y + 16, 0x900D0A14);
            c.fill(x + 3, y + 3, x + 13, y + 13, (Ui.ACCENT & 0x00FFFFFF) | 0xE0000000);
            c.drawText(tr, name, x + 18, y + 4, Ui.TEXT, true);
            y += 18;
        }
        return y;
    }

    private static void key(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        c.fill(x, y, x + w, y + h, down ? ((Ui.ACCENT & 0x00FFFFFF) | 0xE0000000) : 0xB00D0A14);
        c.drawBorder(x, y, w, h, down ? Ui.ACCENT2 : Ui.BORDER);
        c.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : Ui.MUTED, true);
    }

    private static void keystrokes(DrawContext c, TextRenderer tr, MinecraftClient mc, int x, int y) {
        int s = 22;
        key(c, tr, x + s + 2, y, s, s, "W", mc.options.forwardKey.isPressed());
        key(c, tr, x, y + s + 2, s, s, "A", mc.options.leftKey.isPressed());
        key(c, tr, x + s + 2, y + s + 2, s, s, "S", mc.options.backKey.isPressed());
        key(c, tr, x + 2 * (s + 2), y + s + 2, s, s, "D", mc.options.rightKey.isPressed());
        int w = (3 * s + 4 - 2) / 2;
        key(c, tr, x, y + 2 * (s + 2), w, 18, "LMB " + cps(LMB), lWas);
        key(c, tr, x + w + 2, y + 2 * (s + 2), w, 18, "RMB " + cps(RMB), rWas);
        key(c, tr, x, y + 2 * (s + 2) + 20, 3 * s + 4, 12, "SPACE", mc.options.jumpKey.isPressed());
    }

    private static void keybinds(DrawContext c, TextRenderer tr, int x, int y) {
        String[][] rows = {
                {"Menu", bind(ChestEspClient.menuKey)},
                {"Coin 1", bind(ChestEspClient.pos1Key)},
                {"Coin 2", bind(ChestEspClient.pos2Key)},
                {"Zoom", bind(ChestEspClient.zoomKey)},
        };
        int w = 110, h = 16 + rows.length * 13;
        c.fill(x, y, x + w, y + h, 0xB00D0A14);
        c.fill(x, y, x + 2, y + h, Ui.ACCENT);
        c.drawText(tr, "Raccourcis", x + 8, y + 4, Ui.ACCENT2, true);
        for (int i = 0; i < rows.length; i++) {
            c.drawText(tr, rows[i][0], x + 8, y + 16 + i * 13, Ui.TEXT, true);
            c.drawText(tr, rows[i][1], x + w - 8 - tr.getWidth(rows[i][1]), y + 16 + i * 13, Ui.MUTED, true);
        }
    }

    private static String bind(KeyBinding k) {
        return k == null ? "-" : k.getBoundKeyLocalizedText().getString();
    }

    private static void armor(DrawContext c, TextRenderer tr, MinecraftClient mc, ClientPlayerEntity p) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int x = mc.getWindow().getScaledWidth() - 26;
        int y = mc.getWindow().getScaledHeight() / 2 - 40;
        for (EquipmentSlot slot : slots) {
            ItemStack st = p.getEquippedStack(slot);
            c.fill(x - 3, y - 2, x + 19, y + 18, 0xB00D0A14);
            c.fill(x - 3, y - 2, x - 1, y + 18, Ui.ACCENT);
            if (!st.isEmpty()) {
                c.drawItem(st, x, y);
                c.drawStackOverlay(tr, st, x, y);
            }
            y += 22;
        }
    }

    /** Barre de cap en haut de l'ecran. */
    private static void compass(DrawContext c, TextRenderer tr, ClientPlayerEntity p, int sw) {
        int cw = 300, x0 = (sw - cw) / 2, y0 = 6;
        c.fill(x0, y0, x0 + cw, y0 + 20, 0xB00D0A14);
        c.drawBorder(x0, y0, cw, 20, Ui.BORDER);
        float heading = MathHelper.wrapDegrees(p.getYaw() + 180f);
        if (heading < 0) heading += 360f;
        int base = Math.round(heading / 15f) * 15;
        for (int k = -6; k <= 6; k++) {
            int deg = base + k * 15;
            float off = (deg - heading) / 90f * (cw / 2f - 12);
            int n = ((deg % 360) + 360) % 360;
            String label = n % 45 == 0 ? DIRS[n / 45] : String.valueOf(n);
            boolean center = Math.abs(deg - heading) < 7.5f;
            int lx = x0 + cw / 2 + Math.round(off) - tr.getWidth(label) / 2;
            if (lx < x0 + 4 || lx + tr.getWidth(label) > x0 + cw - 4) continue;
            c.drawText(tr, label, lx, y0 + 6, center ? 0xFFFFFFFF : (n % 45 == 0 ? Ui.ACCENT2 : Ui.MUTED), true);
        }
        c.drawCenteredTextWithShadow(tr, "v", sw / 2, y0 + 22, Ui.ACCENT);
    }

    private static void effects(DrawContext c, TextRenderer tr, ClientPlayerEntity p, int sw) {
        Collection<StatusEffectInstance> list = p.getStatusEffects();
        if (list.isEmpty()) return;
        int w = 130, x = sw - w - 34, y = 40;
        int h = 16 + list.size() * 14;
        c.fill(x, y, x + w, y + h, 0xB00D0A14);
        c.fill(x, y, x + 2, y + h, Ui.ACCENT);
        c.drawText(tr, "Effets", x + 8, y + 4, Ui.ACCENT2, true);
        int ry = y + 16;
        for (StatusEffectInstance e : list) {
            String name = e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1);
            String time;
            if (e.isInfinite()) {
                time = "--";
            } else {
                int s = e.getDuration() / 20;
                time = (s / 60) + ":" + String.format("%02d", s % 60);
            }
            c.drawText(tr, name, x + 8, ry, Ui.TEXT, true);
            c.drawText(tr, time, x + w - 6 - tr.getWidth(time), ry, Ui.MUTED, true);
            ry += 14;
        }
    }

    private static void playerCard(DrawContext c, TextRenderer tr, ClientPlayerEntity p, int sw) {
        int w = 130, x = sw - w - 34, y = 6;
        c.fill(x, y, x + w, y + 26, 0xB00D0A14);
        c.fill(x, y, x + 2, y + 26, Ui.ACCENT);
        String name = p.getName().getString();
        c.drawText(tr, name, x + 8, y + 4, Ui.TEXT, true);
        String hp = (int) Math.ceil(p.getHealth()) + "/" + (int) Math.ceil(p.getMaxHealth());
        c.drawText(tr, hp, x + w - 6 - tr.getWidth(hp), y + 4, Ui.MUTED, true);
        float t = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
        c.fill(x + 8, y + 18, x + w - 6, y + 21, Ui.OFF);
        c.fill(x + 8, y + 18, x + 8 + (int) ((w - 14) * t), y + 21, Ui.lerp(Ui.RED, Ui.GREEN, t));
    }

    private static void inventory(DrawContext c, TextRenderer tr, ClientPlayerEntity p, int sw, int sh) {
        int x0 = sw - 9 * 18 - 10, y0 = sh - 3 * 18 - 40;
        c.fill(x0 - 4, y0 - 4, x0 + 9 * 18 + 4, y0 + 3 * 18 + 4, 0xB00D0A14);
        c.fill(x0 - 4, y0 - 4, x0 - 2, y0 + 3 * 18 + 4, Ui.ACCENT);
        for (int i = 9; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            int col = (i - 9) % 9, row = (i - 9) / 9;
            c.drawItem(st, x0 + col * 18, y0 + row * 18);
            c.drawStackOverlay(tr, st, x0 + col * 18, y0 + row * 18);
        }
    }

    // --------------------------------------------------------------------- CPS

    private static void pollMouse(MinecraftClient mc) {
        if (mc.currentScreen != null) {
            lWas = false;
            rWas = false;
            return;
        }
        long h = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (l && !lWas) LMB.addLast(now);
        if (r && !rWas) RMB.addLast(now);
        lWas = l;
        rWas = r;
    }

    private static int cps(Deque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }
}
