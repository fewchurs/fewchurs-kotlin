# Fewchurs SDK — rules applied automatically to host apps.
# Serialized DTOs are internal; keep their generated serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.fewchurs.sdk.internal.network.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fewchurs.sdk.internal.network.**$$serializer { *; }
