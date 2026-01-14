import BuildHelper.*
import MimaSettings.mimaSettings
import org.scalajs.linker.interface.ModuleKind

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

Global / excludeLintKeys ++= Set(
  zioGolemExamplesJS / testFrameworks,
  zioGolemQuickstartJS / testFrameworks
)

addCommandAlias("build", "; fmt; coverage; root/test; coverageReport")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("mimaChecks", "all schemaJVM/mimaReportBinaryIssues")
addCommandAlias(
  "golemPublishLocal",
  """set ThisBuild / version := "0.0.0-SNAPSHOT"; schemaJVM/publishLocal; schemaJS/publishLocal; zioGolemModelJVM/publishLocal; zioGolemModelJS/publishLocal; zioGolemMacros/publishLocal; zioGolemCoreJS/publishLocal; zioGolemCoreJVM/publishLocal"""
)
addCommandAlias(
  "testJVM",
  "+schemaJVM/test; +chunkJVM/test; +streamsJVM/test; +schema-avro/test; " +
    "+zioGolemModelJVM/test; +zioGolemCoreJVM/test; +zioGolemMacros/test; +zioGolemTools/test; " +
    "benchmarks/test; examples/test"
)
addCommandAlias(
  "testJS",
  "+schemaJS/test; +chunkJS/test; +streamsJS/test; +zioGolemModelJS/test; +zioGolemCoreJS/test"
)
addCommandAlias(
  "testNative",
  "+schemaNative/test; +chunkNative/test; +streamsNative/test"
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
    `schema-avro`,
    streams.jvm,
    streams.js,
    streams.native,
    chunk.jvm,
    chunk.js,
    chunk.native,
    scalaNextTests.jvm,
    scalaNextTests.js,
    scalaNextTests.native,
    benchmarks,
    zioGolemModel.jvm,
    zioGolemModel.js,
    zioGolemCore.jvm,
    zioGolemCore.js,
    zioGolemMacros,
    zioGolemTools,
    zioGolemExamples.jvm,
    zioGolemExamples.js,
    zioGolemQuickstart.js,
    zioGolemQuickstart.jvm,
    zioGolemSbt,
    examples
  )

lazy val schema = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-schema"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    compileOrder := CompileOrder.JavaThenScala,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC45" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.24"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24"     % Test
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
          "io.github.kitlangton" %%% "neotype" % "0.4.10" % Test
        )
    })
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"            % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.4.10" % Test
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
  .settings(buildInfoSettings("zio.blocks.streams"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    )
  )

lazy val chunk = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-chunk"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.chunk"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
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
      "dev.zio"        %% "zio-test"     % "2.1.24" % Test,
      "dev.zio"        %% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %% "neotype" % "0.4.10" % Test
        )
    })
  )

lazy val scalaNextTests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-scala-next-tests", Seq("3.7.4")))
  .dependsOn(schema)
  .settings(crossProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    publish / skip        := true,
    mimaPreviousArtifacts := Set()
  )
  .jsSettings(jsSettings)
  .nativeSettings(nativeSettings)

lazy val examples = project
  .settings(stdSettings("zio-blocks-examples"))
  .dependsOn(schema.jvm)
  .dependsOn(streams.jvm)
  .dependsOn(`schema-avro`)
  .settings(
    publish / skip := true
  )

lazy val benchmarks = project
  .settings(stdSettings("zio-blocks-benchmarks", Seq("3.7.4")))
  .dependsOn(schema.jvm)
  .dependsOn(chunk.jvm)
  .dependsOn(`schema-avro`)
  .enablePlugins(JmhPlugin)
  .settings(
    scalaVersion       := "3.3.7",
    crossScalaVersions := Seq("3.3.7"),
    Compile / skip     := true,
    Test / skip        := true,
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.8",
      "com.sksamuel.avro4s"                   %% "avro4s-core"           % "5.0.14",
      "dev.zio"                               %% "zio-json"              % "0.7.45",
      "dev.zio"                               %% "zio-schema-avro"       % "1.7.5",
      "dev.zio"                               %% "zio-schema-json"       % "1.7.5",
      "io.github.arainko"                     %% "chanterelle"           % "0.1.2",
      "com.softwaremill.quicklens"            %% "quicklens"             % "1.9.12",
      "dev.optics"                            %% "monocle-core"          % "3.3.0",
      "dev.optics"                            %% "monocle-macro"         % "3.3.0",
      "dev.zio"                               %% "zio-test"              % "2.1.24",
      "dev.zio"                               %% "zio-test-sbt"          % "2.1.24" % Test
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

// ---------------------------------------------------------------------------
// zio-golem modules (kept distinct from existing modules)
// ---------------------------------------------------------------------------

lazy val zioGolemModel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("golem/model"))
  .settings(stdSettings("zio-golem-model"))
  .settings(
    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Seq.empty
      }
    }
  )
  .dependsOn(schema)
  .jsSettings(jsSettings)

lazy val zioGolemCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("golem/core"))
  .settings(stdSettings("zio-golem-core"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %%% "scalatest" % "3.2.19" % Test
    )
  )
  .settings(
    // Match zioGolemModel/macros: compile per-Scala-version sources from src/main/scala-2 and src/main/scala-3.
    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Seq.empty
      }
    }
  )
  .jsSettings(jsSettings)
  .dependsOn(zioGolemModel)

