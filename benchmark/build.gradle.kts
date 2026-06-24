plugins {
    kotlin("jvm")
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
    testLogging {
        showStandardStreams = true
    }
    // Forward java.library.path to the test JVM (for CGAL native lib)
    systemProperty("java.library.path",
        System.getProperty("java.library.path") ?: ""
    )
    // Also support a custom property for explicit override
    val cgalLibPath = project.findProperty("cgal.library.path") as String?
    if (cgalLibPath != null) {
        jvmArgs("-Djava.library.path=$cgalLibPath")
    }
}

// Exclude benchmark from default check lifecycle task
tasks.named("check") {
    enabled = false
}

dependencies {
    implementation(project(":shared-base"))
    implementation(project(":kotlin-engine"))
    implementation(project(":cgal-engine"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("net.jqwik:jqwik:1.8.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
