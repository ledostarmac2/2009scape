package content.region.morytania.quest.creatureoffenkenstrain;

import core.game.node.Node;
import core.game.node.entity.Entity;
import core.game.node.entity.player.Player;
import core.game.system.task.Pulse;
import core.game.world.map.Location;
import static core.api.ContentAPIKt.resetAnimator;
import static core.api.ContentAPIKt.teleport;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Lambda;

/**
 * Single-player replacement for the Fenkenstrain memorial push listener.
 *
 * The stock listener only teleports from one exact memorial tile. The graveyard
 * has two memorials with the same object id, and force-completed quest state can
 * leave the working one ambiguous. This replacement accepts both local memorials
 * and ignores the quest flag gate.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class CreatureOfFenkenstrainListeners$defineListeners$13 extends Lambda implements Function2 {
    public static final CreatureOfFenkenstrainListeners$defineListeners$13 INSTANCE =
            new CreatureOfFenkenstrainListeners$defineListeners$13();

    CreatureOfFenkenstrainListeners$defineListeners$13() {
        super(2);
    }

    @Override
    public Object invoke(Object first, Object second) {
        Player player = (Player) first;
        Node node = (Node) second;
        Location location = node.getLocation();
        System.out.println("[ExperimentCave] stock listener replacement fired at " + location);
        if (location != null && location.getX() >= 3568 && location.getX() <= 3584
                && location.getY() >= 3520 && location.getY() <= 3532) {
            player.sendMessage("You push the memorial aside and climb down into the cave.");
            final Location destination = Location.create(3577, 9927, 0);
            player.getPulseManager().run(new Pulse(3) {
                @Override
                public boolean pulse() {
                    resetAnimator(player);
                    teleport((Entity) player, destination, null);
                    return true;
                }
            });
            return Boolean.TRUE;
        }
        player.sendMessage("The memorial does not budge.");
        return Boolean.TRUE;
    }
}
