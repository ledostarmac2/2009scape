package custom.bots;

import content.global.bots.Adventurer;
import content.global.bots.ChickenKiller;
import content.global.bots.CoalMiner;
import content.global.bots.CosmicCrafter;
import content.global.bots.CowKiller;
import content.global.bots.DraynorFisher;
import content.global.bots.DraynorWillows;
import content.global.bots.GenericSlayerBot;
import content.global.bots.GlassBlowingBankstander;
import content.global.bots.Idler;
import content.global.bots.LawCrafter;
import content.global.bots.LobsterCatcher;
import content.global.bots.NatureCrafter;
import content.global.bots.NonBankingMiner;
import content.global.bots.SeersMagicTrees;
import content.global.bots.SharkCatcher;
import content.global.bots.VarrockEssenceMiner;
import core.game.bots.AIPlayer;
import core.game.bots.CombatBotAssembler;
import core.game.bots.GeneralBotCreator;
import core.game.bots.Script;
import core.game.bots.SkillingBotAssembler;
import core.game.node.entity.combat.CombatStyle;
import core.game.world.ImmerseWorld;
import core.game.world.map.Location;

import java.util.Random;

public final class BotWorldEnhancer {
    private static final Random RNG = new Random();
    private static boolean fixedSpawned;

    private static final int[][] CITY_CENTERS = {
        {3221, 3219, 0}, {3212, 3428, 0}, {2965, 3379, 0}, {3092, 3245, 0},
        {3165, 3488, 0}, {2757, 3477, 0}, {2662, 3305, 0}, {2808, 3437, 0},
        {2612, 3093, 0}, {2533, 3572, 0}, {3492, 3488, 0}, {2606, 3153, 0},
        {2897, 3546, 0}, {3046, 4978, 1}
    };

    private static final int[][] WORLD_ROUTES = {
        {2443, 3083, 0}, {2505, 3165, 0}, {2579, 3294, 0}, {2659, 3438, 0},
        {2725, 3485, 0}, {2806, 3435, 0}, {2887, 3422, 0}, {2965, 3379, 0},
        {3032, 3348, 0}, {3104, 3249, 0}, {3184, 3261, 0}, {3253, 3420, 0},
        {3295, 3186, 0}, {3360, 3268, 0}, {3428, 3538, 0}, {3508, 3496, 0},
        {3565, 3288, 0}, {3650, 3520, 0}, {2330, 3170, 0}, {2200, 3230, 0},
        {2099, 3918, 0}, {2350, 3800, 0}, {2520, 3895, 0}, {2760, 3615, 0}
    };

    private static final int[][] DUNGEONS = {
        {3237, 9859, 0}, {3096, 9867, 0}, {2884, 9797, 0}, {2844, 9583, 0},
        {2710, 9564, 0}, {3005, 9550, 0}, {3022, 9739, 0}, {3046, 9756, 0},
        {2793, 9996, 0}, {2807, 10002, 0}, {3428, 3538, 0}, {1859, 5243, 0},
        {3555, 9945, 0}, {3185, 5470, 0}, {2445, 5178, 0}, {3169, 9572, 0}
    };

    private static final int[][] ORE_SPOTS = {
        {3182, 3374, 0}, {3037, 9737, 0}, {3046, 9756, 0}, {3018, 3339, 0},
        {3287, 3369, 0}, {3292, 3310, 0}, {2730, 9875, 0}, {2822, 2998, 0}
    };

    private static final int[][] TREE_SPOTS = {
        {3092, 3233, 0}, {2702, 3397, 0}, {2720, 3499, 0}, {3210, 3500, 0},
        {2674, 3420, 0}, {2818, 3438, 0}, {3170, 3428, 0}, {2459, 3422, 0}
    };

    private BotWorldEnhancer() {
    }

