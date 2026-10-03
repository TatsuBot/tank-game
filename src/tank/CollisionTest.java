package tank;

/**
 * Headless collision test: drives the player straight at a stationary
 * enemy and asserts the hulls never overlap. Run:
 *   java -cp out tank.CollisionTest
 */
public final class CollisionTest {
    public static void main(String[] args) {
        GameLogic g = new GameLogic();
        g.obstacles.clear(); // controlled setup: hull-to-hull collision only

        GameLogic.Tank p = g.player;
        GameLogic.Tank e = g.enemies.get(0);
        p.x = 100; p.y = 100; p.hullAngle = 0; p.hp = p.maxHp; p.alive = true;
        e.x = 180; e.y = 100; e.hp = e.maxHp; e.alive = true;

        GameLogic.Input in = new GameLogic.Input();
        in.forward = true;
        in.aimX = 800; in.aimY = 100;

        for (int i = 0; i < 180; i++) { // 3 simulated seconds, straight into the enemy
            g.update(1.0 / 60.0, in);
            if (g.tanksOverlapping()) {
                throw new AssertionError("tanks overlapped at frame " + i
                        + " dist=" + Math.hypot(p.x - e.x, p.y - e.y));
            }
        }

        double dist = Math.hypot(p.x - e.x, p.y - e.y);
        System.out.println("player stopped at " + (int) p.x + "," + (int) p.y
                + " enemy at " + (int) e.x + "," + (int) e.y
                + " hull distance=" + String.format("%.1f", dist));
        if (dist > 40) throw new AssertionError("player never reached the enemy, dist=" + dist);
        System.out.println("COLLISION TEST PASSED");
    }
}
