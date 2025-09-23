plugins {
    java
    id("io.github.goooler.shadow") version "8.1.7"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.stifflered"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    // Local jars from ./libs (Kotlin DSL form)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("**/*.jar"))))

    // Server-provided APIs (do NOT shade)
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("com.github.stefvanschie.inventoryframework:IF:0.10.19")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")

    // Shaded lib (so 'net.wesjd' resolves in IDE and at runtime)
    implementation("net.wesjd:anvilgui:1.10.8-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // Shadow config → name the shaded jar exactly as desired
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        // Relocate ONLY the anvilgui dependency
        relocate("net.wesjd.anvilgui", "com.stifflered.bartercontainer.shaded.anvilgui") {
            include("net/wesjd/anvilgui/**")
        }

        // >>> naming tweaks <<<
        archiveBaseName.set("BarterBarrels")                 // final name prefix
        archiveVersion.set(project.version.toString())       // "1.0.0"
        archiveClassifier.set("")                            // remove default "-all"

        // minimize() // keep off if you use reflection
    }

    // Ensure ./gradlew build produces the shaded jar with the custom name
    build {
        dependsOn(named("shadowJar"))
    }

    // Keep your dev server version exactly as requested
    runServer {
        minecraftVersion("1.21.8")
    }
}
