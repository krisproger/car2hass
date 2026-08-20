# Dynamic resource lookup via getIdentifier("sensor_" + key / "enum_" + id / etc.).
# R8 must keep the R class resource table entries; the string resources themselves
# are never shrunk because isShrinkResources is false, but the R class fields must
# not be removed as "unused" since they are looked up by name at runtime.
-keepclassmembers class **.R$string {
    public static int *;
}
-keepclassmembers class **.R$raw {
    public static int *;
}

# osmdroid uses reflection (MapView inflation and module loading) and its own
# logging/tile managers. Keep the whole library to avoid runtime class lookups
# failing on the obfuscated jar.
-keep class org.osmdroid.** { *; }

# JSON-serialized model classes: org.json reflects over fields/methods, so keep
# their members (getters/builders called by name would break otherwise).
-keep class com.diplustohass.GeofenceZone { *; }
-keep class com.diplustohass.DashboardTile { *; }
-keep class com.diplustohass.rules.Rule { *; }
-keep class com.diplustohass.rules.RuleCondition { *; }
-keep class com.diplustohass.rules.RuleAction { *; }
