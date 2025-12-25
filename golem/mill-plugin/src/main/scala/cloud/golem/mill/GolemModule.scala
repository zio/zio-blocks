package cloud.golem.mill

import mill._
import mill.api.PathRef
import mill.scalajslib._
import os.Path

object GolemExports {
  object TsType {
    val string: String  = "string"
    val number: String  = "number"
    val boolean: String = "boolean"
    val void: String    = "void"
    val any: String     = "any"
  }

  final case class Field(name: String, tsType: String)

  sealed trait Constructor
  object Constructor {
    case object NoArg extends Constructor
    final case class Scalar(argName: String, tsType: String, scalaFactoryArgs: Seq[String] = Nil) extends Constructor
    final case class Positional(params: Seq[Field], scalaFactoryArgs: Seq[String] = Nil) extends Constructor
    final case class Record(inputTypeName: String, fields: Seq[Field], scalaFactoryArgs: Seq[String] = Nil)
        extends Constructor
  }

  final case class MethodParam(name: String, tsType: String, implArgExpr: String = "")

  final case class Method(
    name: String,
    tsReturnType: String,
    isAsync: Boolean = true,
    params: Seq[MethodParam] = Nil,
    implMethodName: String = ""
  )

  final case class Export(
    agentName: String,
    traitClass: String,
    className: String = "",
    scalaFactory: String = "",
    scalaShimImplClass: String,
    constructor: Constructor,
    typeDeclarations: Seq[String] = Nil,
    methods: Seq[Method]
  )
}

trait GolemModule extends ScalaJSModule {

  /** Command used to invoke Node.js (default: `node`). */
  def golemNodeCommand: String = "node"

  /** Command used to invoke npm (default: `npm`). */
  def golemNpmCommand: String = "npm"

  /** Directory where npm installs dependencies. */
  def golemNodeModulesDir: Path = moduleDir / "node_modules"

  /** Runs `npm install` (if necessary) before executing Scala.js bundles. */
  def golemSetup: T[Unit] = Task {
    val log     = Task.log
    val modules = golemNodeModulesDir
    if (os.exists(modules)) {
      log.info("node_modules already present; skipping npm install.")
    } else {
      log.info("Installing npm dependencies via `npm install`...")
      val proc = os.proc(golemNpmCommand, "install").call(cwd = moduleDir, check = false)
      if (proc.exitCode != 0)
        throw new RuntimeException(s"`npm install` failed with exit code ${proc.exitCode}")
    }
  }

  /** Runs fastLinkJS and returns the generated bundle path. */
  def golemFastLink: T[PathRef] = Task {
    val log    = Task.log
    val report = fastLinkJS()
    val bundle = report.dest
    log.info(s"Golem quickstart bundle linked at ${bundle.path}")
    bundle
  }

  // --- Generic public primitives (Phase 2 of public SDK plan) ---

  /** Root dir for generated golem apps (default: `.golem-apps`). */
  def golemAppRoot: Path = os.pwd / ".golem-apps"

  /** App name (default: Mill module name). */
  def golemAppName: String = moduleDir.last

  /**
   * Qualified component name (e.g. `org:component`). Must be overridden to use
   * primitives.
   */
  def golemComponent: String = ""

  /** Component template for golem-cli (default: `ts`). */
  def golemComponentTemplate: String = "ts"

  /** golem-cli command (default: `golem-cli`). */
  def golemCli: String = "golem-cli"

