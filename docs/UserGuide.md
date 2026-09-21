# HotShop User Guide

## Requirements

Install Java 25. A graphical desktop is required.

## Start HotShop

From the project directory on Windows:

```powershell
.\gradlew.bat run
```

On macOS/Linux, use `./gradlew run` (run `chmod +x gradlew` first if needed).
The first build downloads dependencies and requires internet access.

An 800 by 600 window titled **HotShop** displays **Welcome to HotShop**.
Resize the window as desired and close it with the operating system's close button.

## Run the packaged application

Build on the operating system and architecture where the JAR will run:

```powershell
.\gradlew.bat shadowJar
java -jar release/HotShop.jar
```

The JAR includes JavaFX libraries for the build machine's platform, but does not
include Java itself. The CI artifact targets Linux.

## Current scope

This starter has a welcome screen only. Accounts, authentication, listings,
and buyer/seller workflows are not available.
