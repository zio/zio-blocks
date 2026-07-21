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
    startYear     := Some(2024),
    headerLicense := Some(HeaderLicense.ALv2("2024-2026", "John A. De Goes and the ZIO Contributors")),
    developers    := List(
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
  zioGolemTestAgents / testFrameworks
)

com.github.sbt.git.SbtGit.useReadableConsoleGit

addCommandAlias("build", "; fmt; coverage; root/test; coverageReport")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "fmtChanged",
  "; set scalafmtFilter in ThisBuild := \"diff-ref=main\"; scalafmtAll; set scalafmtFilter in ThisBuild := \"\""
)
addCommandAlias(
  "fmtDirty",
  "; set scalafmtFilter in ThisBuild := \"diff-ref=HEAD\"; scalafmtAll; set scalafmtFilter in ThisBuild := \"\""
)
addCommandAlias(
  "checkDirty",
  "; set scalafmtFilter in ThisBuild := \"diff-ref=HEAD\"; scalafmtCheckAll; set scalafmtFilter in ThisBuild := \"\""
)
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("mimaChecks", "all schemaJVM/mimaReportBinaryIssues")
addCommandAlias(
  "golemPublishLocal", {
    val setVersion = """set ThisBuild / version := "0.0.0-SNAPSHOT""""
    val noDoc      = """set ThisBuild / packageDoc / publishArtifact := false"""
    val deps       = List(
      "typeidJVM/publishLocal",
      "typeidJS/publishLocal",
      "chunkJVM/publishLocal",
      "chunkJS/publishLocal",
      "markdownJVM/publishLocal",
      "markdownJS/publishLocal",
      "schemaJVM/publishLocal",
      "schemaJS/publishLocal"
    )
    val golem = List(
      "zioGolemModelJVM/publishLocal",
      "zioGolemModelJS/publishLocal",
      "zioGolemMacros/publishLocal",
      "zioGolemCoreJS/publishLocal"
    )
    List(
      // Scala 3.8.3 JVM / 3.3.7 JS (via jsSettings) for deps
      List(setVersion, noDoc) ++ deps,
      // Scala 3.8.3 for all Golem projects (Symbol.newClass requires 3.5+)
      List("++3.8.3", setVersion, noDoc) ++ golem,
      // Scala 2.13 for deps + Golem
      List("++2.13.18", setVersion, noDoc) ++ deps ++ golem,
      // Scala 2.12 for sbt plugin
      List("++2.12.21!", setVersion, noDoc, "zioGolemSbt/publishLocal")
    ).flatten.mkString("; ")
  }
)
addCommandAlias(
  "golemTest3",
  "++3.8.2; zioGolemModelJVM/test; zioGolemModelJS/test; zioGolemCoreJS/test; zioGolemMacros/test; zioGolemTestAgents/fastLinkJS; zioGolemIntegrationTests/test"
)
addCommandAlias(
  "golemTest2",
  "++2.13.18; zioGolemModelJVM/test; zioGolemModelJS/test; zioGolemCoreJS/test; zioGolemMacros/test; zioGolemTestAgents/fastLinkJS"
)
addCommandAlias(
  "golemTestAll",
  "golemTest3; golemTest2"
)
lazy val testJVMScala2Command =
  "typeidJVM/test; maybeJVM/test; chunkJVM/test; combinatorsJVM/test; ringbufferJVM/test; schemaJVM/test; streamsJVM/test; schema-toonJVM/test; schema-messagepackJVM/test; schema-avro/test; " +
    "schema-thrift/test; schema-bson/test; schema-xmlJVM/test; schema-yamlJVM/test; schema-csvJVM/test; contextJVM/test; scopeJVM/test; muxJVM/test; configJVM/test; config-yamlJVM/test; config-jsonJVM/test; config-hoconJVM/test; mediatypeJVM/test; " +
    "endpointJVM/test; openapiJVM/test; smithy/test; codegen/test; htmlJVM/test; asyncJVM/test" +
    whenJdkAtLeast(25, "telemetryJVM/test; otel/test")

lazy val testJVMScala3Command =
  "typeidJVM/test; maybeJVM/test; chunkJVM/test; combinatorsJVM/test; ringbufferJVM/test; schemaJVM/test; streamsJVM/test; schema-toonJVM/test; schema-messagepackJVM/test; schema-avro/test; " +
    "schema-thrift/test; schema-bson/test; schema-xmlJVM/test; schema-yamlJVM/test; schema-csvJVM/test; contextJVM/test; scopeJVM/test; muxJVM/test; mediatypeJVM/test; http-modelJVM/test; " +
    "http-model-schemaJVM/test; configJVM/test; config-yamlJVM/test; config-jsonJVM/test; config-hoconJVM/test; endpointJVM/test; openapiJVM/test; smithy/test; sqlJVM/test; sql-zio/test; codegen/test; htmlJVM/test; datastarJVM/test; htmxJVM/test; asyncJVM/test" +
    whenJdkAtLeast(25, "telemetryJVM/test; otel/test")

lazy val testJSScala2Command =
  "typeidJS/test; maybeJS/test; chunkJS/test; combinatorsJS/test; ringbufferJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; openapiJS/test; " +
    "schema-xmlJS/test; schema-yamlJS/test; schema-csvJS/test; contextJS/test; scopeJS/test; muxJS/test; mediatypeJS/test; configJS/test; config-yamlJS/test; config-jsonJS/test; config-hoconJS/test; endpointJS/test; htmlJS/test; asyncJS/test"

lazy val testJSScala3Command =
  "typeidJS/test; maybeJS/test; chunkJS/test; combinatorsJS/test; ringbufferJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; openapiJS/test; " +
    "schema-xmlJS/test; schema-yamlJS/test; schema-csvJS/test; contextJS/test; scopeJS/test; muxJS/test; mediatypeJS/test; http-modelJS/test; http-model-schemaJS/test; configJS/test; config-yamlJS/test; config-jsonJS/test; config-hoconJS/test; endpointJS/test; sqlJS/test; htmlJS/test; datastarJS/test; htmxJS/test; asyncJS/test"

lazy val testJS1Scala2Command =
  "typeidJS/test; maybeJS/test; chunkJS/test; combinatorsJS/test; ringbufferJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; asyncJS/test"

lazy val testJS1Scala3Command =
  "typeidJS/test; maybeJS/test; chunkJS/test; combinatorsJS/test; ringbufferJS/test; schemaJS/test; streamsJS/test; schema-toonJS/test; schema-messagepackJS/test; asyncJS/test"

