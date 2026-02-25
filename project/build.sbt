import sbt.*
import sbt.Keys.*

// This is the sbt *meta-build* (the build for the build).
//
// We want the repo build to be self-contained from a clean checkout, but we also want a single source of truth for the
// publishable `zio-golem-sbt` plugin implementation. To achieve that, we compile the plugin’s canonical source file
// (and resources) from `golem/sbt` as part of this meta-build classpath.
lazy val root = (project in file("."))
  .settings(
    Compile / unmanagedSources ++= {
      val repoRoot = baseDirectory.value.getParentFile
      Seq(repoRoot / "golem" / "sbt" / "src" / "main" / "scala" / "golem" / "sbt" / "GolemPlugin.scala")
    },
    Compile / unmanagedResourceDirectories += {
      val repoRoot = baseDirectory.value.getParentFile
      repoRoot / "golem" / "sbt" / "src" / "main" / "resources"
    },
    libraryDependencies += "org.scalameta" %% "scalameta" % "4.14.5"
  )
