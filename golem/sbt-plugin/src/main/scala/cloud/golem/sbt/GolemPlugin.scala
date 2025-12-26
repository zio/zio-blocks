package cloud.golem.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._

import java.io.File
import java.net.{InetSocketAddress, ServerSocket, Socket}
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import scala.sys.process._

object GolemPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin

  override def trigger: PluginTrigger = noTrigger

  // Default value for golemBridgeSpec: sbt settings cannot be null, and we want "unset" to be distinct from
  // a real BridgeSpec instance.
  private val BridgeSpecUnset: AnyRef = new AnyRef {}

  object autoImport {
    val golemNodeCommand    = settingKey[String]("Command used to invoke Node.js")
    val golemNpmCommand     = settingKey[String]("Command used to invoke npm")
    val golemNodeModulesDir = settingKey[File]("Directory containing node_modules")

    val golemFastLink = taskKey[File]("Runs fastLinkJS and returns the generated bundle")
    val golemSetup    = taskKey[Unit]("Ensures npm dependencies are installed")
    val golemRun      = taskKey[Unit]("Builds the Scala.js bundle and runs it with Node.js")

    // Generic public primitives (Phase 2 of public SDK plan)
    val golemAppRoot           = settingKey[File]("Root dir for generated Golem apps (default .golem-apps)")
    val golemAppName           = settingKey[String]("App name")
    val golemComponent         = settingKey[String]("Qualified component name (e.g. org:component)")
    val golemComponentTemplate =
      settingKey[String]("Component template for golem-cli (default ts)")
    val golemCli        = settingKey[String]("golem-cli command (default golem-cli)")
    val golemCliFlags   = settingKey[Seq[String]]("Flags to pass to golem-cli (e.g. --local)")
    val golemTimeoutSec = settingKey[Int]("Timeout (seconds) for individual golem-cli steps")

    val golemBundleFileName =
      settingKey[String]("Filename to copy Scala.js bundle to inside component src/ (default scala.js)")
    val golemBridgeMainTs =
      settingKey[String]("TypeScript guest bridge written to src/main.ts (required for golemWire)")
    val golemBridgeSpec =
      settingKey[AnyRef]("Bridge generation spec; used when golemBridgeMainTs is empty")
    val golemBridgeSpecProviderClass =
      settingKey[String](
        "Optional fully-qualified class name that can generate the bridge via tooling-core (metadata-driven). " +
          "The class must be JVM-loadable and have a public no-arg constructor."
      )
    val golemBridgeSpecManifestPath =
      settingKey[File](
        "Optional BridgeSpec manifest file (BridgeSpecManifest .properties); used when spec/provider are unset"
      )
    val golemGenerateBridgeMainTs =
      taskKey[String]("Generate src/main.ts from golemBridgeSpec (used when golemBridgeMainTs is empty)")

    // ---------------------------------------------------------------------------
    // Settings-based exports (Scala-only configuration; generates a hidden BridgeSpec manifest)
    // ---------------------------------------------------------------------------

    /**
     * TypeScript type expression used by the deterministic TS bridge generator.
     *
     * Examples:
     *   - `"string"`, `"number"`, `"boolean"`, `"void"`
     *   - `"Name"` (custom declared type)
     *   - `"number | null"` (union)
     *   - `"string[]"` (array)
     */
    object GolemTsType {
      val string: String  = "string"
      val number: String  = "number"
      val boolean: String = "boolean"
      val void: String    = "void"
      val any: String     = "any"
    }

    final case class GolemParam(name: String, tsType: String)

    sealed trait GolemConstructor
    object GolemConstructor {
      case object NoArg extends GolemConstructor
      final case class Scalar(argName: String, tsType: String, scalaFactoryArgs: Seq[String] = Nil)
          extends GolemConstructor
      final case class Positional(params: Seq[GolemParam], scalaFactoryArgs: Seq[String] = Nil) extends GolemConstructor
      final case class Record(
        inputTypeName: String,
        fields: Seq[GolemParam],
        scalaFactoryArgs: Seq[String] = Nil
      ) extends GolemConstructor
    }

    final case class GolemMethodParam(name: String, tsType: String, implArgExpr: String = "")

    final case class GolemExport(
      agentName: String,
      traitClass: String,
      className: String = "",
      scalaFactory: String = "",
      scalaShimImplClass: String,
      constructor: GolemConstructor,
      typeDeclarations: Seq[String] = Nil,
      methods: Seq[GolemMethod]
    )

    final case class GolemMethod(
      name: String,
      tsReturnType: String,
      isAsync: Boolean = true,
      params: Seq[GolemMethodParam] = Nil,
      implMethodName: String = ""
    )

    /**
     * Scala-only export configuration; when non-empty, tooling generates a
     * BridgeSpec manifest automatically.
     */
    val golemExports =
      settingKey[Seq[GolemExport]](
        "List of exported agents (Scala-only); used to auto-generate a hidden BridgeSpec manifest."
      )

    /**
     * When `golemExports` is empty, attempt to auto-detect exports from
     * compiled classes annotated with `@agentDefinition` /
     * `@agentImplementation`.
     *
     * Currently this auto mode supports only "primitive-only" agents
     * (String/Boolean/numbers, Option[T], List[T]). More complex shapes still
     * require explicit `golemExports`.
     */
    val golemAutoExports =
      settingKey[Boolean]("Enable auto-detection of exports when golemExports is empty (primitive-only for now).")

    /**
     * Ensures the BridgeSpec manifest exists (generating it from golemExports
     * when configured).
     */
    val golemEnsureBridgeSpecManifest =
      taskKey[File]("Ensure BridgeSpec manifest exists (auto-generated from golemExports when configured).")

    val golemScalaShimExportTopLevel =
      settingKey[String]("JS export name for the generated Scala shim object (default: __golemInternalScalaAgents)")
    val golemScalaShimObjectName =
      settingKey[String]("Scala object name for the generated Scala shim")
    val golemScalaShimPackage =
      settingKey[String]("Scala package for the generated Scala shim (internal)")
    val golemGenerateScalaShim =
      taskKey[Seq[File]]("Generate an internal Scala.js shim from BridgeSpec manifest into managed sources")

    val golemScaffold     = taskKey[File]("Ensures the golem app/component scaffold exists; returns component dir")
    val golemWire         = taskKey[File]("Copies Scala.js bundle and writes src/main.ts; returns component dir")
    val golemBuild        = taskKey[Unit]("Runs golem-cli app build for the configured component")
    val golemDeploy       = taskKey[Unit]("Runs golem-cli app deploy for the configured component")
    val golemDeployUpdate =
      inputKey[Unit](
        "Deploy, then update agent instances to the latest component version: golemDeployUpdate [agentId ...] [--all] [--mode auto|manual] [--no-await] [--target <version>]"
      )
    val golemInvoke =
      inputKey[Unit](
        "Invoke an agent method: golemInvoke <agentId> <fullyQualifiedMethod> [arg1] [arg2] ..."
      )

    // ---------------------------------------------------------------------------
    // App run conveniences (deploy + invoke / deploy + repl script)
    // ---------------------------------------------------------------------------

    /**
     * Default agentId used by `golemAppRun`. Must be a golem-cli agent id
     * string.
     */
    val golemRunAgentId =
      settingKey[String]("Agent id for golemAppRun (golem-cli agent id string).")

    /**
     * Default fully-qualified method used by `golemAppRun` (e.g.
     * org:comp/agent-type.{method}).
     */
    val golemRunFunction =
      settingKey[String]("Fully-qualified method for golemAppRun (golem-cli function string).")

    /** Default args (WAVE literals) passed to `golemAppRun`. */
    val golemRunArgs =
      settingKey[Seq[String]]("Default invocation args (WAVE literals) for golemAppRun.")

    /**
     * When true, golemAppRun/golemAppRunScript will run golemDeploy first
     * (default: true).
     */
    val golemRunDeployFirst =
      settingKey[Boolean]("Deploy before running (default: true).")

    /**
     * Optional publish step to run before deploy+run.
     *
     * Default is a no-op. In monorepo / snapshot workflows, you can set this to
     * e.g. `publishLocal` or a custom task.
     */
    val golemRunPublish =
      taskKey[Unit]("Optional publish step to run before golemAppRun/golemAppRunScript (default: no-op).")

    /** When true, run golemRunPublish before deploy+run (default: false). */
    val golemRunPublishFirst =
      settingKey[Boolean]("Run golemRunPublish before deploy+run (default: false).")

    /**
     * Timeout (seconds) for golem-cli repl used by golemAppRunScript (default:
     * 60).
     */
    val golemReplTimeoutSec =
      settingKey[Int]("Timeout (seconds) for golem-cli repl in golemAppRunScript (default: 60).")

    /** When true, pass --disable-stream to golem-cli repl (default: true). */
    val golemReplDisableStream =
      settingKey[Boolean]("Pass --disable-stream to golem-cli repl (default: true).")

    /**
     * Deploy (optional) then invoke a configured agent method using golem-cli.
     */
    val golemAppRun =
      taskKey[Unit]("Deploy (optional) then invoke a configured method via golem-cli (developer convenience).")

    /**
     * Deploy (optional) then run a golem-cli repl script: golemAppRunScript
     * <path-to.rib>.
     */
    val golemAppRunScript =
      inputKey[Unit]("Deploy (optional) then run a golem-cli repl script: golemAppRunScript <path-to.rib>.")

    /** Default update mode for golemDeployUpdate (auto|manual). */
    val golemUpdateMode =
      settingKey[String]("Default agent update mode used by golemDeployUpdate (auto|manual).")

    /** When true, pass --await to golem-cli agent update (default: true). */
    val golemUpdateAwait =
      settingKey[Boolean]("Await agent updates in golemDeployUpdate (default: true).")

    /**
     * Optional explicit target version for golemDeployUpdate (default: latest).
     */
    val golemUpdateTargetVersion =
      settingKey[Option[String]]("Optional explicit target version for golemDeployUpdate (default: latest).")

    // ---------------------------------------------------------------------------
    // Local runtime management (developer convenience)
    // ---------------------------------------------------------------------------

    /**
     * Hostname to use when checking whether the local router is reachable
     * (default: 127.0.0.1).
     */
    val golemRouterHost = settingKey[String]("Golem router host for local management (default: 127.0.0.1).")

    /**
     * Port to use when checking whether the local router is reachable (default:
     * 9881).
     */
    val golemRouterPort = settingKey[Int]("Golem router port for local management (default: 9881).")

    /**
     * Data directory used when starting a local `golem server run` (default:
     * <buildRoot>/.golem-local).
     */
    val golemLocalDataDir = settingKey[File]("Local golem server data dir (default: <buildRoot>/.golem-local).")

    /**
     * When true (default), `golemBuild`/`golemDeploy`/`golemInvoke` will
     * auto-start local server when using `--local`.
     */
    val golemStartLocalServer =
      settingKey[Boolean]("Auto-start a local golem server when using GOLEM_CLI_FLAGS=--local (default: true).")

    /** File used to persist the PID for the managed local server. */
    val golemLocalServerPidFile = settingKey[File]("PID file for managed local server.")

    /** File used to capture logs for the managed local server. */
    val golemLocalServerLogFile = settingKey[File]("Log file for managed local server.")

    /**
     * Starts a local golem server/router if needed (no-op if already reachable
     * or not in `--local` mode).
     */
    val golemLocalUp = taskKey[Unit]("Start local golem server/router if needed (developer convenience).")

    /**
     * Stops the managed local golem server/router (no-op if none is running).
     */
    val golemLocalDown = taskKey[Unit]("Stop managed local golem server/router (developer convenience).")

    // This plugin intentionally exposes only the generic primitives above.
  }

  import autoImport._

  private def requireCommandOnPath(cmd: String, friendly: String): Unit =
    Tooling.requireCommandOnPath(cmd, friendly)

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private def defaultTsClassNameFromTrait(traitClass: String): String = {
    val simple0 = traitClass.split('.').lastOption.getOrElse("Agent")
    val simple  = simple0.stripSuffix("$")
    "Scala" + simple
  }

  private def defaultScalaFactoryFromTrait(traitClass: String): String = {
    val simple0 = traitClass.split('.').lastOption.getOrElse("Agent")
    val simple  = simple0.stripSuffix("$")
    "new" + simple
  }

  private def scalaAgentsExprForExportTopLevel(exportTopLevel: String): String = {
    val n = exportTopLevel.trim
    s"(scalaExports as any).$n ?? (globalThis as any).$n"
  }

  private def defaultAgentNameFromTrait(traitClass: String): String = {
    val simple0 = traitClass.split('.').lastOption.getOrElse("agent")
    val simple  = simple0.stripSuffix("$")
    simple
      .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
      .toLowerCase
  }

  private final case class AutoDetectedExport(
    agentName: String,
    traitClass: String,
    implClass: String,
    ctorParams: Vector[(String, String, String)],
    methods: Vector[(String, Boolean, String, Vector[(String, String, String)])]
  )

  private def autoDetectExportsFromClassfiles(
    classDir: File,
    classpath: Seq[File],
    logInfo: String => Unit
  ): Either[String, Vector[AutoDetectedExport]] = {
    import scala.jdk.CollectionConverters._

    def listClassNames(dir: File): Vector[String] = {
      val base = dir.toPath
      if (!dir.exists()) return Vector.empty
      java.nio.file.Files
        .walk(base)
        .iterator()
        .asScala
        .filter(p => p.toString.endsWith(".class"))
        .filterNot { p =>
          val n = p.getFileName.toString
          n == "module-info.class" || n == "package-info.class"
        }
        .map { p =>
          val rel = base.relativize(p).toString
          rel.stripSuffix(".class").replace(java.io.File.separatorChar, '.').replace('/', '.')
        }
        .toVector
    }

    def tsTypeOf(tpe: java.lang.reflect.Type): Either[String, String] = {
      def mapClass(c: Class[_]): Option[String] =
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

      def isLikelyScalaRecordClass(c: Class[_]): Boolean = {
        // IMPORTANT: sbt runs on Scala 2.12, but the scanned classes are typically Scala 2.13/Scala 3
        // and are loaded in an isolated classloader. Do NOT use `classOf[scala.Product]` here
        // (classloader mismatch). Use a name/shape based heuristic instead.
        val looksLikeProduct =
          c.getInterfaces.exists(_.getName == "scala.Product") ||
            c.getMethods.exists(m => m.getName == "productArity" && m.getParameterCount == 0)

        looksLikeProduct &&
        !c.isInterface &&
        !java.lang.reflect.Modifier.isAbstract(c.getModifiers) &&
        !c.isPrimitive &&
        !c.getName.startsWith("scala.") &&
        !c.getName.startsWith("java.")
      }

      def recordTsTypeOf(c: Class[_], depth: Int): Either[String, String] =
        if (depth <= 0) Left(s"Auto exports record mapping hit max depth for ${c.getName}")
        else {
          // Scala.js (and some Scala 3 encodings) may not expose case class params as Java fields reliably.
          // Try fields first, then fall back to zero-arg accessor methods declared on the class.
          val fields =
            c.getDeclaredFields.toVector
              .filterNot(f => f.isSynthetic)
              .filterNot(f => java.lang.reflect.Modifier.isStatic(f.getModifiers))
              .filterNot(f => f.getName == "$outer")

          val accessors =
            c.getMethods.toVector
              .filter(m => m.getDeclaringClass == c)
              .filterNot(_.isSynthetic)
              .filter(m => m.getParameterCount == 0)
              .filterNot(m => m.getName == "copy" || m.getName == "productPrefix")
              .filterNot(m => m.getName.startsWith("product") || m.getName.startsWith("canEqual"))
              .filterNot(m => m.getName == "hashCode" || m.getName == "equals" || m.getName == "toString")

          val members: Vector[(String, java.lang.reflect.Type)] =
            if (fields.nonEmpty) fields.map(f => f.getName -> f.getGenericType)
            else accessors.map(m => m.getName -> m.getGenericReturnType)

          if (members.isEmpty) Left(s"Unsupported type for auto exports: ${c.getName}")
          else {
            val partsE: Vector[Either[String, String]] =
              members.map { case (n, t) =>
                tsTypeOfWithDepth(t, depth - 1).map(ts => s"$n: $ts")
              }
            if (partsE.exists(_.isLeft)) partsE.collectFirst { case Left(e) => Left(e) }.get
            else Right("{ " + partsE.collect { case Right(v) => v }.mkString("; ") + " }")
          }
        }

      def tsTypeOfWithDepth(tpe0: java.lang.reflect.Type, depth: Int): Either[String, String] =
        tpe0 match {
          case c: Class[_] =>
            mapClass(c) match {
              case Some(ts) => Right(ts)
              case None     =>
                if (isLikelyScalaRecordClass(c)) recordTsTypeOf(c, depth)
                else Left(s"Unsupported type for auto exports: ${c.getName}")
            }

          case p: java.lang.reflect.ParameterizedType =>
            val raw  = p.getRawType.asInstanceOf[Class[_]].getName
            val args = p.getActualTypeArguments.toVector
            raw match {
              case "scala.Option" =>
                if (args.length != 1) Left(s"Unsupported Option arity for auto exports: ${p.getTypeName}")
                else tsTypeOfWithDepth(args.head, depth).map(inner => s"$inner | null")
              case "scala.collection.immutable.List" | "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
                if (args.length != 1) Left(s"Unsupported collection arity for auto exports: ${p.getTypeName}")
                else tsTypeOfWithDepth(args.head, depth).map(inner => s"$inner[]")
              case "scala.collection.immutable.Set" | "scala.collection.Set" =>
                if (args.length != 1) Left(s"Unsupported set arity for auto exports: ${p.getTypeName}")
                else tsTypeOfWithDepth(args.head, depth).map(inner => s"$inner[]")
              case "scala.collection.immutable.Map" | "scala.collection.Map" =>
                if (args.length != 2) Left(s"Unsupported map arity for auto exports: ${p.getTypeName}")
                else {
                  tsTypeOfWithDepth(args.head, depth).flatMap {
                    case "string" =>
                      tsTypeOfWithDepth(args(1), depth).map(v => s"Record<string, $v>")
                    case other =>
                      Left(s"Only string map keys are supported for auto exports (found: $other)")
                  }
                }
              case "scala.concurrent.Future" =>
                Left("Future[...] is only supported as a method return type in auto exports")
              case other =>
                Left(s"Unsupported generic type for auto exports: $other (${p.getTypeName})")
            }

          case other =>
            Left(s"Unsupported type for auto exports: ${other.getTypeName}")
        }

      tpe match {
        case other => tsTypeOfWithDepth(other, depth = 4)
      }
    }

    // Scala type mapping used for the generated Scala shim signatures.
    // This is what lets users write `Int` (etc) in their @agentImplementation constructors/methods without
    // needing to know about Scala.js numeric interop details.
    def scalaParamTypeOf(tpe: java.lang.reflect.Type): String = {
      def looksLikeScalaProductClass(c: Class[_]): Boolean =
        c.getInterfaces.exists(_.getName == "scala.Product") ||
          c.getMethods.exists(m => m.getName == "productArity" && m.getParameterCount == 0)

      def mapClass(c: Class[_]): String =
        if (c == classOf[String]) "String"
        else if (c == java.lang.Boolean.TYPE || c == classOf[java.lang.Boolean]) "Boolean"
        else if (c == java.lang.Integer.TYPE || c == classOf[java.lang.Integer]) "Int"
        else if (c == java.lang.Long.TYPE || c == classOf[java.lang.Long]) "Long"
        else if (c == java.lang.Double.TYPE || c == classOf[java.lang.Double]) "Double"
        else if (c == java.lang.Float.TYPE || c == classOf[java.lang.Float]) "Float"
        else if (c == java.lang.Short.TYPE || c == classOf[java.lang.Short]) "Short"
        else if (c == java.lang.Byte.TYPE || c == classOf[java.lang.Byte]) "Byte"
        else if (c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE) "Unit"
        else if (
          (looksLikeScalaProductClass(c) || tsTypeOf(c).isRight) &&
          !c.getName.startsWith("scala.") &&
          !c.getName.startsWith("java.")
        ) c.getName
        else "js.Any"

      tpe match {
        case c: Class[_] =>
          mapClass(c)
        case p: java.lang.reflect.ParameterizedType =>
          val raw = p.getRawType.asInstanceOf[Class[_]].getName
          raw match {
            case "scala.Option" =>
              "js.Any"
            case "scala.collection.immutable.List" | "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
              "js.Array[js.Any]"
            case "scala.concurrent.Future" =>
              // Only supported as return types; never a param type in this auto mode.
              "js.Any"
            case _ =>
              "js.Any"
          }
        case _ =>
          "js.Any"
      }
    }

    def isAnnByName(a: java.lang.annotation.Annotation, fqcn: String): Boolean =
      a.annotationType().getName == fqcn

    def readAgentDefinitionAnn(cls: Class[_]): Option[(String, String)] =
      cls.getAnnotations.find(a => isAnnByName(a, "cloud.golem.runtime.annotations.agentDefinition")).map { ann =>
        val tn       = Option(ann.annotationType().getMethod("typeName").invoke(ann)).map(_.toString).getOrElse("")
        val typeName =
          if (tn.trim.nonEmpty) tn.trim
          else defaultAgentNameFromTrait(cls.getName)
        (cls.getName, typeName)
      }

    val urls = (classpath :+ classDir).distinct.map(_.toURI.toURL).toArray
    val cl   = new java.net.URLClassLoader(urls, null) // isolate from sbt's classloader

    // Scala 3 does not reliably emit Scala-defined annotations as Java classfile annotations.
    // For minimal-config autoExports, we treat any interface that extends BaseAgent as an agent trait,
    // and any concrete class implementing exactly one such trait as an implementation.
    val baseAgentCls = Class.forName("cloud.golem.sdk.BaseAgent", false, cl)

    def isAgentTrait(cls: Class[_]): Boolean =
      cls.isInterface && cls.getName != "cloud.golem.sdk.BaseAgent" && baseAgentCls.isAssignableFrom(cls)

    val loaded: Vector[Class[_]] = {
      val b = Vector.newBuilder[Class[_]]
      listClassNames(classDir).foreach { n =>
        try b += Class.forName(n, false, cl)
        catch { case _: Throwable => () }
      }
      b.result()
    }

    def agentTraitName(cls: Class[_]): Option[(String, String)] =
      if (!isAgentTrait(cls)) None
      else {
        readAgentDefinitionAnn(cls) match {
          case Some(v) => Some(v)
          case None    => Some(cls.getName -> defaultAgentNameFromTrait(cls.getName))
        }
      }

    val traits: Map[String, String] =
      loaded.flatMap(agentTraitName).toMap

    if (traits.isEmpty) return Right(Vector.empty)

    val impls =
      loaded.collect {
        case c
            if !c.isInterface &&
              baseAgentCls.isAssignableFrom(c) &&
              !java.lang.reflect.Modifier.isAbstract(c.getModifiers) &&
              !c.getName.contains("$agentClient$") =>
          c
      }

    val exports = impls.flatMap { impl =>
      val implementedTraits = impl.getInterfaces.toVector.map(_.getName).filter(traits.contains)
      implementedTraits match {
        case Vector(traitName) =>
          val agentName = traits(traitName)
          val ctor      = impl.getConstructors.toVector.sortBy(_.getParameterCount).headOption.orNull
          if (ctor == null) None
          else {
            val ctorParams =
              ctor.getGenericParameterTypes.toVector.zipWithIndex.map { case (tpe, idx) =>
                val name = s"arg$idx"
                tsTypeOf(tpe).map(ts => (name, ts, scalaParamTypeOf(tpe)))
              }
            if (ctorParams.exists(_.isLeft)) {
              val err = ctorParams.collectFirst { case Left(e) => e }.getOrElse("unknown")
              logInfo(s"[golem] autoExports: skipping ${impl.getName} (constructor: $err)")
              None
            } else {
              val ctorTs = ctorParams.collect { case Right(v) => v }.toVector

              val traitCls = Class.forName(traitName, false, cl)
              val methods0 =
                traitCls.getDeclaredMethods.toVector
                  .filter(m => m.getDeclaringClass == traitCls)
                  .filterNot(_.isSynthetic)
                  .filterNot(m => m.getName.contains("$"))

              // Scala 3 often erases value-type type arguments in generic signatures (e.g. Future[Int] -> Future[Object]).
              // We still want minimal-config auto-exports to work for "primitive-only" agents, and we must not emit `any`
              // because the Golem TS schema generator rejects it. As a fallback, infer the boxed primitive return type
              // by scanning the implementation classfile bytecode for BoxesRunTime.boxToX calls.
              def inferFutureReturnTsFromImplBytecode(
                implClassName: String,
                methodName: String,
                paramCount: Int
              ): Option[String] = {
                import java.io.{ByteArrayInputStream, DataInputStream}
                import java.nio.file.Files

                final case class CpUtf8(value: String)
                final case class CpClass(nameIndex: Int)
                final case class CpNameAndType(nameIndex: Int, descIndex: Int)
                final case class CpMethodRef(classIndex: Int, nameAndTypeIndex: Int)

                def u1(in: DataInputStream): Int  = in.readUnsignedByte()
                def u2(in: DataInputStream): Int  = in.readUnsignedShort()
                def u4(in: DataInputStream): Long = in.readInt().toLong & 0xffffffffL

                def countDescriptorParams(desc: String): Int = {
                  val start = desc.indexOf('(')
                  val end   = desc.indexOf(')')
                  if (start < 0 || end < 0 || end <= start) return -1
                  var i     = start + 1
                  var count = 0
                  while (i < end) {
                    desc.charAt(i) match {
                      case 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z' =>
                        count += 1; i += 1
                      case 'L' =>
                        val semi = desc.indexOf(';', i)
                        if (semi < 0 || semi > end) return -1
                        count += 1; i = semi + 1
                      case '[' =>
                        // skip all '[' then parse the element type
                        while (i < end && desc.charAt(i) == '[') i += 1
                        // element
                        if (i >= end) return -1
                        desc.charAt(i) match {
                          case 'L' =>
                            val semi = desc.indexOf(';', i)
                            if (semi < 0 || semi > end) return -1
                            count += 1; i = semi + 1
                          case _ =>
                            count += 1; i += 1
                        }
                      case _ =>
                        return -1
                    }
                  }
                  count
                }

                def classFilePath(name: String): java.nio.file.Path =
                  new java.io.File(classDir, name.replace('.', '/') + ".class").toPath

                val path = classFilePath(implClassName)
                if (!Files.exists(path)) return None
                val bytes = Files.readAllBytes(path)
                val in    = new DataInputStream(new ByteArrayInputStream(bytes))

                val magic = in.readInt()
                if (magic != 0xcafebabe) return None
                u2(in) // minor
                u2(in) // major

                val cpCount = u2(in)
                val cp      = new Array[AnyRef](cpCount)
                var idx     = 1
                while (idx < cpCount) {
                  val tag = u1(in)
                  tag match {
                    case 1 => // Utf8
                      cp(idx) = CpUtf8(in.readUTF())
                    case 7 => // Class
                      cp(idx) = CpClass(u2(in))
                    case 12 => // NameAndType
                      cp(idx) = CpNameAndType(u2(in), u2(in))
                    case 10 => // Methodref
                      cp(idx) = CpMethodRef(u2(in), u2(in))
                    case 9 | 11 => // Fieldref / InterfaceMethodref
                      u2(in); u2(in)
                    case 8 => // String
                      u2(in)
                    case 3 | 4 => // Integer/Float
                      in.readInt()
                    case 5 | 6 => // Long/Double (take two entries)
                      in.readLong(); idx += 1
                    case 15 => // MethodHandle
                      u1(in); u2(in)
                    case 16 => // MethodType
                      u2(in)
                    case 18 => // InvokeDynamic
                      u2(in); u2(in)
                    case other =>
                      return None
                  }
                  idx += 1
                }

                def utf(i: Int): Option[String]                     = cp.lift(i).collect { case CpUtf8(v) => v }
                def classNameFromClassIndex(i: Int): Option[String] =
                  cp.lift(i).collect { case CpClass(ni) => ni }.flatMap(utf).map(_.replace('/', '.'))
                def nameAndType(i: Int): Option[(String, String)] =
                  cp.lift(i).collect { case CpNameAndType(ni, di) => (ni, di) }.flatMap { case (ni, di) =>
                    for (n <- utf(ni); d <- utf(di)) yield (n, d)
                  }

                // skip access_flags, this_class, super_class
                u2(in); u2(in); u2(in)
                // interfaces
                val ifaces = u2(in)
                var k      = 0
                while (k < ifaces) { u2(in); k += 1 }
                // fields
                val fields = u2(in)
                k = 0
                while (k < fields) {
                  u2(in); u2(in); u2(in) // access, name, desc
                  val ac = u2(in)
                  var a  = 0
                  while (a < ac) { u2(in); val len = u4(in); in.skipBytes(len.toInt); a += 1 }
                  k += 1
                }
                // methods
                val methods = u2(in)
                k = 0
                while (k < methods) {
                  u2(in) // access
                  val nameIdx = u2(in)
                  val descIdx = u2(in)
                  val ac      = u2(in)
                  val mName   = utf(nameIdx).getOrElse("")
                  val mDesc   = utf(descIdx).getOrElse("")
                  val matches =
                    mName == methodName && countDescriptorParams(mDesc) == paramCount

                  var a = 0
                  while (a < ac) {
                    val anIdx = u2(in)
                    val len   = u4(in).toInt
                    val an    = utf(anIdx).getOrElse("")
                    if (matches && an == "Code") {
                      // max_stack, max_locals
                      u2(in); u2(in)
                      val codeLen = u4(in).toInt
                      val code    = new Array[Byte](codeLen)
                      in.readFully(code)
                      // Scan for invokestatic to BoxesRunTime.boxToX
                      var pc = 0
                      while (pc < code.length - 2) {
                        val op = code(pc) & 0xff
                        // 0xbb = new (often used for case class instantiation)
                        if (op == 0xbb && pc + 2 < code.length) {
                          val cpIndex = ((code(pc + 1) & 0xff) << 8) | (code(pc + 2) & 0xff)
                          classNameFromClassIndex(cpIndex) match {
                            case Some(cn)
                                if !cn.startsWith("scala.") && !cn.startsWith("java.") && !cn.startsWith("sun.") =>
                              try {
                                val c0 = Class.forName(cn, false, cl)
                                tsTypeOf(c0).toOption.foreach(ts => return Some(ts))
                              } catch { case _: Throwable => () }
                            case _ => ()
                          }
                          pc += 3
                        } else if ((op == 0xb8 || op == 0xb6 || op == 0xb7) && pc + 2 < code.length) {
                          val cpIndex = ((code(pc + 1) & 0xff) << 8) | (code(pc + 2) & 0xff)
                          cp.lift(cpIndex).collect { case CpMethodRef(ci, nti) => (ci, nti) } match {
                            case Some((ci, nti)) =>
                              val cn  = classNameFromClassIndex(ci).getOrElse("")
                              val nat = nameAndType(nti)
                              // Heuristic for case classes: Scala often compiles `Foo(...)` to `Foo$.apply(...)`
                              // (with the `new Foo` living inside apply). If we see that call, infer Foo.
                              nat.map(_._1) match {
                                case Some("apply")
                                    if cn.endsWith("$") && !cn.startsWith("scala.") && !cn.startsWith("java.") =>
                                  val candidate = cn.stripSuffix("$")
                                  try {
                                    val c0 = Class.forName(candidate, false, cl)
                                    tsTypeOf(c0).toOption.foreach(ts => return Some(ts))
                                  } catch { case _: Throwable => () }
                                case _ => ()
                              }
                              if (cn == "scala.runtime.BoxesRunTime") {
                                nat.map(_._1) match {
                                  case Some(
                                        "boxToInteger" | "boxToLong" | "boxToDouble" | "boxToFloat" | "boxToShort" |
                                        "boxToByte"
                                      ) =>
                                    return Some("number")
                                  case Some("boxToBoolean") =>
                                    return Some("boolean")
                                  case _ => ()
                                }
                              }
                            case None => ()
                          }
                          pc += 3
                        } else {
                          pc += 1
                        }
                      }

                      // Skip the rest of Code attribute (exception table + attrs)
                      val exCount = u2(in)
                      var e       = 0
                      while (e < exCount) { u2(in); u2(in); u2(in); u2(in); e += 1 }
                      val codeAttrs = u2(in)
                      var ca        = 0
                      while (ca < codeAttrs) { u2(in); val clen = u4(in); in.skipBytes(clen.toInt); ca += 1 }
                    } else {
                      in.skipBytes(len)
                    }
                    a += 1
                  }
                  k += 1
                }

                None
              }

              val methodsE: Vector[Either[String, (String, Boolean, String, Vector[(String, String, String)])]] =
                methods0.map { m: java.lang.reflect.Method =>
                  // Prefer the *trait* method signature for types (it is usually more precise than Scala 3 impl signatures,
                  // which can erase type arguments to Object). Use impl bytecode only as a fallback for primitives.
                  val implMethodOpt =
                    impl.getMethods.toVector.find(mm =>
                      mm.getName == m.getName && mm.getParameterTypes.length == m.getParameterTypes.length
                    )
                  val out: Either[String, (String, Boolean, String, Vector[(String, String, String)])] =
                    m.getGenericReturnType match {
                      case p: java.lang.reflect.ParameterizedType
                          if p.getRawType.asInstanceOf[Class[_]].getName == "scala.concurrent.Future" =>
                        val outTpe = p.getActualTypeArguments.headOption.getOrElse(classOf[Object])
                        val outTsE = tsTypeOf(outTpe) match {
                          case Right("any") | Left(_) if outTpe == classOf[Object] =>
                            inferFutureReturnTsFromImplBytecode(impl.getName, m.getName, m.getParameterTypes.length)
                              .toRight(
                                s"Unsupported type for auto exports: ${outTpe.getTypeName} on $traitName.${m.getName} (Scala 3 erased type; please configure explicit golemExports)"
                              )
                          case other => other
                        }
                        val paramsTs: Vector[Either[String, (String, String, String)]] =
                          m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt0, i) =>
                            val pn = s"arg$i"
                            // If Scala 3 erased the param type to Object, try the impl method signature.
                            val pt =
                              pt0 match {
                                case c: Class[_] if c == classOf[Object] =>
                                  implMethodOpt.map(_.getGenericParameterTypes.apply(i)).getOrElse(pt0)
                                case other => other
                              }
                            tsTypeOf(pt).map(ts => (pn, ts, scalaParamTypeOf(pt)))
                          }
                        if (outTsE.isLeft) Left(outTsE.left.get)
                        else if (paramsTs.exists(_.isLeft)) paramsTs.collectFirst { case Left(e) => Left(e) }.get
                        else Right((m.getName, true, outTsE.toOption.get, paramsTs.collect { case Right(v) => v }))

                      case c: Class[_] if c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE =>
                        val paramsTs: Vector[Either[String, (String, String, String)]] =
                          m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt0, i) =>
                            val pn = s"arg$i"
                            val pt =
                              pt0 match {
                                case c: Class[_] if c == classOf[Object] =>
                                  implMethodOpt.map(_.getGenericParameterTypes.apply(i)).getOrElse(pt0)
                                case other => other
                              }
                            tsTypeOf(pt).map(ts => (pn, ts, scalaParamTypeOf(pt)))
                          }
                        if (paramsTs.exists(_.isLeft)) paramsTs.collectFirst { case Left(e) => Left(e) }.get
                        else Right((m.getName, false, "void", paramsTs.collect { case Right(v) => v }))

                      case other =>
                        Left(
                          s"Auto exports only supports Future[...] or Unit return types; found ${other.getTypeName} on $traitName.${m.getName}"
                        )
                    }
                  out
                }

              if (methodsE.exists(_.isLeft)) {
                val err = methodsE.collectFirst { case Left(e) => e }.getOrElse("unknown")
                logInfo(s"[golem] autoExports: skipping $traitName ($err)")
                None
              } else {
                Some(
                  AutoDetectedExport(
                    agentName = agentName,
                    traitClass = traitName,
                    implClass = impl.getName,
                    ctorParams = ctorTs,
                    methods = methodsE.collect { case Right(v) => v }
                  )
                )
              }
            }
          }
        case _ =>
          None
      }
    }

    Right(exports)
  }

  // Access tooling-core via reflection so the sbt build-definition classpath stays stable.
  private object Tooling {
    private lazy val cls = Class.forName("cloud.golem.tooling.GolemTooling")

    private lazy val mReadResourceUtf8 =
      cls.getMethod("readResourceUtf8", classOf[ClassLoader], classOf[String])

    private lazy val mStripHttpApi =
      cls.getMethod("stripHttpApiFromGolemYaml", classOf[java.nio.file.Path], classOf[java.util.function.Consumer[_]])

    private lazy val mIsNoisy =
      cls.getMethod("isNoisyUpstreamWarning", classOf[String])

    private lazy val mRequireCommandOnPath =
      cls.getMethod("requireCommandOnPath", classOf[String], classOf[String])

    private lazy val mEnsureTsComponentScaffold =
      cls.getMethod(
        "ensureTsComponentScaffold",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[java.util.function.Consumer[_]]
      )

    private lazy val mEnsureTsAppScaffold =
      cls.getMethod(
        "ensureTsAppScaffold",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[java.util.function.Consumer[_]]
      )

    private lazy val mWireTsComponent =
      cls.getMethod(
        "wireTsComponent",
        classOf[java.nio.file.Path],
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[java.util.function.Consumer[_]]
      )

    private lazy val mGenerateScalaShimFromManifest =
      cls.getMethod(
        "generateScalaShimFromManifest",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[String]
      )

    private lazy val mGenerateBridgeMainTsFromManifest =
      cls.getMethod("generateBridgeMainTsFromManifest", classOf[java.nio.file.Path])

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
    ): java.nio.file.Path = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mEnsureTsComponentScaffold.invoke(null, appDir, componentQualified, consumer).asInstanceOf[java.nio.file.Path]
    }

    def ensureTsAppScaffold(
      appRoot: java.nio.file.Path,
      appName: String,
      componentQualified: String,
      logInfo: String => Unit
    ): java.nio.file.Path = {
      val consumer: java.util.function.Consumer[String] = (s: String) => logInfo(s)
      mEnsureTsAppScaffold.invoke(null, appRoot, appName, componentQualified, consumer).asInstanceOf[java.nio.file.Path]
    }

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

    def generateBridgeMainTsFromManifest(manifest: java.nio.file.Path): String =
      mGenerateBridgeMainTsFromManifest.invoke(null, manifest).asInstanceOf[String]
  }

  private object BridgeGen {
    private lazy val cls       = Class.forName("cloud.golem.tooling.bridge.TypeScriptBridgeGenerator")
    private lazy val mGenerate =
      cls.getMethods
        .find(m => m.getName == "generate" && m.getParameterTypes.length == 1)
        .getOrElse(sys.error("Missing TypeScriptBridgeGenerator.generate(BridgeSpec) on classpath"))

    def generate(spec: AnyRef): String =
      mGenerate.invoke(null, spec).asInstanceOf[String]
  }

  private object BridgeManifest {
    private lazy val cls   = Class.forName("cloud.golem.tooling.bridge.BridgeSpecManifest")
    private lazy val mRead = cls.getMethod("read", classOf[java.nio.file.Path])

    def read(path: java.nio.file.Path): AnyRef =
      mRead.invoke(null, path).asInstanceOf[AnyRef]
  }

  private def generateBridgeFromProvider(
    providerClassName: String,
    cp: Seq[File],
    log: Logger
  ): (String, AnyRef) = {
    val urls = cp.distinct.map(_.toURI.toURL).toArray
    val cl   = new java.net.URLClassLoader(urls, null) // isolated, so tooling-core types line up within this loader
    try {
      val providerCls = cl.loadClass(providerClassName)
      val provider    = providerCls.getDeclaredConstructor().newInstance()
      val getMethod   = providerCls.getMethods
        .find(_.getName == "get")
        .getOrElse(
          sys.error(s"$providerClassName does not define a get() method (expected java.util.function.Supplier)")
        )
      val spec = getMethod.invoke(provider)

      val genCls = cl.loadClass("cloud.golem.tooling.bridge.TypeScriptBridgeGenerator")
      val mGen   = genCls.getMethods
        .find(m => m.getName == "generate" && m.getParameterTypes.length == 1)
        .getOrElse(
          sys.error("Missing TypeScriptBridgeGenerator.generate(BridgeSpec) on classpath")
        )
      (mGen.invoke(null, spec).asInstanceOf[String], spec.asInstanceOf[AnyRef])
    } finally {
      try cl.close()
      catch { case _: Throwable => () }
    }
  }

  /**
   * golem-cli's `component new ts` scaffold currently includes a sample
   * `httpApi` definition that calls `counter-agent(...)`. Our Scala.js examples
   * don't ship that agent, and `app deploy` will fail while compiling the RIB
   * bindings unless we remove that section.
   *
   * We keep this as a text transformation to avoid taking on a YAML dependency
   * in the plugin.
   */
  private def stripHttpApiFromGolemYaml(componentDir: File, log: Logger): Unit =
    Tooling.stripHttpApiFromGolemYaml(componentDir.toPath, msg => log.info(msg))

  // Prefer coreutils `timeout` for reliable kill of hung processes; fall back to manual Await.
  private def isNoisyUpstreamWarning(line: String): Boolean =
    Tooling.isNoisyUpstreamWarning(line)

  private def runWithTimeout(cmd: Seq[String], cwd: File, label: String, timeoutSec: Int, log: Logger): Int = {
    log.info(s"[$label] starting (cwd=${cwd.getAbsolutePath}): ${cmd.mkString(" ")}")
    val timeoutCmd = Seq("timeout", s"${timeoutSec}s") ++ cmd
    val proc       = Process(timeoutCmd, cwd).run(
      ProcessLogger(
        line => if (!isNoisyUpstreamWarning(line)) log.info(line),
        line => if (!isNoisyUpstreamWarning(line)) log.error(line)
      )
    )
    val futureExit = Future(blocking(proc.exitValue()))
    try {
      Await.result(futureExit, (timeoutSec + 10).seconds) // small grace
    } catch {
      case _: TimeoutException =>
        log.error(s"[$label] timed out after ${timeoutSec}s; killing process")
        proc.destroy()
        -999
    }
  }

  private def runWithTimeoutCapture(
    cmd: Seq[String],
    cwd: File,
    label: String,
    timeoutSec: Int,
    log: Logger
  ): (Int, String) = {
    log.info(s"[$label] starting (cwd=${cwd.getAbsolutePath}): ${cmd.mkString(" ")}")
    val timeoutCmd = Seq("timeout", s"${timeoutSec}s") ++ cmd
    val out        = new StringBuilder
    val proc       = Process(timeoutCmd, cwd).run(
      ProcessLogger(
        line => {
          if (!isNoisyUpstreamWarning(line)) log.info(line)
          out.append(line).append('\n')
        },
        line => {
          if (!isNoisyUpstreamWarning(line)) log.error(line)
          out.append(line).append('\n')
        }
      )
    )
    val futureExit = Future(blocking(proc.exitValue()))
    try {
      val exit = Await.result(futureExit, (timeoutSec + 10).seconds)
      (exit, out.result())
    } catch {
      case _: TimeoutException =>
        log.error(s"[$label] timed out after ${timeoutSec}s; killing process")
        proc.destroy()
        (-999, out.result())
    }
  }

  private lazy val runQuickstartTask = Def.task {
    val log    = streams.value.log
    val node   = golemNodeCommand.value
    val bundle = golemFastLink.value
    val base   = baseDirectory.value

    log.info(s"Running ${bundle.getName} with $node ...")
    val env  = "GOLEM_QUICKSTART" -> "1"
    val exit = Process(Seq(node, bundle.getAbsolutePath), base, env).!
    if (exit != 0) sys.error(s"Golem quickstart execution failed with exit code $exit")
    else log.info("Golem quickstart completed successfully.")
  }

  private def ensurePortFree(host: String, port: Int): Unit = {
    var socket: ServerSocket = null
    try {
      socket = new ServerSocket()
      socket.setReuseAddress(true)
      socket.bind(new InetSocketAddress(host, port))
    } catch {
      case _: Throwable =>
        sys.error(
          s"Port $host:$port is already in use; stop the existing process or set GOLEM_ROUTER_PORT to a free port."
        )
    } finally {
      if (socket != null) {
        try socket.close()
        catch { case _: Throwable => () }
      }
    }
  }

  private def waitForRouter(host: String, port: Int, attempts: Int, log: Logger): Unit = {
    var remaining = attempts
    var connected = false
    while (!connected && remaining > 0) {
      val socket = new Socket()
      try {
        socket.connect(new InetSocketAddress(host, port), 1000)
        connected = true
      } catch {
        case _: Throwable =>
          Thread.sleep(1000)
          remaining -= 1
      } finally {
        try socket.close()
        catch { case _: Throwable => () }
      }
    }
    if (!connected) sys.error(s"Timed out waiting for Golem router at $host:$port")
  }

  private def writeBridgeSources(srcDir: File, bundleName: String): Unit = {
    val mainTs =
      """import "./metadata";
        |import "./hostTestsAgent";
        |
        |export { guest, loadSnapshot, saveSnapshot } from "./scalaBridge";
        |""".stripMargin
    val bridgeTs =
      s"""// @ts-ignore Scala.js bundle does not ship TypeScript declarations
         |import * as scalaExports from "./$bundleName";
         |
         |function requireExport<T>(name: string): T {
         |  const value = (scalaExports as Record<string, unknown>)[name];
         |  if (value === undefined || value === null) {
         |    throw new Error(\u0060Missing Scala.js export "${name}". Did hostTests/fastLinkJS finish successfully?\u0060);
         |  }
         |  return value as T;
         |}
         |
         |export const guest = requireExport<any>("guest");
         |export const loadSnapshot = requireExport<any>("loadSnapshot");
         |export const saveSnapshot = requireExport<any>("saveSnapshot");
         |
         |export interface HostTestHarness {
         |  run(tests: string[] | undefined): Promise<string>;
         |}
         |
         |export const hostTestHarness: HostTestHarness = requireExport("hostTestHarness");
         |""".stripMargin
    val agentTs =
      """import { BaseAgent, agent, description, prompt } from "@golemcloud/golem-ts-sdk";
        |import { hostTestHarness } from "./scalaBridge";
        |
        |@agent({ name: "scala-host-tests-harness" })
        |export class HostTestsAgent extends BaseAgent {
        |  constructor() {
        |    super();
        |  }
        |
        |  @prompt("Execute the Scala host RPC test suite")
        |  @description("Runs the Scala.js RPC harness on the live Golem host and returns a JSON report")
        |  async runTests(tests: string[] | undefined): Promise[String] {
        |    return await hostTestHarness.run(tests);
        |  }
        |}
        |""".stripMargin.replace("Promise[String]", "Promise<string>")
    val metadataTs =
      """// Metadata registration is optional for host-tests and can be generated separately.
        |export {};
        |""".stripMargin

    IO.write(srcDir / "main.ts", mainTs)
    IO.write(srcDir / "scalaBridge.ts", bridgeTs)
    IO.write(srcDir / "hostTestsAgent.ts", agentTs)
    IO.write(srcDir / "metadata.ts", metadataTs)
  }

  private def writeMinimalGolemYaml(component: String, dir: File): Unit = {
    val yaml =
      s"""components:
         |  $component:
         |    template: ts
         |
         |dependencies:
         |  $component:
         |""".stripMargin
    IO.write(dir / "golem.yaml", yaml)
  }

  private def componentSlug(qualified: String): String =
    qualified.replace(":", "-")

  private def ensureAppScaffold(
    appRoot: File,
    appName: String,
    component: String,
    log: Logger
  ): File = {
    IO.createDirectory(appRoot)
    val appDir = appRoot / appName
    if (!appDir.exists()) log.info(s"[golem] Creating deterministic app scaffold at ${appDir.getAbsolutePath}")
    Tooling.ensureTsAppScaffold(appRoot.toPath, appName, component, msg => log.info(msg)).toFile
  }

  private def ensureComponentScaffoldTs(
    appDir: File,
    component: String,
    template: String,
    cliBase: Seq[String],
    flags: Seq[String],
    timeoutSec: Int,
    log: Logger
  ): File = {
    val slug         = componentSlug(component)
    val componentDir = appDir / s"components-ts/$slug"
    if (!componentDir.exists()) log.info(s"[golem] Creating deterministic TS component scaffold for $component")
    val ensured = Tooling.ensureTsComponentScaffold(appDir.toPath, component, msg => log.info(msg))
    ensured.toFile
  }

  private def ensureAppAndComponentScaffold(
    appRoot: File,
    appName: String,
    component: String,
    template: String,
    cliBase: Seq[String],
    flags: Seq[String],
    timeoutSec: Int,
    log: Logger
  ): File = {
    val appDir       = ensureAppScaffold(appRoot, appName, component, log)
    val componentDir = ensureComponentScaffoldTs(appDir, component, template, cliBase, flags, timeoutSec, log)
    stripHttpApiFromGolemYaml(componentDir, log)
    componentDir
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    golemNodeCommand    := "node",
    golemNpmCommand     := "npm",
    golemNodeModulesDir := baseDirectory.value / "node_modules",
    golemFastLink       := {
      val bundle = (Compile / fastLinkJS / scalaJSLinkedFile).value.data
      streams.value.log.info(s"Golem quickstart bundle linked at ${bundle.getAbsolutePath}")
      bundle
    },
    golemSetup := {
      val log     = streams.value.log
      val modules = golemNodeModulesDir.value
      val base    = baseDirectory.value
      val npm     = golemNpmCommand.value

      if (modules.exists()) log.info("node_modules already present; skipping npm install.")
      else {
        log.info("Installing npm dependencies via `npm install`...")
        val exit = Process(Seq(npm, "install"), base).!
        if (exit != 0) sys.error(s"`npm install` failed with exit code $exit")
      }
    },
    golemRun := (runQuickstartTask dependsOn golemSetup).value,

    // Generic primitives defaults
    golemAppRoot           := (ThisBuild / baseDirectory).value / ".golem-apps",
    golemAppName           := name.value,
    golemComponent         := "",
    golemComponentTemplate := "ts",
    golemCli               := "golem-cli",
    // Allow env-driven overrides so CI/users can switch between local and cloud without editing build files.
    // Example: GOLEM_CLI_FLAGS="--cloud -p my-profile"
    golemCliFlags := sys.env
      .get("GOLEM_CLI_FLAGS")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.split("\\s+").toSeq)
      .getOrElse(Seq("--local")),
    golemTimeoutSec := 180,
    // Default to `<golemAppName>.js` so most projects don't need to set this explicitly.
    // (Override if you need backwards-compatibility with existing scaffolds that expect e.g. `scala.js`.)
    golemBundleFileName          := s"${golemAppName.value}.js",
    golemBridgeMainTs            := "", // required for golemWire; examples set this explicitly
    golemBridgeSpec              := BridgeSpecUnset,
    golemBridgeSpecProviderClass := "",
    // Default to a managed target location; when golemExports is set, we auto-generate this file.
    golemBridgeSpecManifestPath   := (Compile / resourceManaged).value / "golem" / "bridge-spec.properties",
    golemExports                  := Nil,
    golemAutoExports              := true,
    golemEnsureBridgeSpecManifest := Def.taskDyn {
      val log      = streams.value.log
      val exports  = golemExports.value
      val manifest = golemBridgeSpecManifestPath.value

      def writeManifestFromExports(exports: Seq[GolemExport]): File = {
        IO.createDirectory(manifest.getParentFile)

        val scalaBundleImport = s"./${golemBundleFileName.value}"
        val sb                = new StringBuilder()
        sb.append("scalaBundleImport=").append(scalaBundleImport).append("\n")
        sb.append("scalaAgentsExpr=")
          .append(scalaAgentsExprForExportTopLevel(golemScalaShimExportTopLevel.value))
          .append("\n\n")

        exports.zipWithIndex.foreach { case (e, idx) =>
          val p         = s"agents.$idx."
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
            case autoImport.GolemConstructor.NoArg =>
              sb.append(p).append("constructor.kind=noarg\n")
            case autoImport.GolemConstructor.Scalar(argName, tsType, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=scalar\n")
              sb.append(p).append("constructor.tsType=").append(tsType).append("\n")
              sb.append(p).append("constructor.argName=").append(argName).append("\n")
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }
            case autoImport.GolemConstructor.Positional(params, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=positional\n")
              params.zipWithIndex.foreach { case (par, pi) =>
                sb.append(p).append(s"constructor.param.$pi.name=").append(par.name).append("\n")
                sb.append(p).append(s"constructor.param.$pi.tsType=").append(par.tsType).append("\n")
              }
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }
            case autoImport.GolemConstructor.Record(inputTypeName, fields, scalaFactoryArgs) =>
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

        IO.write(manifest, sb.result())
        log.info(s"[golem] Wrote BridgeSpec manifest from golemExports at ${manifest.getAbsolutePath}")
        manifest
      }

      if (exports.nonEmpty) Def.task(writeManifestFromExports(exports))
      else if (!golemAutoExports.value) Def.task(manifest)
      else
        Def.task {
          // IMPORTANT: do NOT use `Compile / fullClasspath` here.
          // This task can run during `Compile / sourceGenerators` (i.e. while `compile` is being constructed),
          // and `fullClasspath` depends on `Compile / products` which depends on `Compile / compile`,
          // creating a self-dependency and an apparent "hang" in sbt's execution engine.
          val classDir = (Compile / classDirectory).value
          val cp       =
            (Compile / dependencyClasspath).value.map(_.data) ++ scalaInstance.value.allJars

          autoDetectExportsFromClassfiles(classDir, cp, s => log.info(s)) match {
            case Left(err) =>
              sys.error(err)
            case Right(Vector()) =>
              // No detected exports; keep returning the configured path. Downstream will prompt user to configure bridge.
              manifest
            case Right(detected) =>
              IO.createDirectory(manifest.getParentFile)
              val scalaBundleImport = s"./${golemBundleFileName.value}"
              val sb                = new StringBuilder()
              sb.append("scalaBundleImport=").append(scalaBundleImport).append("\n")
              sb.append("scalaAgentsExpr=")
                .append(scalaAgentsExprForExportTopLevel(golemScalaShimExportTopLevel.value))
                .append("\n\n")

              detected.zipWithIndex.foreach { case (e, idx) =>
                val p            = s"agents.$idx."
                val className    = defaultTsClassNameFromTrait(e.traitClass)
                val scalaFactory = defaultScalaFactoryFromTrait(e.traitClass)

                sb.append(p).append("agentName=").append(e.agentName).append("\n")
                sb.append(p).append("className=").append(className).append("\n")
                sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")
                sb.append(p).append("scalaShimImplClass=").append(e.implClass).append("\n")

                e.ctorParams match {
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

              IO.write(manifest, sb.result())
              log.info(s"[golem] Wrote BridgeSpec manifest from auto-detected exports at ${manifest.getAbsolutePath}")
              manifest
          }
        }
    }.value,
    golemScalaShimExportTopLevel := "__golemInternalScalaAgents",
    golemScalaShimObjectName     := "GolemInternalScalaAgents",
    golemScalaShimPackage        := "cloud.golem.internal",

    // App run defaults (opt-in: users must set agentId/function, otherwise golemAppRun errors)
    golemRunAgentId      := "",
    golemRunFunction     := "",
    golemRunArgs         := Nil,
    golemRunDeployFirst  := true,
    golemRunPublishFirst := false,
    golemRunPublish      := {},
    golemReplTimeoutSec  := sys.env
      .get("GOLEM_REPL_TIMEOUT_SEC")
      .flatMap(s => scala.util.Try(s.toInt).toOption)
      .getOrElse(60),
    golemReplDisableStream   := true,
    golemUpdateMode          := "auto",
    golemUpdateAwait         := true,
    golemUpdateTargetVersion := None,
    golemAppRun              := Def.taskDyn {
      Def.task {
        val log = streams.value.log

        // Safe to always depend on this; it is a no-op unless in `--local` mode.
        val _ = golemLocalUp.value

        val agentId = golemRunAgentId.value.trim
        val fn      = golemRunFunction.value.trim
        if (agentId.isEmpty || fn.isEmpty) {
          sys.error(
            "golemAppRun requires golemRunAgentId and golemRunFunction to be set.\n" +
              "Example:\n" +
              "  golemRunAgentId := \"org:component/agent-type()\"\n" +
              "  golemRunFunction := \"org:component/agent-type.{method}\""
          )
        }

        val flags       = golemCliFlags.value
        val args        = golemRunArgs.value
        val appDir      = golemAppRoot.value / golemAppName.value
        val golemCliCmd = golemCli.value
        val cliBase     = Seq("env", "-u", "ARGV0", golemCliCmd)

        val cmd = cliBase ++ (flags ++ (Seq("--yes", "agent", "invoke", agentId, fn) ++ args))
        log.info(s"[golem] golemAppRun: invoking $agentId $fn")
        val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent invoke", golemTimeoutSec.value, log)
        if (exit != 0) sys.error(s"golem-cli agent invoke failed with exit code $exit\n$out")
      }
    }.dependsOn(
      Def.taskDyn {
        val publish =
          if (golemRunPublishFirst.value) Def.task(golemRunPublish.value) else Def.task {}
        val deploy =
          if (golemRunDeployFirst.value) golemDeployUpdate.toTask("") else Def.task {}
        Def.task {}.dependsOn(publish, deploy)
      }
    ).value,
    golemAppRunScript := Def.inputTaskDyn {
      val log           = streams.value.log
      val scriptPathStr = spaceDelimited("<script-file>").parsed.headOption.getOrElse("")
      if (scriptPathStr.trim.isEmpty) sys.error("Usage: golemAppRunScript <path-to.rib>")

      val scriptFile0 = file(scriptPathStr)
      val scriptFile  =
        if (scriptFile0.isAbsolute) scriptFile0
        else (ThisBuild / baseDirectory).value / scriptFile0.getPath

      Def.task {
        // Safe to always depend on this; it is a no-op unless in `--local` mode.
        val _ = golemLocalUp.value

        if (!scriptFile.exists())
          sys.error(s"Script file not found: ${scriptFile.getAbsolutePath}")

        val flags       = golemCliFlags.value
        val appDir      = golemAppRoot.value / golemAppName.value
        val component   = golemComponent.value
        val golemCliCmd = golemCli.value
        val cliBase     = Seq("env", "-u", "ARGV0", golemCliCmd)

        val replArgs =
          Seq("--yes", "repl", component, "--script-file", scriptFile.getAbsolutePath) ++
            (if (golemReplDisableStream.value) Seq("--disable-stream") else Nil)

        log.info(s"[golem] golemAppRunScript: running repl script ${scriptFile.getAbsolutePath}")
        val (exit, out) =
          runWithTimeoutCapture(cliBase ++ (flags ++ replArgs), appDir, "repl", golemReplTimeoutSec.value, log)
        if (exit != 0) sys.error(s"golem-cli repl failed with exit code $exit\n$out")
      }
    }.dependsOn(
      Def.taskDyn {
        val publish =
          if (golemRunPublishFirst.value) Def.task(golemRunPublish.value) else Def.task {}
        val deploy =
          if (golemRunDeployFirst.value) golemDeployUpdate.toTask("") else Def.task {}
        Def.task {}.dependsOn(publish, deploy)
      }
    ).evaluated,

    // Local runtime management defaults
    golemRouterHost         := sys.env.getOrElse("GOLEM_ROUTER_HOST", "127.0.0.1"),
    golemRouterPort         := sys.env.get("GOLEM_ROUTER_PORT").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(9881),
    golemLocalDataDir       := (ThisBuild / baseDirectory).value / ".golem-local",
    golemStartLocalServer   := true,
    golemLocalServerPidFile := golemLocalDataDir.value / "server.pid",
    golemLocalServerLogFile := golemLocalDataDir.value / "server.log",
    golemLocalDown          := {
      val log     = streams.value.log
      val pidFile = golemLocalServerPidFile.value
      if (!pidFile.exists()) {
        log.info("[golem] No managed local server PID file found; nothing to stop.")
      } else {
        val pid = IO.read(pidFile).trim
        if (pid.nonEmpty) {
          log.info(s"[golem] Stopping managed local server (pid=$pid)")
          try scala.sys.process.Process(Seq("kill", "-TERM", pid)).!
          catch { case _: Throwable => () }
          Thread.sleep(500)
          try scala.sys.process.Process(Seq("kill", "-KILL", pid)).!
          catch { case _: Throwable => () }
        }
        IO.delete(pidFile)
      }
    },
    golemLocalUp := {
      val log     = streams.value.log
      val flags   = golemCliFlags.value
      val isLocal = flags.contains("--local") && !flags.contains("--cloud")

      if (!isLocal) {
        log.info("[golem] golemLocalUp: GOLEM_CLI_FLAGS is not local; skipping local server management.")
      } else if (!golemStartLocalServer.value) {
        log.info("[golem] golemLocalUp: golemStartLocalServer=false; skipping local server management.")
      } else {
        val host     = golemRouterHost.value
        val port     = golemRouterPort.value
        val dataDir  = golemLocalDataDir.value
        val pidFile  = golemLocalServerPidFile.value
        val logFile  = golemLocalServerLogFile.value
        val golemBin = sys.env.getOrElse("GOLEM_BIN", "golem")

        def routerReachable(): Boolean =
          try {
            val sock = new Socket()
            sock.connect(new InetSocketAddress(host, port), 500)
            sock.close()
            true
          } catch { case _: Throwable => false }

        if (routerReachable()) {
          log.info(s"[golem] golemLocalUp: router already reachable at $host:$port")
        } else {
          // If a previous run left a PID file around, attempt to stop it first.
          if (pidFile.exists()) {
            val stale = IO.read(pidFile).trim
            if (stale.nonEmpty) {
              log.warn(s"[golem] golemLocalUp: found stale pid file (pid=$stale); attempting to stop")
              try scala.sys.process.Process(Seq("kill", "-TERM", stale)).!
              catch { case _: Throwable => () }
              Thread.sleep(500)
              try scala.sys.process.Process(Seq("kill", "-KILL", stale)).!
              catch { case _: Throwable => () }
            }
            IO.delete(pidFile)
          }

          log.info(s"[golem] Starting local golem server on $host:$port (dataDir=${dataDir.getAbsolutePath})")
          ensurePortFree(host, port)
          IO.createDirectory(dataDir)
          IO.write(logFile, "")

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
              dataDir.getAbsolutePath,
              "--router-port",
              port.toString
            )

          val pb = new java.lang.ProcessBuilder(cmd: _*)
          pb.directory((ThisBuild / baseDirectory).value)
          pb.redirectErrorStream(true)
          pb.redirectOutput(java.lang.ProcessBuilder.Redirect.appendTo(logFile))
          val p   = pb.start()
          val pid = p.pid().toString
          IO.write(pidFile, pid)

          waitForRouter(host, port, attempts = 120, log = log)
          log.info(s"[golem] Local router is reachable at $host:$port (pid=$pid)")
        }
      }
    },
    Compile / sourceGenerators += golemGenerateScalaShim.taskValue,
    golemGenerateScalaShim := {
      val log = streams.value.log
      // IMPORTANT: do NOT call `golemEnsureBridgeSpecManifest` from a source generator.
      //
      // During a clean build, sourceGenerators run before classfiles exist, so auto-detection cannot succeed.
      // If we call the task here, sbt will cache its "no manifest written" result for the session and later
      // `golemWire` won't be able to force a re-run after compilation.
      //
      // Instead, we only generate the shim *if* the manifest already exists on disk (typically created by golemWire/golemDeploy).
      val manifest = golemBridgeSpecManifestPath.value.toPath
      if (!java.nio.file.Files.exists(manifest)) {
        Nil
      } else {
        val src = Tooling.generateScalaShimFromManifest(
          manifest,
          golemScalaShimExportTopLevel.value,
          golemScalaShimObjectName.value,
          golemScalaShimPackage.value
        )
        if (src.trim.isEmpty) Nil
        else {
          val dir  = (Compile / sourceManaged).value / "golem" / "internal"
          val file = dir / s"${golemScalaShimObjectName.value}.scala"
          IO.createDirectory(dir)
          IO.write(file, src)
          log.info(s"[golem] Generated Scala shim at ${file.getAbsolutePath}")
          Seq(file)
        }
      }
    },
    golemGenerateBridgeMainTs := {
      val log = streams.value.log
      // Keep `.value` dependencies outside conditionals.
      val cp           = (Compile / fullClasspath).value.map(_.data)
      val manifestFile = golemEnsureBridgeSpecManifest.value

      val spec = golemBridgeSpec.value
      if (!(spec eq BridgeSpecUnset)) BridgeGen.generate(spec)
      else {
        val provider = golemBridgeSpecProviderClass.value.trim
        if (provider.nonEmpty) {
          log.info(s"[golem] Generating bridge via provider class: $provider")
          val (ts, _) = generateBridgeFromProvider(provider, cp, log)
          ts
        } else {
          val manifestPath = manifestFile.toPath
          if (!java.nio.file.Files.exists(manifestPath)) ""
          else Tooling.generateBridgeMainTsFromManifest(manifestPath)
        }
      }
    },
    golemScaffold := {
      val log        = streams.value.log
      val appRoot    = golemAppRoot.value
      val appName    = golemAppName.value
      val component  = golemComponent.value
      val template   = golemComponentTemplate.value
      val flags      = golemCliFlags.value
      val timeoutSec = golemTimeoutSec.value

      if (component.trim.isEmpty)
        sys.error(
          "golemComponent is not set. Provide e.g. `golemComponent := \"org:component\"` before running golemScaffold."
        )

      // Deterministic scaffold; golem-cli is only required for build/deploy/invoke.
      ensureAppAndComponentScaffold(appRoot, appName, component, template, Nil, flags, timeoutSec, log)
    },
    golemWire := Def.taskDyn {
      val log          = streams.value.log
      val componentDir = golemScaffold.value

      // IMPORTANT: enforce ordering.
      //
      // In sbt, `.value` dependencies are extracted statically; if we reference both `compile` and
      // `golemEnsureBridgeSpecManifest` in the same task, sbt is free to run them in either order.
      //
      // So we use a *nested* `Def.taskDyn`:
      // - stage 1 task depends only on `compile`
      // - after that completes, we return a task that ensures the manifest and then wires/links
      Def.taskDyn {
        val _compiledOnce = (Compile / compile).value
        // Stage 2 depends only on manifest generation (which depends on compiled classfiles).
        // This ensures the manifest exists before any tasks that read it (bridge generation, shim generation, etc).
        Def.taskDyn {
          val _manifestEnsured = golemEnsureBridgeSpecManifest.value

          // Stage 3: force evaluation of managed sources after the manifest exists, so `golemGenerateScalaShim`
          // can emit the shim and the subsequent `fastLink` will see it as an input.
          Def.taskDyn {
            val _managed = (Compile / managedSources).value
            Def.task {
              val bundleName  = golemBundleFileName.value
              val specSetting = golemBridgeSpec.value
              val provider    = golemBridgeSpecProviderClass.value.trim

              // Keep `.value` dependencies outside conditionals.
              val cp = (Compile / fullClasspath).value.map(_.data)
              // Precompute so sbt's task linter doesn't treat it as a conditional dependency.
              val generatedMainTsFromManifest = golemGenerateBridgeMainTs.value.trim

              // We just created the manifest file, but the initial compilation (done to produce classfiles for auto-detection)
              // ran before the manifest existed, so the shim source generator returned Nil.
              // Force a recompilation so the shim gets generated and included in the linked bundle.
              IO.delete((Compile / classDirectory).value)

              val bundle = golemFastLink.value

              val (mainTsContent, usedSpecOpt): (String, Option[AnyRef]) =
                Option(golemBridgeMainTs.value).map(_.trim).filter(_.nonEmpty) match {
                  case Some(ts) => (ts, None)
                  case None     =>
                    if (!(specSetting eq BridgeSpecUnset)) {
                      val s = specSetting.asInstanceOf[AnyRef]
                      (BridgeGen.generate(s), Some(s))
                    } else if (provider.nonEmpty) {
                      val (ts, specObj) = generateBridgeFromProvider(provider, cp, log)
                      (ts, Some(specObj))
                    } else {
                      // Fall back to the manifest-driven generator (golemExports / autoExports).
                      // This is the minimal-config path: users set golemComponent (and optionally golemExports),
                      // and the plugin generates the bridge deterministically.
                      if (generatedMainTsFromManifest.nonEmpty) (generatedMainTsFromManifest, None) else ("", None)
                    }
                }

              if (mainTsContent.trim.isEmpty) {
                sys.error(
                  "No bridge configured. Set golemBridgeMainTs or golemBridgeSpec before running golemWire."
                )
              }

              val srcDir = componentDir / "src"
              IO.createDirectory(srcDir)
              Tooling.wireTsComponent(
                componentDir.toPath,
                bundle.toPath,
                bundleName,
                mainTsContent,
                msg => log.info(msg)
              )
              componentDir
            }
          }
        }
      }
    }.value,
    golemBuild := {
      val log          = streams.value.log
      val _            = golemLocalUp.value
      val componentDir = golemWire.value
      val appDir       = componentDir.getParentFile.getParentFile // <app>/components-ts/<slug>
      val component    = golemComponent.value
      val golemCliCmd  = golemCli.value
      val flags        = golemCliFlags.value
      val timeoutSec   = golemTimeoutSec.value
      val cliBase      = Seq("env", "-u", "ARGV0", golemCliCmd)

      log.info("[golem] Running golem-cli app build")
      val exit = runWithTimeout(
        cliBase ++ (flags ++ Seq("--yes", "app", "build", component)),
        appDir,
        "app build",
        timeoutSec,
        log
      )
      if (exit != 0) sys.error(s"golem-cli app build failed with exit code $exit")
    },
    golemDeploy := {
      val log          = streams.value.log
      val _            = golemLocalUp.value
      val componentDir = golemWire.value
      val appDir       = componentDir.getParentFile.getParentFile
      val component    = golemComponent.value
      val golemCliCmd  = golemCli.value
      val flags        = golemCliFlags.value
      val timeoutSec   = golemTimeoutSec.value
      val cliBase      = Seq("env", "-u", "ARGV0", golemCliCmd)

      log.info("[golem] Running golem-cli app deploy")
      val exit = runWithTimeout(
        cliBase ++ (flags ++ Seq("--yes", "app", "deploy", component)),
        appDir,
        "app deploy",
        timeoutSec,
        log
      )
      if (exit != 0) sys.error(s"golem-cli app deploy failed with exit code $exit")
    },
    golemDeployUpdate := {
      val _   = golemLocalUp.value
      val log = streams.value.log

      val rawArgs = spaceDelimited("<arg>").parsed.toVector

      // tiny flag parser (keep plugin dependency-free)
      var mode: String           = golemUpdateMode.value
      var await: Boolean         = golemUpdateAwait.value
      var target: Option[String] = golemUpdateTargetVersion.value
      val agentIds               = scala.collection.mutable.ArrayBuffer.empty[String]
      var updateAll              = false

      var i = 0
      while (i < rawArgs.length) {
        rawArgs(i) match {
          case "--all" =>
            updateAll = true
            i += 1
          case "--mode" if i + 1 < rawArgs.length =>
            mode = rawArgs(i + 1)
            i += 2
          case "--no-await" =>
            await = false
            i += 1
          case "--await" =>
            await = true
            i += 1
          case "--target" if i + 1 < rawArgs.length =>
            target = Some(rawArgs(i + 1))
            i += 2
          case other if other.startsWith("--") =>
            sys.error(s"Unknown flag for golemDeployUpdate: $other")
          case agentId =>
            agentIds += agentId
            i += 1
        }
      }

      // If no explicit agent ids were provided, default to updating all agents for the component.
      if (agentIds.isEmpty) updateAll = true

      // Deploy first (new component version)
      val _deploy = golemDeploy.value

      val componentDir = golemScaffold.value
      val appDir       = componentDir.getParentFile.getParentFile
      val component    = golemComponent.value
      val golemCliCmd  = golemCli.value
      val flags        = golemCliFlags.value
      val timeoutSec   = golemTimeoutSec.value
      val cliBase      = Seq("env", "-u", "ARGV0", golemCliCmd)

      val awaitFlag = if (await) Seq("--await") else Nil
      val modeArg   = Seq(mode)
      val targetArg = target.toSeq

      def listAgentsForComponent(): Vector[String] = {
        // golem-cli agent list --format json returns:
        // { "workers": [ { "componentName": "...", "workerName": "agent-type(...)" }, ... ] }
        val cmd         = cliBase ++ (flags ++ Seq("--yes", "--format", "json", "agent", "list", component))
        val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent list", timeoutSec, log)
        if (exit != 0) sys.error(s"golem-cli agent list failed with exit code $exit\n$out")

        // Extract workerName values without adding a JSON dependency.
        // Match JSON string content with escape support: ((?:\\.|[^"\\])*)
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
                case '"'                     => sb.append('"'); i += 2
                case '\\'                    => sb.append('\\'); i += 2
                case 'n'                     => sb.append('\n'); i += 2
                case 'r'                     => sb.append('\r'); i += 2
                case 't'                     => sb.append('\t'); i += 2
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

        val workerNames: Vector[String] =
          workerNamePattern.findAllMatchIn(out).map(m => unescapeJsonString(m.group(1))).toVector

        workerNames.map(wn => s"$component/$wn").distinct
      }

      val resolvedAgents: Vector[String] =
        if (updateAll || agentIds.isEmpty) {
          val ids = listAgentsForComponent()
          if (ids.isEmpty) log.info(s"[golem] No existing agents found for component $component; nothing to update.")
          else log.info(s"[golem] Updating ${ids.length} agent(s) for component $component")
          ids
        } else agentIds.toVector

      resolvedAgents.foreach { agentId =>
        log.info(s"[golem] Updating agent $agentId (mode=$mode, target=${target.getOrElse("latest")}, await=$await)")
        // Updates can legitimately take several minutes (even locally), especially when many agents exist.
        // Use a larger timeout than the generic CLI timeout to avoid spurious failures.
        val updateTimeoutSec = math.max(timeoutSec, 600)
        val cmd              =
          cliBase ++ (flags ++ (Seq("--yes", "agent", "update") ++ awaitFlag ++ Seq(agentId) ++ modeArg ++ targetArg))
        val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent update", updateTimeoutSec, log)
        if (exit != 0) {
          // golem-cli sometimes reports a non-zero exit with this message even if the update has completed quickly.
          // Treat it as a soft success and let subsequent invocations observe the new version.
          if (await && out.contains("update is not pending anymore, but no outcome has been found")) {
            log.info(
              s"[golem] agent update returned a transient status for $agentId; continuing (CLI reported no outcome yet)"
            )
          } else {
            sys.error(s"golem-cli agent update failed with exit code $exit for agentId=$agentId\n$out")
          }
        }
      }
    },
    golemInvoke := {
      val _    = golemLocalUp.value
      val log  = streams.value.log
      val args = spaceDelimited("<arg>").parsed
      if (args.lengthCompare(2) < 0)
        sys.error("Usage: golemInvoke <agentId> <fullyQualifiedMethod> [arg1] [arg2] ...")

      val agentId  = args.head
      val function = args(1)
      val payloads = args.drop(2)

      val componentDir = golemScaffold.value
      val appDir       = componentDir.getParentFile.getParentFile
      val golemCliCmd  = golemCli.value
      val flags        = golemCliFlags.value
      val timeoutSec   = golemTimeoutSec.value
      val cliBase      = Seq("env", "-u", "ARGV0", golemCliCmd)

      val cmd         = cliBase ++ (flags ++ (Seq("--yes", "agent", "invoke", agentId, function) ++ payloads))
      val (exit, out) = runWithTimeoutCapture(cmd, appDir, "agent invoke", timeoutSec, log)
      if (exit != 0) sys.error(s"golem-cli agent invoke failed with exit code $exit\n$out")
    }

    // Harness/demo flows live outside the plugin; this plugin exposes only the generic primitives.
  )
}
