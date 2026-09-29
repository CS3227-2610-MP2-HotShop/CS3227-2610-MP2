# HotShop

HotShop is a desktop marketplace application where you can buy and sell items,
search listings, exchange messages, make offers, and arrange meetups. One account
supports both buying and selling.

HotShop stores data locally and works offline after setup. Separate installations
do not automatically share listings or messages. No database server is needed.

## Download and run

1. Install **JDK 25** (Java Development Kit). Check that `java -version` reports
   version 25, and set `JAVA_HOME` to your JDK installation if needed.
2. Open the [HotShop repository](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2),
   select **Code > Download ZIP**, and extract the ZIP.
3. Open a terminal in the extracted project folder containing `gradlew` and
   `gradlew.bat`, then run the command for your operating system:

   **Windows (PowerShell):**

   ```powershell
   .\gradlew.bat run
   ```

   **macOS/Linux:**

   ```bash
   chmod +x gradlew
   ./gradlew run
   ```

The included Gradle 9.1.0 Wrapper downloads the required dependencies on the first
run, so an internet connection is needed for initial setup. You do not need to
install Gradle separately. A graphical desktop is required.

When HotShop opens, create an account from the login page, then log in to get
started. Your data is saved in the `.hotshop` folder in your home directory and
is kept between runs.

## Prebuilt JARs

Ready-to-run JARs for 64-bit Windows (`HotShop-windows.jar`) and Linux
(`HotShop-linux.jar`) are attached to the
[latest release](https://github.com/CS3227-2610-MP2-HotShop/CS3227-2610-MP2/releases/latest)
and kept in `release/`. They still need Java 25. See the
[User Guide](https://cs3227-2610-mp2-hotshop.github.io/CS3227-2610-MP2/docs/userGuide/GettingStarted.html)
for how to run them.

The `release/` folder also contains `HotShop-mac.jar` for Intel Java runtimes
and `HotShop-mac-aarch64.jar` for Apple Silicon Java runtimes. Both require Java 25.
These macOS JARs were packaged on Windows and still require launch verification
on their respective Mac architectures.

## Build a runnable JAR (optional)

Run `.\gradlew.bat shadowJar` on Windows or `./gradlew shadowJar` on macOS/Linux.
Then launch the generated application with:

```bash
java -jar release/HotShop.jar
```

Build the JAR on the operating system and architecture where you will run it.
It bundles JavaFX and other dependencies, but still requires Java 25.

To package macOS dependencies explicitly, use `shadowJar -PjavafxPlatform=mac`
or `shadowJar -PjavafxPlatform=mac-aarch64` with the Gradle Wrapper. These create
`release/HotShop-mac.jar` and `release/HotShop-mac-aarch64.jar`, respectively,
even when packaging on another operating system.

## Guides

To view our User Guides and Developer Guides, they can be found in our [website](https://cs3227-2610-mp2-hotshop.github.io/CS3227-2610-MP2/).
