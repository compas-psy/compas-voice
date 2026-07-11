# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable model classes and their generated serializers
-keep,includedescriptorclasses class ru.cmpas.voice.**$$serializer { *; }
-keepclassmembers class ru.cmpas.voice.** {
    *** Companion;
}
-keepclasseswithmembers class ru.cmpas.voice.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Media3 ---
# Media3 ships its own consumer rules; nothing extra usually required.
