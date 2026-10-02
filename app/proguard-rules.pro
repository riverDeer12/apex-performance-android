# kotlinx.serialization keeps the generated serializers it needs through its
# own consumer rules; only the models' companion lookups need to stay intact.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class software.rdd.apexperformance.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
