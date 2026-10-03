package tank;

/**
 * Headless smoke test: runs the simulation without a display and
 * asserts the core loop works. Run:
 *   java -cp out tank.SmokeTest
 */
public final class SmokeTest {
    public static void main(String[] args) {
        GameLogic g = new GameLogic();
        GameLogic.Input in = new GameLogic.Input();
        in.forward = true;
        in.aimX = 800; in.aimY = 140;

        double startX = g.player.x, startY = g.player.y;

        // drive + fire for ~5 simulated seconds
        for (int i = 0; i < 300; i++) {
            in.fire = (i % 75 == 0);
            g.update(1.0 / 60.0, in);
            in.fire = false;
        }

        boolean moved = Math.hypot(g.player.x - startX, g.player.y - startY) > 1;
        System.out.println("player pos: " + (int) g.player.x + "," + (int) g.player.y
                + " hp=" + (int) g.player.hp + " alive=" + g.player.alive);
        System.out.println("enemies alive: " + g.enemiesAlive() + "/" + g.enemies.size()
                + " obstacles=" + g.obstacles.size()
                + " caches=" + g.cachesRemaining() + "/" + g.caches.size()
                + " world=" + (int) g.worldW + "x" + (int) g.worldH
                + " shotsFired=" + g.shotsFired + " shellsInFlight=" + g.shells.size());
        System.out.println("moved=" + moved + " playerAlive=" + g.player.alive);

        if (!moved) throw new AssertionError("player did not move");
        if (g.shotsFired == 0) throw new AssertionError("no shots fired");
        if (g.obstacles.isEmpty()) throw new AssertionError("no obstacles generated");
        if (g.caches.isEmpty()) throw new AssertionError("no caches generated");
        if (g.worldW <= GameLogic.VIEW_W || g.worldH <= GameLogic.VIEW_H)
            throw new AssertionError("world is not bigger than the viewport");
        if (g.tanksOverlapping()) throw new AssertionError("tanks overlap after simulation");
        System.out.println("SMOKE TEST PASSED");
    }
}
