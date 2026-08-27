buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.21-1.0.27")
        classpath(
            "com.mikepenz.aboutlibraries.plugin.android:" +
                "com.mikepenz.aboutlibraries.plugin.android.gradle.plugin:15.0.3",
        )
    }
}

// 统一 JVM 编译目标为 Java 17，解决 Kotlin(jdk21) 与 Java(1.8) 目标不一致问题
// 必须通过 AGP 的 compileOptions DSL 设置，直接改 JavaCompile task 属性会被 AGP 覆盖
allprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    // 兜底：确保 Kotlin 编译（含 KSP）也统一到 Java 17
    // Kotlin Android 插件通常会自动从 AGP compileOptions 继承，此处显式设置以防万一
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}
