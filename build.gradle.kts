plugins {
    java
    id("com.gradleup.shadow") version "9.0.2"
}

group = "me.mtynnn"
version = "1.6.0"

providers.environmentVariable("VALERIN_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.nexomc.com/releases")
    maven("https://repo.nightexpressdev.com/releases")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.mysql:mysql-connector-j:9.4.0")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.17")
    compileOnly("com.nexomc:nexo:1.26.0")
    compileOnly("su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.sk89q.worldguard:worldguard-core:7.0.17")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    // Avoid Windows/OneDrive ZipFS locks when Gradle closes dependency JARs.
    options.isFork = true
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("thin")
}
