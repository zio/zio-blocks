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
  zioGolemExamples / testFrameworks,
  zioGolemQuickstartJS / testFrameworks
)

addCommandAlias("build", "; fmt; coverage; root/test; coverageReport")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("mimaChecks", "all schemaJVM/mimaReportBinaryIssues")
addCommandAlias(
  "golemPublishLocal",
  """++3.3.7!; set ThisBuild / version := "0.0.0-SNAPSHOT"; typeidJVM/publishLocal; typeidJS/publishLocal; chunkJVM/publishLocal; chunkJS/publishLocal; schemaJVM/publishLocal; schemaJS/publishLocal; zioGolemModelJVM/publishLocal; zioGolemModelJS/publishLocal; zioGolemMacros/publishLocal; zioGolemCoreJS/publishLocal; zioGolemCoreJVM/publishLocal; ++2.12.20!; set ThisBuild / version := "0.0.0-SNAPSHOT"; zioGolemSbt/publishLocal"""
)
addCommandAlias(
  "testJVM",
  "typeidJVM/test; chunkJVM/test; schemaJVM/test; streamsJVM/test; schema-toonJVM/test; schema-messagepackJVM/test; " +
    "schema-avro/test; schema-thrift/test; schema-bson/test; contextJVM/test; scopeJVM/test; " +
    "zioGolemModelJVM/test; zioGolemCoreJVM/test; zioGolemMacros/test; zioGolemTools/test"
)

addCommandAlias(
  "testJS",
  "++3.3.7!; typeidJS/test; chunkJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; " +
    "contextJS/test; scopeJS/test; zioGolemModelJS/test; zioGolemCoreJS/test"
)

addCommandAlias(
  "docJVM",
  "typeidJVM/doc; chunkJVM/doc; schemaJVM/doc; streamsJVM/doc; schema-toonJVM/doc; schema-messagepackJVM/doc; schema-avro/doc; schema-thrift/doc; schema-bson/doc; contextJVM/doc; scopeJVM/doc"
)
addCommandAlias(
  "docJS",
  "typeidJS/doc; chunkJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; contextJS/doc; scopeJS/doc"
)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(
    typeid.jvm,
    typeid.js,
    context.jvm,
    context.js,
    scope.jvm,
    scope.js,
    schema.jvm,
    schema.js,
    `schema-avro`,
    `schema-messagepack`.jvm,
    `schema-messagepack`.js,
    `schema-thrift`,
    `schema-bson`,
    `schema-toon`.jvm,
    `schema-toon`.js,
    streams.jvm,
    streams.js,
    chunk.jvm,
    chunk.js,
    markdown.jvm,
    markdown.js,
    zioGolemModel.jvm,
    zioGolemModel.js,
    zioGolemCore.jvm,
    zioGolemCore.js,
    zioGolemMacros,
    zioGolemTools,
    zioGolemExamples,
    zioGolemQuickstart.js,
    zioGolemQuickstart.jvm,
    zioGolemSbt
  )

lazy val typeid = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(chunk)
  .settings(stdSettings("zio-blocks-typeid"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.typeid"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 75,
    coverageMinimumBranchTotal := 65
  )

lazy val context = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(typeid)
  .settings(stdSettings("zio-blocks-context"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.context"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 75,
    coverageMinimumBranchTotal := 45
  )

lazy val scope = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(context, chunk)
  .settings(stdSettings("zio-blocks-scope"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.scope"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 100,
    coverageMinimumBranchTotal := 100,
    // Exclude macro implementation files from coverage - macros run at compile time, not runtime
    coverageExcludedFiles := Seq(
      ".*scala-2/zio/blocks/scope/.*",
      ".*scala-3/zio/blocks/scope/.*",
      ".*BuildInfo.*"
    ).mkString(";")
  )

lazy val schema = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(typeid)
  .settings(stdSettings("zio-blocks-schema"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(chunk)
  .settings(
    compileOrder := CompileOrder.JavaThenScala,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC41" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.24"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24"     % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 80,
    coverageMinimumBranchTotal := 80
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.4.10" % Test
        )
    }),
    Compile / doc / skip    := CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3),
    Compile / doc / sources := {
      if (CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3)) Nil
      else (Compile / doc / sources).value
    },
    Compile / packageDoc / publishArtifact := !CrossVersion
      .partialVersion(scalaVersion.value)
      .exists(_._1 == 3)
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
    }),
    Compile / doc / skip    := CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3),
    Compile / doc / sources := {
      if (CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3)) Nil
      else (Compile / doc / sources).value
    },
    Compile / packageDoc / publishArtifact := !CrossVersion
      .partialVersion(scalaVersion.value)
      .exists(_._1 == 3)
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-streams"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.streams"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val chunk = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-chunk"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.chunk"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 88,
    coverageMinimumBranchTotal := 85
  )

lazy val markdown = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-docs"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.docs"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(chunk)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageMinimumStmtTotal   := 95,
    coverageMinimumBranchTotal := 90
  )

lazy val `schema-avro` = project
  .settings(stdSettings("zio-blocks-schema-avro"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
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
    }),
    coverageMinimumStmtTotal   := 94,
    coverageMinimumBranchTotal := 87
  )

