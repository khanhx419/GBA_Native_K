# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.gba.nativeemu.core.** { *; }
