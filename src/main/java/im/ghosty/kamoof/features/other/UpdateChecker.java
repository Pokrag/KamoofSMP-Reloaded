package im.ghosty.kamoof.features.other;

import im.ghosty.kamoof.KamoofPlugin;
import im.ghosty.kamoof.features.Feature;
import com.google.gson.*;
import im.ghosty.kamoof.utils.*;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static im.ghosty.kamoof.KamoofPlugin.*;

public final class UpdateChecker extends Feature {
	
	// Dépôt GitHub de KamoofSMP Reloaded (aucun upstream vers le projet d'origine).
	private static final String REPOSITORY = "Pokrag/KamoofSMP-Reloaded";
	private static final String RELEASE_API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
	private static final String RELEASES_PAGE = "https://github.com/" + REPOSITORY + "/releases";
	private static final String USER_AGENT = REPOSITORY;
	
	@Getter
	private static UpdateChecker instance;
	
	private final String currentVersion;
	private boolean hasUpdate = false;
	private boolean lastCheckFailed = false;
	private String newVersion;
	private String changelog;
	private String downloads;
	private String updateLink;
	
	private Component message;
	private BukkitTask task;
	
	public UpdateChecker() {
		instance = this;
		currentVersion = KamoofPlugin.getInstance().getDescription().getVersion().trim().toLowerCase();
	}
	
	@Override
	public boolean isEnabled() {
		return config().getBoolean("autoupdate.fetch") && !data().getBoolean("debug", false);
	}
	
	@Override
	public void onEnable() {
		super.onEnable();
		
		final boolean[] shouldRestart = {true};
		task = Bukkit.getScheduler().runTaskTimerAsynchronously(KamoofPlugin.getInstance(), () -> {
			boolean download = config().getBoolean("autoupdate.download");
			if (checkForUpdate() && (!download || !downloadUpdate(updateLink, shouldRestart[0]))) {
				hasUpdate = true;
				Bukkit.getConsoleSender().sendMessage(this.message);
			}
			shouldRestart[0] = false;
		}, 1L, 24 * 60 * 60 * 20L);
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		task.cancel();
		task = null;
	}
	
	public boolean checkForUpdate() {
		try {
			lastCheckFailed = false;
			sendMessage("UPDATE_CHECKING");
			HTTPUtils.Response response = HTTPUtils.get(RELEASE_API,
				new HashMap<>() {{
					put("Accept", "application/vnd.github+json");
					put("User-Agent", USER_AGENT);
				}});
			
			// Aucune release publiée pour le moment (404) : rien à proposer.
			if (response.status() > 299) {
				sendMessage("UPDATE_ALREADY_LAST");
				return false;
			}
			
			JsonObject object = JsonParser.parseString(response.response()).getAsJsonObject();
			if (!object.has("tag_name") || object.get("tag_name").isJsonNull()) {
				sendMessage("UPDATE_ALREADY_LAST");
				return false;
			}
			
			// GitHub renvoie le tag (ex: "v1.5.9") : on retire le "v" et on compare.
			newVersion = object.get("tag_name").getAsString().trim().toLowerCase().replaceFirst("^v", "");
			
			if (isNewer(newVersion, currentVersion)) {
				
				int downloadsCount = countDownloads(object);
				if (downloadsCount < 1_000)
					downloads = String.valueOf(downloadsCount);
				else if (downloadsCount < 1_000_000)
					downloads = String.format("%.2fk", (float) (downloadsCount / 1_000));
				else
					downloads = String.format("%.2fM", (float) (downloadsCount / 1_000_000)); // why not
				
				changelog = formatChangelog(object.has("body") && !object.get("body").isJsonNull()
					? object.get("body").getAsString()
					: "");
				
				String hover = String.format(Lang.get("NEW_VERSION_HOVER"), newVersion, downloads, changelog);
				String url = object.has("html_url") && !object.get("html_url").isJsonNull()
					? object.get("html_url").getAsString()
					: RELEASES_PAGE;
				String message = String.format(Lang.get("NEW_VERSION"), hover, url, currentVersion, newVersion);
				this.message = Message.deserialize(message);
				
				updateLink = getFileURL(object);
				return true;
			}
			
			sendMessage("UPDATE_ALREADY_LAST");
		} catch (Throwable exc) {
			exc.printStackTrace();
			lastCheckFailed = true;
			sendMessage("UPDATE_CHECKER_FAIL");
		}
		return false;
	}
	
