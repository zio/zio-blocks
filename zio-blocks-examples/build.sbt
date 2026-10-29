ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "3.9.0"

val zioBlocksVersion = "0.0.56"

// For Scala 3.10+, artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 10 => "zio-blocks-next"
    case _                               => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

lazy val commonSettings = Seq(
  publish / skip := true,
  run / fork     := true,
  scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked"),
  // Scala 3.8+ artifacts are published with `@experimental` APIs, which consumers must opt into.
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, minor)) if minor >= 8 => Seq("-experimental")
      case _                              => Seq.empty
    }
  }
)

lazy val root = (project in file("."))
  .aggregate(effectIntegration, gettingStarted)
  .settings(
    commonSettings,
    name := "zio-blocks-examples",
    libraryDependencies ++= Seq(
      zioBlocks("ringbuffer").value
    )
  )

// Examples for "How to Use ZIO Blocks in a ZIO or cats-effect Application": the one build here that
// depends on ZIO and cats-effect at once. Run with: sbt "effectIntegration/runMain effectintegration.<Object>"
lazy val effectIntegration = (project in file("effect-integration"))
  .settings(
    commonSettings,
    name := "effect-integration",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("sql").value,
      zioBlocks("streams").value,
      zioBlocks("async").value,
      zioBlocks("scope").value,
      "org.xerial"     % "sqlite-jdbc" % "3.53.4.0",
      "dev.zio"       %% "zio"         % "2.1.27",
      "dev.zio"       %% "zio-streams" % "2.1.27",
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "co.fs2"        %% "fs2-core"    % "3.14.0"
    )
  )

// The program of "Your First Five Minutes" (docs/getting-started/first-five-minutes.md): Schema, Scope and Chunk together.
// Run with: sbt "gettingStarted/runMain gettingstarted.FirstFiveMinutes"
lazy val gettingStarted = (project in file("getting-started"))
  .settings(
    commonSettings,
    name := "getting-started",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("scope").value
    )
  )
