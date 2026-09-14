plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("net.jqwik:jqwik:1.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