lazy val testJS2Scala2Command =
  "openapiJS/test; schema-xmlJS/test; schema-yamlJS/test; schema-csvJS/test; contextJS/test; scopeJS/test; mediatypeJS/test; configJS/test; config-yamlJS/test; config-jsonJS/test; config-hoconJS/test; htmlJS/test"

lazy val testJS2Scala3Command =
  "openapiJS/test; schema-xmlJS/test; schema-yamlJS/test; schema-csvJS/test; contextJS/test; scopeJS/test; mediatypeJS/test; http-modelJS/test; http-model-schemaJS/test; configJS/test; config-yamlJS/test; config-jsonJS/test; config-hoconJS/test; endpointJS/test; sqlJS/test; htmlJS/test; datastarJS/test; htmxJS/test"

lazy val docJVMScala2Command =
  "typeidJVM/doc; maybeJVM/doc; chunkJVM/doc; combinatorsJVM/doc; ringbufferJVM/doc; schemaJVM/doc; streamsJVM/doc; schema-toonJVM/doc; schema-messagepackJVM/doc; schema-avro/doc; " +
    "schema-thrift/doc; schema-bson/doc; schema-xmlJVM/doc; schema-yamlJVM/doc; schema-csvJVM/doc; contextJVM/doc; scopeJVM/doc; muxJVM/doc; mediatypeJVM/doc; " +
    "endpointJVM/doc; openapiJVM/doc; smithy/doc; codegen/doc; htmlJVM/doc; asyncJVM/doc" +
    whenJdkAtLeast(25, "telemetryJVM/doc; otel/doc")

lazy val docJVMScala3Command =
  "typeidJVM/doc; maybeJVM/doc; chunkJVM/doc; combinatorsJVM/doc; ringbufferJVM/doc; schemaJVM/doc; streamsJVM/doc; schema-toonJVM/doc; schema-messagepackJVM/doc; schema-avro/doc; " +
    "schema-thrift/doc; schema-bson/doc; schema-xmlJVM/doc; schema-yamlJVM/doc; schema-csvJVM/doc; contextJVM/doc; scopeJVM/doc; muxJVM/doc; mediatypeJVM/doc; http-modelJVM/doc; " +
    "http-model-schemaJVM/doc; openapiJVM/doc; smithy/doc; sqlJVM/doc; sql-zio/doc; codegen/doc; htmlJVM/doc; datastarJVM/doc; htmxJVM/doc; asyncJVM/doc" +
    whenJdkAtLeast(25, "telemetryJVM/doc; otel/doc")

lazy val docJSScala2Command =
  "typeidJS/doc; maybeJS/doc; chunkJS/doc; combinatorsJS/doc; ringbufferJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; openapiJS/doc; " +
    "schema-xmlJS/doc; schema-yamlJS/doc; schema-csvJS/doc; contextJS/doc; scopeJS/doc; muxJS/doc; mediatypeJS/doc; endpointJS/doc; htmlJS/doc; asyncJS/doc"

lazy val docJSScala2Batch1Command =
  "typeidJS/doc; maybeJS/doc; chunkJS/doc; combinatorsJS/doc; ringbufferJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; asyncJS/doc"

lazy val docJSScala2Batch2Command =
  "openapiJS/doc; schema-xmlJS/doc; schema-yamlJS/doc; schema-csvJS/doc; contextJS/doc; scopeJS/doc; mediatypeJS/doc; htmlJS/doc"

lazy val docJSScala3Command =
  "typeidJS/doc; maybeJS/doc; chunkJS/doc; combinatorsJS/doc; ringbufferJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; openapiJS/doc; " +
    "schema-xmlJS/doc; schema-yamlJS/doc; schema-csvJS/doc; contextJS/doc; scopeJS/doc; muxJS/doc; mediatypeJS/doc; http-modelJS/doc; http-model-schemaJS/doc; sqlJS/doc; htmlJS/doc; datastarJS/doc; htmxJS/doc; asyncJS/doc"

lazy val docJSScala3Batch1Command =
  "typeidJS/doc; maybeJS/doc; chunkJS/doc; combinatorsJS/doc; ringbufferJS/doc; schemaJS/doc; streamsJS/doc; schema-toonJS/doc; schema-messagepackJS/doc; openapiJS/doc; asyncJS/doc"

lazy val docJSScala3Batch2Command =
  "schema-xmlJS/doc; schema-yamlJS/doc; schema-csvJS/doc; contextJS/doc; scopeJS/doc; mediatypeJS/doc; http-modelJS/doc; http-model-schemaJS/doc; endpointJS/doc; sqlJS/doc; htmlJS/doc; datastarJS/doc; htmxJS/doc"

def whenJdkAtLeast(minVersion: Int, command: String): String = {
  val currentVersion = System.getProperty("java.specification.version", "17").toInt
  if (currentVersion >= minVersion) s"; $command" else ""
}
def commandForScalaVersion(name: String, scala2Command: String, scala3Command: String): Command =
  Command.command(name) { state =>
    val extracted = Project.extract(state)
    val version   = extracted.get(LocalProject("schemaJVM") / scalaVersion)
    val selected  = CrossVersion.partialVersion(version) match {
      case Some((2, _)) => scala2Command
      case _            => scala3Command
    }

    selected.split(';').foldLeft(state) { case (current, command) =>
      Command.process(command.trim, current)
    }
  }

commands ++= Seq(
  commandForScalaVersion("testJVM", testJVMScala2Command, testJVMScala3Command),
  commandForScalaVersion("testJS", testJSScala2Command, testJSScala3Command),
  commandForScalaVersion("testJS1", testJS1Scala2Command, testJS1Scala3Command),
  commandForScalaVersion("testJS2", testJS2Scala2Command, testJS2Scala3Command),
  commandForScalaVersion("docJVM", docJVMScala2Command, docJVMScala3Command),
  commandForScalaVersion("docJS1", docJSScala2Batch1Command, docJSScala3Batch1Command),
  commandForScalaVersion("docJS2", docJSScala2Batch2Command, docJSScala3Batch2Command),
  commandForScalaVersion("docJS", docJSScala2Command, docJSScala3Command)
)

