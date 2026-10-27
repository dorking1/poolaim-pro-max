# PoolAim Pro Max ProGuard rules.

# Keep domain models used with Room and serialization
-keep class com.poolaim.promax.data.local.entity.** { *; }
-keep class com.poolaim.promax.domain.model.** { *; }

# OpenCV native bridge: keep all JNI entry points
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Compose compiler handles its own rules automatically.
