# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Rules for builds with R8 switched on (the "optimized" build type) ---

# Firestore fills these classes by reflection (DocumentSnapshot.toObject): it matches document fields to
# getters and backing fields by name, needs the no-argument constructor, and reads the Firestore
# annotations and the generic types of List/Map properties.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.example.data.model.** { *; }

# Home-screen widget: with R8 on, Glance never started its update sessions (provideGlance was never
# called and the widget stayed on the white loading spinner). Keep Glance and the WorkManager it runs on
# as they are.
-keep class androidx.glance.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.glance.**

# Home-screen widget: Glance creates the classes behind actionRunCallback<...>() by name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Sign in with Google through Credential Manager: the Play services provider is looked up by name
# (rule from the Credential Manager documentation).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
