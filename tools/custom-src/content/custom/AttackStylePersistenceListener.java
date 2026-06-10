package content.custom;

import core.api.LoginListener;
import core.game.node.entity.combat.equipment.WeaponInterface;
import core.plugin.Initializable;
import core.game.node.entity.player.Player;
import core.game.node.entity.player.link.Settings;
import core.game.system.task.Pulse;
import core.game.world.GameWorld;

/**
 * Restores the player's saved attack style after login equipment refresh.
 * Login re-equips worn items, which triggers WeaponInterface.ensureStyleIndex()
 * and can remap the style to defensive even when settings.attackStyle is correct.
 */
@Initializable
public final class AttackStylePersistenceListener implements LoginListener {
    private static final String SAVED_ATTACK_STYLE_ATTR = "attack-style:saved-index";
    private static final int RESTORE_DELAY_TICKS = 5;
    private static final int RESTORE_ATTEMPTS = 3;

    @Override
    public void login(Player player) {
        int savedIndex = player.getSettings().getAttackStyleIndex();
        player.setAttribute(SAVED_ATTACK_STYLE_ATTR, savedIndex);

        GameWorld.getPulser().submit(new Pulse(RESTORE_DELAY_TICKS) {
            private int attempts = 0;

            @Override
            public boolean pulse() {
                restoreAttackStyle(player);
                return ++attempts >= RESTORE_ATTEMPTS;
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
            return;
        }

        WeaponInterface.WeaponInterfaces iface = weaponInterface.getWeaponInterface();
        if (iface == null) {
            weaponInterface.updateInterface();
            iface = weaponInterface.getWeaponInterface();
        }
        if (iface == null) {
            return;
        }

        WeaponInterface.AttackStyle[] styles = iface.getAttackStyles();
        int maxIndex = styles.length - 1;
        int clampedIndex = Math.max(0, Math.min(savedIndex, maxIndex));
        WeaponInterface.AttackStyle targetStyle = styles[clampedIndex];

        Settings settings = player.getSettings();
        settings.toggleAttackStyleIndex(clampedIndex);
        player.getProperties().setAttackStyle(targetStyle);
        weaponInterface.updateInterface();
    }
}
