# R8 rules for release builds (debug builds are not minified).

# Gson creates DTOs by reflection and reads field names. Keep DTO fields and
# constructors so the JSON names still match after shrinking.
-keep class com.example.jenkinslearning.data.dto.** {
    <init>(...);
    <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit, OkHttp and Gson ship their own consumer rules, so nothing else is
# needed here. Don't add broad "-keep class **" rules: they defeat shrinking.
