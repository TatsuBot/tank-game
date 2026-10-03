package tank;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Headless-testable game state and simulation for the tank prototype.
 * No Swing / AWT dependencies here so it can be unit-tested without a display.
 *
 * World layout (enemy spawns, cover obstacles, item caches) is generated
 * randomly each run from {@link GameConfig}; set randomSeed in
 * config.properties for a reproducible layout.
 *
 * The world can be larger than the window: TankGame renders a 960x640
 * camera view into it. All coordinates in here are world coordinates.
 */
public final class GameLogic {

    /** Window / camera viewport size (the world itself is in GameConfig). */
    public static final double VIEW_W = 960;
    public static final double VIEW_H = 640;

    private static final double TANK_RADIUS = 18;
    public static final double TILE_SIZE = 48;
    private static final double PLAYER_SPAWN_X = 140;
    private static final double CACHE_RADIUS = 12;
    private static final double PICKUP_DISTANCE = TANK_RADIUS + CACHE_RADIUS + 12;

    /** Preferred enemy spawn spots as fractions of the world size. */
    private static final double[][] ENEMY_SPAWN_FRACTIONS = {
            {0.79, 0.22}, {0.85, 0.56}, {0.71, 0.84}, {0.85, 0.19},
            {0.58, 0.19}, {0.90, 0.81}, {0.50, 0.81}, {0.31, 0.22},
            {0.92, 0.38}, {0.44, 0.56}, {0.65, 0.38}, {0.21, 0.75},
    };

    public enum ShellType { STANDARD, AUX }

    /** Travelable ground. Speed on each type is scaled by its config movement scalar. */
    public enum TerrainType { GRASS, SAND, SWAMP }

    public static final class Input {
        public boolean forward, back, left, right, fire;
        public double aimX = VIEW_W / 2, aimY = 0;
    }

    public static final class Tank {
        public double x, y;
        public double hullAngle;    // radians, 0 = east
        public double turretAngle;  // radians, world-space
        public double hp;
        public final double maxHp;
        public double reload = 0;
        public boolean alive = true;
        public final boolean isPlayer;
        // Player-only shell inventory (enemies always fire STANDARD)
        public int auxShells = 0;
        public ShellType selectedShell = ShellType.STANDARD;
        // AI scratch state
        double patrolX, patrolY;
        double aiThink = 0;

        Tank(double x, double y, boolean isPlayer, double patrolX, double patrolY, double maxHp) {
            this.x = x; this.y = y; this.isPlayer = isPlayer;
            this.patrolX = patrolX; this.patrolY = patrolY;
            this.maxHp = maxHp; this.hp = maxHp;
            this.hullAngle = isPlayer ? -Math.PI / 2 : Math.PI / 2;
            this.turretAngle = this.hullAngle;
        }
    }

    public static final class Shell {
        public double x, y, vx, vy;
        public boolean alive = true;
        public final boolean fromPlayer;
        public final double damage;
        public final ShellType type;
        /** True when the firing tank was inside the base zone; such shots deal no damage. */
        public final boolean firedFromBase;

        Shell(double x, double y, double angle, boolean fromPlayer, double speed, double damage,
              ShellType type, boolean firedFromBase) {
            this.x = x; this.y = y;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.fromPlayer = fromPlayer;
            this.damage = damage;
            this.type = type;
            this.firedFromBase = firedFromBase;
        }
    }

    public static final class Obstacle {
        public final double x, y, w, h;
        public final int tileW, tileH;
        Obstacle(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.tileW = Math.max(1, (int) Math.round(w / TILE_SIZE));
            this.tileH = Math.max(1, (int) Math.round(h / TILE_SIZE));
        }

        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        boolean blocksCircle(double cx, double cy, double r) {
            double nx = Math.max(x, Math.min(cx, x + w));
            double ny = Math.max(y, Math.min(cy, y + h));
            double dx = cx - nx, dy = cy - ny;
            return dx * dx + dy * dy < r * r;
        }

