plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("androidx.compose.runtime:runtime:1.8.0")
    testImplementation(libs.junit)
}
