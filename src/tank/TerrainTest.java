package tank;

import java.util.Properties;

/**
 * Headless terrain + obstacle-shape test. Run:
 *   java -cp out tank.TerrainTest
 */
public final class TerrainTest {
    public static void main(String[] args) {
        GameLogic g = new GameLogic();

        int grass = g.countTerrain(GameLogic.TerrainType.GRASS);
        int sand = g.countTerrain(GameLogic.TerrainType.SAND);
        int swamp = g.countTerrain(GameLogic.TerrainType.SWAMP);
        System.out.println("tiles: " + g.tileCols + "x" + g.tileRows
                + " (" + (int) GameLogic.TILE_SIZE + "px each)"
                + " grass=" + grass + " sand=" + sand + " swamp=" + swamp);
        System.out.println("movement scalars: grass=" + g.config.grassMovementScalar
                + " sand=" + g.config.sandMovementScalar
                + " swamp=" + g.config.swampMovementScalar);
        if (grass == 0) throw new AssertionError("no grass tiles");
        if (sand == 0) throw new AssertionError("no sand tiles");
        if (swamp == 0) throw new AssertionError("no swamp tiles");
        if (g.config.swampMovementScalar >= g.config.grassMovementScalar)
            throw new AssertionError("swamp is not slower than grass");

        boolean hasSingle = false;
        boolean hasVaried = false;
        StringBuilder shapes = new StringBuilder("obstacle shapes (tiles):");
        for (GameLogic.Obstacle o : g.obstacles) {
            shapes.append(' ').append(o.tileW).append('x').append(o.tileH);
            if (o.tileW == 1 && o.tileH == 1) hasSingle = true;
            if (o.tileW > 1 || o.tileH > 1) hasVaried = true;
            // obstacles must sit exactly on the tile grid
            if (Math.abs(o.x / GameLogic.TILE_SIZE - Math.round(o.x / GameLogic.TILE_SIZE)) > 0.001
                    || Math.abs(o.y / GameLogic.TILE_SIZE - Math.round(o.y / GameLogic.TILE_SIZE)) > 0.001
                    || Math.abs(o.w - o.tileW * GameLogic.TILE_SIZE) > 0.001
                    || Math.abs(o.h - o.tileH * GameLogic.TILE_SIZE) > 0.001) {
                throw new AssertionError("obstacle is not tile-aligned: "
                        + o.x + "," + o.y + " " + o.w + "x" + o.h);
            }
        }
        System.out.println(shapes);
        if (!hasSingle) throw new AssertionError("no 1x1 obstacle generated");
        if (!hasVaried) throw new AssertionError("all obstacles are the same 1x1 size");

        // Configured scalar values round-trip through the config loader.
        Properties p = new Properties();
        p.setProperty("grassMovementScalar", "0.9");
        p.setProperty("sandMovementScalar", "0.75");
        p.setProperty("swampMovementScalar", "0.2");
        GameLogic custom = new GameLogic(GameConfig.from(p));
        if (Math.abs(custom.movementScalar(GameLogic.TerrainType.GRASS) - 0.9) > 0.001
                || Math.abs(custom.movementScalar(GameLogic.TerrainType.SAND) - 0.75) > 0.001
                || Math.abs(custom.movementScalar(GameLogic.TerrainType.SWAMP) - 0.2) > 0.001) {
            throw new AssertionError("config movement scalars were not applied");
        }
        System.out.println("custom config scalars applied: grass=0.9 sand=0.75 swamp=0.2");

        // Driving on swamp is measurably slower than driving on grass.
        double grassDist = driveDistance(g, GameLogic.TerrainType.GRASS);
        double swampDist = driveDistance(g, GameLogic.TerrainType.SWAMP);
        System.out.println("drive distance in 1/6s: grass=" + String.format("%.1f", grassDist)
                + "px swamp=" + String.format("%.1f", swampDist) + "px");
        if (grassDist < 15) throw new AssertionError("grass drive too slow: " + grassDist);
        if (swampDist > grassDist * 0.55) throw new AssertionError("swamp drive not slow enough: " + swampDist);

        System.out.println("TERRAIN TEST PASSED");
    }

    /** Teleports the player to a tile of the given type and drives east briefly. */
    private static double driveDistance(GameLogic g, GameLogic.TerrainType type) {
        g.obstacles.clear();
        g.caches.clear();
        for (GameLogic.Tank e : g.enemies) { e.x = 1700; e.y = 150; }
        int[] tile = null;
        for (int r = 0; r < g.tileRows && tile == null; r++) {
            for (int c = 0; c < g.tileCols; c++) {
                if (g.terrainAt(c, r) == type) { tile = new int[]{c, r}; break; }
            }
        }
        if (tile == null) throw new AssertionError("no " + type + " tile found");
        GameLogic.Tank p = g.player;
        p.x = tile[0] * GameLogic.TILE_SIZE + GameLogic.TILE_SIZE / 2;
        p.y = tile[1] * GameLogic.TILE_SIZE + GameLogic.TILE_SIZE / 2;
        p.hullAngle = 0; p.hp = p.maxHp; p.alive = true; p.reload = 0;
        double startX = p.x;
        GameLogic.Input in = new GameLogic.Input();
        in.forward = true;
        in.aimX = p.x + 500; in.aimY = p.y;
        for (int i = 0; i < 10; i++) g.update(1.0 / 60.0, in);
        return p.x - startX;
    }
}
