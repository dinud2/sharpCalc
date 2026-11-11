// Pure Kotlin: calculation engine, editor, key logic and LCD renderer.
// No Android or Compose imports are allowed here (P-4, P-5).
plugins {
    id("org.jetbrains.kotlin.jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
tasks.test {
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
