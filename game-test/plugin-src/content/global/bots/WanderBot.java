package content.global.bots;

import core.game.bots.Script;
import core.game.world.map.Location;

/**
 * Minimal "ambient dungeon dweller" bot: stays near its spawn point and
 * wanders around it so the dungeon feels alive. Used by {@link DungeonBots}.
 */
public class WanderBot extends Script {

    private Location center;
    private int counter = 0;

    public WanderBot() {}

    public WanderBot(Location center) {
        this.center = center;
    }

    @Override
    public void tick() {
        if (center == null) return;
        // Issue a fresh short wander every several ticks so it ambles around.
        if (counter++ % 7 == 0) {
            scriptAPI.randomWalkTo(center, 4);
        }
    }

    @Override
    public Script newInstance() {
        return new WanderBot(center);
    }
}
