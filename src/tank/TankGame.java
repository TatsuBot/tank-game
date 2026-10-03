package tank;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;

/**
 * Swing front-end for the tank prototype. Rendering + input only;
 * all simulation lives in {@link GameLogic}.
 *
 * The panel is a 960x640 viewport. The world can be larger, so drawing
 * is done through a camera that follows the player, and the mouse aim
 * is converted from screen to world coordinates.
 */
public final class TankGame extends JPanel implements ActionListener {

    private final GameLogic game = new GameLogic();
    private final GameLogic.Input input = new GameLogic.Input();
    private Point mouse = new Point(480, 100);
    private double cameraX, cameraY;
    private long lastNanos;

    public TankGame() {
        setPreferredSize(new Dimension((int) GameLogic.VIEW_W, (int) GameLogic.VIEW_H));
        setBackground(new Color(74, 88, 54));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { setKey(e.getKeyCode(), true); }
            @Override public void keyReleased(KeyEvent e) { setKey(e.getKeyCode(), false); }
        });
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mouse = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) { mouse = e.getPoint(); }
            @Override public void mousePressed(MouseEvent e) { input.fire = true; }
            @Override public void mouseReleased(MouseEvent e) { input.fire = false; }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        updateCamera();
        lastNanos = System.nanoTime();
        new Timer(16, this).start();
    }

    private void setKey(int code, boolean down) {
        switch (code) {
            case KeyEvent.VK_W -> input.forward = down;
            case KeyEvent.VK_S -> input.back = down;
            case KeyEvent.VK_A -> input.left = down;
            case KeyEvent.VK_D -> input.right = down;
            case KeyEvent.VK_SPACE -> input.fire = down;
            case KeyEvent.VK_Q -> { if (down) game.cycleShellType(); }
            case KeyEvent.VK_1 -> { if (down) game.selectShellType(GameLogic.ShellType.STANDARD); }
            case KeyEvent.VK_2 -> { if (down) game.selectShellType(GameLogic.ShellType.AUX); }
            case KeyEvent.VK_R -> { if (down && (game.lost() || game.won())) game.reset(); }
        }
    }

    private void updateCamera() {
        cameraX = clamp(game.player.x - GameLogic.VIEW_W / 2, 0, Math.max(0, game.worldW - GameLogic.VIEW_W));
        cameraY = clamp(game.player.y - GameLogic.VIEW_H / 2, 0, Math.max(0, game.worldH - GameLogic.VIEW_H));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;
        updateCamera();
        input.aimX = mouse.x + cameraX; input.aimY = mouse.y + cameraY;
        game.update(dt, input);
        updateCamera();
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform screen = g.getTransform();
        g.translate(-cameraX, -cameraY);

        // travelable terrain tiles (grass / sand, identical behaviour)
        drawTerrain(g);
        drawBaseZone(g);
        // world boundary
        g.setColor(new Color(120, 130, 90));
        g.drawRect(1, 1, (int) game.worldW - 2, (int) game.worldH - 2);

        // obstacles (cover), aligned to terrain tiles
        for (GameLogic.Obstacle o : game.obstacles) {
            g.setColor(new Color(96, 90, 80));
            g.fillRect((int) o.x, (int) o.y, (int) o.w, (int) o.h);
            g.setColor(new Color(70, 66, 58));
            g.drawRect((int) o.x, (int) o.y, (int) o.w, (int) o.h);
            // faint seams so multi-tile blocks read as tiled pieces
            g.setColor(new Color(82, 77, 68));
            for (int tx = 1; tx < o.tileW; tx++) {
                int sx = (int) (o.x + tx * GameLogic.TILE_SIZE);
                g.drawLine(sx, (int) o.y + 2, sx, (int) (o.y + o.h) - 2);
            }
            for (int ty = 1; ty < o.tileH; ty++) {
                int sy = (int) (o.y + ty * GameLogic.TILE_SIZE);
                g.drawLine((int) o.x + 2, sy, (int) (o.x + o.w) - 2, sy);
            }
        }

        // item caches
        for (GameLogic.Cache c : game.caches) {
            if (!c.alive) continue;
            g.setColor(new Color(150, 110, 30));
            g.fillRect((int) c.x - 11, (int) c.y - 11, 22, 22);
            g.setColor(new Color(90, 64, 16));
            g.drawRect((int) c.x - 11, (int) c.y - 11, 22, 22);
            g.setColor(Color.ORANGE);
            g.fillOval((int) c.x - 4, (int) c.y - 7, 8, 14); // shell silhouette
        }

        // shells in flight
        for (GameLogic.Shell s : game.shells) {
            if (s.type == GameLogic.ShellType.AUX) {
                g.setColor(Color.ORANGE);
                g.fillOval((int) s.x - 5, (int) s.y - 5, 10, 10);
            } else {
                g.setColor(Color.YELLOW);
                g.fillOval((int) s.x - 3, (int) s.y - 3, 6, 6);
            }
        }

        for (GameLogic.Tank e : game.enemies) drawTank(g, e, new Color(112, 76, 58));
        drawTank(g, game.player, new Color(73, 90, 64));

        g.setTransform(screen);
        drawHud(g);
    }

    /** Yellow base safe zone: translucent fill, dashed border, and a label. */
    private void drawBaseZone(Graphics2D g) {
        GameLogic.BaseZone b = game.baseZone;
        if (b == null) return;
        Stroke oldStroke = g.getStroke();
        g.setColor(new Color(255, 214, 64, 78));
        g.fillRect((int) b.x, (int) b.y, (int) b.w, (int) b.h);
        g.setColor(new Color(250, 204, 21, 210));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{10f, 7f}, 0f));
        g.drawRect((int) b.x + 1, (int) b.y + 1, (int) b.w - 2, (int) b.h - 2);
        g.setStroke(oldStroke);
        g.setColor(new Color(113, 83, 10));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("BASE", (int) b.x + 8, (int) b.y + 18);
    }

    /** Stable per-tile hash so decoration never flickers between frames. */
    private static int tileHash(int col, int row, int salt) {
        int h = col * 374761393 + row * 668265263 + salt * 974634211;
        h = (h ^ (h >> 13)) * 1274126177;
        return Math.abs(h ^ (h >> 16));
    }

    private void drawTerrain(Graphics2D g) {
        int tile = (int) GameLogic.TILE_SIZE;
        int minCol = Math.max(0, (int) (cameraX / GameLogic.TILE_SIZE));
        int maxCol = Math.min(game.tileCols - 1, (int) ((cameraX + GameLogic.VIEW_W) / GameLogic.TILE_SIZE));
        int minRow = Math.max(0, (int) (cameraY / GameLogic.TILE_SIZE));
        int maxRow = Math.min(game.tileRows - 1, (int) ((cameraY + GameLogic.VIEW_H) / GameLogic.TILE_SIZE));
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                int x = col * tile, y = row * tile;
                int hash = tileHash(col, row, 1);
                if (game.terrainAt(col, row) == GameLogic.TerrainType.SAND) {
                    g.setColor(hash % 2 == 0 ? new Color(170, 148, 98) : new Color(178, 156, 104));
                    g.fillRect(x, y, tile, tile);
                    // sand speckles
                    g.setColor(new Color(146, 124, 78));
                    for (int i = 0; i < 4; i++) {
                        int dx = tileHash(col, row, 20 + i) % 40 + 4;
                        int dy = tileHash(col, row, 40 + i) % 40 + 4;
                        g.fillOval(x + dx, y + dy, 3, 2);
                    }
                } else if (game.terrainAt(col, row) == GameLogic.TerrainType.SWAMP) {
                    // murky mud base
                    g.setColor(hash % 2 == 0 ? new Color(69, 74, 46) : new Color(62, 69, 43));
                    g.fillRect(x, y, tile, tile);
                    // dull water pools
                    for (int i = 0; i < 2; i++) {
                        int dx = tileHash(col, row, 100 + i) % 24 + 4;
                        int dy = tileHash(col, row, 120 + i) % 26 + 8;
                        g.setColor(new Color(70, 96, 84));
                        g.fillOval(x + dx, y + dy, 18, 8);
                        g.setColor(new Color(104, 130, 112));
                        g.fillOval(x + dx + 4, y + dy + 2, 8, 3);
                    }
                    // mud flecks and reeds
                    g.setColor(new Color(96, 86, 58));
                    for (int i = 0; i < 4; i++) {
                        int dx = tileHash(col, row, 140 + i) % 40 + 4;
                        int dy = tileHash(col, row, 160 + i) % 40 + 4;
                        g.fillOval(x + dx, y + dy, 4, 2);
                    }
                    g.setColor(new Color(39, 56, 33));
                    for (int i = 0; i < 2; i++) {
                        int dx = tileHash(col, row, 180 + i) % 36 + 6;
                        int dy = tileHash(col, row, 200 + i) % 28 + 16;
                        g.drawLine(x + dx, y + dy, x + dx - 2, y + dy - 12);
                        g.drawLine(x + dx + 4, y + dy, x + dx + 6, y + dy - 14);
                    }
                } else {
                    drawGrassTile(g, x, y, col, row);
                }
                g.setColor(new Color(0, 0, 0, 22));
                g.drawRect(x, y, tile, tile);
            }
        }
    }

    /**
     * Simpler grass, inspired by classic top-down handheld RPG tiles:
     * one bright flat green per tile, a faint checker between neighbouring
     * tiles, and a few clean three-stroke tufts. No mottling or flecks.
     * (Original drawing code and colours, in that general style.)
     */
    private void drawGrassTile(Graphics2D g, int x, int y, int col, int row) {
        boolean light = ((col + row) & 1) == 0;
        g.setColor(light ? new Color(104, 176, 66) : new Color(98, 168, 61));
        g.fillRect(x, y, (int) GameLogic.TILE_SIZE, (int) GameLogic.TILE_SIZE);

        // Three small tufts per tile, placed by the stable tile hash.
        for (int i = 0; i < 3; i++) {
            int dx = tileHash(col, row, 320 + i) % 34 + 7;
            int dy = tileHash(col, row, 340 + i) % 30 + 13;
            // darker blades
            g.setColor(new Color(55, 124, 42));
            g.drawLine(x + dx, y + dy, x + dx - 3, y + dy - 7);
            g.drawLine(x + dx + 3, y + dy, x + dx + 3, y + dy - 9);
            g.drawLine(x + dx + 6, y + dy, x + dx + 9, y + dy - 7);
            // one lighter blade per tuft
            g.setColor(new Color(139, 207, 92));
            g.drawLine(x + dx + 3, y + dy, x + dx + 5, y + dy - 8);
        }
    }

    private void drawTank(Graphics2D g, GameLogic.Tank t, Color body) {
        if (!t.alive) {
            drawHull(g, t, new Color(56, 56, 53));
            drawTurret(g, t, new Color(48, 48, 46));
            return;
        }
        drawHull(g, t, body);
        drawTurret(g, t, body);
        // hp bar
        g.setColor(Color.BLACK);
        g.fillRect((int) t.x - 22, (int) t.y - 36, 44, 5);
        g.setColor(t.isPlayer ? Color.GREEN : Color.RED);
        g.fillRect((int) t.x - 22, (int) t.y - 36, (int) (44 * Math.max(0, t.hp) / t.maxHp), 5);
    }

    /**
     * Longer, lower hull in muted military colours: soft shadow, segmented
     * tracks, side skirts, a tapered nose, camouflage blotches, and quiet
     * panel detail. No bright stripes or cartoon highlights.
     */
    private void drawHull(Graphics2D g, GameLogic.Tank t, Color body) {
        AffineTransform old = g.getTransform();
        Stroke oldStroke = g.getStroke();
        g.translate(t.x, t.y);
        g.rotate(t.hullAngle);
        g.setStroke(new BasicStroke(1.3f));

        // soft shadow, offset down-right
        g.setColor(new Color(0, 0, 0, 55));
        g.fillRoundRect(-21, -11, 48, 28, 9, 9);

        // tracks with tread segments
        for (int side = -1; side <= 1; side += 2) {
            int y = side < 0 ? -17 : 10;
            g.setColor(new Color(35, 36, 38));
            g.fillRoundRect(-24, y, 48, 7, 3, 3);
            g.setColor(new Color(62, 64, 66));
            for (int tx = -22; tx < 24; tx += 5) g.drawLine(tx, y + 1, tx, y + 5);
        }

        // side skirts over the tracks
        g.setColor(body.darker());
        g.fillRect(-22, -14, 44, 4);
        g.fillRect(-22, 10, 44, 4);

        // long tapered hull
        int[] hx = {-22, -13, 11, 20, 23, 20, 11, -13, -22, -24};
        int[] hy = {-11, -13, -12, -7, 0, 7, 12, 13, 11, 0};
        g.setColor(body);
        g.fillPolygon(hx, hy, hx.length);
        g.setColor(body.darker().darker());
        g.drawPolygon(hx, hy, hx.length);

        // camouflage blotches
        g.setColor(new Color(body.darker().getRed(), body.darker().getGreen(), body.darker().getBlue(), 150));
        int[] c1x = {-19, -9, -5, -11, -19};
        int[] c1y = {-9, -11, -4, -1, -4};
        g.fillPolygon(c1x, c1y, c1x.length);
        int[] c2x = {1, 10, 16, 9, 1};
        int[] c2y = {4, 3, 8, 11, 9};
        g.fillPolygon(c2x, c2y, c2x.length);
        g.setColor(new Color(body.brighter().getRed(), body.brighter().getGreen(), body.brighter().getBlue(), 110));
        int[] c3x = {-7, 2, 6, -1, -8};
        int[] c3y = {-11, -10, -5, -3, -6};
        g.fillPolygon(c3x, c3y, c3x.length);

        // sloped front glacis
        int[] gx = {11, 20, 23, 20, 11, 15};
        int[] gy = {-12, -7, 0, 7, 12, 0};
        g.setColor(body.brighter());
        g.fillPolygon(gx, gy, gx.length);

        // rear deck vents and a tow line along the side
        g.setColor(body.darker().darker());
        for (int vy = -7; vy <= 7; vy += 5) g.drawLine(-20, vy, -13, vy);
        g.setColor(new Color(30, 30, 30, 130));
        g.drawLine(-18, -13, 8, -13);

        g.setStroke(oldStroke);
        g.setTransform(old);
    }

    /**
     * Square turret: segmented barrel with a small muzzle brake, square
     * mantlet and hatch, rear stowage bin, and camouflage.
     */
    private void drawTurret(Graphics2D g, GameLogic.Tank t, Color body) {
        AffineTransform old = g.getTransform();
        Stroke oldStroke = g.getStroke();
        g.translate(t.x, t.y);
        g.rotate(t.turretAngle);
        g.setStroke(new BasicStroke(1.3f));

        // barrel: tapered, with thermal-sleeve segments and a small brake
        int[] bx = {8, 38, 40, 38, 8};
        int[] by = {-3, -2, 0, 2, 3};
        g.setColor(new Color(38, 39, 41));
        g.fillPolygon(bx, by, bx.length);
        g.setColor(new Color(56, 57, 59));
        g.fillRect(16, -3, 6, 6);
        g.fillRect(26, -3, 5, 6);
        g.setColor(new Color(30, 30, 32));
        g.fillRect(37, -4, 6, 8);
        g.drawLine(39, -3, 39, 3);

        // mantlet where the barrel meets the turret (square)
        g.setColor(body.darker());
        g.fillRect(8, -7, 8, 14);
        g.setColor(body.darker().darker());
        g.drawRect(8, -7, 8, 14);

        // rear stowage bin
        g.setColor(body.darker());
        g.fillRect(-21, -9, 7, 18);
        g.setColor(body.darker().darker());
        g.drawRect(-21, -9, 7, 18);
        g.drawLine(-21, -3, -14, -3);
        g.drawLine(-21, 3, -14, 3);

        // square turret body with only the front corners clipped
        int[] tx = {-16, 10, 16, 16, 10, -16};
        int[] ty = {-12, -12, -7, 7, 12, 12};
        g.setColor(new Color(
                Math.min(255, body.getRed() + 14),
                Math.min(255, body.getGreen() + 14),
                Math.min(255, body.getBlue() + 14)));
        g.fillPolygon(tx, ty, tx.length);
        g.setColor(body.darker().darker());
        g.drawPolygon(tx, ty, tx.length);
        // flat side panel lines reinforce the boxy shape
        g.setColor(new Color(body.darker().getRed(), body.darker().getGreen(), body.darker().getBlue(), 120));
        g.drawLine(-16, -6, 10, -6);
        g.drawLine(-16, 6, 10, 6);

        // camouflage blotch and a single square hatch
        g.setColor(new Color(body.darker().getRed(), body.darker().getGreen(), body.darker().getBlue(), 150));
        int[] cx = {-12, -2, 5, 0, -11};
        int[] cy = {-11, -10, -5, -3, -6};
        g.fillPolygon(cx, cy, cx.length);
        g.setColor(body.darker());
        g.fillRoundRect(-7, -6, 11, 11, 3, 3);
        g.setColor(body.darker().darker());
        g.drawRoundRect(-7, -6, 11, 11, 3, 3);
        g.drawLine(-4, 0, 2, 0);

        g.setStroke(oldStroke);
        g.setTransform(old);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        GameLogic.Tank p = game.player;
        g.drawString("Hull: " + (int) Math.max(0, p.hp) + " / " + (int) p.maxHp, 12, 22);
        double reloadFrac = 1 - (p.reload / game.config.playerReloadSeconds);
        g.drawString(p.reload <= 0 ? "READY" : "Reloading...", 12, 42);
        g.setColor(Color.BLACK);
        g.fillRect(12, 48, 120, 8);
        g.setColor(Color.ORANGE);
        g.fillRect(12, 48, (int) (120 * Math.max(0, Math.min(1, reloadFrac))), 8);
        g.setColor(Color.WHITE);
        g.drawString("Enemies left: " + game.enemiesAlive(), 12, 74);

        String shellLine;
        if (p.selectedShell == GameLogic.ShellType.AUX) {
            shellLine = "Shell: AUX x" + p.auxShells + "  (Q / 1-2 switch)";
        } else if (p.auxShells > 0) {
            shellLine = "Shell: STANDARD  ·  AUX x" + p.auxShells + " carried (Q / 2 to load)";
        } else {
            shellLine = "Shell: STANDARD  ·  find a cache for AUX";
        }
        g.drawString(shellLine, 12, 94);
        if (game.isTankInBase(p)) {
            g.setColor(new Color(255, 224, 102));
            g.drawString("IN BASE — SAFE", 12, 114);
            g.setColor(Color.WHITE);
        }
        g.drawString("WASD drive · Mouse aim · Click/Space fire · Q shell · R reset", 12, getHeight() - 12);

        drawMinimap(g);

        if (game.won() || game.lost()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            String msg = game.won() ? "VICTORY" : "DESTROYED";
            FontMetrics fm = g.getFontMetrics();
            g.setColor(game.won() ? Color.GREEN : Color.RED);
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            String sub = "Press R to play again";
            fm = g.getFontMetrics();
            g.drawString(sub, (getWidth() - fm.stringWidth(sub)) / 2, getHeight() / 2 + 30);
        }
    }

    private void drawMinimap(Graphics2D g) {
        double scale = 192 / game.worldW;
        int mw = 192, mh = (int) Math.round(game.worldH * scale);
        int mx = getWidth() - mw - 12, my = 12;
        // terrain tiles on the minimap
        for (int row = 0; row < game.tileRows; row++) {
            for (int col = 0; col < game.tileCols; col++) {
                GameLogic.TerrainType tt = game.terrainAt(col, row);
                g.setColor(tt == GameLogic.TerrainType.SAND ? new Color(170, 148, 98)
                        : tt == GameLogic.TerrainType.SWAMP ? new Color(70, 96, 84)
                        : new Color(104, 176, 66));
                g.fillRect(mx + (int) (col * GameLogic.TILE_SIZE * scale),
                        my + (int) (row * GameLogic.TILE_SIZE * scale),
                        (int) Math.ceil(GameLogic.TILE_SIZE * scale),
                        (int) Math.ceil(GameLogic.TILE_SIZE * scale));
            }
        }
        g.setColor(Color.GRAY);
        g.drawRect(mx, my, mw, mh);
        // base safe zone
        if (game.baseZone != null) {
            GameLogic.BaseZone b = game.baseZone;
            g.setColor(new Color(255, 214, 64, 180));
            g.fillRect(mx + (int) (b.x * scale), my + (int) (b.y * scale),
                    Math.max(4, (int) (b.w * scale)), Math.max(4, (int) (b.h * scale)));
        }
        // obstacles
        g.setColor(new Color(150, 145, 135));
        for (GameLogic.Obstacle o : game.obstacles) {
            g.fillRect(mx + (int) (o.x * scale), my + (int) (o.y * scale),
                    Math.max(2, (int) (o.w * scale)), Math.max(2, (int) (o.h * scale)));
        }
        // caches
        g.setColor(Color.ORANGE);
        for (GameLogic.Cache c : game.caches) if (c.alive)
            g.fillOval(mx + (int) (c.x * scale) - 2, my + (int) (c.y * scale) - 2, 4, 4);
        // enemies
        g.setColor(Color.RED);
        for (GameLogic.Tank e : game.enemies) if (e.alive)
            g.fillOval(mx + (int) (e.x * scale) - 2, my + (int) (e.y * scale) - 2, 5, 5);
        // player
        g.setColor(Color.CYAN);
        GameLogic.Tank p = game.player;
        g.fillOval(mx + (int) (p.x * scale) - 3, my + (int) (p.y * scale) - 3, 6, 6);
        // current view
        g.setColor(Color.WHITE);
        g.drawRect(mx + (int) (cameraX * scale), my + (int) (cameraY * scale),
                (int) (GameLogic.VIEW_W * scale), (int) (GameLogic.VIEW_H * scale));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Tank Prototype v0.9 — Java");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            TankGame panel = new TankGame();
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
