-dontwarn com.google.re2j.**
-dontwarn java.beans.**
-dontobfuscate

# PaddleOCR
-keep class com.equationl.paddleocr4android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}