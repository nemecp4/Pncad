plugins {
    kotlin("jvm")
    application
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

application {
    mainClass.set("com.openscadviewer.engine.KotlinEngineMainKt")
}

dependencies {
    api(project(":shared-base"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