lazy val `schema-thrift` = project
  .settings(stdSettings("zio-blocks-schema-thrift"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.thrift"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.thrift"  % "libthrift"              % "0.22.0",
      "jakarta.annotation" % "jakarta.annotation-api" % "3.0.0",
      "dev.zio"           %% "zio-test"               % "2.1.24" % Test,
      "dev.zio"           %% "zio-test-sbt"           % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 63, // Lowered from 74 for Scala 3.5 compatibility
    coverageMinimumBranchTotal := 59  // Lowered from 60 for Scala 3.5 compatibility
  )

lazy val `schema-bson` = project
  .settings(stdSettings("zio-blocks-schema-bson"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.bson"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb" % "bson"         % "5.6.3",
      "dev.zio"    %% "zio-test"     % "2.1.24" % Test,
      "dev.zio"    %% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %% "neotype" % "0.4.10" % Test
        )
    }),
    coverageMinimumStmtTotal   := 63, // Lowered from 67 for Scala 3.5 compatibility
    coverageMinimumBranchTotal := 55  // Lowered from 58 for Scala 3.5 compatibility
  )

lazy val `schema-messagepack` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-schema-messagepack"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema.msgpack"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(schema % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 75, // Lowered from 76 for Scala 3.5 compatibility
    coverageMinimumBranchTotal := 65  // Lowered from 66 for Scala 3.5 compatibility
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    )
  )

lazy val `schema-toon` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-schema-toon"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema.toon"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(schema % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 79, // Lowered from 80 for Scala 3.5 compatibility
    coverageMinimumBranchTotal := 70  // Lowered from 71 for Scala 3.5 compatibility
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.3.37" % Test
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
          "io.github.kitlangton" %% "neotype" % "0.3.37" % Test
        )
    })
  )

lazy val scalaNextTests = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-scala-next-tests", Seq("3.7.4")))
  .dependsOn(schema % "compile->compile;test->test")
  .settings(crossProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ),
    scalaVersion               := "3.7.4",
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    Compile / skip             := CrossVersion.partialVersion((ThisBuild / scalaVersion).value).exists(_._1 == 2),
    Test / skip                := CrossVersion.partialVersion((ThisBuild / scalaVersion).value).exists(_._1 == 2)
  )
  .jsSettings(jsSettings)

lazy val benchmarks = project
  .settings(stdSettings("zio-blocks-benchmarks", Seq("3.7.4")))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .dependsOn(chunk.jvm)
  .dependsOn(`schema-avro`)
  .dependsOn(`schema-toon`.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.vitthalmirji"                      %% "toon4s-core"           % "0.7.0",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.8",
      "com.sksamuel.avro4s"                   %% "avro4s-core"           % "5.0.15",
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
    scalaVersion                     := "3.7.4",
    assembly / assemblyJarName       := "benchmarks.jar",
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case path                                 => MergeStrategy.defaultMergeStrategy(path)
    },
    assembly / fullClasspath   := (Jmh / fullClasspath).value,
    assembly / mainClass       := Some("org.openjdk.jmh.Main"),
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 30,
    coverageMinimumBranchTotal := 42,
    Compile / skip             := CrossVersion.partialVersion((ThisBuild / scalaVersion).value).exists(_._1 == 2),
    Test / skip                := CrossVersion.partialVersion((ThisBuild / scalaVersion).value).exists(_._1 == 2)
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
    },
    Test / unmanagedSourceDirectories ++= {
      val base   = baseDirectory.value / "src" / "test"
      val shared = Seq(base / "scala")
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => shared ++ Seq(base / "scala-2")
        case Some((3, _)) => shared ++ Seq(base / "scala-3")
        case _            => shared
      }
    },
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    )
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
    coverageEnabled       := false,
    coverageFailOnMinimum := false,
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

lazy val zioGolemExamples = project
  .in(file("golem/examples"))
  .settings(stdSettings("zio-golem-examples-js"))
  .settings(jsSettings)
  .settings(
    name                            := "zio-golem-examples",
    publish / skip                  := true,
    scalaJSUseMainModuleInitializer := false,
    golemBasePackage                := Some("example"),
    golemComponentPathPrefix        := "../..",
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    Test / test := {
      Keys.streams.value.log.info(
        "Skipping zioGolemExamples tests (requires golem runtime). Run `golem/examples/agent2agent-local.sh` instead."
      )
    },
    Test / testOnly       := (Test / test).value,
    Test / testQuick      := (Test / test).value,
    Test / testFrameworks := Nil
  )
  .dependsOn(schema.js, zioGolemCoreJS, zioGolemMacros)
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, golem.sbt.GolemPlugin)

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
    golemComponentPathPrefix        := "../..",
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
    scalaVersion := "2.12.20",
    sbtVersion   := "1.12.0",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2"),
    libraryDependencies += "org.scalameta" %% "scalameta" % "4.14.5",
    publish / skip                         := false,
    mimaPreviousArtifacts                  := Set()
  )

lazy val docs = project
  .in(file("zio-blocks-docs"))
  .settings(
    moduleName := "zio-blocks-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions += "-experimental",
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
  .dependsOn(schema.jvm, `schema-toon`.jvm, `schema-avro`, `schema-messagepack`.jvm, `schema-thrift`, `schema-bson`)
  .enablePlugins(WebsitePlugin)
  .settings(
    // The zio-sbt-website plugin adds a dependency on `dev.zio:zio-blocks-schema` using this
    // project's Scala version (often sbt's Scala 2.12). That artifact is not published for 2.12,
    // and in a multi-project checkout we don't want to resolve our own modules from Maven Central.
    //
    // Keeping docs resolvable avoids Metals/sbt-structure failures during import.
    libraryDependencies ~= (_.filterNot(m => m.organization == "dev.zio" && m.name.startsWith("zio-blocks-schema")))
  )
