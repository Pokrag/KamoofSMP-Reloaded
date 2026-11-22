package im.ghosty.kamoof.commands;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.api.KamoofSMP;
import im.ghosty.kamoof.features.disguise.DisguiseManager;
import im.ghosty.kamoof.features.other.JoinMessages;
import im.ghosty.kamoof.features.other.UpdateChecker;
import im.ghosty.kamoof.features.ritual.*;
import im.ghosty.kamoof.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static im.ghosty.kamoof.KamoofPlugin.*;

/**
 * La commande <code>/kamoofsmp</code>, permettant d'accéder aux paramètres du plugin, aux crédits... à différentes sous-commandes.
 * <p>
 * Elle est également utilisée pour l'acceptation de pactes.
 *
 * @since 1.0
 */
public final class KamoofCMD implements CommandExecutor, TabCompleter {
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			Lang.send(sender, "PLAYER_ONLY");
			return true;
		}
		
		if (args.length == 2 && args[0].equalsIgnoreCase("pacte")) {
			if (!args[1].equalsIgnoreCase("1") && !args[1].equalsIgnoreCase("2"))
				return true;
			
			ItemStack book = player.getInventory().getItemInMainHand();
			UUID uuid;
			if ((uuid = RitualBook.getUUID(book)) == null) {
				book = player.getInventory().getItemInOffHand();
				if ((uuid = RitualBook.getUUID(book)) == null)
					return true;
			}
			
			if (RitualHandler.getPacte(player) != null) {
				Message.send(player, "messages.already-chose", Map.of("player", KamoofSMP.getInstance().getName(player)));
				return true;
			}
			
			if (!RitualHandler.isValidUUID(uuid))
				return true;
			
			book.setAmount(0);
			RitualHandler.setPacte(player, args[1]);
			return true;
		}
		
		if (!player.hasPermission("kamoofsmp.admin")) {
			return showCredits(sender);
		} else if (args.length == 0)
			return showArgs(sender);
		
		switch (args[0].toLowerCase()) {
			case "info": {
				return showCredits(player);
			}
			case "config": {
				player.openInventory(ConfigGUI.getMainMenu().getInventory());
				return true;
			}
			case "menu": {
				if (args.length >= 2 && args[1].equalsIgnoreCase("chat")) {
					sendActionsChat(player);
					return true;
				}
				player.openInventory(ConfigGUI.getActionsMenu(player).getInventory());
				return true;
			}
			case "givehead": {
				if (args.length < 2) {
					player.sendMessage(String.format("§a§l[KamoofSMP] §cUsage: /%s givehead <username>", label.toLowerCase()));
					return true;
				}
				GiveHeadCMD.execute(player, args[1]);
				return true;
			}
			case "reload": {
				getInstance().reloadConfig();
				Lang.init();
				Lang.send(player, "CONFIG_RELOADED");
				return true;
			}
			case "update": {
				UpdateChecker.getInstance().installUpdate(player);
				return true;
			}
			case "setup": {
				if(!CompatibilityUtils.isMinecraft1_21()) {
					Lang.send(player, "NOT_MINECRAFT_1_21");
					return true;
				}
				if (!config().getBoolean("ritual.enabled")) {
					Lang.send(player, "RITUAL_DISABLED");
					return true;
				}
				if (player.getInventory().addItem(RitualSetup.getItems()).isEmpty())
					Lang.send(player, "SETUP_GIVEN");
				else
					Lang.send(player, "INVENTORY_FULL");
				return true;
			}
			case "undisguise": {
				if (args.length < 2) {
					StringBuilder message = new StringBuilder(Lang.PREFIX + "<yellow><b>Undisguise:<reset>");
					HashMap<String, String> disguises = getDisguised();
					if (!disguises.isEmpty()) {
						List<String> keys = disguises.keySet().stream().toList();
						List<String> values = disguises.values().stream().toList();
						for (int i = 0; i < disguises.size(); i++) {
							String key = keys.get(i);
							String value = values.get(i);
							message.append(String.format("<br><click:run_command:'/kamoofsmp undisguise %s'><hover:show_text:\"" + Lang.get("UNDISGUISE_HOVER") + "\"><white>%s <gray>→ <#ffddff>%s</hover></click>", key, key, key, value));
						}
					} else {
						message.append(Lang.get("UNDISGUISE_NONE"));
					}
					Message.send(player, message.toString());
					return true;
				}
				String name = args[1];
				OfflinePlayer target = Bukkit.getOfflinePlayer(name);
				KamoofSMP.getInstance().disguise(target, null);
				Lang.send(player, "UNDISGUISED", KamoofSMP.getInstance().getName(target));
				return true;
			}
			case "english": {
				// set la langue en anglais
				config().set("language", "en");
				getInstance().saveConfig();
				Lang.init();
				// fais réapparaître le message de first join
				JoinMessages.done = false;
				data().set("firstJoinMsg", false);
				saveData();
				// confirmation
				String message = Lang.PREFIX + "<white>The language has been set to <b>English</b>!<br>Reconnect to receive the first-time instructions in English as well.";
				Message.send(player, message);
				return true;
			}
			case "pacterun": {
				RitualHandler.runAnimation(player);
				return true;
			}
			case "pactebook": {
				player.getWorld().dropItemNaturally(player.getLocation(), RitualBook.getBook(RitualHandler.addNewUUID()));
				return true;
			}
			case "ignorespigotcrash": {
				JoinMessages.ignoreSpigot = true;
				data().set("ignoreSpigotCrash", true);
				saveData();
				Lang.send(player, "SPIGOT_IGNORED");
				return true;
			}
			case "debug": {
				boolean val = !data().getBoolean("debug", false);
				data().set("debug", val);
				saveData();
				Message.send(player, Lang.PREFIX + (val ? "<b><white>DEBUG MODE!! :3" : "<red>Debug Mode disabled."));
				return true;
			}
			case "fakemsg": {
				if (args.length < 3)
					return true;
				boolean self = args[1].equalsIgnoreCase("self") || args[1].equalsIgnoreCase("a");
				boolean join = args[2].equalsIgnoreCase("join") || args[2].equalsIgnoreCase("connect");
				DisguiseManager.broadcastFake(player, self, join);
				return true;
			}
			default: {
				return showArgs(player);
			}
		}
	}
	
	private static final List<String> validArgs = Arrays.asList("info", "config", "menu", "reload", "update", "setup", "givehead", "undisguise"),
		devArgs = new ArrayList<>(validArgs);
	
	static {
		devArgs.addAll(Arrays.asList("english", "pacterun", "pactebook", "ignorespigotcrash", "debug"));
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (sender.hasPermission("kamoofsmp.admin")) {
			if (args.length <= 1) {
				return data().getBoolean("debug", false) ? devArgs : validArgs;
			}
			if (args[0].equalsIgnoreCase("givehead")) {
				ArrayList<String> values = new ArrayList<>();
				for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
					if (player == null || player.getName() == null)
						continue;
					values.add(player.getName());
				}
				if (args.length == 2) {
					String toCheck = args[1].toLowerCase().trim();
					values.removeIf(player -> !player.toLowerCase().trim().contains(toCheck));
				}
				return values;
			}
			if (args[0].equalsIgnoreCase("undisguise")) {
				List<String> values = new ArrayList<>(getDisguised().keySet());
				if (args.length == 2) {
					String toCheck = args[1].toLowerCase().trim();
					values.removeIf(player -> !player.toLowerCase().trim().contains(toCheck));
				}
				return values;
			}
		}
		return List.of();
	}
	
	/**
	 * Envoie, dans le chat, les mêmes actions que {@link ConfigGUI#getActionsMenu(Player)} :
	 * le retrait du déguisement et les quatre faux messages de connexion/déconnexion.
	 *
	 * @param player La cible
	 */
	private void sendActionsChat(Player player) {
		String a = KamoofSMP.getInstance().getName(player);
		if (a == null || a.isEmpty())
			a = "?";
		String nickname = DisguiseManager.getNickname(player);
		String b = (nickname == null || nickname.isEmpty()) ? "?" : nickname;
		String hover = Lang.get("MENU_CHAT_HOVER");
		
		StringBuilder message = new StringBuilder(Lang.get("MENU_CHAT_HEADER"));
		message.append(chatButton("/undisguise", hover, title("MENU_UNDISGUISE")));
		message.append(chatButton("/kamoofsmp fakemsg self leave", hover, String.format(title("MENU_FAKE_SELF_DISCONNECT"), a)));
		message.append(chatButton("/kamoofsmp fakemsg nick join", hover, String.format(title("MENU_FAKE_NICK_CONNECT"), b)));
		message.append(chatButton("/kamoofsmp fakemsg self join", hover, String.format(title("MENU_FAKE_SELF_CONNECT"), a)));
		message.append(chatButton("/kamoofsmp fakemsg nick leave", hover, String.format(title("MENU_FAKE_NICK_DISCONNECT"), b)));
		Message.send(player, message.toString());
	}
	
	private static String chatButton(String command, String hover, String label) {
		return String.format("<br><click:run_command:'%s'><hover:show_text:\"%s\"><white>[%s]</hover></click>", command, hover, label);
	}
	
	private static String title(String key) {
		return Lang.get(key).split("\\|", 2)[0];
	}
	
	public HashMap<String, String> getDisguised() {
		HashMap<String, String> values = new HashMap<>();
		for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
			if (player == null || player.getName() == null)
				continue;
			String disguise = KamoofSMP.getInstance().getDisguise(player);
			if (disguise == null)
				continue;
			values.put(KamoofSMP.getInstance().getName(player), disguise);
		}
		return values;
	}
	
	/**
	 * Envoyer les arguments de la commande
	 *
	 * @param sender La cible
	 * @return <code>true</code>
	 */
	public boolean showArgs(CommandSender sender) {
		String version = getInstance().getDescription().getVersion();
		Lang.send(sender, "MAIN_ARGUMENTS", version);
		return true;
	}
	
	/**
	 * Envoyer les crédits du KamoofSMP
	 *
	 * @param sender La cible
	 * @return <code>true</code>
	 */
	public boolean showCredits(CommandSender sender) {
		String version = getInstance().getDescription().getVersion();
		Lang.send(sender, "CREDITS", version, Lang.SUPPORT);
		return true;
	}
	
}
