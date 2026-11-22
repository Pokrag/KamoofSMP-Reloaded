package im.ghosty.kamoof.features.disguise;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.earth2me.essentials.utils.DateUtil;
import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
import im.ghosty.kamoof.utils.Lang;
import im.ghosty.kamoof.utils.Message;
import im.ghosty.kamoof.utils.MinecraftAPI;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.MojangSkinDataResult;
import net.skinsrestorer.api.property.SkinApplier;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Classe utilitaire pour déguiser un joueur.
 * <p>
 * Le pseudo est géré par <b>EssentialsX</b> et le skin (ainsi que la cape) par
 * <b>SkinsRestorer</b>. L'affichage (tab-list / nametag) est forcé manuellement
 * pour ne pas dépendre de la configuration d'EssentialsX.
 *
 * @since 1.0
 */
@UtilityClass
public final class DisguiseManager {
	
	/**
	 * Déguiser le {@link Player} donné avec le pseudo <code>name</code>.
	 *
	 * @param player La cible
	 * @param name   Le déguisement
	 */
	public static void disguise(Player player, String name) {
		// nom invalide, on part de l'idée que c'est un undisguise (on déguise en rien)
		if (name == null || name.isEmpty()) {
			undisguise(player);
			return;
		}
		// B (A) -> B, inutile
		if (Objects.equals(KamoofSMP.getInstance().getDisguise(player), name))
			return;
		// B (A) -> A, il utilise sa propre tête, autant retirer son déguisement
		if (Objects.equals(KamoofSMP.getInstance().getName(player), name)) {
			undisguise(player);
			return;
		}
		
		apply(player, name);
	}
	
	/**
	 * Applique (ou <b>ré-applique</b>) le déguisement <b>sans aucun court-circuit</b>.
	 * <p>
	 * Contrairement à {@link #disguise(Player, String)}, cette méthode force la
	 * ré-application du pseudo (tab-list <b>et</b> nametag au-dessus de la tête) et du
	 * skin, même si EssentialsX / SkinsRestorer ont déjà mémorisé une partie de l'état.
	 * Elle est donc utilisée à la reconnexion d'un joueur déguisé.
	 *
	 * @param player La cible
	 * @param name   Le déguisement
	 */
	public static void apply(Player player, String name) {
		if (name == null || name.isEmpty()) {
			undisguise(player);
			return;
		}
		applyNickname(player, name, false);
		applySkin(player, name);
	}
	
	/**
	 * Retirer le déguisement du {@link Player} donné.
	 *
	 * @param player La cible
	 */
	public static void undisguise(Player player) {
		// s'il n'est pas déguisé, évitons de refresh dans le vide
		if (getNickname(player) == null)
			return;
		
		clearNickname(player, false);
		clearSkin(player);
	}
	
	/**
	 * Diffuse un faux message de connexion ou de déconnexion, afin de renforcer l'illusion
	 * d'un déguisement (p. ex. faire croire que le joueur déguisé a quitté le serveur, puis que
	 * le pseudo imité l'a rejoint).
	 *
	 * @param player La cible
	 * @param self   <code>true</code> pour utiliser le vrai pseudo (A), <code>false</code> pour le déguisement (B)
	 * @param join   <code>true</code> pour un message de connexion, <code>false</code> pour une déconnexion
	 */
	public static void broadcastFake(Player player, boolean self, boolean join) {
		String name = self ? KamoofSMP.getInstance().getName(player) : getNickname(player);
		if (name == null || name.isEmpty()) {
			Lang.send(player, "MENU_FAKE_NODISGUISE");
			return;
		}
		broadcastFake(name, join, player);
	}
	
