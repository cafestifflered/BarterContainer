plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("xyz.jpenilla.run-paper") version "1.0.6"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1"
}

group = "com.stifflered"

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.bytecode.space/repository/maven-public/")
}

dependencies {
    implementation(fileTree("libs").matching {
        include("**/*.jar")
    })

    implementation("com.github.stefvanschie.inventoryframework:IF:0.10.13")
    compileOnly("io.papermc.paper:paper-api:1.19-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.8.2")
}

bukkit {
    main = "com.stifflered.bartercontainer.BarterContainer"
    name = rootProject.name
    apiVersion = "1.19"
    version = "1.0.0"
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    shadowJar {
        dependencies {

        }

    }

    runServer {
        minecraftVersion("1.20.4")
    }

}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}