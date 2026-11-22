package im.ghosty.kamoof.features.disguise;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.features.Feature;
import im.ghosty.kamoof.utils.Message;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static im.ghosty.kamoof.KamoofPlugin.*;

/**
 * La Feature
 * @since 1.0
 */
public final class DisguiseRestaurer extends Feature {
	
	@Override
	public boolean isEnabled() {
		return config().getBoolean("disguise.restaure");
	}
	
	public static String get(UUID uuid) {
		return data().getString("restaurer." + uuid);
	}
	
	public static void set(UUID uuid, String disguise) {
		data().set("restaurer." + uuid, disguise);
		saveData();
	}
	
	public static HashMap<OfflinePlayer, String> getRestaures() {
		HashMap<OfflinePlayer, String> map = new HashMap<>();
		AtomicBoolean shouldSaveData = new AtomicBoolean(false);
		
		data().getConfigurationSection("restaurer").getValues(false).forEach((uuid, disguise) -> {
			try {
				OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
				if(player.getName() == null)
					throw new RuntimeException();
				map.put(player, (String) disguise);
			} catch(Throwable exc) {
				data().set("restaurer."+uuid, null);
				shouldSaveData.set(true);
			}
		});
		
		if(shouldSaveData.get())
			saveData();
		return map;
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(), () -> {
			Bukkit.getOnlinePlayers().forEach(player -> {
				String name = player.getName();
				String disguise = get(player.getUniqueId());
				if (disguise != null) {
					set(player.getUniqueId(), null);
					if (!name.equalsIgnoreCase(disguise))
						DisguiseManager.apply(player, disguise);
				}
			});
			saveData();
		}, 1L);
		
	}
	
	@Override
	public void onStop() {
		super.onStop();
		if(!isEnabled()) {
			data().set("restaurer", null);
			saveData();
			return;
		}
		Bukkit.getOnlinePlayers().forEach(player -> {
			String nickname = DisguiseManager.getNickname(player);
			if (nickname != null && !player.getName().equalsIgnoreCase(nickname))
				set(player.getUniqueId(), nickname);
		});
		saveData();
	}
	
	/**
	 * Pré-enregistre le pseudo déguisé <b>dès le login</b>, avant que le serveur n'envoie
	 * les paquets <code>PLAYER_INFO</code> (qui portent le pseudo affiché au-dessus de la
	 * tête) aux autres joueurs. Sans cela, les autres joueurs verraient le vrai pseudo
	 * jusqu'à ce qu'un rafraîchissement survienne.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onLogin(PlayerLoginEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		String disguise = get(uuid);
		if (disguise != null)
			NametagManager.setNickname(uuid, disguise);
	}
	
	@EventHandler(priority = EventPriority.HIGH)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		String name = player.getName();
		UUID uuid = player.getUniqueId();
		String disguise = get(uuid);
		if (disguise == null)
			return;
		set(uuid, null);
		// Ré-application forcée : pseudos (tab-list + nametag) ET skin, même si
		// EssentialsX / SkinsRestorer ont déjà mémorisé une partie de l'état.
		DisguiseManager.apply(player, disguise);
		
		labelJoinMessage:
		{
			String message = event.getJoinMessage();
			if (message == null)
				break labelJoinMessage;
			event.setJoinMessage(message.replace(name, disguise));
		}
		
		Message.send(player, "messages.restaure", Map.of("player", name, "nick", disguise));
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		String nickname = DisguiseManager.getNickname(player);
		if (nickname == null)
			return;
		set(player.getUniqueId(), nickname);
	}
}