lazy val docsGenerateReadmeLocal =
  taskKey[Unit]("Generate README.md from docs/index.md without website plugin tag lookup")

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(
    typeid.jvm,
    typeid.js,
    maybe.jvm,
    maybe.js,
    combinators.jvm,
    combinators.js,
    context.jvm,
    context.js,
    scope.jvm,
    scope.js,
    `scope-examples`,
    sql.jvm,
    sql.js,
    `sql-zio`,
    schema.jvm,
    schema.js,
    `schema-avro`,
    `schema-messagepack`.jvm,
    `schema-messagepack`.js,
    `schema-thrift`,
    `schema-bson`,
    codegen,
    `schema-toon`.jvm,
    `schema-toon`.js,
    `schema-xml`.jvm,
    `schema-xml`.js,
    openapi.jvm,
    openapi.js,
    `schema-yaml`.jvm,
    `schema-yaml`.js,
    `schema-csv`.js,
    `schema-csv`.jvm,
    streams.jvm,
    streams.js,
    chunk.jvm,
    chunk.js,
    mediatype.jvm,
    mediatype.js,
    config.jvm,
    config.js,
    `config-yaml`.jvm,
    `config-yaml`.js,
    `config-json`.jvm,
    `config-json`.js,
    `config-hocon`.jvm,
    `config-hocon`.js,
    `http-model`.jvm,
    `http-model`.js,
    `http-model-schema`.jvm,
    `http-model-schema`.js,
    `http-model-examples`,
    endpoint.jvm,
    endpoint.js,
    `endpoint-examples`,
    markdown.jvm,
    markdown.js,
    html.jvm,
    html.js,
    datastar.jvm,
    datastar.js,
    htmx.jvm,
    htmx.js,
    async.jvm,
    async.js,
    `async-benchmarks`,
    `async-benchmarks-scala2`,
    `async-benchmarks-js`,
    `zio-blocks-htmx-examples`,
    zioGolemModel.jvm,
    zioGolemModel.js,
    zioGolemCoreJS,
    zioGolemMacros,
    scalaNextTests.jvm,
    scalaNextTests.js,
    benchmarks,
    `scope-benchmarks`,
    `streams-benchmark`,
    docs,
    `schema-examples`,
    `streams-examples`,
    `async-examples`,
    ringbuffer.jvm,
    ringbuffer.js,
    ringbufferBenchmarks,
    mux.jvm,
    mux.js,
    `mux-examples`,
    smithy,
    `smithy-examples`,
    telemetry.jvm,
    telemetry.js,
    otel
  )

lazy val ringbuffer = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-ringbuffer"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.ringbuffer"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 75,
    coverageMinimumBranchTotal := 67
  )

lazy val maybe = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(typeid)
  .settings(stdSettings("zio-blocks-maybe"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.maybe"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    // Scala 3 opaque/inline Maybe companion methods execute in tests but can still
    // report zero aggregate scoverage under the root coverage task.
    coverageFailOnMinimum      := false,
    coverageMinimumStmtTotal   := 95,
    coverageMinimumBranchTotal := 90
  )

lazy val combinators = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-combinators"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.combinators"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect"  % scalaVersion.value, // Compile scope for macros
          "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 58,
    coverageMinimumBranchTotal := 25
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 92,
    coverageMinimumBranchTotal := 80
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 78,
    coverageMinimumBranchTotal := 65
  )

lazy val sql = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(schema, scope)
  .settings(stdSettings("zio-blocks-sql", Seq(BuildHelper.Scala3, BuildHelper.Scala33)))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.sql"))
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
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.xerial"     % "sqlite-jdbc" % "3.53.2.0" % Test,
      "org.postgresql" % "postgresql"  % "42.7.13"  % Test
    )
  )

lazy val `sql-zio` = project
  .settings(stdSettings("zio-blocks-sql-zio", Seq(BuildHelper.Scala3, BuildHelper.Scala33)))
  .dependsOn(sql.jvm)
  .settings(buildInfoSettings("zio.blocks.sql.zio"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % "2.1.26",
      "dev.zio" %% "zio-test"     % "2.1.24" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.24" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `scope-examples` = project
  .settings(stdSettings("zio-blocks-scope-examples", Seq(BuildHelper.Scala3, BuildHelper.Scala33)))
  .dependsOn(scope.jvm)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `scope-benchmarks` = project
  .in(file("scope-benchmarks"))
  .settings(stdSettings("zio-blocks-scope-benchmarks", Seq("3.8.3")))
  .dependsOn(scope.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val mux = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .jvmConfigure(_.dependsOn(ringbuffer.jvm % "compile->compile;test->test"))
  .settings(stdSettings("zio-blocks-mux"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.mux"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `mux-examples` = project
  .in(file("mux-examples"))
  .settings(stdSettings("zio-blocks-mux-examples", Seq(BuildHelper.Scala3)))
  .dependsOn(mux.jvm)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    scalacOptions -= "-Werror",
    scalacOptions += "-Wconf:msg=.*App.*deprecated.*:s"
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
  .dependsOn(maybe)
  .settings(
    compileOrder := CompileOrder.JavaThenScala,
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC47" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.26"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26"     % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 85,
    coverageMinimumBranchTotal := 81
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.5.0" % Test
        )
    })
  )
  .jsSettings(
    Compile / doc / sources := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => (Compile / sources).value
        case _            => Nil
      }
    },
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"            % "2.7.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb"       % "2.7.0",
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.5.0" % Test
        )
    })
  )

lazy val telemetry = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(context, chunk)
  .settings(stdSettings("zio-blocks-telemetry"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.telemetry"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.25" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.25" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 82,
    coverageMinimumBranchTotal := 72,
    coverageExcludedFiles      := Seq(
      ".*PlatformExecutor.*",
      ".*BuildInfo.*"
    ).mkString(";"),
    Compile / scalacOptions ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq("-Wconf:cat=unchecked:s")
      else Nil
    }
  )
  .jvmSettings(
    mimaSettings(failOnProblem = false),
    // Tests share GlobalLogState (mutable singleton); parallel specs cause race conditions.
    Test / parallelExecution := false,
    Compile / scalacOptions  := {
      val base = (Compile / scalacOptions).value
      base.zipWithIndex.flatMap { case (opt, i) =>
        if ((opt == "11" || opt == "17") && i > 0 && base(i - 1) == "-release") Seq("25")
        else Seq(opt)
      }
    }
  )
  .jsSettings(jsSettings)

lazy val otel = project
  .in(file("otel"))
  .settings(stdSettings("zio-blocks-telemetry-otel"))
  .dependsOn(telemetry.jvm)
  .settings(buildInfoSettings("zio.blocks.telemetry.otel"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.25" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.25" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ =>
        Seq()
    }),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )
  .settings(
    mimaSettings(failOnProblem = false),
    Compile / scalacOptions := {
      val base = (Compile / scalacOptions).value
      base.zipWithIndex.flatMap { case (opt, i) =>
        if ((opt == "11" || opt == "17") && i > 0 && base(i - 1) == "-release") Seq("25")
        else Seq(opt)
      }
    }
  )

