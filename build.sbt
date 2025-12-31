import BuildHelper.*
import MimaSettings.mimaSettings
import org.scalajs.linker.interface.ModuleKind

Global / onChangedBuildSource := ReloadOnSourceChanges

// Mill dependency used to compile the Mill plugin. Override for compatibility checks:
//   GOLEM_MILL_LIBS_VERSION=... sbt "project zioGolemMillPlugin" compile
lazy val golemMillLibsVersion: String =
  sys.env.getOrElse("GOLEM_MILL_LIBS_VERSION", "1.1.0-RC3")

lazy val golemHostTests =
  taskKey[Unit]("Run the host-backed Scala.js harness via golem-cli (repo-local; uses public primitives).")

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
  zioGolemHostTests / testFrameworks
)

addCommandAlias("build", "; fmt; coverage; root/test; coverageReport")
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("mimaChecks", "all schemaJVM/mimaReportBinaryIssues")
addCommandAlias(
  "golemPublishMillPluginForE2E",
  """set zioGolemToolingCore/version := "0.0.0-SNAPSHOT"; set zioGolemToolingCore/publish / skip := false; zioGolemToolingCore/publishLocal; set zioGolemMillPlugin/version := "0.0.0-SNAPSHOT"; set zioGolemMillPlugin/publish / skip := false; zioGolemMillPlugin/publishLocal"""
)
addCommandAlias(
  "golemPublishSbtPluginForE2E",
  """set zioGolemToolingCore/version := "0.0.0-SNAPSHOT"; set zioGolemToolingCore/publish / skip := false; zioGolemToolingCore/publishLocal; set zioGolemSbtPlugin/version := "0.0.0-SNAPSHOT"; set zioGolemSbtPlugin/publish / skip := false; zioGolemSbtPlugin/publishLocal"""
)

// Repo-local development helper. Kept separate from plain `publishLocal` because this repo publishes multiple modules
// (some normally skipped) in a known-good order for local consumption.
addCommandAlias(
  "golemPublishLocal",
  """set ThisBuild / version := "0.0.0-SNAPSHOT"; schemaJVM/publishLocal; schemaJS/publishLocal; zioGolemModelJVM/publishLocal; zioGolemModelJS/publishLocal; zioGolemMacros/publishLocal; zioGolemCoreJS/publishLocal; zioGolemCoreJVM/publishLocal; set zioGolemToolingCore/publish / skip := false; zioGolemToolingCore/publishLocal; set zioGolemSbtPlugin/publish / skip := false; zioGolemSbtPlugin/publishLocal"""
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
    zioGolemToolingCore,
    zioGolemExamples.jvm,
    zioGolemExamples.js,
    zioGolemQuickstart.js,
    zioGolemQuickstart.jvm,
    zioGolemHostTests,
    zioGolemMillPlugin,
    zioGolemSbtPlugin,
    docs
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
      "dev.zio" %%% "zio-prelude"  % "1.0.0-RC44" % Test,
      "dev.zio" %%% "zio-test"     % "2.1.23"     % Test,
      "dev.zio" %%% "zio-test-sbt" % "2.1.23"     % Test
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
          "io.github.kitlangton" %%% "neotype" % "0.3.37" % Test
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
          "io.github.kitlangton" %%% "neotype" % "0.3.37" % Test
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
    scalaVersion       := "3.7.4",
    crossScalaVersions := Seq("3.7.4"),
    Compile / skip     := true,
    Test / skip        := true,
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

lazy val zioGolemToolingCore = project
  .in(file("golem/tooling-core"))
  .settings(
    name             := "zio-golem-tooling-core",
    autoScalaLibrary := false,
    crossPaths       := false,
    scalaVersion     := "2.12.19",
    Compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    Compile / doc / javacOptions           := Nil,
    Compile / packageDoc / publishArtifact := false,
    libraryDependencies += "junit"          % "junit" % "4.13.2" % Test,
    publish / skip                         := true
  )

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
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
    Test / test := {
      Keys.streams.value.log.info(
        "Skipping zioGolemExamplesJS tests (requires golem runtime). Run `golem/examples/agent2agent-local.sh` instead."
      )
    },
    Test / testOnly       := (Test / test).value,
    Test / testQuick      := (Test / test).value,
    Test / testFrameworks := Nil,
    // Minimal example app: rely on annotation-based auto detection of exports.
    // This keeps the “hello world” story minimal (agent trait + impl only).
    cloud.golem.sbt.GolemPlugin.autoImport.golemAppName   := "scala-examples",
    cloud.golem.sbt.GolemPlugin.autoImport.golemComponent := "scala:examples"
  )
  .enablePlugins(cloud.golem.sbt.GolemPlugin)
  .dependsOn(zioGolemToolingCore)