        boolean overlaps(Obstacle o, double pad) {
            return x - pad < o.x + o.w && x + w + pad > o.x
                    && y - pad < o.y + o.h && y + h + pad > o.y;
        }
    }

    /** Item cache: drive within pickup range to gain one aux shell. */
    public static final class Cache {
        public final double x, y;
        public boolean alive = true;
        Cache(double x, double y) { this.x = x; this.y = y; }
    }

    /** Yellow base safe zone. Fixed for the life of a generated world. */
    public static final class BaseZone {
        public final double x, y, w, h;
        BaseZone(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }

        public boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        boolean intersects(double rx, double ry, double rw, double rh, double pad) {
            return x - pad < rx + rw && x + w + pad > rx
                    && y - pad < ry + rh && y + h + pad > ry;
        }
    }

    public final GameConfig config;
    public final double worldW, worldH;
    public final int tileCols, tileRows;
    private final TerrainType[][] terrain;
    public final Tank player;
    public final List<Tank> enemies = new ArrayList<>();
    public final List<Shell> shells = new ArrayList<>();
    public final List<Obstacle> obstacles = new ArrayList<>();
    public final List<Cache> caches = new ArrayList<>();
    public BaseZone baseZone;
    private final Random rng;
    public int shotsFired = 0;

    public GameLogic() {
        this(GameConfig.loadDefault());
    }

    GameLogic(GameConfig config) {
        this.config = config;
        this.worldW = config.worldWidth;
        this.worldH = config.worldHeight;
        this.tileCols = (int) Math.ceil(worldW / TILE_SIZE);
        this.tileRows = (int) Math.ceil(worldH / TILE_SIZE);
        this.terrain = new TerrainType[tileRows][tileCols];
        this.rng = config.randomSeed != null ? new Random(config.randomSeed) : new Random();
        player = new Tank(PLAYER_SPAWN_X, worldH - 140, true, 0, 0, config.playerHealth);
        buildWorld();
    }

    /** (Re)generates enemy spawns, random cover obstacles, and item caches. */
    private void buildWorld() {
        enemies.clear();
        obstacles.clear();
        caches.clear();
        for (int i = 0; i < config.enemyCount; i++) {
            double sx, sy;
            if (i < ENEMY_SPAWN_FRACTIONS.length) {
                sx = ENEMY_SPAWN_FRACTIONS[i][0] * worldW;
                sy = ENEMY_SPAWN_FRACTIONS[i][1] * worldH;
            } else { // more enemies than preset spots: scatter away from the player
                sx = 80 + rng.nextDouble() * (worldW - 160);
                sy = 80 + rng.nextDouble() * (worldH - 160);
            }
            enemies.add(new Tank(sx, sy, false, sx, sy, config.enemyHealth));
        }
        generateTerrain();
        generateBaseZone();
        generateObstacles();
        generateCaches();
    }

    /** Places one tile-aligned base zone at a random spot, then keeps it fixed for this world. */
    private void generateBaseZone() {
        int tileW = config.baseWidthTiles;
        int tileH = config.baseHeightTiles;
        int maxTileX = Math.max(1, tileCols - tileW - 1);
        int maxTileY = Math.max(1, tileRows - tileH - 1);
        for (int attempt = 0; attempt < 100; attempt++) {
            int tileX = 1 + rng.nextInt(maxTileX);
            int tileY = 1 + rng.nextInt(maxTileY);
            BaseZone cand = new BaseZone(tileX * TILE_SIZE, tileY * TILE_SIZE,
                    tileW * TILE_SIZE, tileH * TILE_SIZE);
            if (cand.contains(player.x, player.y)) continue;
            boolean onEnemy = false;
            for (Tank e : enemies) if (cand.contains(e.x, e.y)) { onEnemy = true; break; }
            if (onEnemy) continue;
            baseZone = cand;
            return;
        }
        // Extremely crowded configuration: keep the first random placement rather than none.
        baseZone = new BaseZone(TILE_SIZE, TILE_SIZE, tileW * TILE_SIZE, tileH * TILE_SIZE);
    }

