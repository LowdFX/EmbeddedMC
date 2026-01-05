plugins {
    id("fabric-loom") version "1.13.6"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("embeddedmc") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    // Minecraft & Fabric
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Cloth Config (GUI)
    modApi("me.shedaniel.cloth:cloth-config-fabric:${project.property("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    // HTTP Client
    include(implementation("com.squareup.okhttp3:okhttp:${project.property("okhttp_version")}")!!)

    // JSON (Minecraft already includes Gson, but we ensure a specific version)
    include(implementation("com.google.code.gson:gson:${project.property("gson_version")}")!!)
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
