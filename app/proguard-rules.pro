# GSYNK — правила R8 (минификация выключена в v1.0, файл на будущее)

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.gsynk.app.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.gsynk.app.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
