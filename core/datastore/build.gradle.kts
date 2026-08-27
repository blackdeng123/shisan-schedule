import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")

extensions.configure<LibraryExtension>("android") {
    namespace = "com.shisan.campuspro.core.datastore"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", libs.androidx.datastore.preferences)
    add("implementation", libs.androidx.security.crypto)
}
