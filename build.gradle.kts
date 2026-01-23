import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.hellza"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor 3.2.2 (BOM)
    implementation(platform("io.ktor:ktor-bom:3.2.2"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-sessions")
    // ★追加
    implementation("io.ktor:ktor-server-status-pages")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Firestore
    implementation("com.google.cloud:google-cloud-firestore:3.35.0")

    // ★これを追加: Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")

    testImplementation(kotlin("test"))
}

kotlin {
    // Cloud Buildpacks が Java21 を使うので 21 で統一
    jvmToolchain(21)
}

application {
    // src/main/kotlin/com/hellza/cloudrun/Application.kt の main() 想定
    mainClass.set("com.hellza.cloudrun.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

/**
 * Buildpacks が「Main-Class manifest を持つ jar」を探すため、
 * fat jar(app.jar)を作って manifest に Main-Class を入れる。
 */
tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("app.jar") // build/libs/app.jar に固定
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
}

// Buildpacks は `./gradlew clean assemble ...` を叩くので assemble で shadowJar を必ず作る
tasks.named("assemble") {
    dependsOn("shadowJar")
}
