# JMkvpropedit

A batch GUI for [mkvpropedit](https://mkvtoolnix.download/docs/mkvpropedit.html) (part of [MKVToolNix](https://mkvtoolnix.download/)), written in Java (Swing).

Apply the same metadata edits — titles, track flags, languages, chapters, tags, attachments — to many Matroska files at once, without typing long command lines by hand.

- **Version:** 1.5.2 (single source of truth: [`gradle.properties`](gradle.properties))
- **Toolchain:** Java 21, Gradle 9
- **License:** BSD-style, see [license.txt](license.txt) (© 2012–2021 Bruno Barbieri)
- **Upstream:** https://github.com/BrunoReX/jmkvpropedit

---

## Features

The main window is a tabbed interface. You configure the edits once, then run them over a whole file list.

### Input

Build the batch file list:

- **Add files** — pick individual Matroska files (`.mkv`, `.mka`, `.mk3d`, `.webm`, `.mks`; case-insensitive).
- **Add folder** — recursive scan; only Matroska extensions are picked up.
- **Reorder / remove / clear** — move selection to top, up, down, bottom; remove or clear the list.
- Drag-and-drop of files onto the text fields where supported (skipped automatically in headless/test environments).

### General

Edits that apply to every file in the batch:

| Setting | What it does |
|--------|----------------|
| **Title** | Set the segment title; supports `{num}` (with start/padding numbering) and `{file_name}` placeholders. |
| **Numbering** | When enabled, `{num}` expands per file (`Start` + `Padding`). |
| **Chapters** | Load chapters from a file (XML/OGM-style sources via the chapter format selector). |
| **Tags** | Load tags from a file (XML). |
| **Extra parameters** | Free-form extra `mkvpropedit` arguments for the general section. |

### Video / Audio / Subtitles

One shared track panel per media type (unified component; the three tabs differ only in labels and the mkvpropedit selector `v` / `a` / `s`):

- **Track list** — combo of tracks for the current type; **Add** / **Remove** tracks (remove is disabled while only one track remains; Opt-mode gets the same `{num}` numbering as the plain command).
- Per-track settings (each with an **Edit this track** switch):
  - Enable / disable track
  - Default track, Forced flags
  - Track name (with numbering placeholders)
  - Language (ISO code combo backed by the bundled language list)
  - Extra parameters
- **Opt variant** — a second command line using mkvpropedit’s `--edit track:@<num>`-style numbering so batch runs can address tracks by index instead of a fixed selector.

### Attachments

Three operations in one panel (unified table + controls):

| Sub-tab | Purpose |
|---------|---------|
| **Add Attachments** | Attach a file with Name / Description / MIME type (MIME list is bundled; `_` placeholder entry is filtered out). |
| **Replace Attachments** | Match an existing attachment by name, ID, or MIME type and replace its data / metadata. |
| **Delete Attachments** | Remove an attachment matched by type + value. |

### Options

- Path to the **`mkvpropedit` executable** (or **Use default** — resolves `mkvpropedit` from `PATH`).
- Persisted in `JMkvpropedit.ini` next to the working directory (created on first save).

### Output / run

| Button | Effect |
|--------|--------|
| **Generate command line** | Preview the `mkvpropedit` command(s) that would run for the current settings. |
| **Process files** | Run the batch: for each file, write an options JSON payload, invoke `mkvpropedit`, stream UTF-8 output into the **Output** tab. The UI stays responsive (SwingWorker + EDT-safe logging). |

Output tab shows the live log of every invocation (success/errors), suitable for copy-paste debugging.

---

## Requirements

| To… | You need |
|-----|----------|
| **Run the Windows installer / app-image** | Nothing else — Java 21 runtime is bundled. |
| **Run from a jar / distZip** | JDK/JRE **21+** on `PATH` (or via `JAVA_HOME`). |
| **Build from source** | **JDK 21** (Gradle wrapper downloads the rest). |
| **Build `.exe` / `.msi` installer** | JDK 21 + **[WiX Toolset 3](https://wixtoolset.org/)** on `PATH` (Windows only). |
| **Actually edit MKV files** | [MKVToolNix](https://mkvtoolnix.download/) (`mkvpropedit`) installed, path set in **Options**. |

---

## Building

### 1. Compile + test (CI equivalent)

```bash
./gradlew build          # Linux/macOS
.\gradlew.bat build      # Windows
```

- Compiles main + tests, runs the JUnit 5 suite.
- Green build is what GitHub Actions enforces on every push/PR (`.github/workflows/ci.yml`).

### 2. Runnable jar + distribution (no bundled JRE)

```bash
.\gradlew.bat installDist
```

Produces a self-contained app layout:

```
build/install/jmkvpropedit/
├── bin/
│   ├── jmkvpropedit        # shell script
│   └── jmkvpropedit.bat    # Windows script
├── lib/
│   ├── jmkvpropedit-1.5.2.jar
│   └── ini4j-0.5.4.jar
└── ...
```

Run it:

```bash
build\install\jmkvpropedit\bin\jmkvpropedit.bat
```

Plain jars also land in `build/libs/jmkvpropedit-1.5.2.jar` (classes only — you still need `ini4j` on the classpath if you launch with bare `java -jar`; prefer `installDist` or the scripts above).

Zip/tar archives of the same layout:

```bash
.\gradlew.bat distZip      # → build/distributions/jmkvpropedit-1.5.2.zip
```

### 3. Self-contained Windows app / installer (`jpackage`)

Uses the JDK 21 `jpackage` tool. The task is **opt-in** — it is not part of `build`, so plain CI never needs WiX.

```bash
# Portable app image: jmkvpropedit.exe + bundled Java 21 runtime (no WiX required)
.\gradlew.bat jpackage -PjpackageType=app-image

# Windows .exe installer (requires WiX Toolset 3)
.\gradlew.bat jpackage

# Windows .msi installer (requires WiX Toolset 3)
.\gradlew.bat jpackage -PjpackageType=msi
```

Output directory: **`build/jpackage/`**.

| Type | Typical output | Needs WiX | Notes |
|------|----------------|-----------|--------|
| `app-image` | `build/jpackage/jmkvpropedit/jmkvpropedit.exe` + `runtime/` | No | Unpack-and-run; ~150+ MB because of the bundled JRE. |
| `exe` (default) | `build/jpackage/jmkvpropedit-1.5.2.exe` | **Yes** | Installer with Start Menu shortcut and directory chooser. |
| `msi` | `build/jpackage/jmkvpropedit-1.5.2.msi` | **Yes** | Same as exe, MSI package. |

The installer/app-image **bundles a Java 21 runtime**, so end users do not need Java installed.

> **Note:** `jpackage` refuses to overwrite an existing image — the Gradle task cleans `build/jpackage/` before each run.

---

## Project layout

```
├── build.gradle.kts          # application plugin, JUnit 5, jpackage task
├── settings.gradle.kts       # pluginManagement + dependencyResolutionManagement
├── gradle.properties         # version=1.5.2, configuration cache
├── .github/workflows/ci.yml  # gradlew build on push/PR (Ubuntu, Temurin 21)
├── src/
│   ├── main/java/.../        # application sources
│   ├── main/resources/       # icons, langcodes.txt, langnames.txt, mimetypes.txt
│   ├── test/java/.../        # JUnit 5 unit tests
│   └── testHarness/java/.../ # framework-free manual check programs (outside Gradle test task)
├── JMkvpropedit.ini          # created at runtime (mkvpropedit path)
└── readme.txt                # short plain-text blurb
```

### Main modules (post-rework)

| Class | Responsibility |
|-------|----------------|
| `JMkvpropedit` | UI shell: window, tabs, wiring only. |
| `CommandBuilder` | Pure settings → `mkvpropedit` argument lists (plain + Opt). |
| `TrackPanel` / `TrackSlot` / `TrackType` | Unified video/audio/subtitle track UI. |
| `AttachmentPanel` (+ model/operation/selector) | Unified add/replace/delete attachment UI. |
| `ProcessRunner` | Spawn `mkvpropedit`, stream UTF-8 output, options-file lifecycle. |
| `IniStore` | Read/write `JMkvpropedit.ini` (ini4j). |
| `FileScanner` | Recursive Matroska folder scan (`Files.walk` + glob). |
| `FileDrop` | Optional drag-and-drop onto text fields (no-op when headless). |
| `Utils` | Context menus and small Swing helpers. |

---

## Tests

```bash
.\gradlew.bat test          # JUnit 5 (src/test)
.\gradlew.bat build         # compile + test + assemble
```

Manual / structural check programs live under `src/testHarness/` and are run individually (they are not wired into the `test` task). They cover track management, file filters, options JSON, Swing threading, repo hygiene, and packaging invariants.

---

## Typical workflow

1. Start the app (installer, app-image, or `installDist` script).
2. **Options** → set the `mkvpropedit` executable (or enable *Use default*).
3. **Input** → add files or a folder.
4. **General / Video / Audio / Subtitles / Attachments** → configure the edits.
5. **Generate command line** → sanity-check the command.
6. **Process files** → run the batch; watch **Output**.

---

## CI

Every push and pull request runs `./gradlew build` on Ubuntu with Temurin 21 (see [`.github/workflows/ci.yml`](.github/workflows/ci.yml)). The workflow also sets the executable bit on `gradlew` so the Linux runner can invoke it.

---

## License

BSD 2-Clause style — see [license.txt](license.txt).
