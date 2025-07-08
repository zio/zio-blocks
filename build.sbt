import BuildHelper.*
import MimaSettings.mimaSettings

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses := List(
      "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("https://degoes.net")
      )
    )
  )
)

addCommandAlias("build", "; fmt; coverage; root/test; coverageReport")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "check",
  "; scalafmtSbtCheck; scalafmtCheckAll"
)

addCommandAlias(
  "mimaChecks",
  "all schemaJVM/mimaReportBinaryIssues"
)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(
    schema.jvm,
    schema.js,
    schema.native,
    streams.jvm,
    streams.js,
    streams.native,
    benchmarks
  )

lazy val schema = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-schema"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    compileOrder := CompileOrder.JavaThenScala,
    scalacOptions ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq(
            "-opt:l:method"
          )
        case _ =>
          Seq(
            "-explain",
            "-explain-cyclic",
            "-Xcheck-macros"
          )
      }),
    libraryDependencies ++= Seq(
      "dev.zio"  %% "zio-prelude"  % "1.0.0-RC41" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.19"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.19"     % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ => Seq()
    }),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-streams"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    scalacOptions ++=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq(
            "-opt:l:method"
          )
        case _ =>
          Seq(
            "-explain",
            "-explain-cyclic"
          )
      }),
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.19" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.19" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val benchmarks = project
  .dependsOn(schema.jvm)
  .settings(stdSettings("zio-blocks-benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(
    crossScalaVersions := Seq(Scala213, "3.7.1"),
    publish / skip     := true,
    libraryDependencies ++= {
      Seq(
        "com.softwaremill.quicklens" %% "quicklens"     % "1.9.12",
        "dev.optics"                 %% "monocle-core"  % "3.3.0",
        "dev.optics"                 %% "monocle-macro" % "3.3.0",
        "dev.zio"                   %%% "zio-test"      % "2.1.19" % Test,
        "dev.zio"                   %%% "zio-test-sbt"  % "2.1.19" % Test
      )
    },
    assembly / assemblyJarName := "benchmarks.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.discard
      case path                          => MergeStrategy.defaultMergeStrategy(path)
    },
    assembly / fullClasspath := (Jmh / fullClasspath).value,
    assembly / mainClass     := Some("org.openjdk.jmh.Main")
  )
