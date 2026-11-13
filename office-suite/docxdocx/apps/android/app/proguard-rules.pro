# Keep JNA & UniFFI bindings for word-mobile-ffi
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class uniffi.word_mobile_ffi.** { *; }
-keepclassmembers class uniffi.word_mobile_ffi.** { *; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
