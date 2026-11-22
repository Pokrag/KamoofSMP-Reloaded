package im.ghosty.kamoof.commands;

import com.samjakob.spigui.buttons.SGButton;
import com.samjakob.spigui.menu.SGMenu;
import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
import im.ghosty.kamoof.features.FeatureManager;
import im.ghosty.kamoof.features.disguise.DisguiseManager;
import im.ghosty.kamoof.features.other.LiarKit;
import im.ghosty.kamoof.utils.*;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static im.ghosty.kamoof.KamoofPlugin.*;

/**
 * Menu de Configuration en-jeu global du KamoofSMP.
 * <p>
 * Ouvrable avec <code>/kamoofsmp config</code> en jeu.
 *
 * @since 1.4
 */
public final class ConfigGUI {
	
	private static void fill(SGMenu menu, Material material) {
		ItemStack item = new ItemStack(material);
		try {
			ItemMeta meta = item.getItemMeta();
			meta.setHideTooltip(true);
			item.setItemMeta(meta);
		} catch (Throwable exc) {
		}
		SGButton glassPaneBtn = new SGButton(item);
		for (int i = 0; i < menu.getPageSize(); i++)
			menu.setButton(i, glassPaneBtn);
	}
	
	/**
	 * @return Le {@link SGMenu} principal
	 */
	public static SGMenu getMainMenu() {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("CFG_TITLE_MAIN"), 3);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		ItemStack itemLang = new ItemStack(Material.DIAMOND);
		ItemMeta metaLang = itemLang.getItemMeta();
		metaLang.addItemFlags(ItemFlag.values());
		metaLang.setDisplayName("§b§l" + Lang.languages.get(Lang.getLocale()));
		metaLang.setLore(List.of("§bClick to switch language."));
		itemLang.setItemMeta(metaLang);
		menu.setButton(10, new SGButton(itemLang).withListener(event -> {
			List<String> keys = Lang.languages.keySet().stream().toList();
			int index = (keys.indexOf(Lang.getLocale()) + 1) % keys.size();
			config().set("language", keys.get(index));
			KamoofPlugin.getInstance().saveConfig();
			Lang.init();
			event.getWhoClicked().openInventory(getMainMenu().getInventory());
		}));
		
		ItemStack itemDisguise = new ItemStack(Material.IRON_INGOT);
		ItemMeta metaDisguise = itemDisguise.getItemMeta();
		metaDisguise.addItemFlags(ItemFlag.values());
		metaDisguise.setDisplayName("§f§l" + Lang.get("CFG_TITLE_DISGUISE"));
		itemDisguise.setItemMeta(metaDisguise);
		menu.setButton(12, new SGButton(itemDisguise).withListener(event -> event.getWhoClicked().openInventory(getDisguiseMenu().getInventory())));
		
		ItemStack itemRitual = new ItemStack(Material.REDSTONE);
		ItemMeta metaRitual = itemRitual.getItemMeta();
		metaRitual.addItemFlags(ItemFlag.values());
		metaRitual.setDisplayName("§c§l" + Lang.get("CFG_TITLE_RITUAL"));
		itemRitual.setItemMeta(metaRitual);
		menu.setButton(14, new SGButton(itemRitual).withListener(event -> {
			if (!CompatibilityUtils.isMinecraft1_21()) {
				Lang.send(event.getWhoClicked(), "NOT_MINECRAFT_1_21");
			}
			event.getWhoClicked().openInventory(getRitualMenu().getInventory());
		}));
		
		ItemStack itemOther = new ItemStack(Material.COAL);
		ItemMeta metaOther = itemOther.getItemMeta();
		metaOther.addItemFlags(ItemFlag.values());
		metaOther.setDisplayName("§7§l" + Lang.get("CFG_TITLE_OTHER"));
		itemOther.setItemMeta(metaOther);
		menu.setButton(16, new SGButton(itemOther).withListener(event -> event.getWhoClicked().openInventory(getOtherMenu().getInventory())));
		