  /**
   * golem-cli flags (default: `--local`).
   *
   * Supports env override: `GOLEM_CLI_FLAGS="--cloud -p my-profile"` to switch
   * modes without editing build files.
   */
  def golemCliFlags: Seq[String] =
    sys.env
      .get("GOLEM_CLI_FLAGS")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.split("\\s+").toSeq)
      .getOrElse(Seq("--local"))

  // ---------------------------------------------------------------------------
  // App run conveniences (deploy + invoke / deploy + repl script)
  // ---------------------------------------------------------------------------

  /** Agent id used by golemAppRun (golem-cli agent id string). */
  def golemRunAgentId: String = ""

  /** Fully-qualified function used by golemAppRun (golem-cli function string). */
  def golemRunFunction: String = ""

  /** Default args (WAVE literals) passed to golemAppRun. */
  def golemRunArgs: Seq[String] = Seq.empty

  /** When true, golemAppRun/golemAppRunScript will run golemDeploy first (default: true). */
  def golemRunDeployFirst: Boolean = true

  /**
   * Optional publish step to run before deploy+run.
   *
   * Default is a no-op. In monorepo / snapshot workflows, override this to publishLocal or a custom task.
   */
  def golemRunPublish: T[Unit] = Task {}

  /** When true, run golemRunPublish before deploy+run (default: false). */
  def golemRunPublishFirst: Boolean = false

  /** Timeout (seconds) for golem-cli repl used by golemAppRunScript (default: 60). */
  def golemReplTimeoutSec: Int =
    sys.env.get("GOLEM_REPL_TIMEOUT_SEC").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(60)

  /** When true, pass --disable-stream to golem-cli repl (default: true). */
  def golemReplDisableStream: Boolean = true

  /** Script file path for golemAppRunScript (default: from GOLEM_REPL_SCRIPT). */
  def golemRunScriptFile: String = sys.env.getOrElse("GOLEM_REPL_SCRIPT", "")

  // ---------------------------------------------------------------------------
  // Local runtime management (developer convenience)
  // ---------------------------------------------------------------------------

  /** Router host used for local server management. */
  def golemRouterHost: String = sys.env.getOrElse("GOLEM_ROUTER_HOST", "127.0.0.1")

  /** Router port used for local server management. */
  def golemRouterPort: Int =
    sys.env.get("GOLEM_ROUTER_PORT").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(9881)

  /** Data dir used when starting a local `golem server run`. */
  def golemLocalDataDir: Path = os.pwd / ".golem-local"

  /** When true (default), golemBuild/golemDeploy/golemInvoke will auto-start local server when using `--local`. */
  def golemStartLocalServer: Boolean = true

  /** PID file for managed local server. */
  def golemLocalServerPidFile: Path = golemLocalDataDir / "server.pid"

  /** Log file for managed local server. */
  def golemLocalServerLogFile: Path = golemLocalDataDir / "server.log"

  /** Start local golem server/router if needed (no-op if already reachable or not in `--local` mode). */
  def golemLocalUp: T[Unit] = Task {
    val log   = Task.log
    val flags = golemCliFlags
    val isLocal = flags.contains("--local") && !flags.contains("--cloud")

    if (!isLocal) log.info("[golem] golemLocalUp: GOLEM_CLI_FLAGS is not local; skipping local server management.")
    else if (!golemStartLocalServer) log.info("[golem] golemLocalUp: golemStartLocalServer=false; skipping.")
    else {
      val host    = golemRouterHost
      val port    = golemRouterPort
      val dataDir = golemLocalDataDir
      val pidFile = golemLocalServerPidFile
      val logFile = golemLocalServerLogFile
      val golemBin = sys.env.getOrElse("GOLEM_BIN", "golem")

      def routerReachable(): Boolean =
        try {
          val sock = new java.net.Socket()
          sock.connect(new java.net.InetSocketAddress(host, port), 500)
          sock.close()
          true
        } catch { case _: Throwable => false }

      if (routerReachable()) log.info(s"[golem] golemLocalUp: router already reachable at $host:$port")
      else {
        if (os.exists(pidFile)) {
          val stale = os.read(pidFile).trim
          if (stale.nonEmpty) {
            log.info(s"[golem] golemLocalUp: found stale pid file (pid=$stale); attempting to stop")
            os.proc("kill", "-TERM", stale).call(check = false)
            Thread.sleep(500)
            os.proc("kill", "-KILL", stale).call(check = false)
          }
          os.remove.all(pidFile)
        }

        log.info(s"[golem] Starting local golem server on $host:$port (dataDir=$dataDir)")
        os.makeDir.all(dataDir)
        os.write.over(logFile, "")

        val cmd =
          Seq(
            "env",
            "-u",
            "ARGV0",
            golemBin,
            "server",
            "run",
            "--clean",
            "--data-dir",
            dataDir.toString,
            "--router-port",
            port.toString
          )

        val pb = new ProcessBuilder(cmd: _*)
        pb.directory(os.pwd.toIO)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toIO))
        val p   = pb.start()
        val pid = p.pid().toString
        os.write.over(pidFile, pid, createFolders = true)

        val deadline = System.nanoTime() + (60L * 1000L * 1000L * 1000L) // 60s
        while (!routerReachable() && System.nanoTime() < deadline) Thread.sleep(500)
        if (!routerReachable()) throw new RuntimeException(s"Local router did not become reachable at $host:$port")
        log.info(s"[golem] Local router is reachable at $host:$port (pid=$pid)")
      }
    }
  }

  /** Stop the managed local golem server/router (no-op if none is running). */
  def golemLocalDown: T[Unit] = Task {
    val log     = Task.log
    val pidFile = golemLocalServerPidFile
    if (!os.exists(pidFile)) log.info("[golem] No managed local server PID file found; nothing to stop.")
    else {
      val pid = os.read(pidFile).trim
      if (pid.nonEmpty) {
        log.info(s"[golem] Stopping managed local server (pid=$pid)")
        os.proc("kill", "-TERM", pid).call(check = false)
        Thread.sleep(500)
        os.proc("kill", "-KILL", pid).call(check = false)
      }
      os.remove.all(pidFile)
    }
  }

  /** Deploy (optional) then invoke a configured method via golem-cli (developer convenience). */
  def golemAppRun: T[Unit] = Task {
    val log = Task.log
    golemLocalUp()
    if (golemRunPublishFirst) golemRunPublish()
    if (golemRunDeployFirst) golemDeployUpdateAll()

    val agentId = golemRunAgentId.trim
    val fn      = golemRunFunction.trim
    if (agentId.isEmpty || fn.isEmpty)
      throw new RuntimeException(
        "golemAppRun requires golemRunAgentId and golemRunFunction to be set.\n" +
          "Example:\n" +
          "  def golemRunAgentId = \"org:component/agent-type()\"\n" +
          "  def golemRunFunction = \"org:component/agent-type.{method}\""
      )

    val appDir  = golemWire() / os.up / os.up
    val cli     = golemCli
    val flags   = golemCliFlags
    val args    = golemRunArgs
    val cmdBase = Seq("env", "-u", "ARGV0", cli) ++ (flags ++ Seq("--yes", "agent", "invoke", agentId, fn))
    val cmd     = cmdBase ++ args
    log.info(s"[golem] golemAppRun: invoking $agentId $fn")
    val exit = runWithTimeout(cmd, appDir, "agent invoke", golemTimeoutSec, log)
    if (exit != 0) throw new RuntimeException(s"golem-cli agent invoke failed with exit code $exit")
  }

  /**
   * Deploy (optional) then run a golem-cli repl script.
   *
   * Usage:
   *   mill -i <module>.golemAppRunScript <path-to.rib>
   */
  def golemAppRunScript: T[Unit] = Task {
    val log = Task.log
    golemLocalUp()
    if (golemRunPublishFirst) golemRunPublish()
    if (golemRunDeployFirst) golemDeployUpdateAll()

    val scriptFile = golemRunScriptFile.trim
    if (scriptFile.isEmpty)
      throw new RuntimeException("golemAppRunScript requires golemRunScriptFile (or env GOLEM_REPL_SCRIPT) to be set.")

    val f0  = os.Path(scriptFile, os.pwd)
    val f   = if (os.exists(f0)) f0 else throw new RuntimeException(s"Script file not found: $f0")
    val appDir = golemWire() / os.up / os.up
    val cli    = golemCli
    val flags  = golemCliFlags
    val base =
      Seq("env", "-u", "ARGV0", cli) ++ (flags ++ Seq("--yes", "repl", golemComponent, "--script-file", f.toString))
    val cmd = base ++ (if (golemReplDisableStream) Seq("--disable-stream") else Nil)
    log.info(s"[golem] golemAppRunScript: running repl script $f")
    val exit = runWithTimeout(cmd, appDir, "repl", golemReplTimeoutSec, log)
    if (exit != 0) throw new RuntimeException(s"golem-cli repl failed with exit code $exit")
  }

  /** Timeout in seconds for golem-cli steps. */
  def golemTimeoutSec: Int = 180

  /**
   * Filename to copy Scala.js bundle to inside component `src/` (default:
   * `<golemAppName>.js`).
   */
  def golemBundleFileName: String = s"${golemAppName}.js"

  /**
   * TypeScript guest bridge written to `src/main.ts` (required for golemWire).
   */
  def golemBridgeMainTs: String = ""

  /**
   * Bridge generation spec; used when golemBridgeMainTs is empty.
   *
   * Note: for Mill, the recommended public workflow is **provider-class**
   * (`golemBridgeSpecProviderClass`) or a checked-in manifest
   * (`golemBridgeSpecManifestPath`). These avoid classloader edge cases that
   * can happen when constructing tooling-core objects directly in `build.mill`.
   */
  def golemBridgeSpec: AnyRef = null

  /**
   * Optional fully-qualified class name that can generate the bridge via
   * tooling-core (metadata-driven).
   *
   * The class must be JVM-loadable and have a public no-arg constructor. It
   * should implement
   * {@code java.util.function.Supplier[cloud.golem.tooling.bridge.BridgeSpec]}.
   */
  def golemBridgeSpecProviderClass: String = ""

  /**
   * Optional BridgeSpec manifest file (BridgeSpecManifest .properties); used
   * when spec/provider are unset.
   *
   * Can be absolute or relative to the Mill workspace.
   */
  def golemBridgeSpecManifestPath: String = ""

  /**
   * Scala-only export configuration; when non-empty, tooling generates a BridgeSpec manifest automatically
   * (written under the module's Task.dest).
   */
  def golemExports: Seq[GolemExports.Export] = Seq.empty

  /**
   * When `golemExports` is empty, attempt to auto-detect exports from compiled classes annotated with
   * `@agentDefinition` / `@agentImplementation`.
   *
   * Currently this auto mode supports only "primitive-only" agents (String/Boolean/numbers, Option[T], List[T]).
   * More complex shapes still require explicit `golemExports`.
   */
  def golemAutoExports: Boolean = true

  /** JS export name for the generated Scala shim object (default: __golemInternalScalaAgents). */
  def golemScalaShimExportTopLevel: String = "__golemInternalScalaAgents"

  /** Scala object name for the generated Scala shim. */
  def golemScalaShimObjectName: String = "GolemInternalScalaAgents"

  /** Scala package for the generated Scala shim (internal). */
  def golemScalaShimPackage: String = "cloud.golem.internal"

  /** Generates the internal Scala shim into managed sources (compile-time). */
  def golemGenerateScalaShim: T[Seq[PathRef]] = Task {
    val log          = Task.log
    val compileRes = compile()
    val compileCp = compileClasspath().map(_.path).toSeq
    val manifest = ensureBridgeSpecManifest(Task.dest, log, compileRes.classes.path, compileCp)
    if (manifest.isEmpty) Seq.empty
    else {
      val src =
        Tooling.generateScalaShimFromManifest(
          manifest.get,
          golemScalaShimExportTopLevel,
          golemScalaShimObjectName,
          golemScalaShimPackage
        )
      if (src.trim.isEmpty) Seq.empty
      else {
        val outDir = Task.dest / "golem" / "internal"
        os.makeDir.all(outDir)
        val outFile = outDir / s"${golemScalaShimObjectName}.scala"
        os.write.over(outFile, src, createFolders = true)
        log.info(s"[golem] Generated Scala shim at $outFile")
        Seq(PathRef(outFile))
      }
    }
  }

  override def generatedSources: T[Seq[PathRef]] = Task {
    super.generatedSources() ++ golemGenerateScalaShim()
  }

  /**
   * Agent id to invoke (e.g. `org:component/agent-type()` or with ctor args).
   */
  def golemInvokeAgentId: String = ""

  /**
   * Fully-qualified WIT function name to invoke (e.g.
   * `org:component/agent-type.{method-name}`).
   */
  def golemInvokeFunction: String = ""

  /** Extra invocation args (WAVE literals), passed as additional CLI args. */
  def golemInvokeArgs: Seq[String] = Seq.empty

  /**
   * Ensures the golem app/component scaffold exists; returns the component dir.
   *
   * Note: Mill tasks can only write within their own `Task.dest`. To keep this
   * deterministic and valid in Mill, `golemScaffold` delegates to `golemWire`,
   * which owns the writes.
   */
  def golemScaffold: T[Path] = Task {
    golemWire()
  }

  /**
   * Copies Scala.js bundle and writes `src/main.ts`; returns the component dir.
   */
  def golemWire: T[Path] = Task {
    val log       = Task.log
    val appRoot   = Task.dest / ".golem-apps"
    val appName   = golemAppName
    val component = golemComponent

    if (component.trim.isEmpty)
      throw new RuntimeException(
        "golemComponent is not set. Override it (e.g. `def golemComponent = \"org:component\"`)."
      )

    os.makeDir.all(appRoot)
    val appDir = appRoot / appName
    if (!os.exists(appDir)) {
      log.info(s"[golem] Creating deterministic app scaffold at $appDir")
      Tooling.ensureTsAppScaffold(appRoot.toNIO, appName, component, msg => log.info(msg))
    }

    val slug         = component.replace(":", "-")
    val componentDir = appDir / "components-ts" / slug
    if (!os.exists(componentDir)) {
      log.info(s"[golem] Creating deterministic TS component scaffold for $component")
      Tooling.ensureTsComponentScaffold(appDir.toNIO, component, msg => log.info(msg))
    }
    stripHttpApiFromGolemYaml(componentDir, log)

    val bundleDir     = fastLinkJS().dest.path
    val bundle        = firstJsFile(bundleDir)
    val bundleName    = golemBundleFileName
    val spec          = golemBridgeSpec
    val providerClass = golemBridgeSpecProviderClass.trim
    val compileRes    = compile()
    val compileCp     = compileClasspath().map(_.path).toSeq
    val manifestAbs   = ensureBridgeSpecManifest(Task.dest, log, compileRes.classes.path, compileCp)
    val mainTs        =
      if (golemBridgeMainTs.trim.nonEmpty) golemBridgeMainTs
      else if (Tooling.bridgeSpecHasAgents(spec)) Tooling.generateBridgeMainTsFromBridgeSpec(spec)
      else if (providerClass.nonEmpty) {
        // Compile first so the provider class is loadable.
        val cps = compileCp.map(_.toNIO).toSeq
        Tooling.generateBridgeMainTsFromProvider(cps, compileRes.classes.path.toNIO, providerClass)
      } else if (manifestAbs.nonEmpty) Tooling.generateBridgeMainTsFromManifest(manifestAbs.get)
      else ""

    if (mainTs.trim.isEmpty)
      throw new RuntimeException(
        "No bridge configured. Override golemBridgeMainTs or set golemBridgeSpecProviderClass or golemBridgeSpecManifestPath (recommended)."
      )

    Tooling.wireTsComponent(componentDir.toNIO, bundle.toNIO, bundleName, mainTs, msg => log.info(msg))
    componentDir
  }

  private def ensureBridgeSpecManifest(
    taskDest: os.Path,
    log: mill.api.Logger,
    classesDir: os.Path,
    compileCp: Seq[os.Path]
  ): Option[java.nio.file.Path] = {
    val configured = golemBridgeSpecManifestPath.trim
    if (configured.nonEmpty) {
      val raw = java.nio.file.Paths.get(configured)
      val abs = if (raw.isAbsolute) raw else os.pwd.toNIO.resolve(raw)
      if (!java.nio.file.Files.exists(abs))
        throw new RuntimeException("BridgeSpec manifest not found: " + abs.toAbsolutePath())
      Some(abs)
    } else if (golemExports.nonEmpty) {
      val out = (taskDest / "golem" / "bridge").toNIO
      java.nio.file.Files.createDirectories(out)
      val file = out.resolve("bridge-spec.properties")
      writeBridgeSpecManifest(file, golemExports, golemBundleFileName, golemScalaShimExportTopLevel)
      log.info(s"[golem] Wrote BridgeSpec manifest from golemExports at $file")
      Some(file)
    } else if (!golemAutoExports) None
    else {
      val out = (taskDest / "golem" / "bridge").toNIO
      java.nio.file.Files.createDirectories(out)
      val file = out.resolve("bridge-spec.properties")

      // Auto-detect exports from compiled classes (primitive-only).
      val cpUrls =
        (compileCp.map(_.toNIO) :+ classesDir.toNIO).distinct.map(_.toUri.toURL).toArray
      val cl     = new java.net.URLClassLoader(cpUrls, null)

      def isAnnByName(a: java.lang.annotation.Annotation, fqcn: String): Boolean =
        a.annotationType().getName == fqcn

      def defaultAgentNameFromTrait(traitClass: String): String = {
        val simple0 = traitClass.split('.').lastOption.getOrElse("agent")
        val simple  = simple0.stripSuffix("$")
        simple
          .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
          .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
          .toLowerCase
      }

      def tsTypeOf(tpe: java.lang.reflect.Type): Either[String, String] = {
        def mapClass(c: Class[?]): Option[String] = {
          if (c == classOf[String]) Some("string")
          else if (c == java.lang.Boolean.TYPE || c == classOf[java.lang.Boolean]) Some("boolean")
          else if (
            c == java.lang.Integer.TYPE || c == classOf[java.lang.Integer] ||
            c == java.lang.Long.TYPE || c == classOf[java.lang.Long] ||
            c == java.lang.Double.TYPE || c == classOf[java.lang.Double] ||
            c == java.lang.Float.TYPE || c == classOf[java.lang.Float] ||
            c == java.lang.Short.TYPE || c == classOf[java.lang.Short] ||
            c == java.lang.Byte.TYPE || c == classOf[java.lang.Byte]
          ) Some("number")
          else if (c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE) Some("void")
          else None
        }

        tpe match {
          case c: Class[_] =>
            mapClass(c).toRight(s"Unsupported type for auto exports: ${c.getName}")
          case p: java.lang.reflect.ParameterizedType =>
            val raw  = p.getRawType.asInstanceOf[Class[?]].getName
            val args = p.getActualTypeArguments.toVector
            raw match {
              case "scala.Option" =>
                if (args.length != 1) Left(s"Unsupported Option arity for auto exports: ${p.getTypeName}")
                else tsTypeOf(args.head).map(inner => s"$inner | null")
              case "scala.collection.immutable.List" | "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
                if (args.length != 1) Left(s"Unsupported collection arity for auto exports: ${p.getTypeName}")
                else tsTypeOf(args.head).map(inner => s"$inner[]")
              case "scala.concurrent.Future" =>
                Left("Future[...] is only supported as a method return type in auto exports")
              case other =>
                Left(s"Unsupported generic type for auto exports: $other (${p.getTypeName})")
            }
          case other =>
            Left(s"Unsupported type for auto exports: ${other.getTypeName}")
        }
      }

      def scalaParamTypeOf(tpe: java.lang.reflect.Type): String = {
        def mapClass(c: Class[?]): String = {
          if (c == classOf[String]) "String"
          else if (c == java.lang.Boolean.TYPE || c == classOf[java.lang.Boolean]) "Boolean"
          else if (c == java.lang.Integer.TYPE || c == classOf[java.lang.Integer]) "Int"
          else if (c == java.lang.Long.TYPE || c == classOf[java.lang.Long]) "Long"
          else if (c == java.lang.Double.TYPE || c == classOf[java.lang.Double]) "Double"
          else if (c == java.lang.Float.TYPE || c == classOf[java.lang.Float]) "Float"
          else if (c == java.lang.Short.TYPE || c == classOf[java.lang.Short]) "Short"
          else if (c == java.lang.Byte.TYPE || c == classOf[java.lang.Byte]) "Byte"
          else if (c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE) "Unit"
          else "js.Any"
        }

        tpe match {
          case c: Class[_] =>
            mapClass(c)
          case p: java.lang.reflect.ParameterizedType =>
            val raw = p.getRawType.asInstanceOf[Class[?]].getName
            raw match {
              case "scala.Option" =>
                "js.Any"
              case "scala.collection.immutable.List" | "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
                "js.Array[js.Any]"
              case _ =>
                "js.Any"
            }
          case _ =>
            "js.Any"
        }
      }

      final case class Detected(
        agentName: String,
        traitClass: String,
        implClass: String,
        ctor: Vector[(String, String, String)],
        methods: Vector[(String, Boolean, String, Vector[(String, String, String)])]
      )

      val classDir = classesDir
      val classNames =
        os.walk(classDir)
          .filter(p => p.ext == "class" && p.last != "module-info.class" && p.last != "package-info.class")
          .map(p => p.relativeTo(classDir).toString.stripSuffix(".class").replace('/', '.'))
          .toVector

      val loaded = classNames.flatMap { n =>
        try Some(Class.forName(n, false, cl))
        catch { case _: Throwable => None }
      }

      val baseAgentCls = Class.forName("cloud.golem.sdk.BaseAgent", false, cl)

      def readAgentDefinitionAnn(cls: Class[?]): Option[String] =
        cls.getAnnotations
          .find(a => isAnnByName(a, "cloud.golem.runtime.annotations.agentDefinition"))
          .flatMap { ann =>
            val tn = Option(ann.annotationType().getMethod("typeName").invoke(ann)).map(_.toString).getOrElse("")
            Option(tn).map(_.trim).filter(_.nonEmpty)
          }

      def isAgentTrait(cls: Class[?]): Boolean =
        cls.isInterface && cls.getName != "cloud.golem.sdk.BaseAgent" && baseAgentCls.isAssignableFrom(cls)

      val traits: Map[String, String] =
        loaded.flatMap { c =>
          if (!isAgentTrait(c)) None
          else {
            val typeName = readAgentDefinitionAnn(c).getOrElse(defaultAgentNameFromTrait(c.getName))
            Some(c.getName -> typeName)
          }
        }.toMap

      val impls =
        loaded.filter(c => !c.isInterface && baseAgentCls.isAssignableFrom(c) && !java.lang.reflect.Modifier.isAbstract(c.getModifiers))

      val detected: Vector[Detected] =
        impls.flatMap { impl =>
          val implementedTraits = impl.getInterfaces.toVector.map(_.getName).filter(traits.contains)
          implementedTraits match {
            case Vector(traitName) =>
              val agentName = traits(traitName)
              val ctor = impl.getConstructors.toVector.sortBy(_.getParameterCount).headOption.orNull
              if (ctor == null) None
              else {
                val ctorParamsE =
                  ctor.getGenericParameterTypes.toVector.zipWithIndex.map { case (tpe, idx) =>
                    tsTypeOf(tpe).map(ts => (s"arg$idx", ts, scalaParamTypeOf(tpe)))
                  }
                if (ctorParamsE.exists(_.isLeft)) None
                else {
                  val ctorParams = ctorParamsE.collect { case Right(v) => v }.toVector

                  val traitCls = Class.forName(traitName, false, cl)
                  val methods0 =
                    traitCls.getDeclaredMethods.toVector
                      .filter(m => m.getDeclaringClass == traitCls)
                      .filterNot(_.isSynthetic)
                      .filterNot(m => m.getName.contains("$"))

                  val methodsE: Vector[Either[String, (String, Boolean, String, Vector[(String, String, String)])]] =
                    methods0.map { m =>
                      m.getGenericReturnType match {
                        case p: java.lang.reflect.ParameterizedType
                            if p.getRawType.asInstanceOf[Class[?]].getName == "scala.concurrent.Future" =>
                          val outTpe = p.getActualTypeArguments.headOption.getOrElse(classOf[Object])
                          val outTsE = tsTypeOf(outTpe)
                          val paramsE =
                            m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt, i) =>
                              tsTypeOf(pt).map(ts => (s"arg$i", ts, scalaParamTypeOf(pt)))
                            }
                          if (outTsE.isLeft) Left(outTsE.swap.getOrElse(""))
                          else if (paramsE.exists(_.isLeft)) Left(paramsE.collectFirst { case Left(e) => e }.get)
                          else Right((m.getName, true, outTsE.toOption.get, paramsE.collect { case Right(v) => v }))

                        case c: Class[?] if c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE =>
                          val paramsE =
                            m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt, i) =>
                              tsTypeOf(pt).map(ts => (s"arg$i", ts, scalaParamTypeOf(pt)))
                            }
                          if (paramsE.exists(_.isLeft)) Left(paramsE.collectFirst { case Left(e) => e }.get)
                          else Right((m.getName, false, "void", paramsE.collect { case Right(v) => v }))

                        case other =>
                          Left(s"Auto exports only supports Future[...] or Unit return types; found ${other.getTypeName} on $traitName.${m.getName}")
                      }
                    }

                  if (methodsE.exists(_.isLeft)) None
                  else Some(Detected(agentName, traitName, impl.getName, ctorParams, methodsE.collect { case Right(v) => v }))
                }
              }
            case _ => None
          }
        }.toVector

      if (detected.isEmpty) None
      else {
        val scalaBundleImport = s"./${golemBundleFileName}"
        val sb = new StringBuilder()
        sb.append("scalaBundleImport=").append(scalaBundleImport).append("\n")
        sb.append("scalaAgentsExpr=").append(s"(scalaExports as any).${golemScalaShimExportTopLevel.trim} ?? (globalThis as any).${golemScalaShimExportTopLevel.trim}").append("\n\n")

        detected.zipWithIndex.foreach { case (e, idx) =>
          val p = s"agents.$idx."
          val className    = "Scala" + e.traitClass.split('.').lastOption.getOrElse("Agent").stripSuffix("$")
          val scalaFactory = "new" + e.traitClass.split('.').lastOption.getOrElse("Agent").stripSuffix("$")

          sb.append(p).append("agentName=").append(e.agentName).append("\n")
          sb.append(p).append("className=").append(className).append("\n")
          sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")
          sb.append(p).append("scalaShimImplClass=").append(e.implClass).append("\n")

          e.ctor match {
            case Vector() =>
              sb.append(p).append("constructor.kind=noarg\n")
            case Vector((argName, tsType, scalaType)) =>
              sb.append(p).append("constructor.kind=scalar\n")
              sb.append(p).append("constructor.argName=").append(argName).append("\n")
              sb.append(p).append("constructor.tsType=").append(tsType).append("\n")
              sb.append(p).append("constructor.scalaType=").append(scalaType).append("\n")
            case params =>
              sb.append(p).append("constructor.kind=positional\n")
              params.zipWithIndex.foreach { case ((n, t, st), pi) =>
                sb.append(p).append(s"constructor.param.$pi.name=").append(n).append("\n")
                sb.append(p).append(s"constructor.param.$pi.tsType=").append(t).append("\n")
                sb.append(p).append(s"constructor.param.$pi.scalaType=").append(st).append("\n")
              }
          }

          e.methods.zipWithIndex.foreach { case ((mName, isAsync, tsRet, params), mi) =>
            val mp = p + s"method.$mi."
            sb.append(mp).append("name=").append(mName).append("\n")
            sb.append(mp).append("isAsync=").append(if (isAsync) "true" else "false").append("\n")
            sb.append(mp).append("tsReturnType=").append(tsRet).append("\n")
            sb.append(mp).append("implMethodName=").append(mName).append("\n")
            params.zipWithIndex.foreach { case ((pn, pt, pst), pi) =>
              val pp = mp + s"param.$pi."
              sb.append(pp).append("name=").append(pn).append("\n")
              sb.append(pp).append("tsType=").append(pt).append("\n")
              sb.append(pp).append("scalaType=").append(pst).append("\n")
              sb.append(pp).append("implArgExpr=").append("").append("\n")
            }
          }

          sb.append("\n")
        }

        java.nio.file.Files.write(file, sb.result().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        log.info(s"[golem] Wrote BridgeSpec manifest from auto-detected exports at $file")
        Some(file)
      }
    }
  }

  private def writeBridgeSpecManifest(
    file: java.nio.file.Path,
    exports: Seq[GolemExports.Export],
    bundleFileName: String,
    scalaShimExportTopLevel: String
  ): Unit = {
    def defaultTsClassNameFromTrait(traitClass: String): String = {
      val simple0 = traitClass.split('.').lastOption.getOrElse("Agent")
      val simple  = simple0.stripSuffix("$")
      "Scala" + simple
    }
    def defaultScalaFactoryFromTrait(traitClass: String): String = {
      val simple0 = traitClass.split('.').lastOption.getOrElse("Agent")
      val simple  = simple0.stripSuffix("$")
      "new" + simple
    }
    def scalaAgentsExprForExportTopLevel(exportTopLevel: String): String = {
      val n = exportTopLevel.trim
      s"(scalaExports as any).$n ?? (globalThis as any).$n"
    }

    val sb = new StringBuilder()
    sb.append("scalaBundleImport=").append(s"./$bundleFileName").append("\n")
    sb.append("scalaAgentsExpr=").append(scalaAgentsExprForExportTopLevel(scalaShimExportTopLevel)).append("\n\n")

    exports.zipWithIndex.foreach { case (e, idx) =>
      val p = s"agents.$idx."
      val className =
        if (e.className.trim.nonEmpty) e.className.trim
        else defaultTsClassNameFromTrait(e.traitClass)
      val scalaFactory =
        if (e.scalaFactory.trim.nonEmpty) e.scalaFactory.trim
        else defaultScalaFactoryFromTrait(e.traitClass)

      sb.append(p).append("agentName=").append(e.agentName).append("\n")
      sb.append(p).append("className=").append(className).append("\n")
      sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")

      e.constructor match {
        case GolemExports.Constructor.NoArg =>
          sb.append(p).append("constructor.kind=noarg\n")
        case GolemExports.Constructor.Scalar(argName, tsType, scalaFactoryArgs) =>
          sb.append(p).append("constructor.kind=scalar\n")
          sb.append(p).append("constructor.tsType=").append(tsType).append("\n")
          sb.append(p).append("constructor.argName=").append(argName).append("\n")
          scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
            sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
          }
        case GolemExports.Constructor.Positional(params, scalaFactoryArgs) =>
          sb.append(p).append("constructor.kind=positional\n")
          params.zipWithIndex.foreach { case (par, pi) =>
            sb.append(p).append(s"constructor.param.$pi.name=").append(par.name).append("\n")
            sb.append(p).append(s"constructor.param.$pi.tsType=").append(par.tsType).append("\n")
          }
          scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
            sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
          }
        case GolemExports.Constructor.Record(inputTypeName, fields, scalaFactoryArgs) =>
          sb.append(p).append("constructor.kind=record\n")
          sb.append(p).append("constructor.inputTypeName=").append(inputTypeName).append("\n")
          fields.zipWithIndex.foreach { case (f, fi) =>
            sb.append(p).append(s"constructor.field.$fi.name=").append(f.name).append("\n")
            sb.append(p).append(s"constructor.field.$fi.tsType=").append(f.tsType).append("\n")
          }
          scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
            sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
          }
      }

      e.typeDeclarations.zipWithIndex.foreach { case (decl, di) =>
        sb.append(p).append(s"typeDecl.$di=").append(decl).append("\n")
      }

      sb.append(p).append("scalaShimImplClass=").append(e.scalaShimImplClass).append("\n")

      e.methods.zipWithIndex.foreach { case (m, mi) =>
        val mp = p + s"method.$mi."
        sb.append(mp).append("name=").append(m.name).append("\n")
        sb.append(mp).append("isAsync=").append(if (m.isAsync) "true" else "false").append("\n")
        sb.append(mp).append("tsReturnType=").append(m.tsReturnType).append("\n")
        sb.append(mp).append("implMethodName=").append(m.implMethodName).append("\n")
        m.params.zipWithIndex.foreach { case (par, pi) =>
          val pp = mp + s"param.$pi."
          sb.append(pp).append("name=").append(par.name).append("\n")
          sb.append(pp).append("tsType=").append(par.tsType).append("\n")
          sb.append(pp).append("implArgExpr=").append(par.implArgExpr).append("\n")
        }
      }

      sb.append("\n")
    }

    os.write.over(os.Path(file), sb.result(), createFolders = true)
  }

  def golemBuild: T[Unit] = Task {
    val log          = Task.log
    golemLocalUp()
    val componentDir = golemWire()
    val appDir       = componentDir / os.up / os.up
    val component    = golemComponent
    val cli          = golemCli
    val flags        = golemCliFlags
    val timeout      = golemTimeoutSec

    Tooling.requireCommandOnPath(cli, "golem-cli")
    log.info("[golem] Running golem-cli app build")
    val exit = runWithTimeout(
      Seq("env", "-u", "ARGV0", cli) ++ (flags ++ Seq("--yes", "app", "build", component)),
      appDir,
      "app build",
      timeout,
      log
    )
    if (exit != 0) throw new RuntimeException(s"golem-cli app build failed with exit code $exit")
  }

  /** Default update mode for golemDeployUpdate (auto|manual). */
  def golemUpdateMode: String = "auto"

  /** When true, pass --await to golem-cli agent update (default: true). */
  def golemUpdateAwait: Boolean = true

  /** Optional explicit target version for golemDeployUpdate (default: latest). */
  def golemUpdateTargetVersion: Option[String] = None

  def golemDeploy: T[Unit] = Task {
    val log          = Task.log
    golemLocalUp()
    val componentDir = golemWire()
    val appDir       = componentDir / os.up / os.up
    val component    = golemComponent
    val cli          = golemCli
    val flags        = golemCliFlags
    val timeout      = golemTimeoutSec

    Tooling.requireCommandOnPath(cli, "golem-cli")
    log.info("[golem] Running golem-cli app deploy")
    val exit = runWithTimeout(
      Seq("env", "-u", "ARGV0", cli) ++ (flags ++ Seq("--yes", "app", "deploy", component)),
      appDir,
      "app deploy",
      timeout,
      log
    )
    if (exit != 0) throw new RuntimeException(s"golem-cli app deploy failed with exit code $exit")
  }

  /**
   * Deploy, then update an existing agent instance to the latest component version.
   *
   * Usage (Mill):
   * - `mill <module>.golemDeployUpdate scala:comp/agent-type("demo",42)`
   * - `mill <module>.golemDeployUpdate scala:comp/agent-type("demo",42) manual 2`
   */
  def golemDeployUpdate(agentId: String = "", mode: String = golemUpdateMode, targetVersion: String = "") = Task.Command {
    val log          = Task.log
    golemLocalUp()

    // Deploy first
    golemDeploy()

    val componentDir = golemScaffold()
    val appDir       = componentDir / os.up / os.up
    runAgentUpdatesResolved(
      appDir = appDir,
      cli = golemCli,
      flags = golemCliFlags,
      component = golemComponent,
      await = golemUpdateAwait,
      mode = mode,
      targetArg = if (targetVersion.trim.nonEmpty) Seq(targetVersion.trim) else golemUpdateTargetVersion.toSeq,
      timeout0 = golemTimeoutSec,
      agentId = agentId,
      log = log
    )
  }

  /**
   * Internal task used by golemAppRun/golemAppRunScript to avoid stale-agent method-missing errors after deploy.
   * Equivalent to `golemDeployUpdate()` (no args -> update all agents), but available as a Task.
   */
  def golemDeployUpdateAll: T[Unit] = Task {
    val log = Task.log
    golemLocalUp()
    golemDeploy()
    val componentDir = golemScaffold()
    val appDir       = componentDir / os.up / os.up
    runAgentUpdatesResolved(
      appDir = appDir,
      cli = golemCli,
      flags = golemCliFlags,
      component = golemComponent,
      await = golemUpdateAwait,
      mode = golemUpdateMode,
      targetArg = golemUpdateTargetVersion.toSeq,
      timeout0 = golemTimeoutSec,
      agentId = "",
      log = log
    )
  }

  private def runAgentUpdatesResolved(
    appDir: os.Path,
    cli: String,
    flags: Seq[String],
    component: String,
    await: Boolean,
    mode: String,
    targetArg: Seq[String],
    timeout0: Int,
    agentId: String,
    log: mill.api.Logger
  ): Unit = {
    val timeout = math.max(timeout0, 600) // updates can take several minutes, even locally

    Tooling.requireCommandOnPath(cli, "golem-cli")

    val awaitFlag = if (await) Seq("--await") else Nil

    def listAgents(): Vector[String] = {
      val cmd       = Seq("env", "-u", "ARGV0", cli) ++ (flags ++ Seq("--yes", "--format", "json", "agent", "list", component))
      val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent list", timeout0, log)
      if (exit != 0) throw new RuntimeException(s"golem-cli agent list failed with exit code $exit\n$out")
      val workerNamePattern = "\"workerName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r

      def unescapeJsonString(s: String): String = {
        val sb = new StringBuilder(s.length)
        var i  = 0
        while (i < s.length) {
          val c = s.charAt(i)
          if (c != '\\') { sb.append(c); i += 1 }
          else if (i + 1 >= s.length) { sb.append('\\'); i += 1 }
          else {
            s.charAt(i + 1) match {
              case '"'  => sb.append('"'); i += 2
              case '\\' => sb.append('\\'); i += 2
              case 'n'  => sb.append('\n'); i += 2
              case 'r'  => sb.append('\r'); i += 2
              case 't'  => sb.append('\t'); i += 2
              case 'u' if i + 5 < s.length =>
                val hex = s.substring(i + 2, i + 6)
                try sb.append(Integer.parseInt(hex, 16).toChar)
                catch { case _: Throwable => sb.append("\\u").append(hex) }
                i += 6
              case other =>
                sb.append(other)
                i += 2
            }
          }
        }
        sb.toString()
      }

      val workerNames = workerNamePattern.findAllMatchIn(out).map(m => unescapeJsonString(m.group(1))).toVector
      workerNames.map(wn => s"$component/$wn").distinct
    }

    val agentIds: Vector[String] =
      if (agentId.trim.isEmpty) {
        val ids = listAgents()
        if (ids.isEmpty) log.info(s"[golem] No existing agents found for component $component; nothing to update.")
        else log.info(s"[golem] Updating ${ids.length} agent(s) for component $component")
        ids
      } else Vector(agentId)

    agentIds.foreach { id =>
      log.info(s"[golem] Updating agent $id (mode=$mode, target=${targetArg.headOption.getOrElse("latest")})")
      val cmd =
        Seq("env", "-u", "ARGV0", cli) ++ (flags ++ (Seq("--yes", "agent", "update") ++ awaitFlag ++ Seq(id, mode) ++ targetArg))

      val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent update", timeout, log)
      if (exit != 0) {
        if (await && out.contains("update is not pending anymore, but no outcome has been found")) {
          log.info(s"[golem] agent update returned a transient status for $id; continuing (CLI reported no outcome yet)")
        } else {
          throw new RuntimeException(s"golem-cli agent update failed with exit code $exit for agentId=$id\n$out")
        }
      }
    }
  }

  def golemInvoke: T[Unit] = Task {
    val log          = Task.log
    golemLocalUp()
    val agentId      = golemInvokeAgentId
    val function     = golemInvokeFunction
    val args         = golemInvokeArgs
    val componentDir = golemWire()
    val appDir       = componentDir / os.up / os.up
    val cli          = golemCli
    val flags        = golemCliFlags
    val timeout      = golemTimeoutSec

    if (agentId.trim.isEmpty || function.trim.isEmpty)
      throw new RuntimeException("golemInvokeAgentId and golemInvokeFunction must be set before running golemInvoke.")

    Tooling.requireCommandOnPath(cli, "golem-cli")
    val cmd =
      Seq("env", "-u", "ARGV0", cli) ++ (flags ++ (Seq("--yes", "agent", "invoke", agentId, function) ++ args))

    val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent invoke", timeout, log)
    if (exit != 0) throw new RuntimeException(s"golem-cli agent invoke failed with exit code $exit\n$out")
  }

  /**
   * Installs dependencies (if needed), links the bundle, and executes it via
   * Node.js.
   */
  def golemRun: T[Unit] = Task {
    val log = Task.log
    golemSetup()
    val bundleRef = golemFastLink()
    val bundle    = bundleRef.path
    val node      = golemNodeCommand
    log.info(s"Running ${bundle.last} with $node ...")
    val exit = os.proc(node, bundle.toString).call(cwd = moduleDir, check = false)
    if (exit.exitCode != 0)
      throw new RuntimeException(s"Golem quickstart execution failed with exit code ${exit.exitCode}")
  }

  // Tooling-core access is done via reflection to keep the Mill plugin classpath stable.
  private object Tooling {
    private lazy val cls = Class.forName("cloud.golem.tooling.GolemTooling")

    private lazy val mReadResourceUtf8 =
      cls.getMethod("readResourceUtf8", classOf[ClassLoader], classOf[String])

    private lazy val mStripHttpApi =
      cls.getMethod("stripHttpApiFromGolemYaml", classOf[java.nio.file.Path], classOf[java.util.function.Consumer[?]])

    private lazy val mIsNoisy =
      cls.getMethod("isNoisyUpstreamWarning", classOf[String])

    private lazy val mRequireCommandOnPath =
      cls.getMethod("requireCommandOnPath", classOf[String], classOf[String])

    private lazy val mEnsureTsComponentScaffold =
      cls.getMethod(
        "ensureTsComponentScaffold",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[java.util.function.Consumer[?]]
      )

    private lazy val mEnsureTsAppScaffold =
      cls.getMethod(
        "ensureTsAppScaffold",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[java.util.function.Consumer[?]]
      )

    private lazy val mGenerateBridgeFromProvider =
      cls.getMethod(
        "generateBridgeMainTsFromProvider",
        classOf[java.util.List[?]],
        classOf[java.nio.file.Path],
        classOf[String]
      )

    private lazy val mGenerateBridgeFromManifest =
      cls.getMethod("generateBridgeMainTsFromManifest", classOf[java.nio.file.Path])

    private lazy val mBridgeSpecHasAgents =
      cls.getMethod("bridgeSpecHasAgents", classOf[Object])

    private lazy val mGenerateBridgeFromBridgeSpec =
      cls.getMethod("generateBridgeMainTsFromBridgeSpec", classOf[Object])

    private lazy val mWireTsComponent =
      cls.getMethod(
        "wireTsComponent",
        classOf[java.nio.file.Path],
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[java.util.function.Consumer[?]]
      )

    private lazy val mGenerateScalaShimFromManifest =
      cls.getMethod(
        "generateScalaShimFromManifest",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[String]
      )

    def readResourceUtf8(path: String): String =
      mReadResourceUtf8
        .invoke(null, getClass.getClassLoader, path)
        .asInstanceOf[String]

    def stripHttpApiFromGolemYaml(componentDir: java.nio.file.Path, logInfo: String => Unit): Unit = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mStripHttpApi.invoke(null, componentDir, consumer)
      ()
    }

    def isNoisyUpstreamWarning(line: String): Boolean =
      mIsNoisy.invoke(null, line).asInstanceOf[Boolean]

    def requireCommandOnPath(cmd: String, friendly: String): Unit =
      mRequireCommandOnPath.invoke(null, cmd, friendly)

    def ensureTsComponentScaffold(
      appDir: java.nio.file.Path,
      componentQualified: String,
      logInfo: String => Unit
    ): Unit = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mEnsureTsComponentScaffold.invoke(null, appDir, componentQualified, consumer)
      ()
    }

    def ensureTsAppScaffold(
      appRoot: java.nio.file.Path,
      appName: String,
      componentQualified: String,
      logInfo: String => Unit
    ): Unit = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mEnsureTsAppScaffold.invoke(null, appRoot, appName, componentQualified, consumer)
      ()
    }

    def generateBridgeMainTsFromProvider(
      classpathEntries: Seq[java.nio.file.Path],
      classesDir: java.nio.file.Path,
      providerClass: String
    ): String = {
      val list = new java.util.ArrayList[java.nio.file.Path]()
      classpathEntries.foreach(p => if (p != null) list.add(p))
      mGenerateBridgeFromProvider.invoke(null, list, classesDir, providerClass).asInstanceOf[String]
    }

    def generateBridgeMainTsFromManifest(manifestPath: java.nio.file.Path): String =
      mGenerateBridgeFromManifest.invoke(null, manifestPath).asInstanceOf[String]

    def bridgeSpecHasAgents(spec: AnyRef): Boolean =
      mBridgeSpecHasAgents.invoke(null, spec).asInstanceOf[Boolean]

    def generateBridgeMainTsFromBridgeSpec(spec: AnyRef): String =
      mGenerateBridgeFromBridgeSpec.invoke(null, spec).asInstanceOf[String]

    def wireTsComponent(
      componentDir: java.nio.file.Path,
      scalaJsBundle: java.nio.file.Path,
      bundleFileName: String,
      mainTs: String,
      logInfo: String => Unit
    ): Unit = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mWireTsComponent.invoke(null, componentDir, scalaJsBundle, bundleFileName, mainTs, consumer)
      ()
    }

    def generateScalaShimFromManifest(
      manifest: java.nio.file.Path,
      exportTopLevel: String,
      objectName: String,
      packageName: String
    ): String =
      mGenerateScalaShimFromManifest
        .invoke(null, manifest, exportTopLevel, objectName, packageName)
        .asInstanceOf[String]

  }

  /**
   * golem-cli's `component new ts` scaffold includes a sample `httpApi`
   * definition that calls `counter-agent(...)`. Our Scala.js examples don't
   * ship that agent, and `app deploy` will fail while compiling the RIB
   * bindings unless we remove that section.
   */
  private def stripHttpApiFromGolemYaml(componentDir: os.Path, log: mill.api.Logger): Unit =
    Tooling.stripHttpApiFromGolemYaml(componentDir.toNIO, msg => log.info(msg))

  private def firstJsFile(dir: Path): Path = {
    val js = os.list(dir).filter(p => p.ext == "js")
    js.headOption.getOrElse(throw new RuntimeException(s"No .js bundle found under $dir"))
  }

  private def isNoisyUpstreamWarning(line: String): Boolean =
    Tooling.isNoisyUpstreamWarning(line)

  private def runWithTimeout(cmd: Seq[String], cwd: Path, label: String, timeoutSec: Int, log: mill.api.Logger): Int = {
    log.info(s"[$label] starting (cwd=$cwd): ${cmd.mkString(" ")}")
    val res = os.proc(cmd).call(cwd = cwd, timeout = timeoutSec * 1000L, check = false)
    if (res.exitCode != 0) log.error(res.err.text())
    res.exitCode
  }

  private def runWithTimeoutCapture(
    cmd: Seq[String],
    cwd: Path,
    label: String,
    timeoutSec: Int,
    log: mill.api.Logger
  ): (Int, String) = {
    log.info(s"[$label] starting (cwd=$cwd): ${cmd.mkString(" ")}")
    val out = new StringBuilder
    val res = os
      .proc(cmd)
      .call(
        cwd = cwd,
        timeout = timeoutSec * 1000L,
        check = false,
        stdout = os.ProcessOutput.Readlines { line =>
          if (!isNoisyUpstreamWarning(line)) log.info(line)
          out.append(line).append('\n')
        },
        stderr = os.ProcessOutput.Readlines { line =>
          if (!isNoisyUpstreamWarning(line)) log.error(line)
          out.append(line).append('\n')
        }
      )
    (res.exitCode, out.result())
  }

  // Harness/demo flows live outside the plugin; this trait exposes only the generic primitives.
}
