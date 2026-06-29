# Gson needs reflective access to model classes (used for export/import).
-keep class com.seqn.timewatcher.data.model.** { *; }
-keepclassmembers class com.seqn.timewatcher.data.model.** { <init>(...); }

# Preserve generic signatures + annotations that Gson/Room/Compose rely on.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Compile-time-only annotation deps (errorprone, via test deps) — silence R8 missing-class warnings.
-dontwarn javax.lang.model.**
