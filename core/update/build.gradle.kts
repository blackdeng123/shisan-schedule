import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

extensions.configure<LibraryExtension>("android") {
    namespace = "com.shisan.campuspro.core.update"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
}

dependencies {
    add("implementation", project(":core:datastore"))
    add("implementation", libs.androidx.core.ktx)
    add("implementation", libs.kotlinx.coroutines.android)
    add("implementation", libs.kotlinx.serialization.json)
    add("implementation", libs.ktor.client.core)
    add("implementation", libs.ktor.client.okhttp)
    add("implementation", libs.ktor.client.content.negotiation)
    add("implementation", libs.ktor.serialization.kotlinx.json)
    add("testImplementation", libs.junit)
    add("testImplementation", libs.kotlinx.coroutines.test)
    add("testImplementation", libs.ktor.client.mock)
}
