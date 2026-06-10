package content.custom;

import core.api.LoginListener;
import core.game.node.entity.combat.equipment.WeaponInterface;
import core.game.node.entity.player.Player;
import core.game.node.entity.player.link.Settings;
import core.game.system.task.Pulse;
import core.game.world.GameWorld;

/**
 * Restores the player's saved attack style after login equipment refresh.
 * Login re-equips worn items, which triggers WeaponInterface.ensureStyleIndex()
 * and can remap the style to defensive even when settings.attackStyle is correct.
 */
public final class AttackStylePersistenceListener implements LoginListener {
    private static final String SAVED_ATTACK_STYLE_ATTR = "attack-style:saved-index";

    @Override
    public void login(Player player) {
        int savedIndex = player.getSettings().getAttackStyleIndex();
        player.setAttribute(SAVED_ATTACK_STYLE_ATTR, savedIndex);

        GameWorld.getPulser().submit(new Pulse(2) {
            @Override
            public boolean pulse() {
                restoreAttackStyle(player);
                return true;
            }
        });
    }

    private static void restoreAttackStyle(Player player) {
        Object saved = player.getAttribute(SAVED_ATTACK_STYLE_ATTR, null);
        if (saved == null) {
            return;
        }

        int savedIndex = ((Number) saved).intValue();
        WeaponInterface weaponInterface = player.getExtension(WeaponInterface.class);
        if (weaponInterface == null) {
            player.removeAttribute(SAVED_ATTACK_STYLE_ATTR);
            return;
        }

        WeaponInterface.WeaponInterfaces iface = weaponInterface.getWeaponInterface();
        if (iface == null) {
            weaponInterface.updateInterface();
            iface = weaponInterface.getWeaponInterface();
        }
        if (iface == null) {
            player.removeAttribute(SAVED_ATTACK_STYLE_ATTR);
            return;
        }

        int maxIndex = iface.getAttackStyles().length - 1;
        int clampedIndex = Math.max(0, Math.min(savedIndex, maxIndex));

        Settings settings = player.getSettings();
        if (settings.getAttackStyleIndex() != clampedIndex) {
            settings.toggleAttackStyleIndex(clampedIndex);
            weaponInterface.updateInterface();
        }

        player.removeAttribute(SAVED_ATTACK_STYLE_ATTR);
    }
}
