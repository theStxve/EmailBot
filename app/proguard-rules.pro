# Only used if isMinifyEnabled is switched on for release builds.

# JavaMail / activation use reflection and service files.
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn com.sun.mail.**

# kotlinx.serialization: keep generated serializers of our @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.example.emailbot.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.example.emailbot.**$$serializer { *; }
