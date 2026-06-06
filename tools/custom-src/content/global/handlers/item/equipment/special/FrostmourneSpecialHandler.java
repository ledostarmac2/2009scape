package content.global.handlers.item.equipment.special;

import core.api.ContentAPIKt;
import core.game.node.entity.Entity;
import core.game.node.entity.combat.BattleState;
import core.game.node.entity.combat.CombatStyle;
import core.game.node.entity.combat.MeleeSwingHandler;
import core.game.node.entity.impl.Animator;
import core.game.node.entity.player.Player;
import core.game.world.update.flag.context.Animation;
import core.plugin.Initializable;
import core.plugin.Plugin;
import core.tools.RandomFunction;

/**
 * Standalone Vesta-style special attack for Frostmourne (item 14545).
 */
@Initializable
public final class FrostmourneSpecialHandler extends MeleeSwingHandler implements Plugin<Object> {
    private static final int SPECIAL_ENERGY = 25;
    private static final Animation ANIMATION = new Animation(10502, Animator.Priority.HIGH);

    public FrostmourneSpecialHandler() {
        super();
    }

    @Override
    public Object fireEvent(String identifier, Object... args) {
        return null;
    }

    @Override
    public Plugin<Object> newInstance(Object arg) throws Throwable {
        CombatStyle.MELEE.getSwingHandler().register(14545, this);
        return this;
    }

    @Override
    public int swing(Entity entity, Entity victim, BattleState state) {
        if (!((Player) entity).getSettings().drainSpecial(SPECIAL_ENERGY)) {
            return -1;
        }
        state.setStyle(CombatStyle.MELEE);
        int hit = 0;
        if (isAccurateImpact(entity, victim, CombatStyle.MELEE, 1.0, 0.25)) {
            hit = calculateHit(entity, victim, 0.2) + RandomFunction.random(calculateHit(entity, victim, 1.0) + 1);
        }
        state.setEstimatedHit(hit);
        return 1;
    }

    @Override
    public void visualize(Entity entity, Entity victim, BattleState state) {
        ContentAPIKt.playGlobalAudio(entity.getLocation(), 2529);
        entity.animate(ANIMATION);
    }
}
