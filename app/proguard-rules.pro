# Compose
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Data models (keep for serialization)
-keep class com.shisan.campuspro.core.model.** { *; }

# Profile Installer
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# Tink / Security Crypto (EncryptedSharedPreferences 底层)
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn javax.annotation.**