		setButtonNote(menu, 4, "CFG_NOTE_MESSAGES");
		
		return menu;
	}
	
	/**
	 * @return Le {@link SGMenu} des paramètres de Disguise et Drop Head
	 */
	public static SGMenu getDisguiseMenu() {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("CFG_TITLE") + ": §f§l" + Lang.get("CFG_TITLE_DISGUISE"), 3);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		setButtonMainMenu(menu, 4);
		
		setButtonBool(menu, 10, "disguise.place-head", getStrings("CFG_DISGUISE_PLACEHEAD"));
		setButtonBool(menu, 11, "disguise.give-back", getStrings("CFG_DISGUISE_GIVEBACK"));
		
		setButtonBool(menu, 13, "disguise.restaure", getStrings("CFG_RESTAURE"));
		
		setButtonBool(menu, 15, "drophead.enabled", getStrings("CFG_DROPHEAD_ENABLED"));
		setButtonBool(menu, 16, "drophead.stackable", getStrings("CFG_DROPHEAD_STACKABLE"));
		
		return menu;
	}
	
	/**
	 * @return Le {@link SGMenu} des paramètres du Rituel
	 */
	public static SGMenu getRitualMenu() {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("CFG_TITLE") + ": §c§l" + Lang.get("CFG_TITLE_RITUAL"), 6);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		setButtonMainMenu(menu, 4);
		
		setButtonBool(menu, 9, "ritual.enabled", getStrings("CFG_RITUAL_ENABLED"));
		setButtonInt(menu, 10, "ritual.dupelimit", value -> Math.max(1, Math.min(9, value)), 1, getStrings("CFG_RITUAL_DUPELIMIT"));
		setButtonInt(menu, 11, "ritual.min-time", value -> Math.max(0, Math.min(value, Math.min(24000, config().getInt("ritual.max-time")))), 500, getStrings("CFG_RITUAL_MINTIME"));
		setButtonInt(menu, 12, "ritual.max-time", value -> Math.min(24000, Math.max(value, Math.max(0, config().getInt("ritual.min-time")))), 500, getStrings("CFG_RITUAL_MAXTIME"));
		
		setButtonInt(menu, 14, "ritual.pactes.bloody.hpboost", value -> Math.max(0, value), 1, getStrings("CFG_RITUAL_BLOODY_HPBOOST"));
		setButtonInt(menu, 15, "ritual.pactes.bloody.heads", value -> Math.max(0, Math.min(64, value)), 1, getStrings("CFG_RITUAL_BLOODY_HEADS"));
		setButtonInt(menu, 17, "ritual.pactes.forgotten.weakness", value -> Math.max(0, Math.min(255, value)), 1, getStrings("CFG_RITUAL_FORGOTTEN_WEAKNESS"));
		
		setButtonValues(
			menu, 28, "ritual.animation.time-incr",
			Arrays.asList(1, 2, 3, 4, 5, 6, 8, 9, 10, 12, 15, 16, 18, 20, 24, 25, 30, 36, 40, 45, 48, 50, 60, 72, 75, 80, 90, 100, 120, 125, 144, 150, 180, 200, 225, 240, 250, 300, 360, 375, 400, 450, 500, 600, 720, 750, 900, 1000, 1125, 1200, 1500, 1800, 2000, 2250, 3000, 3600, 4500, 6000, 9000, 18000),
			getStrings("CFG_RITUAL_ANIM_TIMEINCR")
		);
		setButtonColor(menu, 29, "ritual.animation.color", getStrings("CFG_RITUAL_ANIM_COLOR"));
		setButtonDouble(menu, 30, "ritual.animation.size", value -> Math.max(0.1, Math.min(10, value)), 0.1, getStrings("CFG_RITUAL_ANIM_SIZE"));
		setButtonInt(menu, 32, "ritual.animation.lightning-quantity", value -> Math.max(0, value), 1, getStrings("CFG_RITUAL_ANIM_LIGHTNING_QT"));
		setButtonInt(menu, 33, "ritual.animation.lightning-interval", value -> Math.max(0, value), 1, getStrings("CFG_RITUAL_ANIM_LIGHTNING_INTERVAL"));
		
		setButtonDouble(menu, 37, "ritual.animation.sphere.radius", value -> Math.max(0.5, Math.min(5, value)), 0.1, getStrings("CFG_RITUAL_SPH_RADIUS"));
		setButtonInt(menu, 38, "ritual.animation.sphere.quantity", value -> Math.max(0, value), 50, getStrings("CFG_RITUAL_SPH_QT"));
		setButtonColor(menu, 39, "ritual.animation.sphere.color", getStrings("CFG_RITUAL_SPH_COLOR"));
		setButtonNote(menu, 40, "CFG_RITUAL_SPH_NOTE");
		setButtonDouble(menu, 41, "ritual.animation.sphere.size", value -> Math.max(0.1, Math.min(10, value)), 0.1, getStrings("CFG_RITUAL_SPH_SIZE"));
		setButtonInt(menu, 42, "ritual.animation.sphere.lava-chance", value -> Math.max(0, Math.min(100, value)), 1, getStrings("CFG_RITUAL_SPH_LAVACHANCE"));
		setButtonBool(menu, 43, "ritual.animation.sphere.lava-sound", getStrings("CFG_RITUAL_SPH_LAVASOUND"));
		
		setButtonDouble(menu, 46, "ritual.accepted.lava-radius", value -> Math.max(0.5, Math.min(5, value)), 0.1, getStrings("CFG_RITUAL_ACC_LAVARADIUS"));
		setButtonInt(menu, 47, "ritual.accepted.lava-quantity", value -> Math.max(0, value), 50, getStrings("CFG_RITUAL_ACC_LAVAQT"));
		setButtonBool(menu, 48, "ritual.accepted.lava-sound", getStrings("CFG_RITUAL_ACC_LAVASOUND"));
		setButtonNote(menu, 49, "CFG_RITUAL_ACC_NOTE");
		setButtonInt(menu, 50, "ritual.accepted.flame-quantity", value -> Math.max(0, value), 50, getStrings("CFG_RITUAL_ACC_FLAMEQT"));
		setButtonDouble(menu, 51, "ritual.accepted.flame-speed", value -> Math.max(0.1, Math.min(5, value)), 0.1, getStrings("CFG_RITUAL_ACC_FLAMESPEED"));
		setButtonBool(menu, 52, "ritual.accepted.flame-soul", getStrings("CFG_RITUAL_ACC_FLAMESOUL"));
		
		return menu;
	}
	
	/**
	 * @return Le {@link SGMenu} des paramètres du MaceLimiter et AutoUpdater
	 */
	public static SGMenu getOtherMenu() {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("CFG_TITLE") + ": §7§l" + Lang.get("CFG_TITLE_OTHER"), 3);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		setButtonMainMenu(menu, 4);
		
		setButtonBool(menu, 10, "macelimiter.enabled", getStrings("CFG_MACELIMIT_ENABLED"));
		setButtonInt(menu, 11, "macelimiter.limit", value -> Math.max(0, value), 1, getStrings("CFG_MACELIMIT_LIMIT"));
		setButtonBool(menu, 12, "macelimiter.no-enderchest", getStrings("CFG_MACELIMIT_EC"));
		setButtonInt(menu, 13, "macelimiter.inventory-limit", value -> Math.max(0, value), 1, getStrings("CFG_MACELIMIT_INVENTORY"));
		
		setButtonBool(menu, 15, "autoupdate.fetch", getStrings("CFG_AUTOUPDATE_FETCH"));
		setButtonBool(menu, 16, "autoupdate.download", getStrings("CFG_AUTOUPDATE_DOWNLOAD"));
		
		return menu;
	}
	
	/**
	 * @param player Le joueur qui ouvre le menu (sert à résoudre les pseudos A et B)
	 * @return Le {@link SGMenu} des actions de déguisement : retrait du déguisement et
	 * faux messages de connexion/déconnexion autour du bouton central.
	 */
	public static SGMenu getActionsMenu(Player player) {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("MENU_TITLE"), 3);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		setButtonMainMenu(menu, 4);
		
		String self = KamoofSMP.getInstance().getName(player);
		String nick = DisguiseManager.getNickname(player);
		
		setButtonFake(menu, 11, "MENU_FAKE_SELF_DISCONNECT", self, true, false);
		setButtonFake(menu, 12, "MENU_FAKE_NICK_CONNECT", nick, false, true);
		setButtonUndisguise(menu, 13);
		setButtonFake(menu, 14, "MENU_FAKE_SELF_CONNECT", self, true, true);
		setButtonFake(menu, 15, "MENU_FAKE_NICK_DISCONNECT", nick, false, false);
		
		setButtonLiarKit(menu, 22);
		
		return menu;
	}
	
	/**
	 * @param player Le joueur qui ouvre le menu
	 * @return Le {@link SGMenu} de la <b>Trousse du Menteur</b> : actions payantes (via Vault)
	 * permettant de tromper les autres joueurs.
	 */
	public static SGMenu getLiarKitMenu(Player player) {
		SGMenu menu = KamoofPlugin.getSpiGUI().create("§a§l" + Lang.get("LIARKIT_TITLE"), 3);
		fill(menu, Material.BLACK_STAINED_GLASS_PANE);
		
		setButtonActionsMenu(menu, 4);
		
		LiarKit kit = LiarKit.getInstance();
		String balance = kit.balanceLabel(player);
		String anonymousDuration = LiarKit.durationLabel(kit.anonymousDuration());
		String tabDuration = LiarKit.durationLabel(kit.tabDuration());
		String ghostDuration = LiarKit.durationLabel(kit.ghostDuration());
		String cloneDuration = LiarKit.durationLabel(kit.cloneDuration());
		String silenceDuration = LiarKit.durationLabel(kit.silenceDuration());
		String pigDuration = LiarKit.durationLabel(kit.pigDuration());
		
		setButtonLiar(menu, 10, Material.PLAYER_HEAD, "LIARKIT_BTN_ANONYMOUS", anonymousDuration, kit.priceLabel(player, "anonymous"), balance, kit::buyAnonymous);
		setButtonLiar(menu, 11, Material.ENDER_EYE, "LIARKIT_BTN_TAB", tabDuration, kit.priceLabel(player, "tab"), balance, kit::buyTabVanish);
		setButtonLiar(menu, 12, Material.LIME_DYE, "LIARKIT_BTN_FAKE_JOIN", "", kit.priceLabel(player, "fake-join"), balance, clicker -> kit.fakeMessage(clicker, true));
		setButtonLiar(menu, 13, Material.GRAY_DYE, "LIARKIT_BTN_FAKE_QUIT", "", kit.priceLabel(player, "fake-quit"), balance, clicker -> kit.fakeMessage(clicker, false));
		setButtonLiar(menu, 14, Material.WITHER_SKELETON_SKULL, "LIARKIT_BTN_FAKE_DEATH", "", kit.priceLabel(player, "fake-death"), balance, kit::fakeDeath);
		setButtonLiar(menu, 15, Material.KNOWLEDGE_BOOK, "LIARKIT_BTN_FAKE_ADVANCEMENT", "", kit.priceLabel(player, "fake-advancement"), balance, kit::fakeAdvancement);
		setButtonLiar(menu, 16, Material.ARMOR_STAND, "LIARKIT_BTN_CLONE", cloneDuration, kit.priceLabel(player, "clone"), balance, clicker -> kit.buyClone(clicker, false));
		setButtonLiar(menu, 20, Material.SLIME_BALL, "LIARKIT_BTN_GHOST_JOIN", ghostDuration, kit.priceLabel(player, "ghost-join"), balance, kit::buyGhostJoin);
		setButtonLiar(menu, 21, Material.SKELETON_SKULL, "LIARKIT_BTN_CLONE_MOBILE", cloneDuration, kit.priceLabel(player, "clone-mobile"), balance, clicker -> kit.buyClone(clicker, true));
		setButtonLiar(menu, 22, Material.BELL, "LIARKIT_BTN_SILENCE", silenceDuration, kit.priceLabel(player, "silence"), balance, kit::buySilence);
		setButtonLiar(menu, 23, Material.CREEPER_HEAD, "LIARKIT_BTN_PIGS", pigDuration, kit.priceLabel(player, "pigs-creepers"), balance, kit::promptPigsTarget);
		setButtonLiar(menu, 24, Material.GHAST_TEAR, "LIARKIT_BTN_GHOST", ghostDuration, kit.priceLabel(player, "ghost"), balance, kit::buyVanish);
		
		return menu;
	}
	
	/**
	 * Ajoute le bouton d'accès à la Trousse du Menteur.
	 *
	 * @param menu Le menu à modifier
	 * @param slot Le slot où placer l'item
	 */
	private static void setButtonLiarKit(SGMenu menu, int slot) {
		Duo<String, ArrayList<String>> strings = getStrings("MENU_LIARKIT");
		ItemStack item = new ItemStack(Material.ENDER_EYE);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§d§l" + strings.getA());
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (event.getWhoClicked() instanceof Player player)
				player.openInventory(getLiarKitMenu(player).getInventory());
		}));
	}
	
	/**
	 * Ajoute un bouton d'action payante de la Trousse du Menteur, affichant son prix et le solde
	 * du joueur.
	 *
	 * @param menu     Le menu à modifier
	 * @param slot     Le slot où placer l'item
	 * @param material Le matériel de l'icône
	 * @param key      La clé de langue (titre|lore, avec <code>%duration%</code>/<code>%price%</code>/<code>%balance%</code>)
	 * @param duration La durée formatée (peut être vide)
	 * @param price    Le prix formaté
	 * @param balance  Le solde formaté
	 * @param action   L'action exécutée au clic
	 */
	private static void setButtonLiar(SGMenu menu, int slot, Material material, String key, String duration, String price, String balance, Consumer<Player> action) {
		Duo<String, ArrayList<String>> strings = getStrings(key);
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§d§l" + strings.getA());
		ArrayList<String> lore = new ArrayList<>();
		for (String line : strings.getB())
			lore.add(line
				.replace("%duration%", duration)
				.replace("%price%", price)
				.replace("%balance%", balance));
		meta.setLore(lore);
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (!(event.getWhoClicked() instanceof Player clicker))
				return;
			clicker.closeInventory();
			action.accept(clicker);
		}));
	}
	
	/**
	 * Ajoute un bouton de retour vers le menu des actions.
	 *
	 * @param menu Le menu à modifier
	 * @param slot Le slot où placer l'item
	 */
	private static void setButtonActionsMenu(SGMenu menu, int slot) {
		ItemStack item = new ItemStack(Material.ARROW);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§7" + Lang.get("CFG_GO_BACK"));
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (event.getWhoClicked() instanceof Player player)
				player.openInventory(getActionsMenu(player).getInventory());
		}));
	}
	
	/**
	 * Ajoute un bouton diffusant un faux message de connexion/déconnexion.
	 *
	 * @param menu Le menu à modifier
	 * @param slot Le slot où placer l'item
	 * @param key  La clé de langue (titre|lore, avec <code>%s</code> pour le pseudo affiché)
	 * @param name Le pseudo à afficher (A ou B), ou <code>null</code> si inconnu
	 * @param self <code>true</code> pour le vrai pseudo (A), <code>false</code> pour le déguisement (B)
	 * @param join <code>true</code> pour un message de connexion, <code>false</code> pour une déconnexion
	 */
	private static void setButtonFake(SGMenu menu, int slot, String key, String name, boolean self, boolean join) {
		Duo<String, ArrayList<String>> strings = getStrings(key);
		String label = (name == null || name.isEmpty()) ? "?" : name;
		
		ItemStack item = new ItemStack(join ? Material.LIME_DYE : Material.GRAY_DYE);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName((join ? "§a§l" : "§c§l") + strings.getA().replace("%s", label));
		ArrayList<String> lore = new ArrayList<>();
		for (String line : strings.getB())
			lore.add(line.replace("%s", label));
		meta.setLore(lore);
		item.setItemMeta(meta);
		
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (!(event.getWhoClicked() instanceof Player clicker))
				return;
			clicker.closeInventory();
			DisguiseManager.broadcastFake(clicker, self, join);
		}));
	}
	
	/**
	 * Ajoute le bouton de retrait du déguisement (équivalent de <code>/undisguise</code>).
	 *
	 * @param menu Le menu à modifier
	 * @param slot Le slot où placer l'item
	 */
	private static void setButtonUndisguise(SGMenu menu, int slot) {
		Duo<String, ArrayList<String>> strings = getStrings("MENU_UNDISGUISE");
		ItemStack item = new ItemStack(Material.PLAYER_HEAD);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§c§l" + strings.getA());
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (!(event.getWhoClicked() instanceof Player player))
				return;
			player.closeInventory();
			player.performCommand("undisguise");
		}));
	}
	
	/**
	 * Ajoute un bouton pour retourner au menu principal.
	 *
	 * @param menu Le menu à modifier
	 * @param slot Le slot où placer l'item
	 */
	private static void setButtonMainMenu(SGMenu menu, int slot) {
		ItemStack item = new ItemStack(Material.ARROW);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§7" + Lang.get("CFG_GO_BACK"));
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> event.getWhoClicked().openInventory(getMainMenu().getInventory())));
	}
	
	/**
	 * Ajoute un bouton pour les notes (menu principal).
	 *
	 * @param menu Le menu à modifier (menu principal)
	 * @param slot Le slot où placer l'item
	 * @param key  La clé de langue
	 */
	private static void setButtonNote(SGMenu menu, int slot, String key) {
		ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§a§l" + Lang.get("CFG_NOTE"));
		
		ArrayList<String> lore = new ArrayList<>();
		for (String part : Lang.get(key).replace("§r", "§a").split("\\|")) {
			String[] partSplit = part.split("\\s");
			int i = 0;
			while (i < partSplit.length) {
				StringBuilder line = new StringBuilder();
				while (line.length() < 40 && i < partSplit.length) {
					line.append(partSplit[i]).append(" ");
					i++;
				}
				lore.add("§a" + line);
			}
		}
		
		meta.setLore(lore);
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item));
	}
	
	/**
	 * Ajoute un bouton pour un booléen.
	 *
	 * @param menu    Le menu à modifier
	 * @param slot    Le slot où placer l'item
	 * @param path    La clé du paramètre
	 * @param strings Les valeurs de langue
	 */
	private static void setButtonBool(SGMenu menu, int slot, String path, Duo<String, ArrayList<String>> strings) {
		boolean enabled = config().getBoolean(path);
		ItemStack item = new ItemStack(enabled ? Material.LIME_WOOL : Material.RED_WOOL);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName((enabled ? "§a" : "§c") + "§l" + strings.getA());
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			config().set(path, !enabled);
			getInstance().saveConfig();
			FeatureManager.refresh();
			setButtonBool(menu, slot, path, strings);
			menu.refreshInventory(event.getWhoClicked());
		}));
	}
	
	/**
	 * Ajoute un bouton pour un nombre entier.
	 *
	 * @param menu     Le menu à modifier
	 * @param slot     Le slot où placer l'item
	 * @param path     La clé du paramètre
	 * @param validate La modification (si nécéssaire) à faire de la nouvelle valeur
	 * @param incr     L'incrémentation de la valeur par clic
	 * @param strings  Les valeurs de langue
	 */
	private static void setButtonInt(SGMenu menu, int slot, String path, Function<Integer, Integer> validate, int incr, Duo<String, ArrayList<String>> strings) {
		int value = config().getInt(path);
		ItemStack item = new ItemStack(Material.YELLOW_WOOL, Math.max(1, Math.min(64, (int) Math.floor((double) value / incr))));
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§e§l" + strings.getA() + ": §6§l" + value);
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			int newValue = value;
			switch (event.getClick()) {
				case LEFT -> newValue += incr;
				case RIGHT -> newValue -= incr;
			}
			newValue = validate.apply(newValue);
			config().set(path, newValue);
			getInstance().saveConfig();
			setButtonInt(menu, slot, path, validate, incr, strings);
			menu.refreshInventory(event.getWhoClicked());
		}));
	}
	
	/**
	 * Ajoute un bouton pour un nombre décimal.
	 *
	 * @param menu     Le menu à modifier
	 * @param slot     Le slot où placer l'item
	 * @param path     La clé du paramètre
	 * @param validate La modification (si nécéssaire) à faire de la nouvelle valeur
	 * @param incr     L'incrémentation de la valeur par clic
	 * @param strings  Les valeurs de langue
	 */
	private static void setButtonDouble(SGMenu menu, int slot, String path, Function<Double, Double> validate, double incr, Duo<String, ArrayList<String>> strings) {
		double value = config().getDouble(path);
		ItemStack item = new ItemStack(Material.ORANGE_WOOL, Math.max(1, Math.min(64, (int) Math.floor(value / incr))));
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§e§l" + strings.getA() + ": §6§l" + value);
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			double newValue = value;
			switch (event.getClick()) {
				case LEFT -> newValue += incr;
				case RIGHT -> newValue -= incr;
			}
			newValue = (double) Math.round(newValue * 1000) / 1000;
			newValue = validate.apply(newValue);
			config().set(path, newValue);
			getInstance().saveConfig();
			setButtonDouble(menu, slot, path, validate, incr, strings);
			menu.refreshInventory(event.getWhoClicked());
		}));
	}
	
	/**
	 * Ajoute un bouton pour une valeur parmi une liste.
	 *
	 * @param menu    Le menu à modifier
	 * @param slot    Le slot où placer l'item
	 * @param path    La clé du paramètre
	 * @param values  La liste des valeurs acceptées
	 * @param strings Les valeurs de langue
	 */
	private static void setButtonValues(SGMenu menu, int slot, String path, List<?> values, Duo<String, ArrayList<String>> strings) {
		Object value = config().get(path);
		ItemStack item = new ItemStack(Material.WHITE_WOOL);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§e§l" + strings.getA() + ": §6§l" + value);
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			int index = 0;
			switch (event.getClick()) {
				case LEFT -> {
					try {
						index = (values.indexOf(value) + 1) % values.size();
					} catch (Throwable exc) {
						index = 0;
					}
				}
				case RIGHT -> {
					try {
						index = (values.indexOf(value) - 1) % values.size();
						if (index < 0)
							index = values.size() - 1;
					} catch (Throwable exc) {
						index = 0;
					}
				}
			}
			
			config().set(path, values.get(index));
			getInstance().saveConfig();
			setButtonValues(menu, slot, path, values, strings);
			menu.refreshInventory(event.getWhoClicked());
		}));
	}
	
	/**
	 * Ajoute un bouton pour un texte.
	 *
	 * @param menu      Le menu à modifier
	 * @param slot      Le slot où placer l'item
	 * @param path      La clé du paramètre
	 * @param predicate La vérification de la valeur
	 * @param strings   Les valeurs de langue
	 */
	private static void setButtonString(SGMenu menu, int slot, String path, Predicate<String> predicate, Duo<String, ArrayList<String>> strings) {
		String value = config().getString(path);
		ItemStack item = new ItemStack(Material.CYAN_WOOL);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§3§l" + strings.getA() + ": §6§l" + value);
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (!(event.getWhoClicked() instanceof Player player))
				return;
			new AnvilGUI.Builder().plugin(getInstance())
				.title("§3§l" + strings.getA())
				.text(value)
				.onClick((clickedSlot, stateSnapshot) -> {
					if (clickedSlot != 2)
						return List.of();
					String newValue = stateSnapshot.getText().trim();
					if (predicate.test(newValue)) {
						config().set(path, newValue);
						getInstance().saveConfig();
						setButtonString(menu, slot, path, predicate, strings);
						return List.of(AnvilGUI.ResponseAction.close());
					}
					player.sendActionBar(Message.deserialize(Lang.get("CFG_INVALID_VALUE")));
					player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER, 0.3f, 1.5f);
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(stateSnapshot -> stateSnapshot.getPlayer().openInventory(menu.getInventory()))
				.open(player);
		}));
	}
	
	/**
	 * Ajoute un bouton pour une couleur.
	 *
	 * @param menu    Le menu à modifier
	 * @param slot    Le slot où placer l'item
	 * @param path    La clé du paramètre
	 * @param strings Les valeurs de langue
	 */
	private static void setButtonColor(SGMenu menu, int slot, String path, Duo<String, ArrayList<String>> strings) {
		String value = config().getString(path).toLowerCase().trim();
		Color color = ColorResolver.getColor(value);
		ItemStack item = new ItemStack(Material.CYAN_WOOL);
		ItemMeta meta = item.getItemMeta();
		meta.addItemFlags(ItemFlag.values());
		meta.setDisplayName("§3§l" + strings.getA() + ": " + ColorResolver.toLegacy(color) + "§l" + value);
		meta.setLore(strings.getB());
		item.setItemMeta(meta);
		
		menu.setButton(slot, new SGButton(item).withListener(event -> {
			if (!(event.getWhoClicked() instanceof Player player))
				return;
			new AnvilGUI.Builder().plugin(getInstance())
				.title("§3§l" + strings.getA())
				.text(value)
				.onClick((clickedSlot, stateSnapshot) -> {
					if (clickedSlot != 2)
						return List.of();
					String newValue = stateSnapshot.getText().trim();
					Color newColor = ColorResolver.getColor(newValue);
					if (newColor != null) {
						config().set(path, newValue);
						getInstance().saveConfig();
						setButtonColor(menu, slot, path, strings);
						return List.of(AnvilGUI.ResponseAction.close());
					}
					player.sendActionBar(Message.deserialize(Lang.get("CFG_INVALID_VALUE")));
					player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.MASTER, 0.3f, 1.5f);
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.onClose(stateSnapshot -> stateSnapshot.getPlayer().openInventory(menu.getInventory()))
				.open(player);
		}));
	}
	
	/**
	 * Récupère les valeurs de la langue choisie.
	 * <p>
	 * Est retourné en premier élément le nom du paramètre.
	 * <p>
	 * Est retourné en second élément la description du paramètre. Celle-ci est divisée en segments de plus ou moins 40 caractères.
	 *
	 * @param key La clé de langue
	 */
	private static Duo<String, ArrayList<String>> getStrings(String key) {
		ArrayList<String> lore = new ArrayList<>();
		String[] split = Lang.get(key).split("\\|", 2);
		for (String part : split[1].replace("§r", "§7").split("\\|")) {
			String[] partSplit = part.split("\\s");
			int i = 0;
			while (i < partSplit.length) {
				StringBuilder line = new StringBuilder();
				while (line.length() < 40 && i < partSplit.length) {
					line.append(partSplit[i]).append(" ");
					i++;
				}
				lore.add("§7" + line);
			}
		}
		return new Duo<>(split[0], lore);
	}
	
}
