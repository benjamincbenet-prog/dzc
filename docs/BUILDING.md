# Building

## Project
Open the `dzc` directory as the Gradle project.

## CodeOnTheGo compatibility
The project was maintained for the user's CodeOnTheGo constraints used during development: AGP 8.11.0, Kotlin 1.9.22, Java 17, and SDK 36.

## Typical build
Use CodeOnTheGo's Android build action for the Debug variant. If resource compilation fails, check the first XML error because later Gradle failures can be cascading failures.

## Permissions/runtime testing
Test Bluetooth permissions on Android 12+ and Health Connect availability/permissions on the target device. Verify both manual and auto-sync modes.
