import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure

apply(plugin = "com.android.library")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

extensions.configure<LibraryExtension>("android") {
    namespace = "com.shisan.campuspro.feature.exams"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
}

dependencies {
    add("implementation", project(":core:data"))
    add("implementation", project(":core:model"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:ui"))
    add("implementation", platform(libs.androidx.compose.bom))
    add("implementation", libs.androidx.compose.material3)
    add("implementation", libs.androidx.compose.ui)
    add("implementation", libs.androidx.lifecycle.runtime.compose)
    add("implementation", libs.androidx.lifecycle.viewmodel.compose)
    add("implementation", libs.androidx.lifecycle.viewmodel.ktx)
    add("testImplementation", libs.junit)
    add("testImplementation", libs.kotlinx.coroutines.test)
}
