package im.ghosty.kamoof.features.other;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Passerelle vers l'économie <b>Vault</b>, utilisée par la Trousse du Menteur.
 * <p>
 * Vault est utilisé en mode « pre-built » : la librairie n'est PAS incluse dans le .jar, le
 * plugin Vault et un plugin d'économie doivent simplement être installés sur le serveur (cf.
 * <code>plugin.yml</code>). S'il est absent, la classe reste totalement inerte.
 *
 * @since 1.2
 */
public final class EconomyHook {
	
	private static Economy economy;
	
	private EconomyHook() {
	}
	
	/**
	 * Récupère le service d'économie fourni par Vault (à appeler à l'activation du plugin).
	 */
	public static void init() {
		economy = null;
		try {
			if (Bukkit.getPluginManager().getPlugin("Vault") == null)
				return;
			RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
			if (registration != null)
				economy = registration.getProvider();
		} catch (Throwable ignored) {
		}
	}
	
	/**
	 * @return <code>true</code> si une économie Vault est disponible sur le serveur.
	 */
	public static boolean isAvailable() {
		return economy != null;
	}
	
	/**
	 * @param player La cible
	 * @return Le solde du joueur, ou 0 si l'économie est indisponible
	 */
	public static double balance(OfflinePlayer player) {
		if (economy == null)
			return 0;
		try {
			return economy.getBalance(player);
		} catch (Throwable exc) {
			return 0;
		}
	}
	
	/**
	 * Retire le montant donné du compte du joueur.
	 *
	 * @param player La cible
	 * @param amount Le montant (un montant nul ou négatif est considéré comme gratuit)
	 * @return <code>true</code> si le paiement a réussi
	 */
	public static boolean withdraw(OfflinePlayer player, double amount) {
		if (amount <= 0)
			return true;
		if (economy == null)
			return false;
		try {
			EconomyResponse response = economy.withdrawPlayer(player, amount);
			return response != null && response.transactionSuccess();
		} catch (Throwable exc) {
			return false;
		}
	}
	
	/**
	 * Formate un montant selon l'économie du serveur (symbole monétaire inclus).
	 *
	 * @param amount Le montant
	 * @return Le montant formaté
	 */
	public static String format(double amount) {
		if (economy == null)
			return String.format("%.2f", amount);
		try {
			return economy.format(amount);
		} catch (Throwable exc) {
			return String.format("%.2f", amount);
		}
	}
	
}
