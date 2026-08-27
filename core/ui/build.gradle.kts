import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

extensions.configure<LibraryExtension>("android") {
    namespace = "com.shisan.campuspro.core.ui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:designsystem"))
    add("implementation", platform(libs.androidx.compose.bom))
    add("implementation", libs.androidx.compose.foundation)
    add("implementation", libs.androidx.compose.material3)
    add("implementation", libs.androidx.compose.material.icons.extended)
    add("implementation", libs.androidx.compose.ui)
    add("implementation", libs.androidx.compose.ui.tooling.preview)
    add("implementation", libs.androidx.window)
    add("testImplementation", libs.junit)
    add("androidTestImplementation", platform(libs.androidx.compose.bom))
    add("androidTestImplementation", libs.androidx.compose.ui.test.junit4)
    add("debugImplementation", libs.androidx.compose.ui.test.manifest)
}
