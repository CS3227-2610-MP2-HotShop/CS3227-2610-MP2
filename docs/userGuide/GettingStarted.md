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

Build on the operating system and architecture where the JAR will run:

```powershell
.\gradlew.bat shadowJar
java -jar release/HotShop.jar
```

The JAR includes JavaFX libraries for the build machine's platform, but does not
include Java itself. The CI artifact targets Linux.

To use a separate data folder, put the Java system property before `-jar`, for
example on Windows:

```powershell
java "-Dhotshop.dataDir=C:\HotShop-test-data" -jar release/HotShop.jar
```

Replace the example path with your chosen folder. Passing `-Dhotshop.dataDir=...`
to `gradlew run` does not select the app's folder: the current Gradle run task
does not forward that property to the application. Use the JAR command above.

