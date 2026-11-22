package im.ghosty.kamoof.commands;

import im.ghosty.kamoof.features.drophead.SkullManager;
import im.ghosty.kamoof.utils.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * La commande <code>/myhead</code>, permettant d'obtenir sa propre tête (permission <code>kamoofsmp.head</code>).
 * @since 1.0
 */
public final class HeadCMD implements CommandExecutor {
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			Lang.send(sender, "PLAYER_ONLY");
			return true;
		}
		
		ItemStack item = SkullManager.getSkull(player.getName());
		if (player.getInventory().addItem(item).isEmpty())
			Lang.send(player, "HEAD_GIVEN", player.getName());
		else
			Lang.send(player, "INVENTORY_FULL");
		
		return true;
	}
	
}
