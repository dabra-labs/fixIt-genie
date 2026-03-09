# OkHttp and WebSocket rules
-keepnames class okhttp3.internal.ws.** { *; }
-keepnames class okio.** { *; }
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlinx Serialization rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class ai.fixitbuddy.**$$serializer { public static ** INSTANCE; }
-keepclasseswithmembers class ai.fixitbuddy.** {
    *** *;
}

# Keep serializable classes
-keep @kotlinx.serialization.Serializable class ai.fixitbuddy.** { *; }

# Hilt rules
-keep class dagger.hilt.android.internal.**.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# Compose rules (standard)
-keep class androidx.compose.** { *; }
-dontnote androidx.compose.**

# CameraX rules
-keep class androidx.camera.** { *; }

# Keep view constructors for inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}