    public static void installFixed() {
        if (fixedSpawned) {
            return;
        }
        fixedSpawned = true;

        for (int[] p : DUNGEONS) {
            combat(new GenericSlayerBot(), randomCombatType(), CombatBotAssembler.Tier.MED, loc(p));
            if (RNG.nextBoolean()) {
                combat(new Adventurer(CombatStyle.MELEE), CombatBotAssembler.Type.MELEE, CombatBotAssembler.Tier.LOW, jitter(p, 2));
            }
        }

        for (int[] p : ORE_SPOTS) {
            skill(new NonBankingMiner(), SkillingBotAssembler.Wealth.POOR, loc(p));
            if (RNG.nextBoolean()) {
                skill(new CoalMiner(), SkillingBotAssembler.Wealth.POOR, jitter(p, 2));
            }
        }

        for (int[] p : TREE_SPOTS) {
            skill(RNG.nextBoolean() ? new DraynorWillows() : new SeersMagicTrees(), SkillingBotAssembler.Wealth.AVERAGE, loc(p));
        }

        spawnManySkill(new DraynorFisher(), SkillingBotAssembler.Wealth.POOR, new int[]{3095, 3246, 0}, 8);
        spawnManySkill(new LobsterCatcher(), SkillingBotAssembler.Wealth.AVERAGE, new int[]{2805, 3435, 0}, 6);
        spawnManySkill(new SharkCatcher(), SkillingBotAssembler.Wealth.RICH, new int[]{2604, 3421, 0}, 6);
        spawnManySkill(new NatureCrafter(), SkillingBotAssembler.Wealth.AVERAGE, new int[]{2809, 3008, 0}, 5);
        spawnManySkill(new LawCrafter(), SkillingBotAssembler.Wealth.RICH, new int[]{2446, 3090, 0}, 5);
        spawnManySkill(new CosmicCrafter(), SkillingBotAssembler.Wealth.AVERAGE, new int[]{2407, 4379, 0}, 4);
        spawnManySkill(new VarrockEssenceMiner(), SkillingBotAssembler.Wealth.POOR, new int[]{3253, 3420, 0}, 8);

        spawnManyCombat(new CowKiller(), CombatBotAssembler.Type.MELEE, CombatBotAssembler.Tier.LOW, new int[]{3258, 3268, 0}, 10);
        spawnManyCombat(new ChickenKiller(), CombatBotAssembler.Type.MELEE, CombatBotAssembler.Tier.LOW, new int[]{3235, 3295, 0}, 8);
        spawnManyCombat(new GenericSlayerBot(), CombatBotAssembler.Type.MELEE, CombatBotAssembler.Tier.MED, new int[]{2673, 3635, 0}, 12);

        for (int[] p : CITY_CENTERS) {
            for (int i = 0; i < 6; i++) {
                skill(RNG.nextInt(4) == 0 ? new GlassBlowingBankstander() : new Idler(), randomWealth(), jitter(p, 3));
            }
        }
    }

    public static void spawnRandomAdventurer() {
        int roll = RNG.nextInt(100);
        int[] p = roll < 50 ? pick(CITY_CENTERS) : (roll < 85 ? pick(WORLD_ROUTES) : pick(DUNGEONS));
        CombatStyle style = RNG.nextBoolean() ? CombatStyle.MELEE : CombatStyle.RANGE;
        CombatBotAssembler.Tier tier = RNG.nextInt(100) < 70 ? CombatBotAssembler.Tier.LOW : CombatBotAssembler.Tier.MED;
        Script script = new Adventurer(style);
        if (style == CombatStyle.RANGE) {
            new GeneralBotCreator(script, ImmerseWorld.Companion.getAssembler().RangeAdventurer(tier, jitter(p, 4)));
        } else {
            new GeneralBotCreator(script, ImmerseWorld.Companion.getAssembler().MeleeAdventurer(tier, jitter(p, 4)));
        }
    }

    public static void updateRun(AIPlayer bot) {
        double energy = bot.getSettings().getRunEnergy();
        if (energy >= 100.0d && !bot.getSettings().isRunToggled()) {
            bot.getSettings().setRunToggled(true);
        }
    }

    private static void spawnManySkill(Script script, SkillingBotAssembler.Wealth wealth, int[] p, int count) {
        for (int i = 0; i < count; i++) {
            skill(fresh(script), wealth, jitter(p, 3));
        }
    }

    private static void spawnManyCombat(Script script, CombatBotAssembler.Type type, CombatBotAssembler.Tier tier, int[] p, int count) {
        for (int i = 0; i < count; i++) {
            combat(fresh(script), type, tier, jitter(p, 4));
        }
    }

    private static void skill(Script script, SkillingBotAssembler.Wealth wealth, Location location) {
        try {
            new GeneralBotCreator(script, ImmerseWorld.Companion.getSkillingBotAssembler().produce(wealth, location));
        } catch (Throwable ignored) {
        }
    }

    private static void combat(Script script, CombatBotAssembler.Type type, CombatBotAssembler.Tier tier, Location location) {
        try {
            new GeneralBotCreator(script, ImmerseWorld.Companion.getAssembler().produce(type, tier, location));
        } catch (Throwable ignored) {
        }
    }

    private static Script fresh(Script script) {
        try {
            return script.getClass().getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return script;
        }
    }

    private static CombatBotAssembler.Type randomCombatType() {
        return RNG.nextBoolean() ? CombatBotAssembler.Type.MELEE : CombatBotAssembler.Type.RANGE;
    }

    private static SkillingBotAssembler.Wealth randomWealth() {
        int roll = RNG.nextInt(100);
        return roll < 50 ? SkillingBotAssembler.Wealth.POOR : (roll < 85 ? SkillingBotAssembler.Wealth.AVERAGE : SkillingBotAssembler.Wealth.RICH);
    }

    private static int[] pick(int[][] points) {
        return points[RNG.nextInt(points.length)];
    }

    private static Location loc(int[] p) {
        return Location.create(p[0], p[1], p[2]);
    }

    private static Location jitter(int[] p, int radius) {
        return Location.create(p[0] + RNG.nextInt(radius * 2 + 1) - radius, p[1] + RNG.nextInt(radius * 2 + 1) - radius, p[2]);
    }
}