lazy val zioGolemExamplesJVM = zioGolemExamples.jvm
  .settings(
    name                                   := "zio-golem-examples",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )

lazy val zioGolemHostTests = project
  .in(file("golem/host-tests"))
  .settings(stdSettings("zio-golem-host-tests"))
  .settings(
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    Test / test := {
      if (sys.env.get("GOLEM_HOST_TESTS").contains("1")) golemHostTests.value
      else Keys.streams.value.log.info("Skipping host-tests; set GOLEM_HOST_TESTS=1 to enable.")
    },
    Test / testOnly                                       := (Test / test).value,
    Test / testQuick                                      := (Test / test).value,
    Test / testFrameworks                                 := Nil,
    cloud.golem.sbt.GolemPlugin.autoImport.golemAppName   := "scala-host-tests",
    cloud.golem.sbt.GolemPlugin.autoImport.golemComponent := sys.env
      .getOrElse("GOLEM_COMPONENT_QUALIFIED", "scala:host-tests"),
    cloud.golem.sbt.GolemPlugin.autoImport.golemExports                 := {
      import cloud.golem.sbt.GolemPlugin.autoImport._
      import GolemConstructor._

      Seq(
        GolemExport(
          agentName = "scala-host-tests-harness",
          traitClass = "cloud.golem.hosttests.HostTestsHarness",
          className = "ScalaHostTestsHarness",
          scalaFactory = "newHostTestsHarness",
          scalaShimImplClass = "cloud.golem.hosttests.internal.HostTestsHarnessShimImpl",
          constructor = NoArg,
          typeDeclarations = Seq("type HostTestsInput = { tests?: string[] };"),
          methods = Seq(
            GolemMethod(
              name = "runtests",
              tsReturnType = "string",
              isAsync = true,
              params = Seq(GolemMethodParam("input", "HostTestsInput", "input")),
              implMethodName = "runtests"
            )
          )
        )
      )
    },
    golemHostTests := Def.taskDyn {
      import cloud.golem.sbt.GolemPlugin.autoImport._

      val enabled = sys.env.get("GOLEM_HOST_TESTS").contains("1")
      if (!enabled) Def.task(Keys.streams.value.log.info("[host-tests] Skipping (set GOLEM_HOST_TESTS=1 to enable)."))
      else
        Def.task {
          val log     = Keys.streams.value.log
          val baseDir = (ThisBuild / baseDirectory).value

          val component = golemComponent.value
          val agentType = "scala-host-tests-harness"
          val method    = "runtests"
          // Payload is parsed by golem-cli as WAVE. The method input is a record with an optional `tests` field,
          // so the smallest valid default is to provide the record with `tests: none`.
          val payload     = sys.env.getOrElse("GOLEM_HOST_PAYLOAD", "{ tests: none }")
          val invokeFlags =
            sys.env
              .get("GOLEM_HOST_INVOKE_FLAGS")
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(_.split("\\s+").toSeq)
              .getOrElse(Nil)

          val startServer =
            sys.env
              .get("GOLEM_HOST_START_SERVER")
              .forall(v => v == "1" || v.equalsIgnoreCase("true"))
          val dataDir    = baseDir / sys.env.getOrElse("GOLEM_HOST_DATA_DIR", ".golem-local")
          val routerHost = sys.env.getOrElse("GOLEM_ROUTER_HOST", "127.0.0.1")
          val routerPort =
            sys.env.get("GOLEM_ROUTER_PORT").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(9881)
          val golemBin  = sys.env.getOrElse("GOLEM_BIN", "golem")
          val pidFile   = dataDir / "server.pid"
          val serverLog = dataDir / "server.log"

          def requireCommandOnPath(cmd: String, friendly: String): Unit = {
            val exit = scala.sys.process
              .Process(Seq("bash", "-lc", s"command -v ${cmd}"))
              .!(
                scala.sys.process.ProcessLogger(_ => ())
              )
            if (exit != 0) sys.error(s"$friendly not found on PATH (looked for '$cmd').")
          }

          def ensurePortFree(host: String, port: Int): Unit = {
            var socket: java.net.ServerSocket = null
            try {
              socket = new java.net.ServerSocket()
              socket.setReuseAddress(true)
              socket.bind(new java.net.InetSocketAddress(host, port))
            } catch {
              case t: Throwable =>
                sys.error(
                  s"Port $host:$port is already in use; stop the existing process or set GOLEM_ROUTER_PORT to a free port.\n${t.getMessage}"
                )
            } finally
              if (socket != null)
                try socket.close()
                catch { case _: Throwable => () }
          }

          def waitForRouter(host: String, port: Int, attempts: Int): Unit = {
            var remaining = attempts
            var ok        = false
            while (!ok && remaining > 0) {
              val s = new java.net.Socket()
              try {
                s.connect(new java.net.InetSocketAddress(host, port), 1000)
                ok = true
              } catch {
                case _: Throwable =>
                  Thread.sleep(1000)
                  remaining -= 1
              } finally
                try s.close()
                catch { case _: Throwable => () }
            }
            if (!ok) sys.error(s"Timed out waiting for Golem router at $host:$port")
          }

          def stopServerFromPidFile(reason: String): Unit =
            if (pidFile.exists()) {
              val pidText = IO.read(pidFile).trim
              if (pidText.nonEmpty) {
                log.warn(s"[host-tests] $reason (pid=$pidText)")
                try scala.sys.process.Process(Seq("kill", "-TERM", pidText)).!
                catch { case _: Throwable => () }
                Thread.sleep(500)
                try scala.sys.process.Process(Seq("kill", "-KILL", pidText)).!
                catch { case _: Throwable => () }
              }
              IO.delete(pidFile)
            }

          // Always cleanup the server we start, even if deploy/invoke fails.
          try {
            val appDir = baseDir / "golem" / "host-tests" / "app"
            val componentSlug = component.replace(":", "-")
            val componentDir = appDir / "components-ts" / componentSlug
            if (!appDir.exists())
              sys.error(s"[host-tests] Missing app scaffold at ${appDir.getAbsolutePath}")
            if (!componentDir.exists())
              sys.error(s"[host-tests] Missing component dir at ${componentDir.getAbsolutePath}")

            val golemCliCmd = "golem-cli"
            val cliFlags = sys.env
              .get("GOLEM_CLI_FLAGS")
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(_.split("\\s+").toSeq)
              .getOrElse(Seq("--local"))
            val cliBase = Seq("env", "-u", "ARGV0", golemCliCmd)

            def run(cmd: Seq[String], label: String): Unit = {
              log.info(s"[host-tests] Running: ${cmd.mkString(" ")}")
              val out  = new StringBuilder
              val exit =
                scala.sys.process
                  .Process(cmd, appDir)
                  .!(
                    scala.sys.process.ProcessLogger(
                      line => { log.info(line); out.append(line).append('\n') },
                      line => { log.error(line); out.append(line).append('\n') }
                    )
                  )
              if (exit != 0) sys.error(s"$label failed with exit code $exit\n$out")
            }

            if (startServer) {
              requireCommandOnPath(golemCliCmd, "golem-cli")
              requireCommandOnPath(golemBin, "golem server binary")
              ensurePortFree(routerHost, routerPort)

              IO.createDirectory(dataDir)
              log.info(s"[host-tests] Starting local Golem server (log: ${serverLog.getAbsolutePath})")
              IO.write(serverLog, "")
              val serverCmd =
                s"""echo $$ > "${pidFile.getAbsolutePath}"; exec env -u ARGV0 "$golemBin" server run --clean --data-dir "${dataDir.getAbsolutePath}" --router-port ${routerPort.toString} >> "${serverLog.getAbsolutePath}" 2>&1"""
              scala.sys.process.Process(Seq("bash", "-lc", serverCmd), Some(baseDir)).run()
              Thread.sleep(500)
              waitForRouter(routerHost, routerPort, 60)
            }

            // Deploy via golem-cli directly (avoid sbt task ordering; ensures server is up first).
            run(cliBase ++ (cliFlags ++ Seq("--yes", "app", "deploy", component)), "golem-cli app deploy")

            val agentId = s"$component/$agentType()"
            val fn      = s"$component/$agentType.{$method}"

            val cmd =
              cliBase ++
                (cliFlags ++ (Seq("--yes", "agent", "invoke") ++ invokeFlags ++ Seq(agentId, fn, payload)))

            log.info(s"[host-tests] Invoking $agentId $fn")
            run(cmd, "golem-cli agent invoke")
          } finally {
            if (startServer) stopServerFromPidFile("Stopping golem server that was started by host-tests task")
          }
        }
    }.value,
    publish / skip := true
  )
  .enablePlugins(ScalaJSPlugin, cloud.golem.sbt.GolemPlugin)
  .dependsOn(zioGolemCoreJS, zioGolemToolingCore)

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
    scalaJSUseMainModuleInitializer := false,
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    cloud.golem.sbt.GolemPlugin.autoImport.golemAppName   := "scala-quickstart",
    cloud.golem.sbt.GolemPlugin.autoImport.golemComponent := "scala:quickstart-counter",
    Test / test                                           := Keys.streams.value.log.info("Skipping quickstart tests; run golemDeploy + repl script instead."),
    Test / testOnly                                       := (Test / test).value,
    Test / testQuick                                      := (Test / test).value,
    Test / testFrameworks                                 := Nil
  )
  .jsConfigure(_.enablePlugins(ScalaJSPlugin, cloud.golem.sbt.GolemPlugin))
  .jsConfigure(_.dependsOn(zioGolemCoreJS, zioGolemMacros))
  .jvmConfigure(_.dependsOn(zioGolemCoreJVM, zioGolemMacros))

