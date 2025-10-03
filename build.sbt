import BuildHelper.*
import MimaSettings.mimaSettings

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses     := List(
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
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("mimaChecks", "all schemaJVM/mimaReportBinaryIssues")

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
    scalaNextTests.jvm,
    scalaNextTests.js,
    scalaNextTests.native,
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
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC42" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.21"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.21"     % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    })
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.3.25" % Test
        )
    })
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.3.25" % Test
        )
    })
  )
  .nativeSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    )
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
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.21" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.21" % Test
    )
  )

lazy val scalaNextTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .dependsOn(schema)
  .settings(stdSettings("zio-blocks-scala-next-tests"))
  .settings(crossProjectSettings)
  .settings(
    crossScalaVersions       := Seq("3.7.3"),
    ThisBuild / scalaVersion := "3.7.3",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.21" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.21" % Test
    ),
    publish / skip        := true,
    mimaPreviousArtifacts := Set()
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val benchmarks = project
  .dependsOn(schema.jvm)
  .settings(stdSettings("zio-blocks-benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(
    crossScalaVersions       := Seq("3.7.3"),
    ThisBuild / scalaVersion := "3.7.3",
    libraryDependencies ++= Seq(
      "io.github.arainko"          %% "chanterelle"   % "0.1.1",
      "com.softwaremill.quicklens" %% "quicklens"     % "1.9.12",
      "dev.optics"                 %% "monocle-core"  % "3.3.0",
      "dev.optics"                 %% "monocle-macro" % "3.3.0",
      "dev.zio"                    %% "zio-test"      % "2.1.21" % Test,
      "dev.zio"                    %% "zio-test-sbt"  % "2.1.21" % Test
    ),
    assembly / assemblyJarName       := "benchmarks.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.discard
      case path                          => MergeStrategy.defaultMergeStrategy(path)
    },
    assembly / fullClasspath := (Jmh / fullClasspath).value,
    assembly / mainClass     := Some("org.openjdk.jmh.Main"),
    publish / skip           := true,
    mimaPreviousArtifacts    := Set()
  )
