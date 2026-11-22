package im.ghosty.kamoof.features.other;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.features.disguise.NametagManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les <b>illusions</b> de la Trousse du Menteur qui nécessitent de manipuler directement les
 * paquets (via <b>PacketEvents</b>) : fausse entrée dans la liste des joueurs (TAB), clone de
 * soi-même (NPC), silence (suppression des sons causés par un joueur) et transformation visuelle
 * des cochons en creepers pour un spectateur donné.
 * <p>
 * Toutes ces illusions sont purement côté client : le serveur reste inchangé. La classe est
 * totalement inerte si PacketEvents est absent.
 *
 * @since 1.4
 */
public final class IllusionManager {
	
	private static final Random RANDOM = new Random();
	
	/** Joueurs « muets » : UUID -> (dernière position connue, id d'entité). */
	private static final Map<UUID, Silenced> SILENCED = new ConcurrentHashMap<>();
	/** Spectateurs pour lesquels les cochons sont affichés en creepers. */
	private static final Set<UUID> PIG_VIEWERS = ConcurrentHashMap.newKeySet();
	/** Cochons modifiés (par spectateur) à restaurer lorsque l'illusion prend fin. */
	private static final Map<UUID, List<Affected>> PIG_AFFECTED = new ConcurrentHashMap<>();
	/** Faux joueurs ajoutés à la liste des joueurs (TAB) : id -> entrée. */
	private static final Map<UUID, Ghost> GHOSTS = new ConcurrentHashMap<>();
	/** Clones de joueurs en cours : propriétaire -> clone. */
	private static final Map<UUID, Clone> CLONES = new ConcurrentHashMap<>();
	
	private static IllusionListener listener;
	private static boolean warned;
	
	private IllusionManager() {
	}
	
	/** @return <code>true</code> si PacketEvents est présent et actif sur le serveur. */
	public static boolean isAvailable() {
		return NametagManager.isAvailable();
	}
	
	/** Enregistre l'écouteur de paquets (à appeler à l'activation du plugin). */
	public static void init() {
		attempt(0);
	}
	
