# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class de.timitrain.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class de.timitrain.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
