# Paula

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. Everything happens in the terminal: picocli drives the command
line and JLine draws a full-screen, colour player view. The build produces a
native executable with GraalVM, so there is no JVM to start and no jar to
carry around.

Tracker modules are decoded and mixed by [JavaMod](https://github.com/quippy-git/javamod),
Daniel Becker's pure-Java player, which covers ProTracker, NoiseTracker,
FastTracker II, Scream Tracker, Impulse Tracker, Farandole and MultiTracker
files among others. Its jar is vendored under `lib/` as a small Maven
repository because no current release is published to Maven Central.

## Building

```
mvn package
```

produces `target/paula`, a native executable. The build needs Maven and a
GraalVM for JDK 21 or newer registered in `~/.m2/toolchains.xml` with the
vendor `graalvm`, for example:

```xml
<toolchain>
    <type>jdk</type>
    <provides>
        <version>22</version>
        <vendor>graalvm</vendor>
    </provides>
    <configuration>
        <jdkHome>/home/you/graalvm/graalvm-jdk-22.0.2+9.1/</jdkHome>
    </configuration>
</toolchain>
```

Compilation, tests and `native-image` all run on that toolchain, so
`JAVA_HOME` and `GRAALVM_HOME` do not matter. `mvn test` runs the unit tests
without building the native image.

## Usage

```
paula song.mod another.mod   play the files in order
paula info song.mod          print metadata
paula formats                list supported formats
paula --help
```

Keys while playing:

| Key     | Action                  |
|---------|-------------------------|
| `space` | pause / resume          |
| `←` `→` | seek five seconds       |
| `n`     | next track              |
| `p`     | previous track          |
| `q`     | quit                    |

### Audio output

The native executable streams raw PCM into a system audio command, chosen
automatically: `pacat` (PulseAudio or PipeWire) first, then `aplay` (ALSA).
Pick one explicitly with `--output pulse` or `--output alsa`. When Paula runs
on a JVM, for example from the tests, Java Sound is used instead
(`--output javasound`).

Log output goes to `paula.log` in the working directory so it never
disturbs the player screen.

## Layout

| Package                          | Responsibility                                              |
|----------------------------------|-------------------------------------------------------------|
| `com.adeptum.paula`              | `Paula`, the root picocli command and entry point           |
| `com.adeptum.paula.cli`          | `info` and `formats` subcommands, version provider          |
| `com.adeptum.paula.module`       | module model, loader interface and loader registry          |
| `com.adeptum.paula.module.javamod` | loads tracker modules through JavaMod                     |
| `com.adeptum.paula.audio`        | `AudioSink`, the audio backends and PCM encoding            |
| `com.adeptum.paula.playback`     | `Renderer`, `PlaybackEngine` and the interactive session    |
| `com.adeptum.paula.playback.javamod` | pulls mixed audio from JavaMod into the pipeline        |
| `com.adeptum.paula.playlist`     | playlist navigation                                         |
| `com.adeptum.paula.ui`           | screen rendering, key mapping, theme and JLine terminal     |

Adding a format means implementing `ModuleLoader`, returning a `Module`
whose `createRenderer` produces the audio, and registering the loader in
`ModuleLoaderRegistry`.

Resources the native image must carry are listed in
`src/main/resources/META-INF/native-image/com.adeptum/paula/resource-config.json`.

## License

Copyright © 2026 Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE). JavaMod is copyright Daniel
Becker and licensed under the GNU General Public License, version 3.
