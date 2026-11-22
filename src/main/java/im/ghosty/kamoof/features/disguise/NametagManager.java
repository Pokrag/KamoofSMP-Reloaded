package im.ghosty.kamoof.features.disguise;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import im.ghosty.kamoof.KamoofPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le pseudo affiché <b>au-dessus de la tête</b> des joueurs déguisés.
 * <p>
 * EssentialsX modifie bien le pseudo partout (chat, tab-list, menus) <b>mais pas</b> le nom
 * affiché au-dessus de la tête : ce dernier provient du <i>GameProfile</i> transmis par le
 * paquet <code>PLAYER_INFO</code> (action <code>ADD_PLAYER</code>). La seule façon de le
 * changer est donc de réécrire ce paquet au moment où le serveur l'envoie au client.
 * <p>
 * Cette classe s'appuie sur <b>PacketEvents</b>, utilisé ici en mode « pre-built » : la
 * librairie n'est PAS incluse dans le .jar, le plugin PacketEvents doit simplement être
 * installé sur le serveur (voir <code>plugin.yml</code>). Si PacketEvents est absent, la
 * classe reste totalement inerte et le plugin fonctionne exactement comme avant.
 *
 * @since 1.0
 */
public final class NametagManager {
	
	/** L'UUID d'un joueur déguisé associé à son pseudo déguisé (alimenté depuis le thread principal). */
	private static final Map<UUID, String> NICKNAMES = new ConcurrentHashMap<>();
	
	private static NametagListener listener;
	private static boolean warned;
	
	private NametagManager() {
	}
	
	/**
	 * @return <code>true</code> si le plugin PacketEvents est présent et actif sur le serveur.
	 */
	public static boolean isAvailable() {
		return Bukkit.getPluginManager().getPlugin("packetevents") != null
			&& Bukkit.getPluginManager().isPluginEnabled("packetevents");
	}
	
	/**
	 * Enregistre l'écouteur de paquets (à appeler à l'activation du plugin).
	 * <p>
	 * PacketEvents peut être chargé légèrement après nous : on réessaie quelques fois
	 * avant d'abandonner.
	 */
	public static void init() {
		attempt(0);
	}
	
	private static void attempt(int tries) {
		if (listener != null)
			return;
		
		if (isAvailable()) {
			try {
				listener = new NametagListener();
				PacketEvents.getAPI().getEventManager().registerListener(listener);
				return;
			} catch (Throwable exc) {
				listener = null;
				warn("PacketEvents est présent mais son API n'a pas pu être utilisée : " + exc.getMessage());
				return;
			}
		}
		
		if (tries < 10) {
			Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(), () -> attempt(tries + 1), 10L);
			return;
		}
		
		warn("PacketEvents est introuvable : le pseudo affiché au-dessus de la tête ne sera pas modifié. "
			+ "Installez le plugin PacketEvents (https://github.com/retrooper/packetevents) pour corriger cela.");
	}
	
	/**
	 * Retire l'écouteur de paquets (à appeler à la désactivation du plugin).
	 */
	public static void disable() {
		if (listener == null)
			return;
		try {
			PacketEvents.getAPI().getEventManager().unregisterListener(listener);
		} catch (Throwable ignored) {
		}
		listener = null;
	}
	
	/** Mémorise le pseudo déguisé d'un joueur (ou l'oublie si <code>null</code>). Thread-safe. */
	public static void setNickname(UUID uuid, String nickname) {
		if (nickname == null || nickname.isEmpty())
			NICKNAMES.remove(uuid);
		else
			NICKNAMES.put(uuid, nickname);
	}
	
	/** @return Le pseudo déguisé mémorisé d'un joueur, ou <code>null</code>. */
	public static String nicknameOf(UUID uuid) {
		return NICKNAMES.get(uuid);
	}
	
	private static void warn(String message) {
		if (warned)
			return;
		warned = true;
		Bukkit.getLogger().warning("[KamoofSMP] " + message);
	}
	
	/**
	 * Écoute les paquets envoyés au client et remplace le nom du <i>GameProfile</i> par le
	 * pseudo déguisé, uniquement pour les joueurs concernés.
	 */
	private static final class NametagListener extends PacketListenerAbstract {
		
		@Override
		public void onPacketSend(PacketSendEvent event) {
			try {
				if (NICKNAMES.isEmpty())
					return;
				if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE)
					return;
				
				WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
				// Seule l'action ADD_PLAYER transporte le nom du profil (utilisé par le nametag).
				if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER))
					return;
				
				boolean changed = false;
				for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : packet.getEntries()) {
					UserProfile profile = info.getGameProfile();
					if (profile == null)
						continue;
					
					String nickname = NICKNAMES.get(profile.getUUID());
					if (nickname == null || nickname.equals(profile.getName()))
						continue;
					
					// On reconstruit le profil en conservant l'UUID et les textures (peau/cape),
					// on ne change que le nom.
					info.setGameProfile(new UserProfile(profile.getUUID(), nickname, profile.getTextureProperties()));
					changed = true;
				}
				
				// Indispensable : sans cela, les modifications sont perdues.
				if (changed)
					event.markForReEncode(true);
			} catch (Throwable ignored) {
				// Paquet illisible / version non supportée : on n'y touche pas.
			}
		}
		
	}
	
}