	/**
	 * Diffuse un faux message de connexion ou de déconnexion pour le pseudo donné.
	 * <p>
	 * Si EssentialsX a configuré un message personnalisé (<code>custom-join-message</code> /
	 * <code>custom-quit-message</code> dans son <code>config.yml</code>), c'est ce message qui est utilisé,
	 * et ses balises sont remplacées comme le ferait EssentialsX (cf. {@link #applyEssentialsPlaceholders}).
	 * Sinon, on retombe sur les messages de langue du plugin.
	 *
	 * @param name   Le pseudo à afficher
	 * @param join   <code>true</code> pour un message de connexion, <code>false</code> pour une déconnexion
	 * @param author Le joueur à l'origine du faux message (sert aux balises {ONLINE}/{UPTIME}/{PREFIX}/{SUFFIX})
	 */
	public static void broadcastFake(String name, boolean join, Player author) {
		if (name == null || name.isEmpty())
			return;
		String custom = essentialsCustomMessage(join);
		String raw = custom != null
			? applyEssentialsPlaceholders(custom, name, author)
			: String.format(Lang.get(join ? "FAKE_CONNECT" : "FAKE_DISCONNECT"), name);
		broadcastRaw(raw);
	}
	
	/**
	 * Diffuse un faux message de mort avec le pseudo donné.
	 *
	 * @param name   Le pseudo affiché
	 * @param author Le joueur à l'origine du faux message (peut être <code>null</code>)
	 */
	public static void broadcastFakeDeath(String name, Player author) {
		if (name == null || name.isEmpty())
			return;
		broadcastRaw(String.format(Lang.get("FAKE_DEATH"), name));
	}
	
	/**
	 * Diffuse un faux message de progression (advancement) avec le pseudo et le nom du progrès donnés.
	 *
	 * @param name        Le pseudo affiché
	 * @param advancement Le nom du progrès
	 * @param author      Le joueur à l'origine du faux message (peut être <code>null</code>)
	 */
	public static void broadcastFakeAdvancement(String name, String advancement, Player author) {
		if (name == null || name.isEmpty())
			return;
		broadcastRaw(String.format(Lang.get("FAKE_ADVANCEMENT"), name, advancement));
	}
	
	/**
	 * Envoie un message brut (MiniMessage + codes couleurs <code>&amp;</code>) à tous les joueurs
	 * et à la console.
	 *
	 * @param raw Le message brut
	 */
	private static void broadcastRaw(String raw) {
		if (raw == null || raw.isEmpty())
			return;
		Component message = Message.deserialize(raw);
		for (Player viewer : Bukkit.getOnlinePlayers())
			viewer.sendMessage(message);
		Bukkit.getConsoleSender().sendMessage(message);
	}
	
	/**
	 * Remplace les balises propres aux messages de connexion/déconnexion d'EssentialsX, afin de rester
	 * compatible avec celles-ci (<code>{PLAYER}</code>, <code>{USERNAME}</code>, <code>{NICKNAME}</code>,
	 * <code>{DISPLAYNAME}</code>, <code>{ONLINE}</code>, <code>{UPTIME}</code>, <code>{PREFIX}</code>,
	 * <code>{SUFFIX}</code>).
	 * <p>
	 * Le pseudo affiché (<code>name</code>) est injecté à la place de toutes les balises de pseudo ;
	 * les autres balises sont calculées à partir de l'état actuel du serveur, à l'instar d'EssentialsX.
	 *
	 * @param message Le message brut (issu de la config d'EssentialsX)
	 * @param name    Le pseudo simulé
	 * @param author  Le joueur à l'origine du faux message
	 * @return Le message avec les balises remplacées
	 */
	private static String applyEssentialsPlaceholders(String message, String name, Player author) {
		String result = message
			.replace("{PLAYER}", name)
			.replace("{DISPLAYNAME}", name)
			.replace("{USERNAME}", name)
			.replace("{NICKNAME}", name)
			.replace("{ONLINE}", NumberFormat.getInstance().format(Bukkit.getOnlinePlayers().size()));
		try {
			result = result.replace("{UPTIME}", DateUtil.formatDateDiff(ManagementFactory.getRuntimeMXBean().getStartTime()));
		} catch (Throwable exc) {
		}
		Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
		if (plugin != null && author != null) {
			try {
				Object handler = plugin.getClass().getMethod("getPermissionsHandler").invoke(plugin);
				if (handler != null) {
					result = result
						.replace("{PREFIX}", permissionsValue(handler, author, "getPrefix"))
						.replace("{SUFFIX}", permissionsValue(handler, author, "getSuffix"));
				}
			} catch (Throwable exc) {
			}
		}
		return result;
	}
	
