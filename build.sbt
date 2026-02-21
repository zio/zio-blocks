import BuildHelper.*
import MimaSettings.mimaSettings
import org.scalajs.linker.interface.ModuleKind

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val generateMediaTypes = taskKey[File]("Generate MediaTypes.scala from mime-db")

generateMediaTypes := GenerateMediaTypes.generateMediaTypesTask.value

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
  """set ThisBuild / version := "0.0.0-SNAPSHOT"; typeidJVM/publishLocal; typeidJS/publishLocal; chunkJVM/publishLocal; chunkJS/publishLocal; schemaJVM/publishLocal; schemaJS/publishLocal; zioGolemModelJVM/publishLocal; zioGolemModelJS/publishLocal; zioGolemMacros/publishLocal; zioGolemCoreJS/publishLocal; zioGolemCoreJVM/publishLocal; ++2.12.20!; set ThisBuild / version := "0.0.0-SNAPSHOT"; zioGolemSbt/publishLocal"""
)
addCommandAlias(
  "testJVM",
  "typeidJVM/test; chunkJVM/test; schemaJVM/test; streamsJVM/test; schema-toonJVM/test; schema-messagepackJVM/test; schema-avro/test; schema-thrift/test; schema-bson/test; contextJVM/test; scopeJVM/test; mediatypeJVM/test; " +
    "zioGolemModelJVM/test; zioGolemCoreJVM/test; zioGolemMacros/test; zioGolemTools/test"
)
addCommandAlias(
  "testJS",
  "typeidJS/test; chunkJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; contextJS/test; scopeJS/test; mediatypeJS/test; " +
    "contextJS/test; scopeJS/test; zioGolemModelJS/test; zioGolemCoreJS/test"
)

addCommandAlias(
  "docJVM",
  "typeidJVM/doc; chunkJVM/doc; schemaJVM/doc; streamsJVM/doc; schema-toonJVM/doc; schema-messagepackJVM/doc; schema-avro/doc; schema-thrift/doc; schema-bson/doc; contextJVM/doc; scopeJVM/doc; mediatypeJVM/doc"
)
addCommandAlias(
  "docJS",
  "typeidJS/doc; chunkJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; contextJS/doc; scopeJS/doc; mediatypeJS/doc"
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
    `scope-examples`,
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
    mediatype.jvm,
    mediatype.js,
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
    zioGolemSbt,
    scalaNextTests.jvm,
    scalaNextTests.js,
    benchmarks,
    docs,
    examples
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
    coverageMinimumStmtTotal   := 77,
    coverageMinimumBranchTotal := 69
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
    coverageMinimumStmtTotal   := 76,
    coverageMinimumBranchTotal := 48
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
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 80,
    // Exclude macro implementation files from coverage - macros run at compile time, not runtime
    // Note: Branch coverage is lower because concurrent state machine code has defensive
    // branches (CAS retry loops) that are hard to trigger reliably in tests.
    coverageExcludedFiles := Seq(
      ".*scala-2/zio/blocks/scope/.*",
      ".*scala-3/zio/blocks/scope/.*",
      ".*BuildInfo.*"
    ).mkString(";")
  )

lazy val `scope-examples` = project
  .settings(stdSettings("zio-blocks-scope-examples", Seq(BuildHelper.Scala3)))
  .dependsOn(scope.jvm)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
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
  .dependsOn(markdown)
  .settings(
    compileOrder := CompileOrder.JavaThenScala,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC46" % Test,
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
    coverageMinimumStmtTotal   := 85,
    coverageMinimumBranchTotal := 80,
    coverageExcludedFiles      := ".*BuildInfo.*"
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
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 86
  )

lazy val mediatype = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-mediatype"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.mediatype"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.24" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageMinimumStmtTotal   := 99,
    coverageMinimumBranchTotal := 93
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
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
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
    coverageMinimumStmtTotal   := 74,
    coverageMinimumBranchTotal := 61
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
    coverageMinimumStmtTotal   := 63,
    coverageMinimumBranchTotal := 59
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
    coverageMinimumStmtTotal   := 75,
    coverageMinimumBranchTotal := 66
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
    coverageMinimumStmtTotal   := 79,
    coverageMinimumBranchTotal := 72
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
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
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
    coverageMinimumBranchTotal := 42
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
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"            % "2.6.0" % Test,
      "io.github.cquiroz" %%% "scala-java-time-tzdb"       % "2.6.0" % Test,
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    )
  )
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
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"            % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb"       % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4",
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4"
    ),
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
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"            % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb"       % "2.6.0",
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4",
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4"
    ),
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

lazy val examples = project
  .in(file("zio-blocks-examples"))
  .settings(stdSettings("zio-blocks-examples"))
  .settings(
    publish / skip := true
  )
  .dependsOn(
    schema.jvm,
    markdown.jvm,
    streams.jvm,
    chunk.jvm,
    `schema-toon`.jvm,
    `schema-messagepack`.jvm,
    `schema-avro`,
    `schema-thrift`,
    `schema-bson`
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
    publish / skip                             := true
  )
  .dependsOn(
    schema.jvm,
    markdown.jvm,
    `schema-toon`.jvm,
    `schema-avro`,
    `schema-messagepack`.jvm,
    `schema-thrift`,
    `schema-bson`,
    mediatype.jvm
  )
  .enablePlugins(WebsitePlugin)
