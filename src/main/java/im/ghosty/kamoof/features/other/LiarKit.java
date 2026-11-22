package im.ghosty.kamoof.features.other;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.features.Feature;
import im.ghosty.kamoof.features.disguise.DisguiseManager;
import im.ghosty.kamoof.features.disguise.NametagManager;
import im.ghosty.kamoof.utils.Lang;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La <b>Trousse du Menteur</b> : un ensemble d'actions payantes (via <b>Vault</b>) permettant de
 * tromper les autres joueurs, accessibles depuis <code>/camouf menu</code>.
 * <ul>
 *     <li><b>Anonymat</b> : transforme temporairement le joueur en « Anonymous » (chat inclus) ;</li>
 *     <li><b>Disparition du TAB</b> : retire le joueur de la liste des joueurs (même sans déguisement) ;</li>
 *     <li><b>Faux messages</b> : connexion, déconnexion, mort et progression ;</li>
 *     <li><b>Connexion fantôme</b> : fait croire à une connexion qui se déconnecte toute seule ;</li>
 *     <li><b>Fantôme</b> : rend le joueur totalement invisible (vanish).</li>
 * </ul>
 * Ces actions ne modifient jamais le déguisement « réel » du joueur : un éventuel déguisement en
 * cours est restauré à la fin de l'effet.
 *
 * @since 1.2
 */
public final class LiarKit extends Feature {
	
	@Getter
	private static LiarKit instance;
	
	/** Déguisement (ou <code>null</code>) à restaurer à la fin du mode anonyme. */
	private final Map<UUID, String> anonymousRestore = new ConcurrentHashMap<>();
	/** Tâches terminant le mode anonyme. */
	private final Map<UUID, BukkitTask> anonymousTasks = new ConcurrentHashMap<>();
	/** Tâches ré-appliquant/terminant la disparition du TAB. */
	private final Map<UUID, BukkitTask> tabTasks = new ConcurrentHashMap<>();
	/** Tâches déconnectant automatiquement la connexion fantôme. */
	private final Map<UUID, BukkitTask> ghostTasks = new ConcurrentHashMap<>();
	/** Tâches ré-appliquant/terminant le mode fantôme (vanish). */
	private final Map<UUID, BukkitTask> vanishTasks = new ConcurrentHashMap<>();
	/** Tâches de vie du clone (déplacement + fin). */
	private final Map<UUID, BukkitTask> cloneTasks = new ConcurrentHashMap<>();
	/** Tâches terminant le silence (tous les sons du joueur coupés). */
	private final Map<UUID, BukkitTask> silenceTasks = new ConcurrentHashMap<>();
	/** Tâches terminant l'illusion « cochons en creepers ». */
	private final Map<UUID, BukkitTask> pigTasks = new ConcurrentHashMap<>();
	/** Auteur (pseudo affiché) de la connexion fantôme en cours, pour la déconnexion automatique. */
	private final Map<UUID, String> ghostNames = new ConcurrentHashMap<>();
	/** UUID du faux joueur ajouté au TAB pour la connexion fantôme en cours. */
	private final Map<UUID, UUID> ghostTabs = new ConcurrentHashMap<>();
	/** Joueurs actuellement en mode fantôme (vanish). */
	private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
	
	public LiarKit() {
		instance = this;
	}
	
	@Override
	public boolean isEnabled() {
		return KamoofPlugin.config().getBoolean("liarkit.enabled");
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		clearAll();
	}
	
	@Override
	public void onStop() {
		clearAll();
	}
	
	private void clearAll() {
		for (UUID uuid : anonymousTasks.keySet())
			cancel(anonymousTasks, uuid);
		for (UUID uuid : tabTasks.keySet())
			cancel(tabTasks, uuid);
		for (UUID uuid : ghostTasks.keySet())
			cancel(ghostTasks, uuid);
		for (UUID uuid : vanishTasks.keySet())
			cancel(vanishTasks, uuid);
		for (UUID uuid : cloneTasks.keySet())
			cancel(cloneTasks, uuid);
		for (UUID uuid : silenceTasks.keySet())
			cancel(silenceTasks, uuid);
		for (UUID uuid : pigTasks.keySet())
			cancel(pigTasks, uuid);
		anonymousRestore.clear();
		ghostNames.clear();
		ghostTabs.clear();
		vanished.clear();
	}
	