	/**
	 * Récupère la valeur de {@code getPrefix}/{@code getSuffix} auprès du gestionnaire de permissions
	 * d'EssentialsX, via réflexion pour rester tolérant vis-à-vis des différentes versions de l'API.
	 *
	 * @param handler Le gestionnaire de permissions
	 * @param player  La cible
	 * @param method  Le nom de la méthode ({@code getPrefix} ou {@code getSuffix})
	 * @return La valeur (codes couleur convertis en <code>&amp;</code> pour {@link Message}), ou une chaîne vide si indisponible
	 */
	private static String permissionsValue(Object handler, Player player, String method) {
		try {
			Class<?> type = Class.forName("net.ess3.api.IPermissionsHandler");
			Object value = type.getMethod(method, Player.class).invoke(handler, player);
			return value == null ? "" : value.toString().replace('§', '&');
		} catch (Throwable exc) {
			return "";
		}
	}
	
	/**
	 * Récupère le message de connexion/déconnexion personnalisé d'EssentialsX, s'il est défini.
	 *
	 * @param join <code>true</code> pour la connexion, <code>false</code> pour la déconnexion
	 * @return Le message personnalisé, ou <code>null</code> si EssentialsX est absent ou n'en a pas
	 */
	private static String essentialsCustomMessage(boolean join) {
		Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
		if (essentials == null)
			return null;
		try {
			File file = new File(essentials.getDataFolder(), "config.yml");
			if (!file.isFile())
				return null;
			String message = YamlConfiguration.loadConfiguration(file)
				.getString(join ? "custom-join-message" : "custom-quit-message");
			if (message == null || message.isEmpty() || message.equalsIgnoreCase("none"))
				return null;
			return message;
		} catch (Throwable exc) {
			return null;
		}
	}
	
	// ---------------------------------------------------------------------
	// Pseudo (EssentialsX) + affichage (tab-list / nametag)
	// ---------------------------------------------------------------------
	
	/**
	 * Récupère le pseudo déguisé actuel du joueur, ou <code>null</code> s'il n'est pas déguisé.
	 *
	 * @param player La cible
	 * @return Le déguisement, ou <code>null</code>
	 */
	public static String getNickname(Player player) {
		User user = essentialsUser(player);
		if (user == null)
			return null;
		String nick = user.getNickname();
		return (nick == null || nick.isEmpty()) ? null : nick;
	}
	
	/**
	 * Applique un pseudo au joueur <b>sans toucher à son skin</b> (utilisé par la Trousse du
	 * Menteur pour le mode anonyme). Le pseudo est posé via EssentialsX et l'affichage
	 * (TAB / nametag) est forcé comme pour un déguisement classique.
	 *
	 * @param player La cible
	 * @param name   Le nouveau pseudo
	 */
	public static void applyName(Player player, String name) {
		if (name == null || name.isEmpty()) {
			clearName(player);
			return;
		}
		// Mode « Anonymous » : on force l'affichage (TAB + nametag) sans condition, sinon un
		// pseudo précédent (déguisement, cosmétiques EssentialsX...) resterait affiché EN PLUS.
		applyNickname(player, name, true);
	}

	/**
	 * Retire le pseudo du joueur (retour à son vrai pseudo) <b>sans toucher à son skin</b>.
	 *
	 * @param player La cible
	 */
	public static void clearName(Player player) {
		if (getNickname(player) == null)
			return;
		// Même raison que {@link #applyName} : on force le retour au vrai pseudo pour ne
		// laisser aucun résidu « Anonymous » dans le TAB ou le nametag.
		clearNickname(player, true);
	}
	
	/**
	 * @return L'utilisateur EssentialsX du joueur, ou <code>null</code> si EssentialsX est absent.
	 */
	private static User essentialsUser(Player player) {
		Essentials essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
		return essentials == null ? null : essentials.getUser(player);
	}
	
