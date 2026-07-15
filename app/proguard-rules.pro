-dontwarn androidx.media3.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations

-keepclassmembers class retrofit2.** {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

-keep class com.google.gson.** { *; }
-keep class com.google.common.util.concurrent.** { *; }

-keepclassmembers class com.steel101.musicplayer.network.** {
    <fields>;
}
-keepclassmembers class com.steel101.musicplayer.data.Song {
    <fields>;
}
-keepclassmembers class com.steel101.musicplayer.ui.MusicViewModel$LyricLine {
    <fields>;
}

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn javax.script.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
