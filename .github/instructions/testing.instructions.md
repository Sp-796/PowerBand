---
applyTo: "src/test/java/**/*.java"
description: "Map of the implemented tests and the files to open before changing or adding tests"
---
# Testing

There is no Maven/Gradle build descriptor, testing spec, or persistent automated test source in the repository yet. The Java sources have been validated with `javac --release 8`; add a build descriptor and JUnit 5 tests when the project defines its build setup.

When tests are added, use JUnit 5, mirror the package of the class under test in `src/test/java/`, and name test classes `<ClassName>Test.java`.
