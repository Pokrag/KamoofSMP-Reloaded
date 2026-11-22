package im.ghosty.kamoof.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client léger pour l'API publique <a href="https://mc-api.io/docs">mc-api.io</a>.
 * <p>
 * Contrairement à l'API Mojang (utilisée par SkinsRestorer), mc-api.io sait résoudre les
 * profils des <b>deux éditions</b> : {@code JAVA} et {@code BEDROCK}. C'est donc la seule
 * façon d'obtenir la peau (et les textures signées {@code value}/{@code signature}) d'un
 * compte Bedrock, dont l'UUID dérive du XUID et n'existe pas côté Mojang.
 * <p>
 * Tous les points d'entrée sont des {@code GET}. Le service limite à 60 requêtes/minute par
 * IP sans clé : les réponses sont donc mises en cache en mémoire.
 *
 * @since 1.0
 */
@UtilityClass
public final class MinecraftAPI {
	
	/** URL de base de l'API. */
	public static final String BASE_URL = "https://mc-api.io";
	
	private static final Map<String, String> HEADERS = Map.of(
		"Accept", "application/json",
		"User-Agent", "KamoofSMP-Reloaded"
	);
	
	/** Cache mémoire des profils déjà résolus (clé = chemin de la requête). */
	private static final Map<String, Profile> CACHE = new ConcurrentHashMap<>();
	
	/** L'édition d'un compte Minecraft. */
	public enum Edition {
		JAVA, BEDROCK
	}
	
	/**
	 * Profil d'un joueur, tel que renvoyé par <code>GET /profile/...</code>.
	 *
	 * @param name      Le pseudo
	 * @param uuid      L'UUID (peut être <code>null</code>)
	 * @param xuid      Le XUID (uniquement pour les comptes Bedrock, sinon <code>null</code>)
	 * @param value     La propriété de texture signée (peau + cape), ou <code>null</code>
	 * @param signature La signature de la propriété de texture, ou <code>null</code>
	 */
	public record Profile(String name, UUID uuid, Long xuid, String value, String signature) {
		
		/**
		 * @return <code>true</code> si ce profil porte une propriété de texture exploitable.
		 */
		public boolean hasSkin() {
			return value != null && !value.isEmpty() && signature != null && !signature.isEmpty();
		}
		
	}
	
	/**
	 * Récupère le profil d'un joueur à partir de son UUID (Java ou Bedrock).
	 *
	 * @param uuid L'UUID du joueur
	 * @return Le profil, ou vide si introuvable / API indisponible
	 */
	public static Optional<Profile> byUuid(UUID uuid) {
		if (uuid == null)
			return Optional.empty();
		return fetch("/profile/" + uuid);
	}
	
	/**
	 * Récupère le profil d'un joueur à partir de son pseudo et de son édition.
	 *
	 * @param name    Le pseudo
	 * @param edition L'édition ({@link Edition#JAVA} ou {@link Edition#BEDROCK})
	 * @return Le profil, ou vide si introuvable / API indisponible
	 */
	public static Optional<Profile> byName(String name, Edition edition) {
		if (name == null || name.isEmpty() || edition == null)
			return Optional.empty();
		return fetch("/profile/" + name + "/" + edition.name());
	}
	
	private static Optional<Profile> fetch(String path) {
		Profile cached = CACHE.get(path);
		if (cached != null)
			return Optional.of(cached);
		
		try {
			HTTPUtils.Response response = HTTPUtils.get(BASE_URL + path, HEADERS);
			// 404 = joueur inconnu, 429 = quota dépassé, 503 = service amont indisponible :
			// dans tous les cas on renvoie vide sans mettre en cache.
			if (response.status() != 200)
				return Optional.empty();
			
			Profile profile = parse(response.response());
			if (profile == null)
				return Optional.empty();
			
			CACHE.put(path, profile);
			return Optional.of(profile);
		} catch (Throwable ignored) {
			// L'API ne doit jamais faire échouer le déguisement.
			return Optional.empty();
		}
	}
	
	private static Profile parse(String json) {
		try {
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			
			String value = null;
			String signature = null;
			if (object.has("textures") && object.get("textures").isJsonObject()) {
				JsonObject textures = object.getAsJsonObject("textures");
				value = string(textures, "value");
				signature = string(textures, "signature");
			}
			
			return new Profile(
				string(object, "name"),
				uuid(string(object, "uuid")),
				object.has("xuid") && object.get("xuid").isJsonPrimitive() ? object.get("xuid").getAsLong() : null,
				value,
				signature
			);
		} catch (Throwable ignored) {
			return null;
		}
	}
	
	private static String string(JsonObject object, String key) {
		if (!object.has(key))
			return null;
		JsonElement element = object.get(key);
		return element.isJsonNull() ? null : element.getAsString();
	}
	
	/**
	 * L'API renvoie les UUID avec ou sans tirets : on accepte les deux formats.
	 */
	private static UUID uuid(String raw) {
		if (raw == null || raw.isEmpty())
			return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
		}
		
		String digits = raw.replace("-", "");
		if (digits.length() != 32)
			return null;
		try {
			return new UUID(
				Long.parseUnsignedLong(digits.substring(0, 16), 16),
				Long.parseUnsignedLong(digits.substring(16), 16)
			);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
	
}
