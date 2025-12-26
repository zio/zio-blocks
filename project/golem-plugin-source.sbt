// Make the golem sbt plugin sources available to the build definition (no publishLocal required)
Compile / unmanagedSourceDirectories += baseDirectory.value / ".." / "golem" / "sbt-plugin" / "src" / "main" / "scala"

// Make shared golem tooling available to the build definition as well (Java-only).
Compile / unmanagedSourceDirectories += baseDirectory.value / ".." / "golem" / "tooling-core" / "src" / "main" / "java"
