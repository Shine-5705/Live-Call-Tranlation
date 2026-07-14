-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

-keep class com.twilio.** { *; }
-keepclassmembers class com.twilio.** { *; }
-dontwarn com.twilio.**

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers class com.gnani.livetranslation.** {
    <init>(...);
}
