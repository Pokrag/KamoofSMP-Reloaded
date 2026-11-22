package im.ghosty.kamoof.features.disguise;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import im.ghosty.kamoof.KamoofPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le pseudo affiché <b>au-dessus de la tête</b> des joueurs déguisés.
 * <p>
 * EssentialsX modifie bien le pseudo partout (chat, tab-list, menus) <b>mais pas</b> le nom
 * affiché au-dessus de la tête : ce dernier provient du <i>GameProfile</i> transmis par le
 * paquet <code>PLAYER_INFO</code>. La seule façon de le changer est donc de manipuler ce
 * paquet via <b>PacketEvents</b>.
 * <p>
 * Deux mécanismes complémentaires sont utilisés :
 * <ol>
 *     <li>la <b>réécriture</b> des paquets <code>PLAYER_INFO</code> sortants (utile quand un
 *     joueur rejoint : il reçoit d'emblée le bon pseudo) ;</li>
 *     <li>un <b>rafraîchissement actif</b> ({@link #refresh(UUID)}) : le client
 *     <i>ignore</i> un <code>ADD_PLAYER</code> pour une entrée déjà connue, il faut donc
 *     retirer puis ré-ajouter l'entrée pour mettre à jour le pseudo au-dessus de la tête
 *     d'un joueur déjà visible.</li>
 * </ol>
 * PacketEvents est utilisé en mode « pre-built » : la librairie n'est PAS incluse dans le
 * .jar, le plugin PacketEvents doit simplement être installé sur le serveur (voir
 * <code>plugin.yml</code>). S'il est absent, la classe reste totalement inerte et le plugin
 * fonctionne exactement comme avant.
 *
 * @since 1.0
 */
public final class NametagManager {
	
	/** L'UUID d'un joueur déguisé associé à son pseudo déguisé (alimenté depuis le thread principal). */
	private static final Map<UUID, String> NICKNAMES = new ConcurrentHashMap<>();
	/** Dernier profil complet vu pour un joueur (conserve la peau/cape lors du rafraîchissement). */
	private static final Map<UUID, UserProfile> PROFILES = new ConcurrentHashMap<>();
	/** Joueurs pour lesquels un cycle « masquer/afficher » Bedrock est en cours. */
	private static final Set<UUID> REFRESHING = ConcurrentHashMap.newKeySet();
	
	private static NametagListener listener;
	private static boolean warned;
	private static boolean errorLogged;
	
	private NametagManager() {
	}
	
	/**
	 * @return <code>true</code> si le plugin PacketEvents est présent et actif sur le serveur.
	 */
	public static boolean isAvailable() {
		try {
			return Bukkit.getPluginManager().getPlugin("packetevents") != null
				&& Bukkit.getPluginManager().isPluginEnabled("packetevents");
		} catch (Throwable exc) {
			return false;
		}
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
				Bukkit.getLogger().info("[KamoofSMP] PacketEvents détecté : le pseudo au-dessus de la tête des joueurs déguisés sera modifié.");
				return;
			} catch (Throwable exc) {
				listener = null;
				warn("PacketEvents est présent mais son API n'a pas pu être utilisée : " + exc);
				return;
			}
		}
		
		if (tries < 10) {
			Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(), () -> attempt(tries + 1), 10L);
			return;
		}
		
		warn("PacketEvents est introuvable : le pseudo affiché au-dessus de la tête ne sera pas modifié. "
			+ "Installez le plugin PacketEvents (https://github.com/retrooper/packetevents/releases) pour corriger cela.");
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
	
	/**
	 * Mémorise le pseudo déguisé d'un joueur (ou l'oublie si <code>null</code>) et
	 * <b>rafraîchit immédiatement</b> le pseudo au-dessus de la tête pour tous les joueurs
	 * déjà connectés. Thread-safe.
	 */
	public static void setNickname(UUID uuid, String nickname) {
		if (uuid == null)
			return;
		if (nickname == null || nickname.isEmpty()) {
			NICKNAMES.remove(uuid);
			PROFILES.remove(uuid);
		} else {
			NICKNAMES.put(uuid, nickname);
		}
		try {
			refresh(uuid);
		} catch (Throwable exc) {
			// PacketEvents absent/indisponible : on ne doit JAMAIS casser le déguisement
			// (pseudo EssentialsX + skin SkinsRestorer) à cause du nametag.
			logError("Nametag au-dessus de la tête indisponible : " + exc);
		}
	}
	
	/** @return Le pseudo déguisé mémorisé d'un joueur, ou <code>null</code>. */
	public static String nicknameOf(UUID uuid) {
		return NICKNAMES.get(uuid);
	}
	
	/**
	 * Force l'envoi, à tous les joueurs en ligne, de l'entrée de liste du joueur ciblé avec
	 * son pseudo déguisé.
	 * <p>
	 * C'est indispensable : le client n'accepte la mise à jour du nom du profil que s'il
	 * <b>retire</b> puis <b>re-ajoute</b> l'entrée de liste (un <code>ADD_PLAYER</code> sur une
	 * entrée déjà connue est ignoré par le client, ce qui expliquait l'absence de mise à jour
	 * du pseudo au-dessus de la tête).
	 */
	public static void refresh(UUID uuid) {
		if (uuid == null || !isAvailable())
			return;
		
		// l'envoi de paquets doit se faire sur le thread principal
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> refresh(uuid));
			return;
		}
		
		Player target = Bukkit.getPlayer(uuid);
		if (target == null || !target.isOnline())
			return;
		
		try {
			String nickname = NICKNAMES.get(uuid);
			String name = (nickname == null || nickname.isEmpty()) ? target.getName() : nickname;
			
			// On réutilise le profil complet (peau/cape) déjà vu pour ce joueur, sinon le profil
			// réel connu de PacketEvents, afin de ne pas perdre la peau dans la liste des joueurs.
			UserProfile reference = PROFILES.get(uuid);
			if (reference == null) {
				User user = PacketEvents.getAPI().getPlayerManager().getUser(target);
				reference = user == null ? null : user.getProfile();
			}
			
			List<com.github.retrooper.packetevents.protocol.player.TextureProperty> textures =
				reference == null || reference.getTextureProperties() == null
					? new ArrayList<>()
					: new ArrayList<>(reference.getTextureProperties());
			
			UserProfile profile = new UserProfile(uuid, name, textures);
			
			WrapperPlayServerPlayerInfoRemove remove = new WrapperPlayServerPlayerInfoRemove(uuid);
			WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
				new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile);
			info.setListed(true);
			WrapperPlayServerPlayerInfoUpdate add = new WrapperPlayServerPlayerInfoUpdate(
				EnumSet.of(
					WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
					WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
				),
				info
			);
			
			for (Player viewer : Bukkit.getOnlinePlayers()) {
				PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, remove);
				PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, add);
			}
		} catch (Throwable exc) {
			logError("Impossible de rafraîchir le pseudo au-dessus de la tête : " + exc);
		}
		
		// Geyser ignore la mise à jour du profil pour un joueur déjà connu : il faut recréer
		// l'entité côté Bedrock (cf. la méthode pour le détail).
		try {
			refreshBedrock(target);
		} catch (Throwable exc) {
			logError("Impossible de rafraîchir le pseudo au-dessus de la tête (Bedrock) : " + exc);
		}
	}
	
	/**
	 * Geyser n'applique <b>pas</b> la mise à jour du nom d'un joueur déjà connu : côté Bedrock, le
	 * pseudo affiché au-dessus de la tête est le <i>username</i> de l'entité, figé au moment où
	 * elle est envoyée au client. Réécrire les paquets <code>PLAYER_INFO</code> ne suffit donc pas
	 * (contrairement à un client Java, qui, lui, se met à jour).
	 * <p>
	 * La parade consiste à <b>masquer puis réafficher</b> le joueur pour chaque spectateur Bedrock :
	 * le serveur renvoie alors toute la séquence attendue par Geyser
	 * (<code>PLAYER_INFO_REMOVE</code> + destruction de l'entité, puis <code>PLAYER_INFO_ADD</code>
	 * — réécrit avec le pseudonyme — + création de l'entité), ce qui recrée l'entité Bedrock avec
	 * le bon pseudo au-dessus de la tête.
	 *
	 * @param target Le joueur dont le pseudo vient d'être modifié
	 */
	private static void refreshBedrock(Player target) {
		UUID uuid = target.getUniqueId();
		// évite d'empiler deux cycles masquer/afficher pour le même joueur
		if (!REFRESHING.add(uuid))
			return;
		
		List<Player> viewers = new ArrayList<>();
		for (Player viewer : Bukkit.getOnlinePlayers()) {
			if (viewer.equals(target) || !viewer.canSee(target))
				continue;
			if (!isBedrockPlayer(viewer.getUniqueId()))
				continue;
			viewer.hidePlayer(KamoofPlugin.getInstance(), target);
			viewers.add(viewer);
		}
		
		if (viewers.isEmpty()) {
			REFRESHING.remove(uuid);
			return;
		}
		
		// un léger délai est nécessaire : masquer et réafficher dans le même tick est mutualisé
		// par le serveur et n'entraîne aucune re-création de l'entité.
		Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(), () -> {
			try {
				Player refreshed = Bukkit.getPlayer(uuid);
				if (refreshed != null) {
					for (Player viewer : viewers)
						if (viewer.isOnline())
							viewer.showPlayer(KamoofPlugin.getInstance(), refreshed);
				}
			} finally {
				REFRESHING.remove(uuid);
			}
		}, 1L);
	}
	
	/**
	 * Détermine si un joueur est un joueur <b>Bedrock</b>. La détection utilise, sans dépendance
	 * obligatoire : le format d'UUID propre à Floodgate, l'API de Geyser, puis celle de Floodgate.
	 *
	 * @param uuid L'UUID du joueur
	 * @return <code>true</code> si le joueur provient de Bedrock
	 */
	private static boolean isBedrockPlayer(UUID uuid) {
		if (uuid == null)
			return false;
		// Floodgate attribue toujours des UUID de la forme « 00000000-0000-0000-.... »
		if (uuid.toString().startsWith("00000000-0000-0000-"))
			return true;
		return geyserBedrock(uuid) || floodgateBedrock(uuid);
	}
	
	private static boolean geyserBedrock(UUID uuid) {
		try {
			Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
			Object api = apiClass.getMethod("api").invoke(null);
			Object result = apiClass.getMethod("isBedrockPlayer", UUID.class).invoke(api, uuid);
			return result instanceof Boolean value && value;
		} catch (Throwable ignored) {
			return false;
		}
	}
	
	private static boolean floodgateBedrock(UUID uuid) {
		try {
			Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
			return result instanceof Boolean value && value;
		} catch (Throwable ignored) {
			return false;
		}
	}
	
	private static void warn(String message) {
		if (warned)
			return;
		warned = true;
		Bukkit.getLogger().warning("[KamoofSMP] " + message);
	}
	
	private static void logError(String message) {
		if (errorLogged)
			return;
		errorLogged = true;
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
				if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE)
					return;
				
				WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
				// Seule l'action ADD_PLAYER transporte le nom du profil (utilisé par le nametag).
				if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER))
					return;
				
				if (NICKNAMES.isEmpty())
					return;
				
				// On mémorise le profil complet (avec la peau/cape) des joueurs déguisés pour
				// pouvoir le réutiliser lors d'un rafraîchissement ultérieur.
				for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : packet.getEntries()) {
					UserProfile profile = info.getGameProfile();
					if (profile != null && profile.getUUID() != null && NICKNAMES.containsKey(profile.getUUID()))
						PROFILES.put(profile.getUUID(), profile);
				}
				
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
			} catch (Throwable exc) {
				logError("Erreur lors de la réécriture d'un paquet PLAYER_INFO : " + exc);
			}
		}
		
	}
	
}
