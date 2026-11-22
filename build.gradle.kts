plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "im.ghosty.kamoof"
version = "1.4b"

repositories {
    mavenCentral()
//    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.essentialsx.net/releases/")
    maven("https://repo.codemc.io/repository/ghosty920/")
    maven("https://jitpack.io")
    maven("https://mvn.wesjd.net/")
    flatDir {
        dirs("libs")
    }
}

dependencies {
    implementation(project(":API"))
//    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:26.3.build.257-stable")
//    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")

    // Adventure (API, MiniMessage, serializers) est fourni par Paper 26.3 (Adventure 5.2.0) :
    // on ne le shade plus pour éviter tout conflit de versions (cf. BungeeComponentSerializer).

    compileOnly("net.essentialsx:EssentialsX:2.21.2") { isTransitive = false }
    compileOnly("net.skinsrestorer:skinsrestorer-api:15.12.6") { isTransitive = false }

    // Vault : utilisé en mode « pre-built » pour les actions payantes de la Trousse du Menteur
    // (le plugin Vault + un plugin d'économie doivent être installés sur le serveur, cf. plugin.yml).
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }

    // PacketEvents : utilisé en mode « pre-built » (le plugin PacketEvents doit être installé
    // sur le serveur, cf. plugin.yml). Permet de réécrire le pseudo affiché AU-DESSUS DE LA
    // TÊTE (paquet PLAYER_INFO), ce qu'EssentialsX ne fait pas. Non embarqué (compileOnly).
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
//    implementation("com.samjakob:SpiGUI:v1.4.1")
    implementation(mapOf("name" to "SpiGUI-v2-2.0.0", "ext" to "jar"))
    implementation("net.wesjd:anvilgui:1.10.12-SNAPSHOT")


    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveFileName.set("${project.name}-${project.version}.jar")
        minimize {
            exclude(project(":API"))
            exclude(dependency(".*:SpiGUI-v2-2.0.0:.*"))
            exclude(dependency("net.wesjd:anvilgui:.*"))
        }
        relocate("com.samjakob.spigui", "im.ghosty.kamoof.deps.spigui")
        relocate("net.wesjd.anvilgui", "im.ghosty.kamoof.deps.anvilgui")
    }

    processResources {
        inputs.property("version", version)

        filesMatching(listOf("plugin.yml")) {
            expand(inputs.properties)
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }
}

