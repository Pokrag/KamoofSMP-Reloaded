package im.ghosty.kamoof.features.drophead;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
import im.ghosty.kamoof.features.Feature;
import im.ghosty.kamoof.features.disguise.DisguiseManager;
import im.ghosty.kamoof.features.ritual.RitualHandler;
import org.bukkit.GameRule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * {@link Feature} pour gérer le drop de têtes à la mort d'un joueur.
 * @since 1.0
 */
public final class HeadDropper extends Feature {
	
	/**
	 * On utilise également du code du Rituel, donc on laisse activé et met la vérification + bas
	 */
	@Override
	public boolean isEnabled() {
		return true;
	}
	
	/**
	 * <b>Priorité {@link EventPriority#LOWEST} :</b> le déguisement doit encore être actif à ce moment-là pour
	 * récupérer le pseudo déguisé (voir {@link im.ghosty.kamoof.features.disguise.DisguiseListener#onDeath}).
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		// Si le joueur est déguisé, c'est la tête du déguisement qui tombe à la place de la sienne
		String name = DisguiseManager.getNickname(player);
		if (name == null || name.isEmpty())
			name = KamoofSMP.getInstance().getName(player);
		
		String pacte = RitualHandler.getPacte(player);
		if (pacte != null) {
			if (pacte.equalsIgnoreCase("2"))
				return;
			if (pacte.equalsIgnoreCase("1")) {
				ItemStack skull = SkullManager.getSkull(name);
				skull.setAmount(KamoofPlugin.config().getInt("ritual.pactes.bloody.heads"));
				drop(event, player, skull);
				return;
			}
		}
		if (KamoofPlugin.config().getBoolean("drophead.enabled")) {
			drop(event, player, SkullManager.getSkull(name));
		}
	}
	
	/**
	 * Fait tomber la tête du joueur à sa mort.
	 * <p>
	 * La gamerule <code>keepInventory</code> est une règle <b>différente</b> du drop de tête : quand elle est
	 * activée, les items de l'inventaire ne tombent pas au sol, mais la tête doit tout de même tomber. On la
	 * dépose donc nous-mêmes dans le monde au lieu de la confier aux drops de la mort.
	 *
	 * @param event  La mort du joueur
	 * @param player Le joueur mort
	 * @param skull  La tête à faire tomber
	 */
	private static void drop(PlayerDeathEvent event, Player player, ItemStack skull) {
		if (Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.KEEP_INVENTORY)))
			player.getWorld().dropItemNaturally(player.getLocation(), skull);
		else
			event.getDrops().add(skull);
	}
	
}
