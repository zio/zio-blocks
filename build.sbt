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
addCommandAlias(
  "testJVM",
  "typeidJVM/test; chunkJVM/test; schemaJVM/test; streamsJVM/test; schema-toonJVM/test; schema-messagepackJVM/test; schema-avro/test; schema-thrift/test; schema-bson/test; contextJVM/test"
)
addCommandAlias(
  "testJS",
  "typeidJS/test; chunkJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; contextJS/test"
)

addCommandAlias(
  "docJVM",
  "typeidJVM/doc; chunkJVM/doc; schemaJVM/doc; streamsJVM/doc; schema-toonJVM/doc; schema-messagepackJVM/doc; schema-avro/doc; schema-thrift/doc; schema-bson/doc; contextJVM/doc"
)
addCommandAlias(
  "docJS",
  "typeidJS/doc; chunkJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; contextJS/doc"
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
    scalaNextTests.jvm,
    scalaNextTests.js,
    benchmarks,
    docs
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
      "org.mongodb" % "bson"         % "5.2.1",
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
      "com.vitthalmirji"                      %% "toon4s-core"           % "0.5.0",
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
  .dependsOn(schema.jvm, `schema-toon`.jvm, `schema-avro`, `schema-messagepack`.jvm, `schema-thrift`, `schema-bson`)
  .enablePlugins(WebsitePlugin)
