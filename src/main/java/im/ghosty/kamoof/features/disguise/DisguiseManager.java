package im.ghosty.kamoof.features.disguise;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
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
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

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
		
		applyNickname(player, name);
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
		
		clearNickname(player);
		clearSkin(player);
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
	 * @return L'utilisateur EssentialsX du joueur, ou <code>null</code> si EssentialsX est absent.
	 */
	private static User essentialsUser(Player player) {
		Essentials essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
		return essentials == null ? null : essentials.getUser(player);
	}
	
	private static void applyNickname(Player player, String name) {
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
		
		// 2) On force l'affichage (TAB + nametag) si EssentialsX ne l'a pas fait.
		//    Par défaut, EssentialsX ne modifie PAS la tab-list (change-playerlist: false),
		//    ce qui provoquait l'absence du pseudo dans le menu TAB.
		forceDisplayIfMissing(player, name);
	}
	
	private static void clearNickname(Player player) {
		if (!player.isOnline())
			return;
		
		User user = essentialsUser(player);
		if (user != null) {
			user.setNickname(null);
			user.setDisplayNick();
		}
		if (!player.isOnline())
			return;
		
		// on remet le vrai nom si EssentialsX ne l'a pas restauré
		forceDisplayIfMissing(player, player.getName());
	}
	
	/**
	 * Si la tab-list n'affiche pas encore le nom voulu (c'est-à-dire qu'EssentialsX ne l'a
	 * pas modifiée, car <code>change-playerlist</code> est désactivé par défaut), on force
	 * l'affichage. Sinon on conserve ce qu'EssentialsX a appliqué (préfixe/suffixe compris).
	 *
	 * @param player La cible
	 * @param name   Le nom à afficher
	 */
	private static void forceDisplayIfMissing(Player player, String name) {
		if (!player.isOnline())
			return;
		
		Component current = player.playerListName();
		String plain = current == null ? "" : LegacyComponentSerializer.legacySection().serialize(current);
		if (plain.contains(name))
			return; // EssentialsX (ou un autre plugin) a déjà affiché le bon nom, cosmétiques inclus
		
		player.playerListName(Component.text(name));
		player.displayName(Component.text(name));
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
				SkinProperty property;
				SkinIdentifier identifier;
				
				// 1) Propriété signée du profil Mojang : elle contient la peau ET la cape
				Optional<MojangSkinDataResult> mojang = skinsRestorer.getMojangAPI().getSkin(name);
				if (mojang.isPresent()) {
					property = mojang.get().getSkinProperty();
					identifier = SkinIdentifier.ofPlayer(mojang.get().getUniqueId());
					// on mémorise le skin pour que SkinsRestorer le connaisse (persistance, GUI, refresh)
					skinsRestorer.getSkinStorage().setPlayerSkinData(mojang.get().getUniqueId(), name, property, System.currentTimeMillis() / 1000L);
				} else {
					// 2) Repli : skin géré/caché par SkinsRestorer (peau + cape également)
					Optional<InputDataResult> result = skinsRestorer.getSkinStorage().findOrCreateSkinData(name);
					if (result.isEmpty())
						return;
					property = result.get().getProperty();
					identifier = result.get().getIdentifier();
				}
				
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
