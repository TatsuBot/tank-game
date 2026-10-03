package tank;

/**
 * Headless test for moving shell objects, cache pickups, and the
 * one-shot aux shell. Run:
 *   java -cp out tank.ShellAndCacheTest
 */
public final class ShellAndCacheTest {
    public static void main(String[] args) {
        GameLogic g = new GameLogic();
        g.obstacles.clear();
        g.caches.clear();

        GameLogic.Tank p = g.player;
        p.x = 100; p.y = 500; p.hullAngle = 0; p.hp = p.maxHp; p.alive = true;
        for (GameLogic.Tank e : g.enemies) { e.x = 1500; e.y = 200; }

        GameLogic.Input in = new GameLogic.Input();
        in.aimX = 900; in.aimY = 500; // aim east

        // 1) Firing creates a shell object that travels across the map.
        in.fire = true;
        g.update(1.0 / 60.0, in);
        in.fire = false;
        if (g.shells.size() != 1) throw new AssertionError("no shell created, shells=" + g.shells.size());
        GameLogic.Shell s = g.shells.get(0);
        double startX = s.x;
        System.out.println("shell created at " + (int) s.x + "," + (int) s.y + " type=" + s.type);
        for (int i = 0; i < 30; i++) g.update(1.0 / 60.0, in); // 0.5s of flight
        if (!s.alive) throw new AssertionError("shell vanished mid-flight");
        double travelled = s.x - startX;
        System.out.println("shell travelled " + String.format("%.1f", travelled)
                + " px in 0.5s (still in flight: " + s.alive + ")");
        if (travelled < 80) throw new AssertionError("shell barely moved: " + travelled);

        // 2) Driving onto a cache grants one aux shell.
        g.shells.clear();
        p.reload = 0;
        GameLogic.Cache c = new GameLogic.Cache(p.x + 15, p.y);
        g.caches.add(c);
        g.update(1.0 / 60.0, in);
        if (c.alive) throw new AssertionError("cache was not picked up");
        if (p.auxShells != 1) throw new AssertionError("aux shells after pickup=" + p.auxShells);
        System.out.println("cache picked up, auxShells=" + p.auxShells);

        // 3) Switching to AUX and firing consumes exactly that one shell.
        g.cycleShellType();
        if (p.selectedShell != GameLogic.ShellType.AUX)
            throw new AssertionError("Q-switch did not select AUX");
        in.fire = true;
        g.update(1.0 / 60.0, in);
        in.fire = false;
        if (p.auxShells != 0) throw new AssertionError("aux shell not consumed, left=" + p.auxShells);
        if (p.selectedShell != GameLogic.ShellType.STANDARD)
            throw new AssertionError("did not fall back to STANDARD after firing AUX");
        GameLogic.Shell aux = null;
        for (GameLogic.Shell sh : g.shells) if (sh.type == GameLogic.ShellType.AUX) aux = sh;
        if (aux == null) throw new AssertionError("no AUX shell in flight");
        System.out.println("aux shell fired: damage=" + (int) aux.damage
                + " speed=" + (int) Math.hypot(aux.vx, aux.vy));

        // 4) With no aux carried, trying to select AUX is refused.
        if (g.selectShellType(GameLogic.ShellType.AUX))
            throw new AssertionError("selected AUX with none carried");

        System.out.println("SHELL + CACHE TEST PASSED");
    }
}