	private static void applyNickname(Player player, String name, boolean force) {
		if (!player.isOnline())
			return;
		
		// 1) On demande à EssentialsX de poser le pseudo (et ses cosmétiques : préfixe/suffixe)
		User user = essentialsUser(player);
		if (user != null) {
			user.setNickname(name);
			user.setDisplayNick();
		}
		if (!player.isOnline())
			return;
		
		// 2) Le pseudo au-dessus de la tête : EssentialsX ne le modifie JAMAIS, il faut
		//    réécrire nous-mêmes le GameProfile des paquets PLAYER_INFO (via PacketEvents).
		NametagManager.setNickname(player.getUniqueId(), name);
		
		// 3) On force l'affichage (TAB) si EssentialsX ne l'a pas fait.
		//    Par défaut, EssentialsX ne modifie PAS la tab-list (change-playerlist: false),
		//    ce qui provoquait l'absence du pseudo dans le menu TAB.
		if (force)
			forceDisplay(player, name);
		else
			forceDisplayIfMissing(player, name);
	}
	
	private static void clearNickname(Player player, boolean force) {
		if (!player.isOnline())
			return;
		
		User user = essentialsUser(player);
		if (user != null) {
			user.setNickname(null);
			user.setDisplayNick();
		}
		if (!player.isOnline())
			return;
		
		// on remet le vrai nom au-dessus de la tête, puis dans la tab-list si nécessaire
		NametagManager.setNickname(player.getUniqueId(), null);
		if (force)
			forceDisplay(player, player.getName());
		else
			forceDisplayIfMissing(player, player.getName());
	}
	
	/**
	 * Force l'affichage du pseudo sur le <b>menu TAB</b> et le <b>nametag au-dessus de la tête</b>,
	 * <b>sans condition</b>. Utilisé par le mode « Anonymous » (Trousse du Menteur) pour garantir
	 * que le pseudo précédent ne reste pas affiché en plus du nouveau.
	 *
	 * @param player La cible
	 * @param name   Le nom à afficher
	 */
	private static void forceDisplay(Player player, String name) {
		if (!player.isOnline())
			return;
		player.playerListName(Component.text(name));
		player.displayName(Component.text(name));
	}
	
	/**
	 * Force l'affichage du pseudo déguisé sur les deux affichages gérés <b>séparément</b> par
	 * EssentialsX : le <b>menu TAB</b> (<code>playerListName</code>, option <code>change-playerlist</code>)
	 * et le <b>nametag au-dessus de la tête</b> (<code>displayName</code>, option <code>change-displayname</code>).
	 * On ne touche à chacun que si EssentialsX ne l'a pas déjà fait, afin de conserver ses
	 * cosmétiques (préfixe/suffixe).
	 *
	 * @param player La cible
	 * @param name   Le nom à afficher
	 */
	private static void forceDisplayIfMissing(Player player, String name) {
		if (!player.isOnline())
			return;
		
		// menu TAB
		if (!displayed(player.playerListName(), name))
			player.playerListName(Component.text(name));
		
		// nametag au-dessus de la tête
		if (!displayed(player.displayName(), name))
			player.displayName(Component.text(name));
	}
	
	/**
	 * @param component Le texte actuellement affiché
	 * @param name      Le nom voulu
	 * @return <code>true</code> si le texte affiché contient déjà le nom voulu (cosmétiques inclus)
	 */
	private static boolean displayed(Component component, String name) {
		String plain = component == null ? "" : LegacyComponentSerializer.legacySection().serialize(component);
		return plain.contains(name);
	}
	
	// ---------------------------------------------------------------------
	// Skin + cape (SkinsRestorer)
	// ---------------------------------------------------------------------
	
