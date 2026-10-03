package tank;

/**
 * Headless base safe-zone test. Run:
 *   java -cp out tank.BaseZoneTest
 */
public final class BaseZoneTest {
    public static void main(String[] args) {
        GameLogic g = new GameLogic();
        if (g.baseZone == null) throw new AssertionError("no base zone generated");
        System.out.println("base zone at " + (int) g.baseZone.x + "," + (int) g.baseZone.y
                + " size " + (int) g.baseZone.w + "x" + (int) g.baseZone.h);
        for (GameLogic.Obstacle o : g.obstacles) {
            if (g.baseZone.intersects(o.x, o.y, o.w, o.h, 0)) {
                throw new AssertionError("obstacle overlaps the base zone");
            }
        }

        g.obstacles.clear();
        g.caches.clear();
        GameLogic.Tank p = g.player;
        GameLogic.Tank e = g.enemies.get(0);
        for (int i = 1; i < g.enemies.size(); i++) {
            g.enemies.get(i).x = 1700; g.enemies.get(i).y = 150;
        }

        // 1) A shot hitting a tank inside the base deals no damage.
        double bx = g.baseZone.x + g.baseZone.w / 2;
        double by = g.baseZone.y + g.baseZone.h / 2;
        e.x = bx; e.y = by; e.hp = e.maxHp; e.alive = true;
        p.x = bx - 120; p.y = by; p.hp = p.maxHp; p.alive = true; p.reload = 0;
        shootEast(g, p, e);
        if (e.hp != e.maxHp) throw new AssertionError("tank inside base took damage: hp=" + e.hp);
        System.out.println("shot into base: target hp still " + (int) e.hp);

        // 2) A shot fired from inside the base deals no damage outside it.
        g.shells.clear();
        p.x = bx; p.y = by; p.hp = p.maxHp; p.alive = true; p.reload = 0;
        e.x = bx + 120; e.y = by; e.hp = e.maxHp; e.alive = true;
        shootEast(g, p, e);
        if (e.hp != e.maxHp) throw new AssertionError("shot from base dealt damage: hp=" + e.hp);
        System.out.println("shot out of base: target hp still " + (int) e.hp);

        // 3) The same shot outside the base does damage (sanity check).
        g.shells.clear();
        p.x = 200; p.y = 200; p.hp = p.maxHp; p.alive = true; p.reload = 0;
        e.x = 320; e.y = 200; e.hp = e.maxHp; e.alive = true;
        if (g.isInBase(p.x, p.y) || g.isInBase(e.x, e.y)) {
            p.x = 1500; p.y = 1100; e.x = 1620; e.y = 1100;
        }
        shootEast(g, p, e);
        if (e.hp >= e.maxHp) throw new AssertionError("normal shot outside base dealt no damage");
        System.out.println("normal shot outside base: target hp dropped to " + (int) e.hp);

        System.out.println("BASE ZONE TEST PASSED");
    }

    private static void shootEast(GameLogic g, GameLogic.Tank p, GameLogic.Tank e) {
        GameLogic.Input in = new GameLogic.Input();
        in.aimX = e.x; in.aimY = e.y;
        in.fire = true;
        g.update(1.0 / 60.0, in);
        in.fire = false;
        for (int i = 0; i < 90 && !g.shells.isEmpty(); i++) g.update(1.0 / 60.0, in);
    }
}
