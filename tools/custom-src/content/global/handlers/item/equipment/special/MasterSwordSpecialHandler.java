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
 * Standalone special-attack handler for the Master Sword (item 14670).
 *
 * Dedicated duplicate of the cleave special so the Master Sword is NOT wired into the
 * shared base {@code CleaveSpecialHandler} (Dragon longsword 1305 / 13475). Registers item
 * 14656 only. Slightly stronger than the stock cleave (1.4x) to suit a legendary blade.
 */
@Initializable
public final class MasterSwordSpecialHandler extends MeleeSwingHandler implements Plugin<Object> {
    private static final int SPECIAL_ENERGY = 25;
    private static final Animation ANIMATION = new Animation(1058, Animator.Priority.HIGH);
    private static final Graphics GRAPHIC = new Graphics(248, 96);

    public MasterSwordSpecialHandler() {
        super();
    }

    @Override
    public Object fireEvent(String identifier, Object... args) {
        return null;
    }

    @Override
    public Plugin<Object> newInstance(Object arg) throws Throwable {
        CombatStyle.MELEE.getSwingHandler().register(14656, this);
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
            hit = RandomFunction.random(calculateHit(entity, victim, 1.4) + 1);
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
