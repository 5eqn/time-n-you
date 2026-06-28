# Gson needs reflective access to model classes (used for export/import).
-keep class com.seqn.timewatcher.data.model.** { *; }
-keepclassmembers class com.seqn.timewatcher.data.model.** { <init>(...); }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
