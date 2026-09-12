# Media3 ExoPlayer Proguard
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Aditya Music Models
-keep class com.aditya.music.data.model.** { *; }
