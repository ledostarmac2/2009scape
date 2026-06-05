package content.global.handlers.item.equipment.special;

import core.api.ContentAPIKt;
import core.game.node.entity.Entity;
import core.game.node.entity.combat.BattleState;
import core.game.node.entity.combat.CombatStyle;
import core.game.node.entity.combat.MeleeSwingHandler;
import core.game.node.entity.impl.Animator;
import core.game.node.entity.player.Player;
import core.game.world.update.flag.context.Animation;
import core.game.world.update.flag.context.Graphics;
import core.plugin.Initializable;
import core.plugin.Plugin;
import core.tools.RandomFunction;

/**
 * Standalone special-attack handler for the Infinity Blade (item 14666).
 *
 * This is a dedicated duplicate of the cleave special so the Infinity Blade does NOT have
 * to be wired into the shared base {@code CleaveSpecialHandler} (which serves the Dragon
 * longsword 1305 and 13475). It registers item 14666 only and leaves those assets stock.
 */
@Initializable
public final class InfinityBladeSpecialHandler extends MeleeSwingHandler implements Plugin<Object> {
    private static final int SPECIAL_ENERGY = 25;
    private static final Animation ANIMATION = new Animation(1058, Animator.Priority.HIGH);
    private static final Graphics GRAPHIC = new Graphics(248, 96);

    public InfinityBladeSpecialHandler() {
        super();
    }

    @Override
    public Object fireEvent(String identifier, Object... args) {
        return null;
    }

    @Override
    public Plugin<Object> newInstance(Object arg) throws Throwable {
        CombatStyle.MELEE.getSwingHandler().register(14666, this);
        return this;
    }

    @Override
    public int swing(Entity entity, Entity victim, BattleState state) {
        if (!((Player) entity).getSettings().drainSpecial(SPECIAL_ENERGY)) {
            return -1;
        }
        state.setStyle(CombatStyle.MELEE);
        int hit = 0;
        if (isAccurateImpact(entity, victim, CombatStyle.MELEE)) {
            hit = RandomFunction.random(calculateHit(entity, victim, 1.25) + 1);
        }
        state.setEstimatedHit(hit);
        return 1;
    }

    @Override
    public void visualize(Entity entity, Entity victim, BattleState state) {
        ContentAPIKt.playGlobalAudio(entity.getLocation(), 2529);
        entity.visualize(ANIMATION, GRAPHIC);
    }
}
