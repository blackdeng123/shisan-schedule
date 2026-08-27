apply(plugin = "org.jetbrains.kotlin.jvm")

dependencies {
    add("implementation", project(":core:common"))
    add("implementation", project(":core:model"))
    add("implementation", libs.jsoup)
    add("api", libs.ktor.client.core)
    add("api", libs.ktor.client.okhttp)
    add("api", libs.ktor.client.logging)
    add("implementation", libs.kotlinx.coroutines.core)
    add("implementation", libs.kotlinx.serialization.json)
    add("testImplementation", libs.junit)
    add("testImplementation", libs.kotlinx.coroutines.test)
    add("testImplementation", libs.ktor.client.mock)
}
