# KamoofSMP Reloaded

> ## ⚠️ Projet **vibecodé** et non officiel
> **KamoofSMP Reloaded** est un **portage indépendant et non officiel** du plugin open-source
> **[KamoofSMP Saison 2](https://github.com/Ghosty920/KamoofSMP-S2)** créé par **Ghosty920** et **Solmey**.
>
> Le code de ce portage a été **écrit/généré avec l'aide d'une IA (« vibecoding »)**, sous la supervision d'un
> non-développeur. Il n'est **pas** affilié, **pas** approuvé et **pas** supporté par les auteurs originaux.
>
> 👉 **Projet original : <https://github.com/Ghosty920/KamoofSMP-S2>**
> 👉 **Version Modrinth originale : <https://modrinth.com/plugin/camouf2>**

---

## À propos

Reproduction du **KamoofSMP Saison 2** (concept de Sohran) : lorsqu'un joueur meurt, il fait tomber sa tête.
Utiliser cette tête permet d'en **voler l'apparence et le pseudo** temporairement. Avec 9 têtes, un **rituel**
donne accès à 2 pactes aux avantages/désavantages opposés. Le plugin ajoute aussi une **limite de masses**
fabricables et l'impossibilité d'en stocker dans un enderchest.

**KamoofSMP Reloaded** n'ajoute **aucune fonctionnalité de gameplay** : c'est uniquement un **portage
technique** vers les versions récentes de Paper, destiné à remplacer l'original sur les serveurs Paper 26.3.

## Différences avec l'original

| | Original | KamoofSMP Reloaded |
|---|---|---|
| Serveur | Paper / Purpur 1.20+ | **Paper 26.3** |
| Java | 17+ | **Java 25** |
| Pseudo (déguisement) | NickAPI (intégré) | **EssentialsX** |
| Skin + cape | NickAPI (intégré) | **SkinsRestorer** |
| Auto-update | Pointe vers l'original | **Désactivé** |
| Build | Gradle | Gradle 9.9.1 + Shadow 9.6.1 |

**Pourquoi ces changements ?** NickAPI n'est plus maintenu pour les versions récentes de Minecraft (pas de
module NMS au-delà de la 26.1) et provoquait des plantages au démarrage sur Paper 26.3. Le déguisement repose
donc sur une solution hybride, **indépendante de toute dépendance « upstream »** :
**EssentialsX** pour le pseudonyme, **SkinsRestorer** pour le skin et la cape.

## Prérequis

- **[Paper](https://papermc.io/downloads/paper) (ou fork) 26.3**
- **Java 25**
- **[EssentialsX](https://essentialsx.net/) ≥ 2.22.0** — ⚠️ obligatoire : les versions < 2.22 sont
  incompatibles avec Paper 26.x (erreur de format de version).
- **[SkinsRestorer](https://skinsrestorer.net/) 15.12.6** ou plus récent

## Installation

1. Installe les prérequis ci-dessus dans le dossier `plugins` de ton serveur.
2. Télécharge le `.jar` de la page [Releases](../../releases) et place-le dans `plugins/`.
3. (Re)démarre le serveur.
4. Vérifie qu'aucune erreur n'apparaît dans la console et que `/plugins` liste bien le plugin.

> 💡 L'auto-update est **désactivé par défaut** (`autoupdate.fetch: false`). Ne le réactive pas : il pointe
> vers la version **originale** du plugin, pas vers KamoofSMP Reloaded.

## Compiler

```bash
JAVA_HOME=/chemin/vers/jdk-25 ./gradlew clean shadowJar
```

Le `.jar` est généré dans `build/libs/`.

## Licence et crédits

Ce projet est distribué sous **GNU General Public License v3.0 (GPL-3.0)** — voir [LICENSE](LICENSE).

Cette licence est **conservée à l'identique** de l'original, conformément à ses termes (licence copyleft).
Conformément à la GPL-3.0, les modifications apportées par ce portage sont documentées dans ce README et
dans l'historique Git.

**Crédits :**
- Concept & plugin original : **Ghosty920** et **Solmey** — <https://github.com/Ghosty920/KamoofSMP-S2>
- Portage Paper 26.3 / déguisement hybride (EssentialsX + SkinsRestorer) : **Pokrag**

Aucun auteur original n'est impliqué dans ce portage et aucun support ne doit leur être demandé à son sujet.
Ce projet est un dépôt **indépendant** : il ne suit pas le dépôt d'origine et n'a aucun *upstream* lié.