    public boolean isInBase(double x, double y) {
        return baseZone != null && baseZone.contains(x, y);
    }

    public boolean isTankInBase(Tank t) {
        return t != null && isInBase(t.x, t.y);
    }

    /** Grass everywhere, with irregular sand and swamp patches painted over it. */
    private void generateTerrain() {
        for (int row = 0; row < tileRows; row++) {
            for (int col = 0; col < tileCols; col++) terrain[row][col] = TerrainType.GRASS;
        }
        int sandPatches = 5 + rng.nextInt(3);
        for (int p = 0; p < sandPatches; p++) paintTerrainPatch(TerrainType.SAND, 2, 4, 2, 3);
        int swampPatches = 3 + rng.nextInt(2);
        for (int p = 0; p < swampPatches; p++) paintTerrainPatch(TerrainType.SWAMP, 1.5, 3, 1.5, 2.5);
    }

    private void paintTerrainPatch(TerrainType type, double minRx, double maxRx, double minRy, double maxRy) {
        int cx = rng.nextInt(tileCols);
        int cy = rng.nextInt(tileRows);
        double rx = minRx + rng.nextDouble() * (maxRx - minRx);
        double ry = minRy + rng.nextDouble() * (maxRy - minRy);
        int minCol = Math.max(0, (int) Math.floor(cx - rx - 1));
        int maxCol = Math.min(tileCols - 1, (int) Math.ceil(cx + rx + 1));
        int minRow = Math.max(0, (int) Math.floor(cy - ry - 1));
        int maxRow = Math.min(tileRows - 1, (int) Math.ceil(cy + ry + 1));
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                double dx = (col - cx) / rx;
                double dy = (row - cy) / ry;
                double edge = 0.85 + rng.nextDouble() * 0.3;
                if (dx * dx + dy * dy <= edge * edge && rng.nextDouble() > 0.12) {
                    terrain[row][col] = type;
                }
            }
        }
    }

    public TerrainType terrainAt(int col, int row) {
        int c = Math.max(0, Math.min(tileCols - 1, col));
        int r = Math.max(0, Math.min(tileRows - 1, row));
        return terrain[r][c];
    }

    public TerrainType terrainAtWorld(double x, double y) {
        return terrainAt((int) (x / TILE_SIZE), (int) (y / TILE_SIZE));
    }

    public int countTerrain(TerrainType type) {
        int n = 0;
        for (TerrainType[] row : terrain) for (TerrainType t : row) if (t == type) n++;
        return n;
    }

    /** Speed multiplier for a tank standing on the given terrain. */
    public double movementScalar(TerrainType type) {
        return switch (type) {
            case GRASS -> config.grassMovementScalar;
            case SAND -> config.sandMovementScalar;
            case SWAMP -> config.swampMovementScalar;
        };
    }

    public double movementScalarAt(double x, double y) {
        return movementScalar(terrainAtWorld(x, y));
    }

    private void generateObstacles() {
        // Obstacle footprints in terrain tiles. 1x1 is deliberately first so
        // every generated world includes at least one single-tile block.
        int[][] sizes = {
                {1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 1},
                {1, 3}, {3, 2}, {2, 3}, {4, 1}, {1, 4},
        };
        int placed = 0, guard = 0;
        while (placed < config.obstacleCount && guard++ < config.obstacleCount * 300) {
            int tileW, tileH;
            if (placed == 0) {
                tileW = 1; tileH = 1;
            } else {
                int[] size = sizes[rng.nextInt(sizes.length)];
                tileW = size[0]; tileH = size[1];
            }
            int maxTileX = tileCols - tileW - 1;
            int maxTileY = tileRows - tileH - 1;
            if (maxTileX < 1 || maxTileY < 1) continue;
            int tileX = 1 + rng.nextInt(maxTileX);
            int tileY = 1 + rng.nextInt(maxTileY);
            Obstacle cand = new Obstacle(tileX * TILE_SIZE, tileY * TILE_SIZE,
                    tileW * TILE_SIZE, tileH * TILE_SIZE);
            // keep spawn areas clear so nobody starts stuck inside cover
            if (cand.blocksCircle(player.x, player.y, TANK_RADIUS + 42)) continue;
            boolean bad = false;
            for (Tank e : enemies) if (cand.blocksCircle(e.x, e.y, TANK_RADIUS + 42)) { bad = true; break; }
            if (bad) continue;
            if (baseZone != null && baseZone.intersects(cand.x, cand.y, cand.w, cand.h, 10)) continue;
            for (Obstacle o : obstacles) if (cand.overlaps(o, 10)) { bad = true; break; }
            if (bad) continue;
            obstacles.add(cand);
            placed++;
        }
    }

    private void generateCaches() {
        int placed = 0, guard = 0;
        while (placed < config.cacheCount && guard++ < config.cacheCount * 200) {
            double x = 50 + rng.nextDouble() * (worldW - 100);
            double y = 50 + rng.nextDouble() * (worldH - 100);
            boolean bad = false;
            if (baseZone != null && baseZone.contains(x, y)) continue;
            for (Obstacle o : obstacles) if (o.blocksCircle(x, y, CACHE_RADIUS + 16)) { bad = true; break; }
            if (bad) continue;
            if (Math.hypot(x - player.x, y - player.y) < 90) continue;
            for (Tank e : enemies) if (Math.hypot(x - e.x, y - e.y) < 70) { bad = true; break; }
            if (bad) continue;
            for (Cache c : caches) if (Math.hypot(x - c.x, y - c.y) < 60) { bad = true; break; }
            if (bad) continue;
            caches.add(new Cache(x, y));
            placed++;
        }
    }

    public void reset() {
        player.x = PLAYER_SPAWN_X; player.y = worldH - 140;
        player.hp = player.maxHp; player.alive = true; player.reload = 0;
        player.hullAngle = -Math.PI / 2; player.turretAngle = player.hullAngle;
        player.auxShells = 0; player.selectedShell = ShellType.STANDARD;
        buildWorld(); // fresh enemies, caches, and a fresh random layout (same layout if randomSeed is set)
        shells.clear(); shotsFired = 0;
    }

    public int enemiesAlive() {
        int n = 0;
        for (Tank t : enemies) if (t.alive) n++;
        return n;
    }

    public int cachesRemaining() {
        int n = 0;
        for (Cache c : caches) if (c.alive) n++;
        return n;
    }

    public boolean won() { return enemiesAlive() == 0; }
    public boolean lost() { return !player.alive; }

    /** Q key: swap between the standard shell and the aux shell, if one is carried. */
    public void cycleShellType() {
        if (player.auxShells <= 0) { player.selectedShell = ShellType.STANDARD; return; }
        player.selectedShell = player.selectedShell == ShellType.STANDARD
                ? ShellType.AUX : ShellType.STANDARD;
    }

    /** Direct selection (1 / 2 keys). Returns false if the aux shell was requested but none is carried. */
    public boolean selectShellType(ShellType type) {
        if (type == ShellType.AUX && player.auxShells <= 0) return false;
        player.selectedShell = type;
        return true;
    }

    /** Advance the simulation. dt in seconds (clamped internally). */
    public void update(double dt, Input in) {
        dt = Math.min(dt, 0.05);
        // --- player ---
        if (player.alive) {
            if (in.left)  player.hullAngle -= 1.8 * dt;
            if (in.right) player.hullAngle += 1.8 * dt;
            double move = 0;
            double playerScalar = movementScalarAt(player.x, player.y);
            if (in.forward) move += config.playerSpeed * playerScalar;
            if (in.back)    move -= config.playerSpeed * playerScalar * 0.6;
            moveTank(player, move * dt);
            player.turretAngle = Math.atan2(in.aimY - player.y, in.aimX - player.x);
            player.reload = Math.max(0, player.reload - dt);
            if (in.fire && player.reload <= 0) fire(player, true);
            collectCaches();
        }
        // --- enemies (simple AI) ---
        for (Tank e : enemies) {
            if (!e.alive) continue;
            e.reload = Math.max(0, e.reload - dt);
            e.aiThink -= dt;
            if (e.aiThink <= 0) {
                e.aiThink = 0.25;
                if (rng.nextDouble() < 0.3) {
                    e.patrolX = 80 + rng.nextDouble() * (worldW - 160);
                    e.patrolY = 80 + rng.nextDouble() * (worldH - 160);
                }
            }
            // drive toward patrol point, stop at a standoff distance from player
            double distToPlayer = dist(e, player);
            double tx = distToPlayer < 260 ? e.x : e.patrolX;
            double ty = distToPlayer < 260 ? e.y : e.patrolY;
            double want = Math.atan2(ty - e.y, tx - e.x);
            if (Math.hypot(tx - e.x, ty - e.y) > 30) {
                e.hullAngle = turnToward(e.hullAngle, want, 1.4 * dt);
                double step = config.enemySpeed * movementScalarAt(e.x, e.y) * dt;
                double nx = e.x + Math.cos(e.hullAngle) * step;
                double ny = e.y + Math.sin(e.hullAngle) * step;
                nx = Math.max(TANK_RADIUS, Math.min(worldW - TANK_RADIUS, nx));
                ny = Math.max(TANK_RADIUS, Math.min(worldH - TANK_RADIUS, ny));
                if (!blocked(nx, ny, e)) { e.x = nx; e.y = ny; }
            }
            // aim + fire at player with inaccuracy and line-of-sight check
            if (player.alive && distToPlayer < 430 && hasLineOfSight(e, player)) {
                double aim = Math.atan2(player.y - e.y, player.x - e.x);
                e.turretAngle = turnToward(e.turretAngle, aim, 2.2 * dt);
                if (Math.abs(angleDiff(e.turretAngle, aim)) < 0.08 && e.reload <= 0) {
                    double spread = (rng.nextDouble() - 0.5) * 0.09;
                    e.turretAngle += spread;
                    fire(e, false);
                }
            }
        }
        // --- shells in flight (objects that travel until they hit something) ---
        for (Shell s : shells) {
            if (!s.alive) continue;
            s.x += s.vx * dt; s.y += s.vy * dt;
            if (s.x < 0 || s.x > worldW || s.y < 0 || s.y > worldH) { s.alive = false; continue; }
            boolean hitObs = false;
            for (Obstacle o : obstacles) if (o.contains(s.x, s.y)) { hitObs = true; break; }
            if (hitObs) { s.alive = false; continue; }
            Tank target = s.fromPlayer ? firstHitEnemy(s) : (shellHits(s, player) ? player : null);
            if (target != null) {
                applyDamage(target, s);
                s.alive = false;
            }
        }
        shells.removeIf(s -> !s.alive);
        for (Tank t : enemies) if (t.hp <= 0) t.alive = false;
        if (player.hp <= 0) player.alive = false;
    }

    private void collectCaches() {
        for (Cache c : caches) {
            if (c.alive && Math.hypot(player.x - c.x, player.y - c.y) <= PICKUP_DISTANCE) {
                c.alive = false;
                player.auxShells++;
            }
        }
    }

    private void fire(Tank t, boolean fromPlayer) {
        double mx = t.x + Math.cos(t.turretAngle) * (TANK_RADIUS + 8);
        double my = t.y + Math.sin(t.turretAngle) * (TANK_RADIUS + 8);
        ShellType type = ShellType.STANDARD;
        double speed = config.shellSpeed, damage = config.shellDamage;
        if (fromPlayer && t.selectedShell == ShellType.AUX && t.auxShells > 0) {
            type = ShellType.AUX;
            speed = config.auxShellSpeed;
            damage = config.auxShellDamage;
            t.auxShells--; // one-shot: consumed by firing
            if (t.auxShells == 0) t.selectedShell = ShellType.STANDARD;
        }
        shells.add(new Shell(mx, my, t.turretAngle, fromPlayer, speed, damage, type, isTankInBase(t)));
        t.reload = fromPlayer ? config.playerReloadSeconds : config.enemyReloadSeconds;
        shotsFired++;
    }

    private void applyDamage(Tank target, Shell s) {
        // Base safe zone: shots fired from inside it, and shots hitting a
        // tank inside it, deal no damage. The shell is still consumed.
        if (s.firedFromBase || isTankInBase(target)) return;
        // Armour-ish model: frontal hits do less, rear hits do more.
        double incoming = Math.atan2(s.vy, s.vx);           // shell travel direction
        double facing = target.hullAngle;                    // where the tank faces
        // angle between shell direction and the direction the armour faces it:
        double rel = Math.abs(angleDiff(incoming, facing + Math.PI)); // 0 = head-on into front
        double mult;
        if (rel < 0.6) mult = 0.5;        // front
        else if (rel > 2.4) mult = 1.4;   // rear
        else mult = 1.0;                  // side
        target.hp -= s.damage * mult;
    }

    private boolean shellHits(Shell s, Tank t) {
        return t.alive && Math.hypot(s.x - t.x, s.y - t.y) <= TANK_RADIUS;
    }

    private Tank firstHitEnemy(Shell s) {
        for (Tank e : enemies) if (shellHits(s, e)) return e;
        return null;
    }

    private void moveTank(Tank t, double step) {
        double nx = t.x + Math.cos(t.hullAngle) * step;
        double ny = t.y + Math.sin(t.hullAngle) * step;
        nx = Math.max(TANK_RADIUS, Math.min(worldW - TANK_RADIUS, nx));
        ny = Math.max(TANK_RADIUS, Math.min(worldH - TANK_RADIUS, ny));
        if (!blocked(nx, ny, t)) { t.x = nx; t.y = ny; }
        else if (!blocked(nx, t.y, t)) { t.x = nx; }
        else if (!blocked(t.x, ny, t)) { t.y = ny; }
    }

    /** Is a tank centred at (x, y) colliding with a wall obstacle or another living tank? */
    private boolean blocked(double x, double y, Tank self) {
        for (Obstacle o : obstacles) if (o.blocksCircle(x, y, TANK_RADIUS)) return true;
        return collidesWithOtherTank(x, y, self);
    }

    private boolean collidesWithOtherTank(double x, double y, Tank self) {
        double minDist = TANK_RADIUS * 2;
        if (self != player && player.alive
                && Math.hypot(x - player.x, y - player.y) < minDist) return true;
        for (Tank e : enemies) {
            if (e != self && e.alive
                    && Math.hypot(x - e.x, y - e.y) < minDist) return true;
        }
        return false;
    }

    /** True if two living tanks are overlapping (used by tests). */
    public boolean tanksOverlapping() {
        List<Tank> all = new ArrayList<>();
        if (player.alive) all.add(player);
        for (Tank e : enemies) if (e.alive) all.add(e);
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Tank a = all.get(i), b = all.get(j);
                if (Math.hypot(a.x - b.x, a.y - b.y) < TANK_RADIUS * 2 - 0.5) return true;
            }
        }
        return false;
    }

    private boolean hasLineOfSight(Tank a, Tank b) {
        int steps = 24;
        for (int i = 1; i < steps; i++) {
            double f = i / (double) steps;
            double px = a.x + (b.x - a.x) * f, py = a.y + (b.y - a.y) * f;
            for (Obstacle o : obstacles) if (o.contains(px, py)) return false;
        }
        return true;
    }

    private static double dist(Tank a, Tank b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    /** Smallest signed difference b - a, wrapped to [-pi, pi]. */
    public static double angleDiff(double a, double b) {
        double d = b - a;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private static double turnToward(double current, double target, double maxStep) {
        double d = angleDiff(current, target);
        if (Math.abs(d) <= maxStep) return target;
        return current + Math.signum(d) * maxStep;
    }
}