lazy val telemetryBenchmarks = project
  .in(file("telemetry-benchmarks"))
  .settings(stdSettings("zio-blocks-telemetry-benchmarks", Seq(BuildHelper.Scala3)))
  .dependsOn(telemetry.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    libraryDependencies ++= Seq(
      "com.outr" %% "scribe"      % "3.15.3",
      "com.outr" %% "scribe-file" % "3.15.3"
    )
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(scope, chunk, combinators, ringbuffer)
  .settings(stdSettings("zio-blocks-streams", Seq(Scala3, Scala33, Scala213)))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.streams"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    Compile / doc / scalacOptions ~= (_.filterNot(_ == "-Xfatal-warnings"))
  )
  .jvmSettings(
    mimaSettings(failOnProblem = false),
    // Stream tests create concurrent producer threads; run specs sequentially
    // to avoid CPU starvation across specs (parallelExecution := true is the
    // default from stdSettings).
    Test / parallelExecution := false,
    // Streams requires JDK 21+ (Project Loom virtual threads).
    // Override the default -release flag so Thread.ofVirtual() is available.
    // Only set -release 21 when running on JDK 21+; on JDK 17 CI, skip
    // compilation of streams (tests will be skipped due to compilation failure).
    scalacOptions ~= (opts => removeOptionWithValue(opts, "-release")),
    scalacOptions ++= {
      val jdkVersion = System.getProperty("java.specification.version", "17").toInt
      if (jdkVersion >= 21) Seq("-release", "21") else Seq("-release", jdkVersion.toString)
    },
    Compile / doc / scalacOptions ~= (opts => removeOptionWithValue(opts, "-release")),
    scalacOptions ++= Seq(
      // scope.leak is used intentionally throughout Streams internals
      "-Wconf:msg=being leaked from scope:s",
      // Streams is a WIP module; suppress unused-import warnings during development
      "-Wconf:msg=unused.*import:s",
      "-Wconf:msg=unused.*local.*val:s",
      // Alphanumeric infix in tests (andThen, etc.)
      "-Wconf:msg=Alphanumeric method.*infix:s"
    ),
    javacOptions ++= {
      val jdkVersion = System.getProperty("java.specification.version", "17").toInt
      if (jdkVersion >= 21) Seq("--release", "21") else Seq("--release", jdkVersion.toString)
    }
  )
  .jsSettings(
    jsSettings,
    scalacOptions ++= Seq(
      // scope.leak is used intentionally throughout Streams internals
      "-Wconf:msg=being leaked from scope:s",
      // Alphanumeric infix in tests (andThen, etc.)
      "-Wconf:msg=Alphanumeric method.*infix:s"
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 91,
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageMinimumStmtTotal   := 99,
    coverageMinimumBranchTotal := 93
  )

lazy val config = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(schema, scope, maybe)
  .settings(stdSettings("zio-blocks-config"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.config"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 80,
    coverageMinimumBranchTotal := 70
  )

lazy val `config-yaml` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(config, `schema-yaml`)
  .settings(stdSettings("zio-blocks-config-yaml"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.config.yaml"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `config-json` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(config, schema)
  .settings(stdSettings("zio-blocks-config-json"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.config.json"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `config-hocon` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .dependsOn(config)
  .settings(stdSettings("zio-blocks-config-hocon"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.config.hocon"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `http-model` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-http-model"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.http"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(chunk, mediatype, streams, maybe)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 95,
    coverageMinimumBranchTotal := 94
  )

lazy val `http-model-schema` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-http-model-schema"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.http.schema"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(`http-model`, schema)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 67,
    coverageMinimumBranchTotal := 51
  )

lazy val `http-model-examples` = project
  .in(file("http-model-examples"))
  .settings(stdSettings("zio-blocks-http-model-examples", Seq(BuildHelper.Scala3)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )
  .dependsOn(`http-model`.jvm)

lazy val `zio-blocks-htmx-examples` = project
  .in(file("zio-blocks-htmx-examples"))
  .settings(stdSettings("zio-blocks-htmx-examples", Seq(BuildHelper.Scala3)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )
  .dependsOn(htmx.jvm, html.jvm)

lazy val endpoint = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-endpoint"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.endpoint"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(`http-model`, schema, combinators, mediatype, markdown)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `endpoint-examples` = project
  .in(file("endpoint-examples"))
  .settings(stdSettings("zio-blocks-endpoint-examples", Seq(BuildHelper.Scala3, BuildHelper.Scala33)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )
  .dependsOn(endpoint.jvm)

lazy val markdown = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-markdown"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.markdown"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(chunk)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageMinimumStmtTotal   := 16,
    coverageMinimumBranchTotal := 13
  )

lazy val `schema-avro` = project
  .settings(stdSettings("zio-blocks-schema-avro"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.avro"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.avro" % "avro"         % "1.12.1",
      "dev.zio"        %% "zio-test"     % "2.1.26" % Test,
      "dev.zio"        %% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %% "neotype" % "0.5.0" % Test
        )
    }),
    coverageMinimumStmtTotal   := 96,
    coverageMinimumBranchTotal := 91
  )

lazy val codegen = project
  .settings(stdSettings("zio-blocks-codegen"))
  .settings(buildInfoSettings("zio.blocks.codegen"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 85,
    coverageMinimumBranchTotal := 75
  )

lazy val `schema-thrift` = project
  .settings(stdSettings("zio-blocks-schema-thrift"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.thrift"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.thrift"  % "libthrift"              % "0.24.0",
      "jakarta.annotation" % "jakarta.annotation-api" % "3.0.0",
      "dev.zio"           %% "zio-test"               % "2.1.26" % Test,
      "dev.zio"           %% "zio-test-sbt"           % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 86,
    coverageMinimumBranchTotal := 77
  )

lazy val `schema-bson` = project
  .settings(stdSettings("zio-blocks-schema-bson"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.bson"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb" % "bson"         % "5.9.0",
      "dev.zio"    %% "zio-test"     % "2.1.26" % Test,
      "dev.zio"    %% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %% "neotype" % "0.5.0" % Test
        )
    }),
    coverageMinimumStmtTotal   := 66,
    coverageMinimumBranchTotal := 58
  )

lazy val smithy = project
  .settings(stdSettings("zio-blocks-smithy"))
  .settings(buildInfoSettings("zio.blocks.smithy"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 85,
    coverageMinimumBranchTotal := 76
  )

lazy val `smithy-examples` = project
  .settings(stdSettings("zio-blocks-smithy-examples", Seq(BuildHelper.Scala3)))
  .dependsOn(smithy)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 75,
    coverageMinimumBranchTotal := 67
  )
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 81,
    coverageMinimumBranchTotal := 73
  )
  .jvmSettings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq()
      case _ =>
        Seq(
          "io.github.kitlangton" %%% "neotype" % "0.5.0" % Test
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
          "io.github.kitlangton" %% "neotype" % "0.5.0" % Test
        )
    })
  )

lazy val `schema-xml` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-schema-xml"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema.xml"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(schema % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 79,
    coverageMinimumBranchTotal := 70
  )

lazy val openapi = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-openapi"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.openapi"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(schema % "compile->compile;test->test", markdown)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 81,
    coverageMinimumBranchTotal := 54
  )

lazy val `schema-yaml` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-schema-yaml"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema.yaml"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(schema % "compile->compile;test->test", markdown)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 88,
    coverageMinimumBranchTotal := 83
  )

