package tank;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Small properties-file config for the tank prototype.
 *
 * Loaded once at startup from {@code config.properties} in the working
 * directory (run.sh cd's into the prototype folder, so the file next to
 * run.sh is the one used). Override the location with
 * -Dtank.config=/path/to/file. Missing file or bad values fall back to
 * the same defaults the game shipped with in v0.1.
 */
public final class GameConfig {

    public final int enemyCount;
    public final int obstacleCount;
    public final int worldWidth;            // full map size; the window shows a 960x640 view of it
    public final int worldHeight;
    public final int cacheCount;            // item caches scattered on the map
    public final int baseWidthTiles;        // yellow base safe-zone size, in 48px tiles
    public final int baseHeightTiles;
    public final double playerReloadSeconds; // time between player shots (lower = faster fire)
    public final double enemyReloadSeconds;  // time between enemy shots
    public final double shellSpeed;          // px/s
    public final double playerSpeed;         // px/s
    public final double enemySpeed;          // px/s
    public final double playerHealth;
    public final double enemyHealth;
    public final double grassMovementScalar; // multiplier on tank speed while on grass
    public final double sandMovementScalar;  // multiplier on tank speed while on sand
    public final double swampMovementScalar; // multiplier on tank speed while in a swamp (slow)
    public final double shellDamage;         // before armour multiplier
    public final double auxShellDamage;      // one-shot shell granted by item caches
    public final double auxShellSpeed;       // px/s, heavier = slower than the standard shell
    /** Null = a different random layout every run. */
    public final Long randomSeed;

    private GameConfig(int enemyCount, int obstacleCount, int worldWidth, int worldHeight, int cacheCount,
                       int baseWidthTiles, int baseHeightTiles,
                       double playerReloadSeconds, double enemyReloadSeconds,
                       double shellSpeed, double playerSpeed, double enemySpeed,
                       double playerHealth, double enemyHealth,
                       double grassMovementScalar, double sandMovementScalar, double swampMovementScalar,
                       double shellDamage, double auxShellDamage, double auxShellSpeed,
                       Long randomSeed) {
        this.enemyCount = enemyCount;
        this.obstacleCount = obstacleCount;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.cacheCount = cacheCount;
        this.baseWidthTiles = baseWidthTiles;
        this.baseHeightTiles = baseHeightTiles;
        this.playerReloadSeconds = playerReloadSeconds;
        this.enemyReloadSeconds = enemyReloadSeconds;
        this.shellSpeed = shellSpeed;
        this.playerSpeed = playerSpeed;
        this.enemySpeed = enemySpeed;
        this.playerHealth = playerHealth;
        this.enemyHealth = enemyHealth;
        this.grassMovementScalar = grassMovementScalar;
        this.sandMovementScalar = sandMovementScalar;
        this.swampMovementScalar = swampMovementScalar;
        this.shellDamage = shellDamage;
        this.auxShellDamage = auxShellDamage;
        this.auxShellSpeed = auxShellSpeed;
        this.randomSeed = randomSeed;
    }

    public static GameConfig loadDefault() {
        Path path = Paths.get(System.getProperty("tank.config", "config.properties"));
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Warning: could not read " + path + " (" + e.getMessage() + "), using defaults");
            }
        }
        return from(props);
    }

    static GameConfig from(Properties p) {
        return new GameConfig(
                clampInt(getInt(p, "enemyCount", 3), 0, 12, "enemyCount"),
                clampInt(getInt(p, "obstacleCount", 6), 0, 30, "obstacleCount"),
                clampInt(getInt(p, "worldWidth", 1920), 960, 4000, "worldWidth"),
                clampInt(getInt(p, "worldHeight", 1280), 640, 3000, "worldHeight"),
                clampInt(getInt(p, "cacheCount", 5), 0, 30, "cacheCount"),
                clampInt(getInt(p, "baseWidthTiles", 4), 2, 8, "baseWidthTiles"),
                clampInt(getInt(p, "baseHeightTiles", 3), 2, 8, "baseHeightTiles"),
                clampDouble(getDouble(p, "playerReloadSeconds", 1.2), 0.2, 10, "playerReloadSeconds"),
                clampDouble(getDouble(p, "enemyReloadSeconds", 2.1), 0.2, 10, "enemyReloadSeconds"),
                clampDouble(getDouble(p, "shellSpeed", 220), 50, 600, "shellSpeed"),
                clampDouble(getDouble(p, "playerSpeed", 110), 30, 300, "playerSpeed"),
                clampDouble(getDouble(p, "enemySpeed", 55), 10, 200, "enemySpeed"),
                clampDouble(getDouble(p, "playerHealth", 100), 10, 500, "playerHealth"),
                clampDouble(getDouble(p, "enemyHealth", 100), 10, 500, "enemyHealth"),
                clampDouble(getDouble(p, "grassMovementScalar", 1.0), 0.05, 2.0, "grassMovementScalar"),
                clampDouble(getDouble(p, "sandMovementScalar", 1.0), 0.05, 2.0, "sandMovementScalar"),
                clampDouble(getDouble(p, "swampMovementScalar", 0.35), 0.05, 2.0, "swampMovementScalar"),
                clampDouble(getDouble(p, "shellDamage", 34), 1, 100, "shellDamage"),
                clampDouble(getDouble(p, "auxShellDamage", 68), 1, 200, "auxShellDamage"),
                clampDouble(getDouble(p, "auxShellSpeed", 170), 50, 600, "auxShellSpeed"),
                getSeed(p)
        );
    }

    private static int getInt(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { warn(key, v, def); return def; }
    }

    private static double getDouble(Properties p, String key, double def) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { warn(key, v, def); return def; }
    }

    private static Long getSeed(Properties p) {
        String v = p.getProperty("randomSeed");
        if (v == null || v.isBlank() || v.trim().equalsIgnoreCase("random")) return null;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { warn("randomSeed", v, "random"); return null; }
    }

    private static int clampInt(int v, int min, int max, String key) {
        int c = Math.max(min, Math.min(max, v));
        if (c != v) System.err.println("Warning: " + key + "=" + v + " out of range, using " + c);
        return c;
    }

    private static double clampDouble(double v, double min, double max, String key) {
        double c = Math.max(min, Math.min(max, v));
        if (c != v) System.err.println("Warning: " + key + "=" + v + " out of range, using " + c);
        return c;
    }

    private static void warn(String key, String value, Object def) {
        System.err.println("Warning: bad value for " + key + "='" + value + "', using " + def);
    }
}
