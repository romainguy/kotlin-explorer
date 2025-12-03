-dontoptimize

-dontwarn androidx.compose.desktop.DesktopTheme*
-dontwarn kotlinx.datetime.**

-keep class dev.romainguy.kotlin.explorer.code.*TokenMarker { *; }
-dontnote dev.romainguy.kotlin.explorer.code.*TokenMarker

-keep class org.fife.** { *; }
-dontnote org.fife.**

-keep class sun.misc.Unsafe { *; }
-dontnote sun.misc.Unsafe

-keep class com.jetbrains.JBR* { *; }
-dontnote com.jetbrains.JBR*

-keep class com.sun.jna** { *; }
-dontnote com.sun.jna**

-keep class org.jsoup** { *; }
-dontnote org.jsoup**

-keep class androidx.compose.ui.input.key.KeyEvent_desktopKt { *; }
-dontnote androidx.compose.ui.input.key.KeyEvent_desktopKt

-keep class androidx.compose.ui.input.key.KeyEvent_skikoKt { *; }
-dontnote androidx.compose.ui.input.key.KeyEvent_skikoKt
-dontwarn androidx.compose.ui.input.key.KeyEvent_skikoKt

-dontnote org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.**
-dontwarn org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.**

-keep class org.jetbrains.jewel.ui** { *; }
-dontnote org.jetbrains.jewel.ui**

-dontnote org.jetbrains.jewel.foundation.lazy.**
-dontwarn org.jetbrains.jewel.foundation.lazy.**

-dontnote org.jetbrains.jewel.foundation.util.**
-dontwarn org.jetbrains.jewel.foundation.util.**

-dontnote org.jetbrains.jewel.window.utils.**
-dontwarn org.jetbrains.jewel.window.utils.**

-dontnote org.jetbrains.jewel.ui.component.SpinnerProgressIconGenerator
-dontwarn org.jetbrains.jewel.ui.component.SpinnerProgressIconGenerator