lazy val `schema-csv` = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-schema-csv"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.schema.csv"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(schema)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    coverageMinimumStmtTotal   := 80,
    coverageMinimumBranchTotal := 70
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4" % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4" % Test
    )
  )

lazy val scalaNextTests = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(stdSettings("zio-blocks-scala-next-tests", Seq("3.8.3")))
  .dependsOn(schema % "compile->compile;test->test")
  .settings(crossProjectSettings)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageEnabled            := coverageEnabled.value && scalaBinaryVersion.value != "2.13",
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )
  .jsSettings(jsSettings)

lazy val benchmarks = project
  .settings(stdSettings("zio-blocks-benchmarks", Seq("3.8.3")))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .dependsOn(chunk.jvm)
  .dependsOn(`schema-avro`)
  .dependsOn(`schema-toon`.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.vitthalmirji"                      %% "toon4s-core"           % "0.9.1",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.39.1",
      "com.sksamuel.avro4s"                   %% "avro4s-core"           % "5.0.15",
      "dev.zio"                               %% "zio-json"              % "0.9.2",
      "dev.zio"                               %% "zio-schema-avro"       % "1.8.2",
      "dev.zio"                               %% "zio-schema-json"       % "1.8.2",
      "io.github.arainko"                     %% "chanterelle"           % "0.1.6", // the last version that depends on Scala 3.7.x
      "com.softwaremill.quicklens"            %% "quicklens"             % "1.9.15",
      "dev.optics"                            %% "monocle-core"          % "3.3.0",
      "dev.optics"                            %% "monocle-macro"         % "3.3.0",
      "dev.zio"                               %% "zio-test"              % "2.1.26",
      "dev.zio"                               %% "zio-test-sbt"          % "2.1.26" % Test
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
    coverageEnabled            := coverageEnabled.value && scalaBinaryVersion.value != "2.13",
    coverageMinimumStmtTotal   := 30,
    coverageMinimumBranchTotal := 42
  )

// ---------------------------------------------------------------------------
// zio-golem modules (kept distinct from existing modules)
// ---------------------------------------------------------------------------

lazy val zioGolemModel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("golem/model"))
  .settings(stdSettings("zio-golem-model", Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213)))
  .settings(
    publish / skip := true,
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
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    )
  )
  .dependsOn(schema)
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson"                 % "3.1.0",
      "dev.zio"     %% "zio-schema-derivation" % "1.8.3" % Test
    )
  )
  .jsSettings(jsSettings)
  .jsSettings(
    // Override jsSettings' default Scala 3 filtering: golem modules keep
    // Scala3Golem in crossScalaVersions and use it consistently for 3.x builds.
    crossScalaVersions := Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213),
    scalaVersion       := {
      CrossVersion.partialVersion((ThisBuild / scalaVersion).value) match {
        case Some((3, _)) => BuildHelper.Scala3Golem
        case _            => (ThisBuild / scalaVersion).value
      }
    }
  )

lazy val zioGolemCoreJS = project
  .in(file("golem/core/js"))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin)
  .settings(stdSettings("zio-golem-core", Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213)))
  .settings(jsSettings)
  .settings(
    publish / skip := true,
    // Override jsSettings' default Scala 3 filtering: golem modules keep
    // Scala3Golem in crossScalaVersions and use it consistently for 3.x builds.
    crossScalaVersions := Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213),
    scalaVersion       := {
      CrossVersion.partialVersion((ThisBuild / scalaVersion).value) match {
        case Some((3, _)) => BuildHelper.Scala3Golem
        case _            => (ThisBuild / scalaVersion).value
      }
    },
    libraryDependencies ++= Seq(
      "dev.zio"           %%% "zio-test"                   % "2.1.26" % Test,
      "dev.zio"           %%% "zio-test-sbt"               % "2.1.26" % Test,
      "io.github.cquiroz" %%% "scala-java-time"            % "2.7.0"  % Test,
      "io.github.cquiroz" %%% "scala-java-time-tzdb"       % "2.7.0"  % Test,
      "io.github.cquiroz" %%% "scala-java-locales"         % "1.5.4"  % Test,
      "io.github.cquiroz" %%% "locales-full-currencies-db" % "1.5.4"  % Test
    ),
    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq(base / "scala-2")
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Seq.empty
      }
    }
  )
  .dependsOn(zioGolemModel.js, zioGolemMacros)

lazy val zioGolemMacros = project
  .in(file("golem/macros"))
  .settings(stdSettings("zio-golem-macros", Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213)))
  .settings(
    publish / skip        := true,
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
    }),
    libraryDependencies ++= Seq(
      "dev.zio"     %% "zio-test"              % "2.1.26" % Test,
      "dev.zio"     %% "zio-test-sbt"          % "2.1.26" % Test,
      "com.lihaoyi" %% "ujson"                 % "3.1.0"  % Test,
      "dev.zio"     %% "zio-schema-derivation" % "1.8.3"  % Test
    )
  )
  .dependsOn(zioGolemModel.jvm)

