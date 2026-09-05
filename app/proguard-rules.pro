# ProGuard rules for polymath-filesystem

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Hilt
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# JNI (Keep native methods)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    native <methods>;
}

# Keep libsu annotations
-keep class com.topjohnwu.superuser.** { *; }

# Keep QuickJS
-keep class app.cash.quickjs.** { *; }
