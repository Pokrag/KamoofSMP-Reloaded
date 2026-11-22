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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La <b>Trousse du Menteur</b> : un ensemble d'actions payantes (via <b>Vault</b>) permettant de
 * tromper les autres joueurs, accessibles depuis <code>/camouf menu</code>.
 * <ul>
 *     <li><b>Anonymat</b> : transforme temporairement le joueur en « Anonymous » ;</li>
 *     <li><b>Disparition du TAB</b> : retire le joueur de la liste des joueurs (même sans déguisement) ;</li>
 *     <li><b>Faux messages</b> : diffuse un faux message de connexion/déconnexion (même sans déguisement).</li>
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
		anonymousRestore.clear();
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
		
		String name = DisguiseManager.getNickname(player);
		if (name == null || name.isEmpty())
			name = player.getName();
		DisguiseManager.broadcastFake(name, join, player);
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
	}
	
}
