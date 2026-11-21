## ⚠️ Projet vibecodé et NON officiel

**KamoofSMP Reloaded** est un **portage indépendant et non officiel** du plugin open-source
**[KamoofSMP Saison 2](https://github.com/Ghosty920/KamoofSMP-S2)** créé par **Ghosty920** et **Solmey**.

Le code de ce portage a été **écrit/généré avec l'aide d'une IA** (« vibecoding »), sous la supervision d'une
personne **qui ne code pas**. Il n'est **pas** affilié, **pas** approuvé et **pas** supporté par les auteurs
originaux.

👉 **Plugin original :** https://github.com/Ghosty920/KamoofSMP-S2
👉 **Page Modrinth originale :** https://modrinth.com/plugin/camouf2

> Cette page n'est **pas** le plugin original. Si vous cherchez le projet d'origine, suivez les liens ci-dessus.

---

## À propos

Reproduction du concept **KamoofSMP Saison 2** (idée de Sohran) :

- Quand un joueur **meurt**, il fait tomber sa **tête**.
- Utiliser cette tête permet d'en **voler l'apparence et le pseudo** temporairement (déguisement).
- Avec **9 têtes**, un **rituel** donne accès à **2 pactes** aux avantages et désavantages opposés.
- Le plugin ajoute aussi une **limite de masses** fabricables et empêche leur stockage en enderchest.

**KamoofSMP Reloaded n'ajoute aucune fonctionnalité de gameplay.** C'est uniquement un **portage technique**
vers les versions récentes de Paper, destiné à faire fonctionner l'original sur **Paper 26.3**.

## Différences avec l'original

| | Original | KamoofSMP Reloaded |
|---|---|---|
| Serveur | Paper / Purpur 1.20+ | **Paper 26.3** |
| Java | 17+ | **Java 25** |
| Pseudo (déguisement) | NickAPI (intégré) | **EssentialsX** |
| Skin + cape | NickAPI (intégré) | **SkinsRestorer** |
| Auto-update | Pointe vers l'original | **Désactivé** |
| Build | Gradle | Gradle 9.9.1 + Shadow 9.6.1 |

**Pourquoi ces changements ?** NickAPI n'est plus maintenu pour les versions récentes de Minecraft (aucun
module NMS au-delà de la 26.1) et provoquait des plantages au démarrage sur Paper 26.3. Le déguisement repose
donc sur une solution hybride, **Indépendante de toute dépendance « upstream »** :
**EssentialsX** pour le pseudonyme, **SkinsRestorer** pour le skin et la cape.

## Prérequis

- **Paper** (ou fork) **26.3** — [télécharger](https://papermc.io/downloads/paper)
- **Java 25**
- **EssentialsX ≥ 2.22.0** — [site officiel](https://essentialsx.net/)
  ⚠️ Obligatoire : les versions **< 2.22 sont incompatibles** avec Paper 26.x (erreur de format de version).
- **SkinsRestorer 15.12.6** ou plus récent — [site officiel](https://skinsrestorer.net/)

## Installation

1. Installe les prérequis ci-dessus dans le dossier `plugins` de ton serveur.
2. Télécharge le `.jar` depuis la page [Releases](https://github.com/Pokrag/KamoofSMP-Reloaded/releases)
   et place-le dans `plugins/`.
3. (Re)démarre le serveur.
4. Vérifie qu'aucune erreur n'apparaît dans la console et que `/plugins` liste bien le plugin.

> 💡 L'auto-update est **désactivé par défaut** (`autoupdate.fetch: false` dans `config.yml`). Ne le réactive
> pas : il pointe vers la version **originale** du plugin, pas vers KamoofSMP Reloaded.

## Compiler depuis les sources

```bash
JAVA_HOME=/chemin/vers/jdk-25 ./gradlew clean shadowJar
```

Le `.jar` est généré dans `build/libs/`.

## Licence et crédits

Distribué sous **GNU General Public License v3.0 (GPL-3.0)**.

Cette licence est **conservée à l'identique** de l'original, conformément à ses termes (licence copyleft).
Conformément à la GPL-3.0, les modifications apportées par ce portage sont documentées dans le README et dans
l'historique Git du dépôt.

**Crédits :**

- Concept & plugin original : **Ghosty920** et **Solmey** — https://github.com/Ghosty920/KamoofSMP-S2
- Portage Paper 26.3 / déguisement hybride (EssentialsX + SkinsRestorer) : **Pokrag**

Aucun auteur original n'est impliqué dans ce portage et aucun support ne doit leur être demandé à son sujet.
Ce projet est un dépôt **indépendant** : il ne suit pas le dépôt d'origine et n'a aucun *upstream* lié.