lazy val zioGolemTestAgents = project
  .in(file("golem/test-agents"))
  .settings(stdSettings("zio-golem-examples-js", Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213)))
  .settings(jsSettings)
  .settings(
    // Override jsSettings' default Scala 3 filtering: golem modules keep
    // Scala3Golem in crossScalaVersions and use it consistently for 3.x builds.
    crossScalaVersions := Seq(BuildHelper.Scala3Golem, BuildHelper.Scala213),
    scalaVersion       := {
      CrossVersion.partialVersion((ThisBuild / scalaVersion).value) match {
        case Some((3, _)) => BuildHelper.Scala3Golem
        case _            => (ThisBuild / scalaVersion).value
      }
    },
    publish / skip                  := true,
    name                            := "zio-golem-test-agents",
    scalaJSUseMainModuleInitializer := false,
    golemBasePackage                := Some("example"),

    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"      % "2.7.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.7.0",
      "dev.zio"           %%% "zio-http"             % "3.0.1"
    ),
    Test / test := {
      Keys.streams.value.log.info(
        "Skipping zioGolemTestAgents tests (requires golem runtime). Run integration tests instead."
      )
    },
    Test / testOnly       := (Test / test).value,
    Test / testQuick      := (Test / test).value,
    Test / testFrameworks := Nil,
    Compile / unmanagedSourceDirectories ++= {
      val base = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => Seq(base / "scala-3")
        case _            => Seq.empty
      }
    },
    Compile / scalacOptions ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq(
          "-Wconf:cat=unused-imports:s",
          "-Wconf:msg=private method create .* is never used:s" // @constructor methods are read by macros
        )
      else
        Seq(
          "-Wconf:msg=unused private member:s" // @constructor methods are read by macros
        )
    }
  )
  .dependsOn(schema.js, zioGolemCoreJS, zioGolemMacros)
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, golem.sbt.GolemPlugin)

lazy val zioGolemIntegrationTests = project
  .in(file("golem/integration-tests"))
  .settings(stdSettings("zio-golem-integration-tests", Seq(BuildHelper.Scala3Golem)))
  .settings(
    publish / skip           := true,
    Test / fork              := true,
    Test / parallelExecution := false,
    Test / javaOptions ++= sys.env
      .get("GOLEM_TS_PACKAGES_PATH")
      .map(v => s"-Dgolem.tsPackagesPath=$v")
      .toSeq,
    Test / envVars ++= sys.env
      .get("GOLEM_TS_PACKAGES_PATH")
      .map(v => Map("GOLEM_TS_PACKAGES_PATH" -> v))
      .getOrElse(Map.empty),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.26" % Test,
      "dev.zio" %% "zio-process"  % "0.8.0"  % Test
    )
  )

// ---------------------------------------------------------------------------
// Shared codegen library (consumed by sbt + mill plugins)
// ---------------------------------------------------------------------------

lazy val zioGolemBuildCodegen = project
  .in(file("golem/codegen"))
  .settings(
    publish / skip := true,
    name           := "zio-golem-build-codegen",
    organization   := "dev.zio",
    scalaVersion   := "2.12.21",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.17.2",
      "com.lihaoyi"   %% "ujson"     % "3.1.0",
      "org.scalameta" %% "munit"     % "1.1.0" % Test
    ),
    mimaPreviousArtifacts := Set()
  )

// ---------------------------------------------------------------------------
// Tooling plugins (publishable)
// ---------------------------------------------------------------------------

lazy val zioGolemSbt = project
  .in(file("golem/sbt"))
  .enablePlugins(SbtPlugin)
  .dependsOn(zioGolemBuildCodegen)
  .settings(
    publish / skip := true,
    name           := "zio-golem-sbt",
    organization   := "dev.zio",
    sbtPlugin      := true,
    // sbt plugins compile against sbt's Scala (2.12)
    scalaVersion := "2.12.21",
    sbtVersion   := "1.12.0",
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0"),
    libraryDependencies += "org.scalameta" %% "scalafmt-dynamic" % "3.10.4",
    mimaPreviousArtifacts                  := Set()
  )
lazy val ringbufferBenchmarks = project
  .in(file("ringbuffer-benchmarks"))
  .settings(stdSettings("zio-blocks-ringbuffer-benchmarks", Seq(BuildHelper.Scala3)))
  .dependsOn(ringbuffer.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 99,
    coverageMinimumBranchTotal := 99,
    libraryDependencies ++= Seq(
      "org.jctools" % "jctools-core" % "4.0.6"
    )
  )

lazy val `streams-benchmark` = project
  .in(file("streams-benchmark"))
  .settings(stdSettings("zio-blocks-streams-benchmark", Seq("3.8.3")))
  .dependsOn(streams.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    // Requires JDK 21+ for Thread.ofVirtual() (Project Loom)
    scalacOptions ~= { opts =>
      opts.zipWithIndex.flatMap { case (o, i) => if (o == "-release") None else Some((o, i)) }
        .map(_._1)
    },
    scalacOptions ++= {
      val jdkVersion = System.getProperty("java.specification.version", "17").toInt
      if (jdkVersion >= 21) Seq("-release", "21") else Seq("-release", jdkVersion.toString)
    },
    scalacOptions ++= Seq(
      "-Wconf:msg=being leaked from scope:s",
      "-Wconf:msg=unused.*import:s"
    ),
    javacOptions ++= {
      val jdkVersion = System.getProperty("java.specification.version", "17").toInt
      if (jdkVersion >= 21) Seq("--release", "21") else Seq("--release", jdkVersion.toString)
    },
    libraryDependencies ++= Seq(
      // fs2 — pull-based functional streams (Cats Effect)
      "co.fs2"        %% "fs2-core"    % "3.13.0",
      "org.typelevel" %% "cats-effect" % "3.7.0",
      // Apache Pekko Streams (Apache-2.0 fork of Akka Streams)
      "org.apache.pekko" %% "pekko-stream" % "1.5.0",
      // Kyo — algebraic effect streams (Scala 3 only)
      "io.getkyo" %% "kyo-prelude" % "1.0.0-RC5",
      "io.getkyo" %% "kyo-core"    % "1.0.0-RC5",
      // Ox — direct-style streaming (SoftwareMill, Scala 3 only)
      "com.softwaremill.ox" %% "core" % "1.0.5"
    ),
    assembly / assemblyJarName       := "streams-benchmark.jar",
    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case x if x.contains("reference.conf")    => MergeStrategy.concat
      case path                                 => MergeStrategy.defaultMergeStrategy(path)
    },
    assembly / fullClasspath   := (Jmh / fullClasspath).value,
    assembly / mainClass       := Some("org.openjdk.jmh.Main"),
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

lazy val `schema-examples` = project
  .in(file("schema-examples"))
  .settings(stdSettings("zio-blocks-schema-examples", Seq(BuildHelper.Scala3)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "sourcecode"     % "0.4.4",
      "dev.zio"     %% "zio-sbt-source" % "0.6.0"
    ),
    scalacOptions -= "-Werror",
    scalacOptions += "-Wconf:msg=.*App.*deprecated.*:s"
  )
  .dependsOn(
    schema.jvm,
    markdown.jvm,
    streams.jvm,
    chunk.jvm,
    context.jvm,
    `schema-toon`.jvm,
    `schema-messagepack`.jvm,
    `schema-avro`,
    `schema-thrift`,
    `schema-bson`
  )

