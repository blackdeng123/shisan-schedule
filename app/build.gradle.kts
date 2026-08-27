import com.android.build.api.dsl.ApplicationExtension
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import com.mikepenz.aboutlibraries.plugin.StrictMode
import java.net.URI
import java.util.Properties
import java.util.zip.ZipFile
import java.nio.charset.StandardCharsets
import org.gradle.kotlin.dsl.configure

fun loadPropertiesFile(path: String): Properties = Properties().apply {
    val source = rootProject.file(path)
    if (source.isFile) source.inputStream().use(::load)
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun isAllowedUpdateUrl(value: String, allowLocalhostHttp: Boolean): Boolean {
    if (value.isBlank()) return true
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) return true
    return allowLocalhostHttp &&
        uri.scheme.equals("http", ignoreCase = true) &&
        uri.host == "127.0.0.1"
}

val updateProperties = loadPropertiesFile("update.properties")
val keystoreProperties = loadPropertiesFile("keystore.properties")
val releaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
val releaseManifestUrl = updateProperties.getProperty("manifestUrl", "").trim()
val debugManifestUrl = updateProperties.getProperty(
    "debugManifestUrl",
    "http://127.0.0.1:8080/update-server/dist/test/releases/latest.json",
).trim()

require(isAllowedUpdateUrl(debugManifestUrl, allowLocalhostHttp = true)) {
    "Debug 的 debugManifestUrl 只允许 HTTPS 或 http://127.0.0.1。"
}

if (releaseTaskRequested) {
    require(keystoreProperties.isNotEmpty()) {
        "Release 构建缺少 keystore.properties；请复制 keystore.properties.example 并填写本机签名配置。"
    }
    require(releaseManifestUrl.startsWith("https://")) {
        "Release 构建要求 update.properties 中的 manifestUrl 使用 HTTPS。"
    }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
        require(!keystoreProperties.getProperty(key).isNullOrBlank()) {
            "keystore.properties 缺少 $key。"
        }
    }
}

apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")
apply(plugin = "com.mikepenz.aboutlibraries.plugin.android")

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.shisan.campuspro"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shisan.campuspro"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("APP_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("APP_VERSION_NAME").orNull ?: "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "UPDATE_MANIFEST_URL", debugManifestUrl.asBuildConfigString())
        }
        release {
            isMinifyEnabled = true
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "UPDATE_MANIFEST_URL", releaseManifestUrl.asBuildConfigString())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

extensions.configure<AboutLibrariesExtension>("aboutLibraries") {
    offlineMode.set(false)
    collect {
        includePlatform.set(true)
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
    }
    export {
        prettyPrint.set(true)
        includeMetaData.set(false)
    }
    library {
        requireLicense.set(true)
    }
    license {
        strictMode.set(StrictMode.FAIL)
        allowedLicenses.addAll(
            "Apache-2.0",
            "MIT",
            "BSD-2-Clause",
            "BSD-3-Clause",
            "EPL-1.0",
        )
    }
}

dependencies {
    add("implementation", project(":core:data"))
    add("implementation", project(":core:database"))
    add("implementation", project(":core:datastore"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:model"))
    add("implementation", project(":core:network"))
    add("implementation", project(":core:ui"))
    add("implementation", project(":core:update"))
    add("implementation", project(":feature:auth"))
    add("implementation", project(":feature:schedule"))
    add("implementation", project(":feature:grades"))
    add("implementation", project(":feature:exams"))
    add("implementation", project(":feature:profile"))
    add("implementation", project(":feature:settings"))
    add("implementation", project(":feature:credential"))
    add("implementation", platform(libs.androidx.compose.bom))
    add("implementation", libs.androidx.activity.compose)
    add("implementation", libs.androidx.browser)
    add("implementation", libs.androidx.core.ktx)
    add("implementation", libs.androidx.compose.foundation)
    add("implementation", libs.androidx.compose.material3)
    add("implementation", libs.androidx.compose.ui)
    add("implementation", libs.androidx.lifecycle.runtime.compose)
    add("implementation", libs.androidx.lifecycle.viewmodel.compose)
    add("implementation", libs.androidx.navigation.compose)
    add("implementation", libs.androidx.work.runtime.ktx)
    add("implementation", "androidx.profileinstaller:profileinstaller:1.3.1")
    add("testImplementation", libs.junit)
    add("androidTestImplementation", libs.androidx.work.testing)
}

val verifyReleaseDebugIsolation = tasks.register("verifyReleaseDebugIsolation") {
    group = "verification"
    description = "确认 Release APK 不包含 Debug 开发者工具实现"
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        require(apk.isFile) { "未找到 Release APK：${apk.absolutePath}" }
        val forbidden = listOf(
            "com/shisan/campuspro/debugtools",
            "com.shisan.campuspro.debugtools",
            "debug_tools",
            "vendor_notification_debug",
        )
        ZipFile(apk).use { zip ->
            val leaked = zip.entries().asSequence().filterNot { it.isDirectory }.flatMap { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val latin = String(bytes, StandardCharsets.ISO_8859_1)
                forbidden.asSequence().filter { marker -> latin.contains(marker) }.map { marker -> "${entry.name}:$marker" }
            }.toList()
            require(leaked.isEmpty()) { "Release APK 检测到 Debug 标记：${leaked.joinToString()}" }
        }
    }
}

val verifyNoVendorPushDependencies = tasks.register("verifyNoVendorPushDependencies") {
    group = "verification"
    description = "确认第一阶段未引入厂商或聚合 Push SDK"
    doLast {
        val forbidden = listOf(
            "com.xiaomi.mipush",
            "com.huawei.hms:push",
            "com.heytap.msp",
            "com.vivo.push",
            "com.hihonor.mcs:push",
            "cn.jpush",
            "com.getui",
        )
        val dependencies = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
            .map { it.id.displayName }
        val leaked = dependencies.filter { dependency ->
            forbidden.any { marker -> dependency.contains(marker, ignoreCase = true) }
        }
        require(leaked.isEmpty()) { "第一阶段检测到 Push SDK 依赖：${leaked.joinToString()}" }
    }
}

verifyReleaseDebugIsolation.configure {
    dependsOn(verifyNoVendorPushDependencies)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseDebugIsolation)
}