	// ---------------------------------------------------------------------
	// Configuration
	// ---------------------------------------------------------------------
	
	/** @return Le prix configuré d'une action (0 = gratuit). */
	public double price(String key) {
		return Math.max(0, KamoofPlugin.config().getDouble("liarkit.prices." + key));
	}
	
	/** @return Le prix formaté (symbole monétaire inclus). */
	public String priceLabel(String key) {
		return EconomyHook.format(price(key));
	}
	
	/** @return Le solde formaté du joueur. */
	public String balanceLabel(Player player) {
		return EconomyHook.format(EconomyHook.balance(player));
	}
	
	/** @return Le pseudo utilisé pour le mode anonyme. */
	public String anonymousName() {
		return KamoofPlugin.config().getString("liarkit.anonymous-name", "Anonymous");
	}
	
	/** @return La durée (en secondes) du mode anonyme. */
	public int anonymousDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.anonymous-duration", 60));
	}
	
	/** @return La durée (en secondes) de la disparition du TAB. */
	public int tabDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.tab-duration", 60));
	}
	
	/** @return La durée (en secondes) de la connexion fantôme et du mode fantôme. */
	public int ghostDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.ghost-duration", 60));
	}
	
	/** @return La durée (en secondes) du clone de soi-même. */
	public int cloneDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.clone-duration", 60));
	}
	
	/** @return La durée (en secondes) du silence (sons du joueur coupés). */
	public int silenceDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.silence-duration", 60));
	}
	
	/** @return La durée (en secondes) de l'illusion « cochons en creepers ». */
	public int pigDuration() {
		return Math.max(1, KamoofPlugin.config().getInt("liarkit.pig-duration", 60));
	}
	
	/** @return Le nom du faux progrès affiché. */
	public String advancementName() {
		return KamoofPlugin.config().getString("liarkit.advancement-name", "Progrès Secret");
	}
	
	/** @return Une durée lisible, p. ex. « 1 min 30 s ». */
	public static String durationLabel(int seconds) {
		int minutes = seconds / 60;
		int rest = seconds % 60;
		if (minutes <= 0)
			return rest + " s";
		if (rest == 0)
			return minutes + " min";
		return minutes + " min " + rest + " s";
	}
	
	/** @return Le pseudo affiché actuel du joueur (déguisement s'il y en a un, sinon vrai pseudo). */
	private static String displayName(Player player) {
		String name = DisguiseManager.getNickname(player);
		return (name == null || name.isEmpty()) ? player.getName() : name;
	}
	
	// ---------------------------------------------------------------------
	// Actions payantes
	// ---------------------------------------------------------------------
	
	/**
	 * Transforme temporairement le joueur en « Anonyme ».
	 *
	 * @param player La cible
	 */
	public void buyAnonymous(Player player) {
		if (!check(player))
			return;
		if (!charge(player, price("anonymous")))
			return;
		
		int duration = anonymousDuration();
		UUID uuid = player.getUniqueId();
		// On ne ré-écrase pas le déguisement mémorisé si l'effet est relancé
		// (une chaîne vide signifie « pas de déguisement au préalable »).
		if (!anonymousRestore.containsKey(uuid)) {
			String previous = DisguiseManager.getNickname(player);
			anonymousRestore.put(uuid, previous == null ? "" : previous);
		}
		DisguiseManager.applyName(player, anonymousName());
		
		cancel(anonymousTasks, uuid);
		anonymousTasks.put(uuid, Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(),
			() -> endAnonymous(uuid), duration * 20L));
		Lang.send(player, "LIARKIT_ANONYMOUS_ON", durationLabel(duration));
	}
	
	private void endAnonymous(UUID uuid) {
		cancel(anonymousTasks, uuid);
		String previous = anonymousRestore.remove(uuid);
		Player player = Bukkit.getPlayer(uuid);
		if (player == null || !player.isOnline())
			return;
		if (previous == null || previous.isEmpty())
			DisguiseManager.clearName(player);
		else
			DisguiseManager.applyName(player, previous);
		Lang.send(player, "LIARKIT_ANONYMOUS_OFF");
	}
	
	/**
	 * Retire temporairement le joueur de la liste des joueurs (TAB), sans toucher à l'entité en jeu.
	 *
	 * @param player La cible
	 */
	public void buyTabVanish(Player player) {
		if (!check(player))
			return;
		if (!NametagManager.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		if (!charge(player, price("tab")))
			return;
		
		int duration = tabDuration();
		UUID uuid = player.getUniqueId();
		NametagManager.setTabListed(player, false);
		
		cancel(tabTasks, uuid);
		tabTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(KamoofPlugin.getInstance(), new Runnable() {
			private int left = duration;
			
			@Override
			public void run() {
				Player online = Bukkit.getPlayer(uuid);
				if (online == null || !online.isOnline()) {
					cancel(tabTasks, uuid);
					return;
				}
				if (left <= 0) {
					endTabVanish(uuid);
					return;
				}
				// ré-appliqué chaque seconde pour rattraper les joueurs qui se connectent entre-temps
				NametagManager.setTabListed(online, false);
				left--;
			}
		}, 20L, 20L));
		Lang.send(player, "LIARKIT_TAB_ON", durationLabel(duration));
	}
	
	private void endTabVanish(UUID uuid) {
		cancel(tabTasks, uuid);
		Player player = Bukkit.getPlayer(uuid);
		if (player == null || !player.isOnline())
			return;
		NametagManager.setTabListed(player, true);
		Lang.send(player, "LIARKIT_TAB_OFF");
	}
	
	/**
	 * Diffuse un faux message de connexion/déconnexion avec le pseudo affiché actuel du joueur
	 * (son déguisement s'il en a un, sinon son vrai pseudo).
	 *
	 * @param player La cible
	 * @param join   <code>true</code> pour un message de connexion, <code>false</code> pour une déconnexion
	 */
	public void fakeMessage(Player player, boolean join) {
		if (!check(player))
			return;
		if (!charge(player, price(join ? "fake-join" : "fake-quit")))
			return;
		
		DisguiseManager.broadcastFake(displayName(player), join, player);
	}
	
	/**
	 * Diffuse un faux message de mort avec le pseudo affiché actuel du joueur.
	 *
	 * @param player La cible
	 */
	public void fakeDeath(Player player) {
		if (!check(player))
			return;
		if (!charge(player, price("fake-death")))
			return;
		DisguiseManager.broadcastFakeDeath(displayName(player), player);
	}
	
	/**
	 * Diffuse un faux message de progression (advancement) avec le pseudo affiché actuel du joueur.
	 *
	 * @param player La cible
	 */
	public void fakeAdvancement(Player player) {
		if (!check(player))
			return;
		if (!charge(player, price("fake-advancement")))
			return;
		DisguiseManager.broadcastFakeAdvancement(displayName(player), advancementName(), player);
	}
	
	/**
	 * Fait croire à la connexion du joueur (pseudo affiché actuel). Le joueur « fantôme » se
	 * déconnecte automatiquement au bout de {@link #ghostDuration()} secondes, sauf si l'action
	 * est rachetée entre-temps (renouvellement : la déconnexion est repoussée, sans nouveau message
	 * de connexion).
	 *
	 * @param player La cible
	 */
	public void buyGhostJoin(Player player) {
		if (!check(player))
			return;
		if (!charge(player, price("ghost-join")))
			return;
		
		UUID uuid = player.getUniqueId();
		String name = displayName(player);
		boolean renewed = ghostNames.containsKey(uuid);
		ghostNames.put(uuid, name);
		// on ne rediffuse le message de connexion (et n'ajoute le faux joueur au TAB) que lors du premier achat
		if (!renewed) {
			DisguiseManager.broadcastFake(name, true, player);
			if (IllusionManager.isAvailable()) {
				UUID fakeId = UUID.randomUUID();
				ghostTabs.put(uuid, fakeId);
				IllusionManager.addGhostTab(fakeId, name, IllusionManager.texturesOf(player));
			} else
				Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
		}
		
		cancel(ghostTasks, uuid);
		int duration = ghostDuration();
		ghostTasks.put(uuid, Bukkit.getScheduler().runTaskLater(KamoofPlugin.getInstance(),
			() -> endGhostJoin(uuid), duration * 20L));
		Lang.send(player, renewed ? "LIARKIT_GHOST_RENEWED" : "LIARKIT_GHOST_ON", durationLabel(duration));
	}
	
	private void endGhostJoin(UUID uuid) {
		cancel(ghostTasks, uuid);
		String name = ghostNames.remove(uuid);
		UUID fakeId = ghostTabs.remove(uuid);
		if (fakeId != null)
			IllusionManager.removeGhostTab(fakeId);
		if (name == null || name.isEmpty())
			return;
		Player player = Bukkit.getPlayer(uuid);
		DisguiseManager.broadcastFake(name, false, player);
		if (player != null && player.isOnline())
			Lang.send(player, "LIARKIT_GHOST_OFF");
	}
	
	/**
	 * Rend le joueur totalement invisible (mode <b>fantôme</b>) : il disparaît pour tous les autres
	 * joueurs (entité + LISTE des joueurs) et laisse croire qu'il s'est déconnecté. Le mode se
	 * termine automatiquement au bout de {@link #ghostDuration()} secondes.
	 *
	 * @param player La cible
	 */
	public void buyVanish(Player player) {
		if (!check(player))
			return;
		if (!NametagManager.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		if (!charge(player, price("ghost")))
			return;
		
		UUID uuid = player.getUniqueId();
		vanished.add(uuid);
		applyVanish(player);
		DisguiseManager.broadcastFake(displayName(player), false, player);
		
		int duration = ghostDuration();
		cancel(vanishTasks, uuid);
		vanishTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(KamoofPlugin.getInstance(), new Runnable() {
			private int left = duration;
			
			@Override
			public void run() {
				Player online = Bukkit.getPlayer(uuid);
				if (online == null || !online.isOnline()) {
					cancel(vanishTasks, uuid);
					return;
				}
				if (left <= 0) {
					endVanish(uuid);
					return;
				}
				// ré-appliqué chaque seconde pour rattraper les joueurs qui se connectent entre-temps
				applyVanish(online);
				left--;
			}
		}, 20L, 20L));
		Lang.send(player, "LIARKIT_VANISH_ON", durationLabel(duration));
	}
	
	private void applyVanish(Player player) {
		for (Player viewer : Bukkit.getOnlinePlayers())
			if (!viewer.equals(player))
				viewer.hidePlayer(KamoofPlugin.getInstance(), player);
		NametagManager.setTabListed(player, false);
	}
	
	private void endVanish(UUID uuid) {
		cancel(vanishTasks, uuid);
		if (!vanished.remove(uuid))
			return;
		Player player = Bukkit.getPlayer(uuid);
		if (player == null || !player.isOnline())
			return;
		for (Player viewer : Bukkit.getOnlinePlayers())
			if (!viewer.equals(player))
				viewer.showPlayer(KamoofPlugin.getInstance(), player);
		NametagManager.setTabListed(player, true);
		DisguiseManager.broadcastFake(displayName(player), true, player);
		Lang.send(player, "LIARKIT_VANISH_OFF");
	}
	
	/**
	 * Fait apparaître un <b>clone</b> du joueur (même pseudo, même peau).
	 *
	 * @param player La cible
	 * @param mobile <code>true</code> pour un clone qui se déplace tout seul (IA de « villageois
	 *               idiot », sans reproduction) — plus cher ; <code>false</code> pour un clone immobile
	 */
	public void buyClone(Player player, boolean mobile) {
		if (!check(player))
			return;
		if (!IllusionManager.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		if (!charge(player, price(mobile ? "clone-mobile" : "clone")))
			return;
		
		UUID uuid = player.getUniqueId();
		endClone(uuid, false);
		if (IllusionManager.spawnClone(player, mobile) == null) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		
		final int duration = cloneDuration();
		cancel(cloneTasks, uuid);
		cloneTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(KamoofPlugin.getInstance(), new Runnable() {
			private int left = duration;
			
			@Override
			public void run() {
				Player online = Bukkit.getPlayer(uuid);
				if (online == null || !online.isOnline()) {
					endClone(uuid, false);
					return;
				}
				if (left <= 0) {
					endClone(uuid, true);
					return;
				}
				// immobile : ne fait rien ; mobile : se déplace façon « villageois idiot »
				IllusionManager.moveClone(online);
				left--;
			}
		}, 20L, 20L));
		Lang.send(player, mobile ? "LIARKIT_CLONE_MOBILE_ON" : "LIARKIT_CLONE_ON", durationLabel(duration));
	}
	
	private void endClone(UUID uuid, boolean notify) {
		cancel(cloneTasks, uuid);
		IllusionManager.despawnClone(uuid);
		if (!notify)
			return;
		Player player = Bukkit.getPlayer(uuid);
		if (player != null && player.isOnline())
			Lang.send(player, "LIARKIT_CLONE_OFF");
	}
	
	/**
	 * Rend le joueur <b>silencieux</b> : tous les sons qu'il cause (pas, blocs, dégâts...) sont
	 * supprimés pour les autres joueurs.
	 *
	 * @param player La cible
	 */
	public void buySilence(Player player) {
		if (!check(player))
			return;
		if (!IllusionManager.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		if (!charge(player, price("silence")))
			return;
		
		UUID uuid = player.getUniqueId();
		IllusionManager.setSilenced(player, true);
		
		final int total = silenceDuration() * 20;
		cancel(silenceTasks, uuid);
		silenceTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(KamoofPlugin.getInstance(), new Runnable() {
			private int left = total;
			
			@Override
			public void run() {
				Player online = Bukkit.getPlayer(uuid);
				if (online == null || !online.isOnline()) {
					IllusionManager.setSilenced(player, false);
					cancel(silenceTasks, uuid);
					return;
				}
				if (left <= 0) {
					endSilence(uuid);
					return;
				}
				// la position est rafraîchie souvent car elle sert à localiser les sons à couper
				IllusionManager.refreshSilenced(online);
				left -= 5;
			}
		}, 5L, 5L));
		Lang.send(player, "LIARKIT_SILENCE_ON", durationLabel(silenceDuration()));
	}
	
	private void endSilence(UUID uuid) {
		cancel(silenceTasks, uuid);
		Player player = Bukkit.getPlayer(uuid);
		if (player != null)
			IllusionManager.setSilenced(player, false);
		if (player != null && player.isOnline())
			Lang.send(player, "LIARKIT_SILENCE_OFF");
	}
	
	/**
	 * Fait voir à un joueur <b>tous les cochons comme des creepers</b> (illusion purement côté
	 * client : le serveur reste inchangé).
	 *
	 * @param player Le spectateur (celui qui voit l'illusion)
	 */
	public void buyPigsToCreepers(Player player) {
		if (!check(player))
			return;
		if (!IllusionManager.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_PACKETEVENTS");
			return;
		}
		if (!charge(player, price("pigs-creepers")))
			return;
		
		UUID uuid = player.getUniqueId();
		IllusionManager.setPigsAsCreepers(player, true);
		
		final int duration = pigDuration();
		cancel(pigTasks, uuid);
		pigTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(KamoofPlugin.getInstance(), new Runnable() {
			private int left = duration;
			
			@Override
			public void run() {
				Player online = Bukkit.getPlayer(uuid);
				if (online == null || !online.isOnline()) {
					cancel(pigTasks, uuid);
					return;
				}
				if (left <= 0) {
					endPigs(uuid);
					return;
				}
				left--;
			}
		}, 20L, 20L));
		Lang.send(player, "LIARKIT_PIGS_ON", durationLabel(duration));
	}
	
	private void endPigs(UUID uuid) {
		cancel(pigTasks, uuid);
		Player player = Bukkit.getPlayer(uuid);
		if (player != null)
			IllusionManager.setPigsAsCreepers(player, false);
		if (player != null && player.isOnline())
			Lang.send(player, "LIARKIT_PIGS_OFF");
	}
	
	/**
	 * Chat anonyme : tant que le joueur est en mode « Anonymous » (Trousse du Menteur), son message
	 * est signé du pseudo anonyme au lieu de son vrai pseudo.
	 *
	 * @param event L'événement de chat
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!anonymousRestore.containsKey(player.getUniqueId()))
			return;
		String anon = anonymousName();
		String format = event.getFormat();
		if (format != null && format.contains(player.getName()))
			event.setFormat(format.replace(player.getName(), anon));
	}
	
	/**
	 * @return <code>false</code> (et prévient le joueur) si la Trousse du Menteur est désactivée.
	 */
	private boolean check(Player player) {
		if (isEnabled())
			return true;
		Lang.send(player, "LIARKIT_DISABLED");
		return false;
	}
	
	/**
	 * Fait payer le joueur, si nécessaire.
	 *
	 * @return <code>true</code> si le paiement a réussi (ou si l'action est gratuite)
	 */
	private boolean charge(Player player, double amount) {
		if (amount <= 0)
			return true;
		if (!EconomyHook.isAvailable()) {
			Lang.send(player, "LIARKIT_NO_ECONOMY");
			return false;
		}
		if (!EconomyHook.withdraw(player, amount)) {
			Lang.send(player, "LIARKIT_NOT_ENOUGH", EconomyHook.format(amount), EconomyHook.format(EconomyHook.balance(player)));
			return false;
		}
		Lang.send(player, "LIARKIT_PAID", EconomyHook.format(amount), EconomyHook.format(EconomyHook.balance(player)));
		return true;
	}
	
	private void cancel(Map<UUID, BukkitTask> tasks, UUID uuid) {
		BukkitTask task = tasks.remove(uuid);
		if (task != null)
			task.cancel();
	}
	
	// ---------------------------------------------------------------------
	// Cycle de vie : ne jamais laisser un effet devenir permanent
	// ---------------------------------------------------------------------
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		
		// restaure le pseudo (et non le déguisement) avant qu'EssentialsX ne sauvegarde
		if (anonymousRestore.containsKey(uuid)) {
			cancel(anonymousTasks, uuid);
			String previous = anonymousRestore.remove(uuid);
			if (previous == null || previous.isEmpty())
				DisguiseManager.clearName(player);
			else
				DisguiseManager.applyName(player, previous);
		}
		if (tabTasks.containsKey(uuid)) {
			cancel(tabTasks, uuid);
			NametagManager.setTabListed(player, true);
		}
		if (vanished.remove(uuid)) {
			cancel(vanishTasks, uuid);
			NametagManager.setTabListed(player, true);
		}
		// Le clone et le silence doivent disparaître avec le joueur (sinon ils resteraient fantômes
		// côté serveur). Le clone est retiré pour tous les autres joueurs.
		if (cloneTasks.containsKey(uuid))
			endClone(uuid, false);
		if (silenceTasks.containsKey(uuid)) {
			cancel(silenceTasks, uuid);
			IllusionManager.setSilenced(player, false);
		}
		if (pigTasks.containsKey(uuid)) {
			cancel(pigTasks, uuid);
			IllusionManager.setPigsAsCreepers(player, false);
		}
		// La connexion fantôme continue même après une vraie déconnexion : le « fantôme » doit
		// finir par se déconnecter tout seul (c'est tout l'intérêt de l'illusion).
	}
	
	/**
	 * Filet de sécurité : si un joueur se reconnecte alors qu'il est encore « Anonyme » (déconnexion
	 * pendant l'effet), on retire ce pseudo pour qu'il ne devienne pas permanent.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		String nick = DisguiseManager.getNickname(player);
		if (nick != null && nick.equals(anonymousName()) && !anonymousRestore.containsKey(player.getUniqueId()))
			DisguiseManager.clearName(player);
		// Un joueur en mode fantôme doit rester invisible pour les nouveaux arrivants.
		for (UUID uuid : vanished) {
			Player ghost = Bukkit.getPlayer(uuid);
			if (ghost != null && !ghost.equals(player))
				player.hidePlayer(KamoofPlugin.getInstance(), ghost);
		}
		// Les illusions persistantes (faux joueurs du TAB, clones) doivent être renvoyées aux
		// joueurs qui se connectent pendant qu'elles sont actives.
		IllusionManager.resendTo(player);
	}
	
}