	private static void attempt(int tries) {
		if (listener != null)
			return;
		if (isAvailable()) {
			try {
				listener = new IllusionListener();
				PacketEvents.getAPI().getEventManager().registerListener(listener);
				return;
			} catch (Throwable exc) {
				listener = null;
				warn("PacketEvents est présent mais l'écouteur d'illusions n'a pas pu être enregistré : " + exc);
				return;
			}
		}
		if (tries < 10) {
			Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(), () -> attempt(tries + 1), 10L);
			return;
		}
		warn("PacketEvents est introuvable : les illusions de la Trousse du Menteur seront indisponibles.");
	}
	
	/** Retire l'écouteur et nettoie toutes les illusions en cours (à appeler à la désactivation). */
	public static void disable() {
		try {
			for (Clone clone : CLONES.values())
				despawnClone(clone);
			for (UUID id : GHOSTS.keySet())
				sendAll(new WrapperPlayServerPlayerInfoRemove(id));
		} catch (Throwable ignored) {
		}
		CLONES.clear();
		GHOSTS.clear();
		SILENCED.clear();
		PIG_VIEWERS.clear();
		PIG_AFFECTED.clear();
		if (listener != null) {
			try {
				PacketEvents.getAPI().getEventManager().unregisterListener(listener);
			} catch (Throwable ignored) {
			}
			listener = null;
		}
	}
	
	// ---------------------------------------------------------------------
	// Fausse entrée dans la liste des joueurs (TAB)
	// ---------------------------------------------------------------------
	
	/**
	 * Ajoute un <b>faux joueur</b> dans la liste des joueurs (TAB) de tout le monde.
	 *
	 * @param fakeId   L'UUID du faux joueur
	 * @param name     Son pseudo
	 * @param textures Sa peau (peut être vide)
	 */
	public static void addGhostTab(UUID fakeId, String name, List<TextureProperty> textures) {
		GHOSTS.put(fakeId, new Ghost(name, textures == null ? new ArrayList<>() : new ArrayList<>(textures)));
		if (!isAvailable())
			return;
		runOnMain(() -> {
			for (Player viewer : Bukkit.getOnlinePlayers())
				sendGhostTo(viewer, fakeId, name, textures);
		});
	}
	
	/**
	 * Retire le faux joueur de la liste des joueurs (TAB).
	 *
	 * @param fakeId L'UUID du faux joueur
	 */
	public static void removeGhostTab(UUID fakeId) {
		GHOSTS.remove(fakeId);
		if (!isAvailable())
			return;
		runOnMain(() -> sendAll(new WrapperPlayServerPlayerInfoRemove(fakeId)));
	}
	
	private static void sendGhostTo(Player viewer, UUID fakeId, String name, List<TextureProperty> textures) {
		UserProfile profile = new UserProfile(fakeId, name, textures == null ? new ArrayList<>() : new ArrayList<>(textures));
		WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile);
		info.setListed(true);
		send(viewer, new WrapperPlayServerPlayerInfoUpdate(
			EnumSet.of(
				WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
				WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
			),
			info
		));
	}
	
	// ---------------------------------------------------------------------
	// Clone de soi-même (NPC)
	// ---------------------------------------------------------------------
	
	/**
	 * Fait apparaître un <b>clone</b> du joueur (même pseudo, même peau) à sa position.
	 *
	 * @param owner  Le propriétaire du clone
	 * @param mobile <code>true</code> pour un clone qui se déplace (IA de villageois idiot)
	 * @return L'identifiant du clone, ou <code>null</code> si PacketEvents est indisponible
	 */
	public static UUID spawnClone(Player owner, boolean mobile) {
		if (!isAvailable() || !owner.isOnline())
			return null;
		
		UUID cloneId = UUID.randomUUID();
		int entityId = 1_000_000 + RANDOM.nextInt(1_000_000);
		Location location = owner.getLocation().clone();
		Clone clone = new Clone(owner.getUniqueId(), cloneId, entityId, displayName(owner), texturesOf(owner), location, mobile);
		CLONES.put(owner.getUniqueId(), clone);
		
		runOnMain(() -> {
			for (Player viewer : Bukkit.getOnlinePlayers())
				spawnCloneTo(viewer, clone);
		});
		return cloneId;
	}
	
	private static void spawnCloneTo(Player viewer, Clone clone) {
		UserProfile profile = new UserProfile(clone.cloneId, clone.name, new ArrayList<>(clone.textures));
		WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile);
		info.setListed(true);
		send(viewer, new WrapperPlayServerPlayerInfoUpdate(
			EnumSet.of(
				WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
				WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
			),
			info
		));
		Location loc = clone.location;
		send(viewer, new WrapperPlayServerSpawnEntity(
			clone.entityId, clone.cloneId, EntityTypes.PLAYER,
			new com.github.retrooper.packetevents.protocol.world.Location(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()),
			loc.getYaw(), 0, new Vector3d(0, 0, 0)
		));
	}
	
	/**
	 * Déplace le clone du joueur (déplacement serveur, façon « villageois idiot »).
	 *
	 * @param owner Le propriétaire du clone
	 */
	public static void moveClone(Player owner) {
		Clone clone = CLONES.get(owner.getUniqueId());
		if (clone == null || !clone.mobile || !isAvailable())
			return;
		
		final Location from = clone.location;
		final Location to = from.clone().add(RANDOM.nextInt(-6, 7), 0, RANDOM.nextInt(-6, 7));
		// la direction du regard suit le déplacement (comme une entité qui marche)
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		to.setYaw(yaw);
		to.setPitch(0);
		clone.location = to;
		
		runOnMain(() -> {
			for (Player viewer : Bukkit.getOnlinePlayers()) {
				send(viewer, new WrapperPlayServerEntityTeleport(clone.entityId, new Vector3d(to.getX(), to.getY(), to.getZ()), yaw, 0, true));
				send(viewer, new WrapperPlayServerEntityHeadLook(clone.entityId, yaw));
			}
		});
	}
	
	/**
	 * Fait disparaître le clone du joueur.
	 *
	 * @param owner Le propriétaire du clone
	 */
	public static void despawnClone(Player owner) {
		despawnClone(owner.getUniqueId());
	}
	
	/**
	 * Fait disparaître le clone d'un joueur à partir de son UUID (utile si le joueur est hors-ligne).
	 *
	 * @param owner L'UUID du propriétaire du clone
	 */
	public static void despawnClone(UUID owner) {
		Clone clone = CLONES.remove(owner);
		if (clone != null)
			despawnClone(clone);
	}
	
	private static void despawnClone(Clone clone) {
		if (!isAvailable())
			return;
		runOnMain(() -> {
			sendAll(new WrapperPlayServerDestroyEntities(clone.entityId));
			sendAll(new WrapperPlayServerPlayerInfoRemove(clone.cloneId));
		});
	}
	
	// ---------------------------------------------------------------------
	// Silence (sons causés par un joueur)
	// ---------------------------------------------------------------------
	
	/**
	 * Active ou désactive le <b>silence</b> d'un joueur : tous les sons qu'il cause (pas, blocs,
	 * dégâts...) sont supprimés pour les autres joueurs.
	 *
	 * @param player  La cible
	 * @param silenced <code>true</code> pour rendre muet
	 */
	public static void setSilenced(Player player, boolean silenced) {
		if (!silenced) {
			SILENCED.remove(player.getUniqueId());
			return;
		}
		SILENCED.put(player.getUniqueId(), new Silenced(player.getLocation().clone(), player.getEntityId()));
	}
	
	/** Rafraîchit la position mémorisée d'un joueur muet (à appeler régulièrement). */
	public static void refreshSilenced(Player player) {
		Silenced state = SILENCED.get(player.getUniqueId());
		if (state != null)
			state.location = player.getLocation().clone();
	}
	
	// ---------------------------------------------------------------------
	// Cochons -> creepers (pour un spectateur)
	// ---------------------------------------------------------------------
	
	/**
	 * Active ou désactive l'illusion « tous les cochons sont des creepers » pour un spectateur.
	 *
	 * @param viewer  Le spectateur
	 * @param enabled <code>true</code> pour transformer, <code>false</code> pour restaurer
	 */
	public static void setPigsAsCreepers(Player viewer, boolean enabled) {
		UUID uuid = viewer.getUniqueId();
		if (enabled) {
			PIG_VIEWERS.add(uuid);
			refreshPigs(viewer);
			return;
		}
		PIG_VIEWERS.remove(uuid);
		List<Affected> affected = PIG_AFFECTED.remove(uuid);
		if (affected == null || !isAvailable())
			return;
		runOnMain(() -> {
			synchronized (affected) {
				for (Affected pig : affected)
					restorePig(viewer, pig);
			}
		});
	}
	
	private static void refreshPigs(Player viewer) {
		if (!isAvailable())
			return;
		final Location center = viewer.getLocation();
		final World world = viewer.getWorld();
		runOnMain(() -> {
			double max = 48 * 48;
			for (Pig pig : world.getEntitiesByClass(Pig.class)) {
				if (pig.getLocation().distanceSquared(center) > max)
					continue;
				Affected affected = new Affected(pig.getEntityId(), pig.getUniqueId());
				record(viewer.getUniqueId(), affected);
				sendPig(viewer, affected.entityId, affected.uuid, pig.getLocation(), EntityTypes.CREEPER);
			}
		});
	}
	
	private static void restorePig(Player viewer, Affected pig) {
		send(viewer, new WrapperPlayServerDestroyEntities(pig.entityId));
		Entity entity = pig.uuid == null ? null : Bukkit.getEntity(pig.uuid);
		if (entity instanceof Pig still) {
			// le cochon existe toujours : on le ré-affiche normalement
			sendPig(viewer, pig.entityId, pig.uuid, still.getLocation(), EntityTypes.PIG);
		}
	}
	
	private static void sendPig(Player viewer, int entityId, UUID uuid, Location at, EntityType type) {
		send(viewer, new WrapperPlayServerDestroyEntities(entityId));
		send(viewer, new WrapperPlayServerSpawnEntity(
			entityId, uuid, type,
			new com.github.retrooper.packetevents.protocol.world.Location(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()),
			at.getYaw(), 0, new Vector3d(0, 0, 0)
		));
	}
	
	private static void record(UUID viewer, Affected affected) {
		List<Affected> list = PIG_AFFECTED.computeIfAbsent(viewer, k -> Collections.synchronizedList(new ArrayList<>()));
		synchronized (list) {
			if (!list.contains(affected))
				list.add(affected);
		}
	}
	
	// ---------------------------------------------------------------------
	// Utilitaires
	// ---------------------------------------------------------------------
	
	/** Renvoie les illusions persistantes à un joueur qui vient de se connecter. */
	public static void resendTo(Player viewer) {
		if (!isAvailable())
			return;
		runOnMain(() -> {
			for (Map.Entry<UUID, Ghost> entry : GHOSTS.entrySet())
				sendGhostTo(viewer, entry.getKey(), entry.getValue().name, entry.getValue().textures);
			for (Clone clone : CLONES.values())
				spawnCloneTo(viewer, clone);
		});
	}
	
	/** @return Le pseudo affiché actuel du joueur (voir {@code DisguiseManager}). */
	private static String displayName(Player player) {
		String name = im.ghosty.kamoof.features.disguise.DisguiseManager.getNickname(player);
		return (name == null || name.isEmpty()) ? player.getName() : name;
	}
	
	/** @return Les propriétés de texture (peau/cape) du profil de connexion du joueur. */
	public static List<TextureProperty> texturesOf(Player player) {
		try {
			User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
			UserProfile profile = user == null ? null : user.getProfile();
			if (profile == null || profile.getTextureProperties() == null)
				return new ArrayList<>();
			return new ArrayList<>(profile.getTextureProperties());
		} catch (Throwable exc) {
			return new ArrayList<>();
		}
	}
	
	private static void send(Player viewer, Object packet) {
		try {
			PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
		} catch (Throwable ignored) {
		}
	}
	
	private static void sendAll(Object packet) {
		for (Player viewer : Bukkit.getOnlinePlayers())
			send(viewer, packet);
	}
	
	private static void runOnMain(Runnable runnable) {
		if (Bukkit.isPrimaryThread())
			runnable.run();
		else
			Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), runnable);
	}
	
	private static void warn(String message) {
		if (warned)
			return;
		warned = true;
		Bukkit.getLogger().warning("[KamoofSMP] " + message);
	}
	
	// ---------------------------------------------------------------------
	// Écouteur de paquets
	// ---------------------------------------------------------------------
	
	private static final class IllusionListener extends PacketListenerAbstract {
		
		@Override
		public void onPacketSend(PacketSendEvent event) {
			try {
				PacketType.Play.Server type = (PacketType.Play.Server) event.getPacketType();
				if (type == PacketType.Play.Server.SOUND_EFFECT)
					onSound(event);
				else if (type == PacketType.Play.Server.ENTITY_SOUND_EFFECT)
					onEntitySound(event);
				else if (type == PacketType.Play.Server.SPAWN_ENTITY)
					onSpawn(event);
			} catch (Throwable ignored) {
			}
		}
		
		private void onSound(PacketSendEvent event) {
			if (SILENCED.isEmpty())
				return;
			UUID viewer = event.getUser().getUUID();
			WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(event);
			Vector3d pos = packet.getPosition();
			if (pos == null) {
				Vector3i intPos = packet.getEffectPosition();
				if (intPos == null)
					return;
				pos = new Vector3d(intPos.getX(), intPos.getY(), intPos.getZ());
			}
			for (Map.Entry<UUID, Silenced> entry : SILENCED.entrySet()) {
				if (entry.getKey().equals(viewer))
					continue;
				Location loc = entry.getValue().location;
				if (loc == null)
					continue;
				double dx = pos.getX() - loc.getX();
				double dy = pos.getY() - loc.getY();
				double dz = pos.getZ() - loc.getZ();
				if (dx * dx + dy * dy + dz * dz <= 16.0) {
					event.setCancelled(true);
					return;
				}
			}
		}
		
		private void onEntitySound(PacketSendEvent event) {
			if (SILENCED.isEmpty())
				return;
			WrapperPlayServerEntitySoundEffect packet = new WrapperPlayServerEntitySoundEffect(event);
			int entityId = packet.getEntityId();
			for (Silenced state : SILENCED.values())
				if (state.entityId == entityId) {
					event.setCancelled(true);
					return;
				}
		}
		
		private void onSpawn(PacketSendEvent event) {
			if (PIG_VIEWERS.isEmpty())
				return;
			UUID viewer = event.getUser().getUUID();
			if (!PIG_VIEWERS.contains(viewer))
				return;
			WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
			if (!EntityTypes.PIG.equals(packet.getEntityType()))
				return;
			packet.setEntityType(EntityTypes.CREEPER);
			UUID uuid = packet.getUUID().orElse(null);
			record(viewer, new Affected(packet.getEntityId(), uuid));
			event.markForReEncode(true);
		}
		
	}
	
	// ---------------------------------------------------------------------
	// Structures internes
	// ---------------------------------------------------------------------
	
	private static final class Silenced {
		private volatile Location location;
		private final int entityId;
		
		private Silenced(Location location, int entityId) {
			this.location = location;
			this.entityId = entityId;
		}
	}
	
	private static final class Ghost {
		private final String name;
		private final List<TextureProperty> textures;
		
		private Ghost(String name, List<TextureProperty> textures) {
			this.name = name;
			this.textures = textures;
		}
	}
	
	private static final class Clone {
		private final UUID owner;
		private final UUID cloneId;
		private final int entityId;
		private final String name;
		private final List<TextureProperty> textures;
		private final boolean mobile;
		private Location location;
		
		private Clone(UUID owner, UUID cloneId, int entityId, String name, List<TextureProperty> textures, Location location, boolean mobile) {
			this.owner = owner;
			this.cloneId = cloneId;
			this.entityId = entityId;
			this.name = name;
			this.textures = textures;
			this.location = location;
			this.mobile = mobile;
		}
	}
	
	private record Affected(int entityId, UUID uuid) {
	}
	
}
