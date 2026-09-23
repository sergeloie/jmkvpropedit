JMkvpropedit v1.5.2

A batch GUI for mkvpropedit (part of MKVToolNix) written in Java.
It should work on Windows, Linux and other *nixes (not tested).

The Windows installer bundles a Java 21 runtime, so the application runs
without any system Java installed.

Building from source requires a JDK 21:
    gradlew build

Packaging the self-contained Windows installer (opt-in, output in build/jpackage):
    gradlew jpackage                          exe installer (needs WiX Toolset 3)
    gradlew jpackage -PjpackageType=msi       msi installer (needs WiX Toolset 3)
    gradlew jpackage -PjpackageType=app-image portable app image, no WiX required

Source and downloads at:
https://github.com/BrunoReX/jmkvpropedit
