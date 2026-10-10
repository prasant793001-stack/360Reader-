# LibreOfficeKit is called from native code through JNI; keep every class and member.
-keep class org.libreoffice.kit.** { *; }
-keepclassmembers class org.libreoffice.kit.** { native <methods>; }
