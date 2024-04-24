# Kotlin Explorer

Kotlin Explorer is a desktop tool to quickly and easily disassemble Kotlin code into:
- Android DEX bytecode
- Android OAT assembly

After launching Kotlin Explorer, type valid Kotlin code in the left pane, then click
*Compilation > Compile & Disassemble* or use `Cmd-Shift-D` on macOS, `Ctrl-Shift-D`
on Linux and Windows.

The middle pane will show the Android DEX bytecode, and the right panel
the native assembly resulting from ahead of time compilation (AOT).

![./art/kotlin-explorer.png](./art/kotlin-explorer.png)

# Running Kotlin Explorer

Kotlin Explorer needs to be told where to find the Android SDK and the Kotlin compiler.
Unless you've set `$ANDROID_HOME` and `$KOTLIN_HOME` properly, Kotlin Explorer will ask
you to enter the path to those directories.

For `$ANDROID_HOME`, use the path to the root of the Android SDK (directory containing
`build-tools/`, `platform-tools/`, etc.). Android Studio for macOS stores this in
`$HOME/Library/Android/sdk`.

For `$KOTLIN_HOME`, use the path to the root of your
[Kotlin installation](https://kotlinlang.org/docs/command-line.html). This directory
should contain `bin/kotlinc` and `lib/kotlin-stdlib-*.jar` for instance.

Kotlin explorer also requires `java` and `javap` to be in your `$PATH`.

> [!IMPORTANT]  
> DEX bytecode and OAT assembly will only be displayed if you have an Android
> device or emulator that can be successfully reached via `adb`. The device
> must be recent enough to host the `oatdump` tool on its system image.

# License

Please see [LICENSE](./LICENSE).