lazy val zioGolemQuickstartJS  = zioGolemQuickstart.js
lazy val zioGolemQuickstartJVM = zioGolemQuickstart.jvm

lazy val zioGolemMillPlugin = project
  .in(file("golem/mill-plugin"))
  .settings(stdSettings("zio-golem-mill-plugin"))
  .settings(
    scalaVersion       := Scala3,
    crossScalaVersions := Seq(Scala3),
    scalacOptions -= "-Xcheck-macros",
    scalacOptions -= "-experimental",
    libraryDependencies ++= Seq(
      ("com.lihaoyi" %% "mill-libs-scalajslib" % golemMillLibsVersion % Provided)
        .exclude("org.scala-lang.modules", "scala-xml_3")
        .exclude("org.scala-lang.modules", "scala-collection-compat_3"),
      "org.scala-lang.modules" % "scala-xml_2.13"               % "2.4.0"  % Provided,
      "org.scala-lang.modules" % "scala-collection-compat_2.13" % "2.13.0" % Provided,
      "com.lihaoyi"           %% "os-lib"                       % "0.11.6"
    ),
    publish / skip := true
  )
  .dependsOn(zioGolemToolingCore)

lazy val zioGolemSbtPlugin = project
  .in(file("golem/sbt-plugin"))
  .settings(
    name               := "zio-golem-sbt-plugin",
    sbtPlugin          := true,
    scalaVersion       := "2.12.19",
    crossScalaVersions := Seq("2.12.19"),
    libraryDependencies += Defaults.sbtPluginExtra(
      "org.scala-js" % "sbt-scalajs" % "1.20.1",
      sbtBinaryVersion.value,
      scalaBinaryVersion.value
    ),
    publish / skip := true
  )
  .dependsOn(zioGolemToolingCore)

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
