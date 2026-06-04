package content.global.bots;

import core.api.StartupListener;
import core.game.bots.GeneralBotCreator;
import core.game.world.map.Location;

/**
 * Statically stages a single wandering bot in each of a handful of dungeons
 * at boot, instead of the dynamic, player-centred ImmerseWorld spawning.
 *
 * Pairs with worldprops `max_adv_bots = 0` (ImmerseWorld swarm disabled).
 */
public class DungeonBots implements StartupListener {

    // {x, y} on plane 0 inside each dungeon.
    private static final int[][] SPOTS = {
        {3097, 9867}, // Edgeville Dungeon
        {3237, 9858}, // Varrock Sewers
        {3169, 9572}, // Lumbridge Swamp Caves
        {2884, 9798}, // Taverley Dungeon
        {3549, 9947}, // Experiment Cave
        {3018, 9842}, // Dwarven Mines
    };

    @Override
    public void startup() {
        int spawned = 0;
        for (int[] s : SPOTS) {
            Location loc = Location.create(s[0], s[1], 0);
            try {
                new GeneralBotCreator(new WanderBot(loc), loc);
                spawned++;
            } catch (Throwable t) {
                System.out.println("[DungeonBots] failed to spawn at " + s[0] + "," + s[1] + ": " + t);
            }
        }
        System.out.println("[DungeonBots] staged " + spawned + "/" + SPOTS.length + " dungeon wanderers");
    }
}