lazy val `streams-examples` = project
  .in(file("streams-examples"))
  .settings(stdSettings("zio-blocks-streams-examples", Seq(BuildHelper.Scala3)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "sourcecode"     % "0.4.4",
      "dev.zio"     %% "zio-sbt-source" % "0.6.0"
    ),
    scalacOptions -= "-Werror",
    scalacOptions += "-Wconf:msg=.*App.*deprecated.*:s"
  )
  .dependsOn(
    streams.jvm,
    chunk.jvm
  )

lazy val `async-examples` = project
  .in(file("async-examples"))
  .settings(stdSettings("zio-blocks-async-examples", Seq(BuildHelper.Scala3)))
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    scalacOptions -= "-Werror",
    scalacOptions += "-Wconf:msg=.*App.*deprecated.*:s"
  )
  .dependsOn(async.jvm)

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
    publish / skip                             := true,
    libraryDependencies ++= Seq(
      "dev.zio"    %% "zio-prelude"    % "1.0.0-RC47",
      "dev.zio"    %% "zio-sbt-source" % "0.6.0",
      "org.xerial"  % "sqlite-jdbc"    % "3.53.2.0"
    ),
    // Override @PROJECT_BADGES@ to exclude Sonatype Release, Snapshot, and javadoc badges
    mdocVariables ++= Map(
      "PROJECT_BADGES" -> (
        "[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) " +
          "![CI Badge](https://github.com/zio/zio-blocks/workflows/CI/badge.svg) " +
          "[![ZIO Blocks](https://img.shields.io/github/stars/zio/zio-blocks?style=social)](https://github.com/zio/zio-blocks)"
      )
    ),
    mdocOut := (ThisBuild / baseDirectory).value / "website" / "docs"
  )
  .dependsOn(
    schema.jvm,
    markdown.jvm,
    context.jvm,
    scope.jvm,
    ringbuffer.jvm,
    streams.jvm,
    `schema-toon`.jvm,
    `schema-avro`,
    `schema-messagepack`.jvm,
    `schema-thrift`,
    `schema-bson`,
    `schema-xml`.jvm,
    `schema-csv`.jvm,
    `schema-yaml`.jvm,
    mediatype.jvm,
    endpoint.jvm,
    `http-model`.jvm,
    `http-model-schema`.jvm,
    openapi.jvm,
    codegen,
    html.jvm,
    datastar.jvm,
    smithy,
    htmx.jvm,
    mux.jvm,
    async.jvm,
    sql.jvm
  )
  .enablePlugins(WebsitePlugin)
  .settings(
    docsGenerateReadmeLocal := {
      val docsIndex   = baseDirectory.value / "docs" / "index.md"
      val readmeFile  = baseDirectory.value / "README.md"
      val versionText = version.value
      val badges      =
        "[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) " +
          "![CI Badge](https://github.com/zio/zio-blocks/workflows/CI/badge.svg) " +
          "[![ZIO Blocks](https://img.shields.io/github/stars/zio/zio-blocks?style=social)](https://github.com/zio/zio-blocks)"

      val rendered = IO
        .read(docsIndex)
        .replace("@PROJECT_BADGES@", badges)
        .replace("@VERSION@", versionText)

      val header =
        """[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
          |[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
          |[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)
          |
          |""".stripMargin

      IO.write(readmeFile, header + rendered + "\n")
      sLog.value.info(s"Wrote ${readmeFile.getAbsolutePath} using local README generator")
    }
  )

lazy val html = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-html"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.html"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .dependsOn(chunk, schema)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageExcludedFiles := Seq(
      ".*TemplateInterpolators.*",
      ".*BuildInfo.*"
    ).mkString(";"),
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 90
  )

lazy val datastar = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-datastar", Seq(Scala3, Scala33)))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.datastar"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(html, `http-model`)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    })
  )

lazy val async = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-async"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.blocks.async"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(
    mimaSettings(failOnProblem = false),
    // DCA direct-style implementation: every Scala 3.x JVM build uses it
    // (`scala-3-dca` holds the transform, `scala-3-dca-direct` the
    // AsyncDirect entry point that delegates to it).
    Compile / unmanagedSourceDirectories ++= {
      val sharedMain = baseDirectory.value.getParentFile / "shared" / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq(sharedMain / "scala-3-dca", sharedMain / "scala-3-dca-direct")
        case _ => Seq.empty
      }
    }
  )
  .jsSettings(
    jsSettings,
    // Target ES2017 so Scala.js can emit native async/await (`js.async`/
    // `js.await`), used by the Scala 3.8+ direct-style implementation.
    scalaJSLinkerConfig ~= { _.withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2017)) },
    // The repo-wide `jsSettings` drops the latest Scala (3.8.x) from JS cross
    // builds; async opts back in because its native `js.async`/`js.await`
    // backend exists only on 3.8+ and would otherwise never be compiled or
    // tested.
    crossScalaVersions += Scala3,
    // Direct-style implementation selection on JS:
    //   - Scala 3.8+ → native `js.async`/`js.await` for direct-position
    //     awaits (faster than DCA on JS), with the shared DCA transform as
    //     fallback for awaits under lambdas / by-name args / nested methods
    //     (which `js.await` cannot cross). Needs `scala-3-dca` (the transform
    //     + CpsMonad) but NOT `scala-3-dca-direct` (it ships its own
    //     AsyncDirect).
    //   - Scala 3.x < 3.8 → DCA only (older Scala 3 lacks `js.async`).
    //   - Scala 2 → the shared `scala-2` def-macro (`internal.AsyncMacros`)
    //     emits a platform-neutral flatMap chain, so no JS-specific source dir
    //     is needed; JS behavior is covered by
    //     `js/src/test/scala-2/.../AsyncJsAwaitSpec`.
    //
    // Intentionally validated on the repo default Scala 3.8.3 rather than
    // bumping BuildHelper.Scala3 to 3.8.4-RC1. 3.8.4 fixes the narrow
    // `js.await(js.Promise[Unit])` compile bug, but that single case is
    // explicitly accepted/deferred; a repo-wide RC bump would force ~40
    // unrelated subprojects onto an RC compiler with much larger blast radius.
    // Caveat: a direct `Async[Unit].await` expanding to `js.await(Promise[Unit])`
    // can fail to compile on 3.8.3; revisit when 3.8.4 is stable.
    Compile / unmanagedSourceDirectories ++= {
      val sharedMain = baseDirectory.value.getParentFile / "shared" / "src" / "main"
      val jsMain     = baseDirectory.value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, n)) if n >= 8 => Seq(sharedMain / "scala-3-dca", jsMain / "scala-3.8")
        case Some((3, _))           => Seq(sharedMain / "scala-3-dca", sharedMain / "scala-3-dca-direct")
        case _                      => Seq.empty
      }
    }
  )
  .dependsOn(combinators)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    // dotty-cps-async powers the direct-style `Async.async { ... .await ... }`
    // rewrite on every Scala 3 cell except JS 3.8+ (which uses native
    // `js.async`/`js.await`). Scala 2 uses a hand-written `scala-reflect` macro
    // (`internal.AsyncMacros`) and must not pull DCA onto its classpath. The
    // `_3` artifact (built against 3.3.7) is consumed on 3.8.x via LTS forward
    // compatibility.
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => Seq("io.github.dotty-cps-async" %%% "dotty-cps-async" % "1.3.3")
        case _            => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      }
    },
    // JVM scoverage floor (JS coverage is disabled repo-wide in jsSettings).
    // Measured JVM coverage: Scala 3.8.3 = 93.35% stmt / 91.61% branch,
    // Scala 2.13.18 = 95.84% / 93.55%. The Scala 3 cell is the floor because it
    // additionally compiles the dotty-cps-async bridge (`internal/AsyncDirect`,
    // `AsyncCpsMonad`, `AsyncRuntimeAwait`), whose macro markers / error
    // messages run at compile time (not runtime-instrumentable) and whose HOF
    // blocking fallback only fires when DCA has no AsyncShift. The remaining
    // residual is dead-via-guard defensive branches and Completer CAS-retry
    // races — see async/COVERAGE_DOCS_AUDIT.md for the per-line classification.
    coverageMinimumStmtTotal   := 92,
    coverageMinimumBranchTotal := 89
  )