	/**
	 * Déclenche une vérification manuelle des mises à jour et informe l'expéditeur du résultat.
	 *
	 * @param sender La cible à prévenir
	 */
	public void checkForUpdate(CommandSender sender) {
		Bukkit.getScheduler().runTaskAsynchronously(KamoofPlugin.getInstance(), () -> {
			boolean update = checkForUpdate();
			Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> {
				if (update && message != null)
					sender.sendMessage(message);
				else if (lastCheckFailed)
					Lang.send(sender, "UPDATE_CHECKER_FAIL");
				else
					Lang.send(sender, "UPDATE_ALREADY_LAST");
			});
		});
	}
	
	/**
	 * Vérifie puis installe automatiquement la dernière version : le jar est téléchargé et remplace l'ancien.
	 * La mise à jour est appliquée immédiatement via PlugManX s'il est présent, sinon au prochain redémarrage.
	 *
	 * @param sender La cible à prévenir
	 */
	public void installUpdate(CommandSender sender) {
		Bukkit.getScheduler().runTaskAsynchronously(KamoofPlugin.getInstance(), () -> {
			boolean update = checkForUpdate();
			if (!update) {
				Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> {
					if (lastCheckFailed)
						Lang.send(sender, "UPDATE_CHECKER_FAIL");
					else
						Lang.send(sender, "UPDATE_ALREADY_LAST");
				});
				return;
			}
			
			// Téléchargement du jar (l'ancien est écrasé), sans redémarrage automatique.
			if (!downloadJar(updateLink)) {
				Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(),
					() -> Lang.send(sender, "UPDATE_DOWNLOAD_FAIL"));
				return;
			}
			
			Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(), () -> applyUpdate(sender));
		});
	}
	
	// Applique la mise à jour : recharge à chaud via PlugManX, sinon prévient qu'un redémarrage est requis.
	private void applyUpdate(CommandSender sender) {
		String pluginName = KamoofPlugin.getInstance().getDescription().getName();
		
		if (isPlugManAvailable()) {
			Lang.send(sender, "UPDATE_PLUGMAN_RELOADING");
			CommandSender console = Bukkit.getConsoleSender();
			// PlugManX recharge le jar : on décharge puis on recharge le plugin.
			Bukkit.dispatchCommand(console, "plugman unload " + pluginName);
			Bukkit.dispatchCommand(console, "plugman load " + pluginName);
			return;
		}
		
		showDownloadedMessage();
		sender.sendMessage(this.message);
		Lang.send(sender, "UPDATE_RESTART_REQUIRED");
	}
	
	public boolean downloadUpdate(String url, boolean restart) {
		if (!downloadJar(url))
			return false;
		
		sendMessage("UPDATE_DOWNLOADED");
		
		if (restart) {
			Bukkit.getScheduler().runTask(KamoofPlugin.getInstance(),
				() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart"));
		} else {
			showDownloadedMessage();
		}
		
		return true;
	}
	
	// Télécharge le jar de la dernière version et écrase le fichier du plugin actuel.
	private boolean downloadJar(String url) {
		if (url == null) {
			sendMessage("UPDATE_DOWNLOAD_FAIL");
			return false;
		}
		try {
			sendMessage("UPDATE_DOWNLOADING");
			HTTPUtils.RawResponse response = HTTPUtils.getRaw(url,
				new HashMap<>() {{
					put("User-Agent", USER_AGENT);
				}});
			
			// Ne jamais écraser le plugin avec une page d'erreur : on exige un vrai 200.
			if (response.status() > 299 || response.response().length == 0) {
				sendMessage("UPDATE_DOWNLOAD_FAIL");
				return false;
			}
			
			URL location = KamoofPlugin.class.getProtectionDomain().getCodeSource().getLocation();
			File pluginFile = Utils.getParentPluginFile(location);
			Files.write(Path.of(pluginFile.toURI()), response.response());
			return true;
		} catch (Throwable exc) {
			exc.printStackTrace();
			sendMessage("UPDATE_DOWNLOAD_FAIL");
			return false;
		}
	}
	
	private void showDownloadedMessage() {
		String hover = String.format(Lang.get("NEW_VERSION_HOVER"), newVersion, downloads, changelog);
		String uUrl = RELEASES_PAGE + "/tag/v" + newVersion;
		String raw = String.format(Lang.get("NEW_VERSION_DOWNLOADED"), hover, uUrl, currentVersion, newVersion);
		this.message = Message.deserialize(raw);
		hasUpdate = true;
		Bukkit.getConsoleSender().sendMessage(this.message);
	}
	
	private static boolean isPlugManAvailable() {
		PluginManager pluginManager = Bukkit.getPluginManager();
		return pluginManager.getPlugin("PlugManX") != null || pluginManager.getPlugin("PlugMan") != null;
	}
	
	public String getFileURL(JsonObject obj) {
		if (!obj.has("assets") || obj.get("assets").isJsonNull())
			return null;
		for (JsonElement element : obj.getAsJsonArray("assets")) {
			JsonObject asset = element.getAsJsonObject();
			if (!asset.has("name") || asset.get("name").isJsonNull())
				continue;
			if (!asset.get("name").getAsString().toLowerCase().endsWith(".jar"))
				continue;
			return asset.get("browser_download_url").getAsString();
		}
		return null;
	}
	
	private static int countDownloads(JsonObject release) {
		if (!release.has("assets") || release.get("assets").isJsonNull())
			return 0;
		int total = 0;
		for (JsonElement element : release.getAsJsonArray("assets"))
			total += element.getAsJsonObject().get("download_count").getAsInt();
		return total;
	}
	
	// Compare deux versions segment par segment, en gérant un suffixe lettre :
	// "1.5.10" > "1.5.9" et "1.0b" > "1.0a" > "1.0".
	private static boolean isNewer(String candidate, String current) {
		if (candidate == null || candidate.isEmpty())
			return false;
		String[] candidateParts = candidate.split("\\.");
		String[] currentParts = current.split("\\.");
		int length = Math.max(candidateParts.length, currentParts.length);
		for (int i = 0; i < length; i++) {
			String candidatePart = i < candidateParts.length ? candidateParts[i] : "";
			String currentPart = i < currentParts.length ? currentParts[i] : "";
			int comparison = compareSegment(candidatePart, currentPart);
			if (comparison != 0)
				return comparison > 0;
		}
		return false;
	}
	
	private static int compareSegment(String candidate, String current) {
		int candidateNumber = parseNumber(candidate);
		int currentNumber = parseNumber(current);
		if (candidateNumber != currentNumber)
			return Integer.compare(candidateNumber, currentNumber);
		// Même nombre : on compare le suffixe lettre ("1.0" < "1.0a" < "1.0b").
		return suffix(candidate).compareTo(suffix(current));
	}
	
	private static int parseNumber(String value) {
		try {
			return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
		} catch (NumberFormatException exc) {
			return 0;
		}
	}
	
	private static String suffix(String value) {
		return value.replaceAll("^[0-9]+", "").toLowerCase();
	}
	
	// Adapte un changelog Markdown de release GitHub pour un affichage MiniMessage (tooltip).
	private static String formatChangelog(String body) {
		if (body == null || body.isBlank())
			return "";
		return body.replace("\r\n", "\n")
			.replace("\n", "<br>")
			.replace("\"", "\\\"")
			.replace("`", "");
	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (!hasUpdate || message == null)
			return;
		Player player = event.getPlayer();
		if (!player.hasPermission("kamoofsmp.admin"))
			return;
		
		player.sendMessage(message);
	}
	
	private void sendMessage(String key, Object... args) {
		Lang.send(Bukkit.getConsoleSender(), key, args);
	}
	
}
