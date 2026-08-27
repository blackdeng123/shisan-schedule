import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "com.google.devtools.ksp")

extensions.configure<LibraryExtension>("android") {
    namespace = "com.shisan.campuspro.core.database"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:datastore"))
    add("implementation", project(":core:data"))
    add("api", libs.androidx.room.runtime)
    add("api", libs.androidx.room.ktx)
    add("ksp", libs.androidx.room.compiler)
    add("testImplementation", libs.junit)
}
