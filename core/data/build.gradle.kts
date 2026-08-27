apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:common"))
    add("implementation", project(":core:network"))
    add("implementation", libs.kotlinx.coroutines.core)
    add("testImplementation", libs.junit)
    add("testImplementation", libs.kotlinx.coroutines.test)
    add("testImplementation", libs.ktor.client.mock)
}
