-dontwarn androidx.media3.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep class com.steel101.musicplayer.network.** { *; }
-keep interface com.steel101.musicplayer.network.** { *; }

-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

-keep class com.google.gson.** { *; }
-keep class com.google.common.util.concurrent.** { *; }

-keep class com.steel101.musicplayer.data.Song { *; }
-keep class com.steel101.musicplayer.ui.MusicViewModel$LyricLine { *; }

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.jsoup.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn javax.script.**

-keep class com.kyant.taglib.** { *; }
-keepclassmembers class com.kyant.taglib.** {
    native <methods>;
}

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
