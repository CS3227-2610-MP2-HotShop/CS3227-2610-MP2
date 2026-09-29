---
  layout: default.md
  title: "Getting Started"
  pageNav: 3
---

## Requirements

Install a **JDK 25** (Java Development Kit) to build and run HotShop using the
Gradle Wrapper below. A graphical desktop is required.

## Start HotShop

From the project directory on Windows:

```powershell
.\gradlew.bat run
```

On macOS/Linux, use `./gradlew run` (run `chmod +x gradlew` first if needed).
The first build downloads dependencies and requires internet access.

HotShop opens the **Log in** page. Pages and the sidebar scroll when needed;
listing grids wrap to fewer columns in narrower windows. Keyboard focus is
shown with an outline.

Startup creates or opens a local database and image folder at `.hotshop` in your
home directory (normally `%USERPROFILE%\.hotshop` on Windows, or
`$env:USERPROFILE\.hotshop` in PowerShell). Accounts, listings, offers,
conversations and messages, sales, meetups, and images in that folder survive
application restarts. Only one HotShop instance may use the same folder at a time.
Opening an older HotShop data folder upgrades it automatically without removing
existing accounts.

If startup fails, HotShop displays an error and exits without resetting existing
data. Check folder permissions and close another running HotShop instance before
retrying. Close HotShop before backing up its entire data folder, including images.

To reset all local data, close HotShop and delete its entire data folder. This
permanently removes every local account and all marketplace history and images;
back up the folder first if you want to keep them. The next launch creates an
empty database. If you selected a custom data folder, reset that folder instead.

## Run the packaged application

### Download a prebuilt JAR

Prebuilt JARs for 64-bit Windows and Linux are attached to the
[latest release](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2/releases/latest),
and are also in the repository's `release/` folder:

- `HotShop-windows.jar` for Windows
- `HotShop-linux.jar` for Linux

macOS JARs are available in the repository's `release/` folder:

- `HotShop-mac.jar` for an Intel (x86-64) Java runtime
- `HotShop-mac-aarch64.jar` for an Apple Silicon (AArch64) Java runtime

Each JAR includes JavaFX but not Java itself, so install **JDK 25** first.
Choose the JAR matching your Java runtime's architecture. The macOS packages
were built on Windows; native macOS launch verification is still required.
Start the downloaded JAR from the folder you saved it in, for example on Windows:

```powershell
java -jar HotShop-windows.jar
```

On an Apple Silicon Mac with an AArch64 JDK 25, run:

```bash
java -jar HotShop-mac-aarch64.jar
```

With an Intel JDK 25, use `java -jar HotShop-mac.jar` instead.

<box type="warning">

**Windows 11 with Smart App Control turned on may stop HotShop from starting.**
HotShop's database library unpacks a small unsigned file each time it starts,
and Smart App Control can block it. HotShop then shows **HotShop startup failed**
("Unable to open HotShop data") and exits when you close it, even though the data
folder is fine. This affects `gradlew run` too. You can see whether it is on in Windows Security, under
**App & browser control > Smart App Control**. Turning it off cannot be undone
without reinstalling Windows, so consider running HotShop on another computer
instead.

</box>

### Build the JAR yourself

Build on the operating system and architecture where the JAR will run:

```powershell
.\gradlew.bat shadowJar
java -jar release/HotShop.jar
```

The JAR includes JavaFX libraries for the build machine's platform, but does not
include Java itself. The CI artifact targets Linux.

To build a specific macOS package, run `./gradlew shadowJar -PjavafxPlatform=mac`
or `./gradlew shadowJar -PjavafxPlatform=mac-aarch64`. On Windows, replace
`./gradlew` with `.\gradlew.bat`. The output is `release/HotShop-mac.jar` or
`release/HotShop-mac-aarch64.jar`, respectively.

To use a separate data folder, put the Java system property before `-jar`, for
example on Windows:

```powershell
java "-Dhotshop.dataDir=C:\HotShop-test-data" -jar release/HotShop.jar
```

Replace the example path with your chosen folder. Passing `-Dhotshop.dataDir=...`
to `gradlew run` does not select the app's folder: the current Gradle run task
does not forward that property to the application. Use the JAR command above.

