package im.ghosty.kamoof.features.disguise;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
import im.ghosty.kamoof.api.events.KamoofDisguiseEvent;
import im.ghosty.kamoof.features.Feature;
import im.ghosty.kamoof.features.drophead.SkullManager;
import im.ghosty.kamoof.utils.Message;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

/**
 * {@link Feature} pour gérer le déguisement du joueur et son utilisation.
 *
 * @since 1.0
 */
public final class DisguiseListener extends Feature {
	
	/**
	 * Pourquoi installer le KamoofSMP pour pas l'utiliser :clown:
	 */
	@Override
	public boolean isEnabled() {
		return true;
	}
	
	/**
	 * Utilisation d'une tête
	 *
	 * @since 1.0
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		OfflinePlayer target = SkullManager.getOwner(event.getItem());
		if (target == null)
			return;
		
		switch (event.getAction()) {
			case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR, PHYSICAL -> {
				return;
			}
			case RIGHT_CLICK_BLOCK -> {
				if (!KamoofPlugin.config().getBoolean("disguise.place-head"))
					event.setCancelled(true);
				return;
			}
		}
		
		String name = KamoofSMP.getInstance().getName(target);
		if (name == null)
			name = SkullManager.getName(event.getItem());
		
		String currentNickname = DisguiseManager.getNickname(player);
		// Rien ne changerait (tête visée = déguisement actuel, ou propre tête alors qu'on n'est
		// pas déguisé) : on ne consomme surtout pas la tête pour rien, sinon elle disparaît.
		if (Objects.equals(currentNickname, name) || (currentNickname == null && Objects.equals(KamoofSMP.getInstance().getName(player), name)))
			return;
		
		KamoofDisguiseEvent disguiseEvent = new KamoofDisguiseEvent(player, name);
		Bukkit.getPluginManager().callEvent(disguiseEvent);
		if (disguiseEvent.isCancelled())
			return;
		
		if (player.getGameMode() != GameMode.CREATIVE)
			event.getItem().setAmount(event.getItem().getAmount() - 1);
		
		if (KamoofPlugin.config().getBoolean("disguise.give-back") && currentNickname != null) {
			ItemStack item = SkullManager.getSkull(currentNickname);
			if (!player.getInventory().addItem(item).isEmpty())
				player.getWorld().dropItem(player.getLocation(), item);
		}
		
		DisguiseManager.disguise(player, name);
		Message.send(player, "messages.disguised", Map.of("player", KamoofSMP.getInstance().getName(player), "nick", name));
	}
	
	/**
	 * Perte du déguisement à la mort
	 *
	 * @since 1.0
	 */
	@EventHandler(priority = EventPriority.LOW)
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		String disguise = DisguiseManager.getNickname(player);
		if (disguise == null)
			return;
		KamoofSMP.getInstance().disguise(player, null);
		Message.send(player, "messages.lostdisguise", Map.of("player", KamoofSMP.getInstance().getName(player), "nick", disguise));
		
		DisguiseRestaurer.set(player.getUniqueId(), null);
	}
	
}
