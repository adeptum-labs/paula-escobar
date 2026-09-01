# Paula

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. Everything happens in the terminal: picocli drives the command
line and JLine draws a full-screen, colour player view.

This is the skeleton of the player. The command line, the terminal UI, the
playback pipeline and the module-loading seams are in place. The only loader
so far parses ProTracker headers, and the only renderer produces silence.

## Building

```
mvn package
```

produces `target/paula.jar`, an executable jar with all dependencies.
Java 17 or newer is required.

## Usage

```
java -jar target/paula.jar song.mod another.mod   play the files in order
java -jar target/paula.jar info song.mod          print metadata
java -jar target/paula.jar formats                list supported formats
java -jar target/paula.jar --help
```

Keys while playing:

| Key     | Action         |
|---------|----------------|
| `space` | pause / resume |
| `n`     | next track     |
| `p`     | previous track |
| `q`     | quit           |

Log output goes to `paula.log` in the working directory so it never
disturbs the player screen.

## Layout

| Package                          | Responsibility                                              |
|----------------------------------|-------------------------------------------------------------|
| `com.adeptum.paula`              | `Paula`, the root picocli command and entry point           |
| `com.adeptum.paula.cli`          | `info` and `formats` subcommands, version provider          |
| `com.adeptum.paula.module`       | module model, loader interface and loader registry          |
| `com.adeptum.paula.module.protracker` | ProTracker / NoiseTracker header parser                |
| `com.adeptum.paula.audio`        | `AudioSink` and the Java Sound implementation               |
| `com.adeptum.paula.playback`     | `Renderer`, `PlaybackEngine` and the interactive session    |
| `com.adeptum.paula.playlist`     | playlist navigation                                         |
| `com.adeptum.paula.ui`           | screen rendering, key mapping, theme and JLine terminal     |

Adding a format means implementing `ModuleLoader` and registering it in
`ModuleLoaderRegistry`. Adding sound means implementing `Renderer` and
returning it from the `RendererFactory` used by `Paula`.

## License

Copyright © 2026 Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE).