lazy val `async-benchmarks` = project
  .in(file("async-benchmarks"))
  .settings(stdSettings("zio-blocks-async-benchmarks", Seq("3.8.3")))
  .dependsOn(async.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Seq(
      // Cats Effect IO — boxed effect tree
      "org.typelevel" %% "cats-effect" % "3.7.0",
      // Kyo — algebraic-effect runtime with raw-value `A < S` (Scala 3 only)
      "io.getkyo" %% "kyo-core" % "0.19.0"
    ),
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0,
    // The `Async.async { ... }` direct-style benchmarks expand (via
    // dotty-cps-async) into a `cps.async[Async] { ctx ?=> ... }` whose context
    // parameter DCA leaves unused once every `.await` is rewritten to a flatMap
    // chain. That unused-parameter warning is DCA-generated code we don't
    // control; the repo only auto-suppresses unused warnings under `/test/`
    // paths, so silence it narrowly for the affected benchmark sources here.
    scalacOptions += "-Wconf:id=E198&src=.*AsyncBlock.*:s"
  )

// Scala-2-only JMH benchmarks for the direct-style `Async.async { ... .await ... }`
// macro (`internal.AsyncMacros`). Kept SEPARATE from `async-benchmarks` because
// that module is Scala-3-only (it depends on Kyo) and because Scala 2 def-macros
// must be exercised from a DOWNSTREAM compilation unit. The gate here is
// allocation / generated-code shape (run with `-prof gc`), not cross-runtime
// throughput, so it deliberately pulls no Kyo / Cats Effect comparison deps.
lazy val `async-benchmarks-scala2` = project
  .in(file("async-benchmarks-scala2"))
  .settings(stdSettings("zio-blocks-async-benchmarks-scala2", Seq(BuildHelper.Scala213)))
  .dependsOn(async.jvm)
  .enablePlugins(JmhPlugin)
  .settings(
    publish / skip             := true,
    mimaPreviousArtifacts      := Set(),
    coverageMinimumStmtTotal   := 0,
    coverageMinimumBranchTotal := 0
  )

// JS-native benchmark gate. JMH is JVM-only, so this is a Scala.js main that
// runs hand-rolled throughput + Node heap-delta allocation measurements (see
// `AsyncJsBench`). It depends on `async.js`, so selecting the Scala version
// chooses the cell under test: `++3.8.3; async-benchmarks-js/run` exercises the
// native `js.async`/`js.await` backend; `++3.3.7; async-benchmarks-js/run`
// exercises the dotty-cps-async backend. The `jsEnv` passes Node `--expose-gc`
// so the harness can force a GC between heap reads. No Kyo / Cats Effect deps:
// JMH-grade cross-runtime throughput comparison is JVM-only (see
// `async-benchmarks`); this gate is about the JS hot-path allocation profile.
lazy val `async-benchmarks-js` = project
  .in(file("async-benchmarks-js"))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin)
  .settings(stdSettings("zio-blocks-async-benchmarks-js", Seq(BuildHelper.Scala3, BuildHelper.Scala33)))
  .settings(jsSettings)
  .dependsOn(async.js)
  .settings(
    publish / skip                  := true,
    mimaPreviousArtifacts           := Set(),
    coverageMinimumStmtTotal        := 0,
    coverageMinimumBranchTotal      := 0,
    scalaJSUseMainModuleInitializer := true,
    // Benchmark in production mode (Closure full optimization) so the measured
    // allocation/throughput reflects what ships, not the dev `fastLinkJS` output.
    scalaJSStage := FullOptStage,
    // jsSettings pins JS Scala 3 to 3.3.7; allow the native 3.8.x cell too.
    scalaVersion       := (ThisBuild / scalaVersion).value,
    crossScalaVersions := Seq(BuildHelper.Scala3, BuildHelper.Scala33),
    // ES2017 so the native `js.async`/`js.await` in `async.js` (Scala 3.8+) links.
    scalaJSLinkerConfig ~= {
      _.withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2017))
    },
    // Force a full GC between heap reads in the allocation measurement.
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(
      org.scalajs.jsenv.nodejs.NodeJSEnv.Config().withArgs(List("--expose-gc"))
    ),
    // DCA expands `Async.async { ... }` into a `cps.async[Async] { ctx ?=> ... }`
    // whose context parameter goes unused once every `.await` is rewritten; that
    // E198 warning is DCA-generated code we don't control (same as in
    // `async-benchmarks`).
    scalacOptions += "-Wconf:id=E198&src=.*AsyncJsBench.*:s"
  )

lazy val htmx = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .settings(stdSettings("zio-blocks-http-htmx", Seq(Scala3, Scala33)))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.http.htmx"))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(mimaSettings(failOnProblem = false))
  .jsSettings(jsSettings)
  .jsSettings(
    Compile / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false)),
    Test / scalaJSLinkerConfig ~= (_.withOptimizer(false).withParallel(false))
  )
  .dependsOn(html, schema, `http-model`)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.26" % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Seq()
    }),
    coverageMinimumStmtTotal   := 85,
    coverageMinimumBranchTotal := 75
  )
