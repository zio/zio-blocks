import BuildHelper.*
import MimaSettings.mimaSettings

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    name         := "ZIO Blocks",
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
  .settings(publish / skip := true)
  .aggregate(
    macros.jvm,
    macros.js,
    macros.native,
    schema.jvm,
    schema.js,
    schema.native,
    `schema-avro`,
    streams.jvm,
    streams.js,
    streams.native,
    scalaNextTests.jvm,
    scalaNextTests.js,
    scalaNextTests.native,
    benchmarks,
    docs
  )

// --- MACRO PROJECT (Now Cross-Project) ---
lazy val macros = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("macros"))
  .settings(
    stdSettings("zio-blocks-macros"),
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-optics" % "0.2.2"
    ),
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq("-Ymacro-annotations")
        case Some((3, _))  => Seq("-experimental")
        case _             => Seq()
      }
    }
  )

lazy val schema = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .settings(stdSettings("zio-blocks-schema"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .dependsOn(macros) // <--- Now compatible!
  .settings(
    compileOrder := CompileOrder.JavaThenScala,

    // Flags for usage
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq("-Ymacro-annotations")
        case Some((3, _))  => Seq("-experimental")
        case _             => Seq()
      }
    },
    Test / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 13)) => Seq("-Ymacro-annotations")
        case Some((3, _))  => Seq("-experimental")
        case _             => Seq()
      }
    },
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC44" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.23"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.23"     % Test,
      "dev.zio" %%% "zio-optics"   % "0.2.2"
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ =>
        Seq()
    })
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq()
      case _            =>
        Seq("io.github.kitlangton" %%% "neotype" % "0.3.37" % Test)
    })
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq()
      case _            =>
        Seq("io.github.kitlangton" %%% "neotype" % "0.3.37" % Test)
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
  .settings(buildInfoSettings("zio.blocks.streams"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.23" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.23" % Test
    )
  )

lazy val `schema-avro` = project
  .settings(stdSettings("zio-blocks-schema-avro"))
  .dependsOn(schema.jvm)
  .settings(buildInfoSettings("zio.blocks.schema.avro"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.avro" % "avro"         % "1.12.1",
      "dev.zio"        %% "zio-test"     % "2.1.23" % Test,
      "dev.zio"        %% "zio-test-sbt" % "2.1.23" % Test
    )
  )

lazy val scalaNextTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-scala-next-tests", Seq("3.7.4")))
  .dependsOn(schema)
  .settings(crossProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.23" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.23" % Test
    ),
    Test / scalacOptions ++= Seq("-experimental"),
    publish / skip        := true,
    mimaPreviousArtifacts := Set()
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val benchmarks = project
  .settings(stdSettings("zio-blocks-benchmarks", Seq("3.7.4")))
  .dependsOn(schema.jvm)
  .dependsOn(`schema-avro`)
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.6",
      "com.sksamuel.avro4s"                   %% "avro4s-core"           % "5.0.14",
      "dev.zio"                               %% "zio-json"              % "0.7.45",
      "dev.zio"                               %% "zio-schema-avro"       % "1.7.5",
      "dev.zio"                               %% "zio-schema-json"       % "1.7.5",
      "io.github.arainko"                     %% "chanterelle"           % "0.1.1",
      "com.softwaremill.quicklens"            %% "quicklens"             % "1.9.12",
      "dev.optics"                            %% "monocle-core"          % "3.3.0",
      "dev.optics"                            %% "monocle-macro"         % "3.3.0",
      "dev.zio"                               %% "zio-test"              % "2.1.23",
      "dev.zio"                               %% "zio-test-sbt"          % "2.1.23" % Test
    ),
    assembly / assemblyJarName       := "benchmarks.jar",
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case path                                 => MergeStrategy.defaultMergeStrategy(path)
    },
    assembly / fullClasspath := (Jmh / fullClasspath).value,
    assembly / mainClass     := Some("org.openjdk.jmh.Main"),
    publish / skip           := true,
    mimaPreviousArtifacts    := Set()
  )

lazy val docs = project
  .in(file("zio-blocks-docs"))
  .settings(
    moduleName := "zio-blocks-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := (ThisBuild / name).value,
    mainModuleName                             := (schema.jvm / moduleName).value,
    projectStage                               := ProjectStage.Development,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(schema.jvm),
    publish / skip                             := true
  )
  .dependsOn(schema.jvm)
  .enablePlugins(WebsitePlugin)
