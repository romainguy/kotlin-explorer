# Kotlin Explorer
![image](art/app-icon/icon.iconset/icon_256x256.png)

Kotlin Explorer is a desktop tool to quickly and easily disassemble Kotlin code into:
- Java bytecode
- Android DEX bytecode
- Android OAT assembly

After launching Kotlin Explorer, type valid Kotlin code in the left pane, then click
*Build > Build & Disassemble* or use `Cmd-Shift-D` on macOS, `Ctrl-Shift-D`
on Linux and Windows.

By default, the middle pane will show the Android DEX bytecode, and the right panel
the native assembly resulting from ahead of time compilation (AOT). You can control
which panels are visible using the *View* menu.

![./art/kotlin-explorer.png](./art/kotlin-explorer.png)

# Features

- *Build > Optimize with R8*: turn on R8 optimizations. Turning this on will affect the
  ability to see corresponding source line numbers in the byte code and DEX outputs.
- *View > Sync Lines*: synchronize the current line in the source, byte code, and DEX
  panels. This feature may require R8 optimizations to be turned off to work properly.
- *View > Presentation Mode*: increase the font size to make the content more visible
  when projected.
- *Build > Build on Startup*: to automatically launch a compilation when launching the
  app.
- *Build > Run*: compile the Kotlin source code and run it locally. Any output is sent
  to the logs panel.
- Clicking a jump instruction will show an arrow to the jump destination.
- Shows the number of instructions and branches per method.
- Click a disassembled instruction or register to highlight all occurrences.

# Kotlin Explorer and R8

You can use *Build > Optimize with R8* to optimize the compiled code with the R8 tool.
By default, all public classes/members/etc. will be kept, allowing you to analyze them
in the disassembly panels.

However, keeping everything is not representative of what R8 will do on an actual
application so you can disable that feature, and instead use the `@Keep` annotation
to choose an entry point leading to the desired disassembly. You can also create a
`fun main()` entry point and call your code from there. Be careful to not use constants
when calling other methods as this may lead to aggressive optimization by R8.

# Running Kotlin Explorer

Run Kotlin Explorer with `./gradlew jvmRun`.

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