	/**
	 * @return L'API SkinsRestorer, ou <code>null</code> si SkinsRestorer est absent.
	 */
	private static SkinsRestorer skinsRestorer() {
		if (!Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer"))
			return null;
		try {
			return SkinsRestorerProvider.get();
		} catch (Throwable exc) {
			return null;
		}
	}
	
	private static void applySkin(Player player, String name) {
		SkinsRestorer skinsRestorer = skinsRestorer();
		if (skinsRestorer == null)
			return;
		// les requêtes/stockages sont bloquants : on les exécute hors du thread principal
		Bukkit.getScheduler().runTaskAsynchronously(KamoofPlugin.getInstance(), () -> {
			try {
				SkinProperty property = null;
				SkinIdentifier identifier = null;
				
				// 1) Profil Mojang : propriété signée contenant la peau ET la cape (comptes Java).
				try {
					Optional<MojangSkinDataResult> mojang = skinsRestorer.getMojangAPI().getSkin(name);
					if (mojang.isPresent()) {
						property = mojang.get().getSkinProperty();
						identifier = SkinIdentifier.ofPlayer(mojang.get().getUniqueId());
						// on mémorise le skin pour que SkinsRestorer le connaisse (persistance, GUI, refresh)
						skinsRestorer.getSkinStorage().setPlayerSkinData(mojang.get().getUniqueId(), name, property, System.currentTimeMillis() / 1000L);
					}
				} catch (Throwable ignored) {
				}
				
				// 2) mc-api.io : seul moyen de résoudre la peau/cape d'un compte BEDROCK (et d'un
				//    compte Java introuvable côté Mojang). L'API renvoie une propriété signée.
				if (property == null) {
					for (MinecraftAPI.Edition edition : MinecraftAPI.Edition.values()) {
						Optional<MinecraftAPI.Profile> found = MinecraftAPI.byName(name, edition);
						if (found.isEmpty() || !found.get().hasSkin())
							continue;
						MinecraftAPI.Profile profile = found.get();
						property = SkinProperty.of(profile.value(), profile.signature());
						if (profile.uuid() != null) {
							identifier = SkinIdentifier.ofPlayer(profile.uuid());
							skinsRestorer.getSkinStorage().setPlayerSkinData(profile.uuid(), name, property, System.currentTimeMillis() / 1000L);
						} else {
							identifier = SkinIdentifier.ofCustom(name);
							skinsRestorer.getSkinStorage().setCustomSkinData(name, property);
						}
						break;
					}
				}
				
				// 3) Repli : skin géré/caché par SkinsRestorer (peau + cape également)
				if (property == null) {
					try {
						Optional<InputDataResult> result = skinsRestorer.getSkinStorage().findOrCreateSkinData(name);
						if (result.isPresent()) {
							property = result.get().getProperty();
							identifier = result.get().getIdentifier();
						}
					} catch (Throwable ignored) {
					}
				}
				
				if (property == null || identifier == null)
					return;
				
				skinsRestorer.getPlayerStorage().setSkinIdOfPlayer(player.getUniqueId(), identifier);
				applyProperty(player, property);
			} catch (Throwable exc) {
				Bukkit.getLogger().warning("[KamoofSMP] Impossible d'appliquer le skin de " + name + " : " + exc.getMessage());
			}
		});
	}
	
	private static void clearSkin(Player player) {
		SkinsRestorer skinsRestorer = skinsRestorer();
		if (skinsRestorer == null)
			return;
		Bukkit.getScheduler().runTaskAsynchronously(KamoofPlugin.getInstance(), () -> {
			try {
				skinsRestorer.getPlayerStorage().removeSkinIdOfPlayer(player.getUniqueId());
				
				// on remet la vraie peau du joueur (cape comprise) plutôt qu'une peau vide
				Optional<SkinProperty> own = skinsRestorer.getSkinStorage().updatePlayerSkinData(player.getUniqueId());
				if (own.isPresent()) {
					applyProperty(player, own.get());
					return;
				}
				
				Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> {
					if (!player.isOnline())
						return;
					try {
						skinsRestorer.getSkinApplier(Player.class).applySkin(player);
					} catch (Throwable ignored) {
					}
				});
			} catch (Throwable exc) {
				Bukkit.getLogger().warning("[KamoofSMP] Impossible de retirer le skin : " + exc.getMessage());
			}
		});
	}
	
	/**
	 * Applique la propriété de texture (peau + cape) au joueur sur le thread principal.
	 */
	private static void applyProperty(Player player, SkinProperty property) {
		Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> {
			if (!player.isOnline())
				return;
			SkinsRestorer skinsRestorer = skinsRestorer();
			if (skinsRestorer == null)
				return;
			try {
				SkinApplier<Player> applier = skinsRestorer.getSkinApplier(Player.class);
				applier.applySkin(player, property);
			} catch (Throwable ignored) {
			}
		});
	}
	
}
