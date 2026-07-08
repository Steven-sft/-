# ProGuard rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep Room entities
-keep class com.drivingrecorder.data.model.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.drivingrecorder.**$$serializer { *; }
-keepclassmembers class com.drivingrecorder.** { *** Companion; }
-keepclasseswithmembers class com.drivingrecorder.** { kotlinx.serialization.KSerializer serializer(...); }