lazy val zioGolemCoreJS  = zioGolemCore.js.dependsOn(zioGolemMacros)
lazy val zioGolemCoreJVM = zioGolemCore.jvm.dependsOn(zioGolemMacros)

lazy val zioGolemMacros = project
  .in(file("golem/macros"))
  .settings(stdSettings("zio-golem-macros"))
  .settings(
    scalacOptions += "-language:experimental.macros",
    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Seq.empty
      }
    },
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _            => Seq.empty
    })
  )
  .dependsOn(zioGolemModel.jvm)

lazy val zioGolemTools = project
  .in(file("golem/tools"))
  .settings(stdSettings("zio-golem-tools"))
  .settings(
    fork := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi"   %% "ujson"                 % "3.1.0",
      "org.scalatest" %% "scalatest"             % "3.2.19" % Test,
      "dev.zio"       %% "zio-schema"            % "1.1.1"  % Test,
      "dev.zio"       %% "zio-schema-derivation" % "1.1.1"  % Test
    )
  )
  .dependsOn(zioGolemModel.jvm, zioGolemMacros)

lazy val zioGolemExamples = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("golem/examples"))
  .settings(stdSettings("zio-golem-examples"))
  .settings(crossProjectSettings)
  .settings(
    publish / skip := true
  )
  .dependsOn(schema)
  .jsSettings(jsSettings)
  .jsConfigure(_.dependsOn(zioGolemCoreJS, zioGolemMacros))
  .jvmConfigure(_.dependsOn(zioGolemCoreJVM, zioGolemMacros, zioGolemTools))

lazy val zioGolemExamplesJS = zioGolemExamples.js
  .settings(
    name                            := "zio-golem-examples-js",
    scalaJSUseMainModuleInitializer := false,
    golemBasePackage                := Some("golem.examples"),
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    Test / test := {
      Keys.streams.value.log.info(
        "Skipping zioGolemExamplesJS tests (requires golem runtime). Run `golem/examples/agent2agent-local.sh` instead."
      )
    },
    Test / testOnly       := (Test / test).value,
    Test / testQuick      := (Test / test).value,
    Test / testFrameworks := Nil
  )
  .enablePlugins(golem.sbt.GolemPlugin)

lazy val zioGolemExamplesJVM = zioGolemExamples.jvm
  .settings(
    name                                   := "zio-golem-examples",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )

// ---------------------------------------------------------------------------
// Quickstart (in-repo) - crossProject: shared traits, JS impls, JVM typed client example
// ---------------------------------------------------------------------------

lazy val zioGolemQuickstart = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("golem/quickstart"))
  .settings(stdSettings("zio-golem-quickstart"))
  .settings(
    publish / skip := true
    // stdSettings already controls compiler flags across the repo; avoid duplicating -experimental here.
  )
  .jsSettings(jsSettings)
  .jsSettings(
    // For golem-cli wrapper generation, ensure agent registration runs when the JS module is loaded.
    // We do this via a tiny Scala.js `main` (see `golem/quickstart/js/.../Boot.scala`).
    scalaJSUseMainModuleInitializer := true,
    golemBasePackage                := Some("golem.quickstart"),
    Compile / mainClass             := Some("golem.quickstart.Boot"),
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / test           := Keys.streams.value.log.info("Skipping quickstart tests; run golemDeploy + repl script instead."),
    Test / testOnly       := (Test / test).value,
    Test / testQuick      := (Test / test).value,
    Test / testFrameworks := Nil
  )
  .jsEnablePlugins(golem.sbt.GolemPlugin)
  .jsConfigure(_.dependsOn(zioGolemCoreJS, zioGolemMacros))
  .jvmConfigure(_.dependsOn(zioGolemCoreJVM, zioGolemMacros))

lazy val zioGolemQuickstartJS  = zioGolemQuickstart.js
lazy val zioGolemQuickstartJVM = zioGolemQuickstart.jvm

// ---------------------------------------------------------------------------
// Tooling plugins (publishable)
// ---------------------------------------------------------------------------

lazy val zioGolemSbt = project
  .in(file("golem/sbt"))
  .enablePlugins(SbtPlugin)
  .settings(
    name         := "zio-golem-sbt",
    organization := "dev.zio",
    sbtPlugin    := true,
    // sbt plugins compile against sbt's Scala (2.12)
    scalaVersion          := "2.12.20",
    sbtVersion            := "1.12.0",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2"),
    publish / skip        := false,
    mimaPreviousArtifacts := Set()
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
    // The docs site build is not meant to be part of `+Test / compile`.
    // Website plugin settings can pull in Scala 2.12 (sbt's Scala), which then tries to resolve
    // unpublished `_2.12` artifacts for project dependencies.
    Compile / skip := true,
    Test / skip    := true,
    publish / skip := true
  )
  .dependsOn(schema.jvm)
  .enablePlugins(WebsitePlugin)
  .settings(
    // The zio-sbt-website plugin adds a dependency on `dev.zio:zio-blocks-schema` using this
    // project's Scala version (often sbt's Scala 2.12). That artifact is not published for 2.12,
    // and in a multi-project checkout we don't want to resolve our own modules from Maven Central.
    //
    // Keeping docs resolvable avoids Metals/sbt-structure failures during import.
    libraryDependencies ~= (_.filterNot(m => m.organization == "dev.zio" && m.name.startsWith("zio-blocks-schema")))
  )
