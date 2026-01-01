package cloud.golem.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._

import java.io.File

object GolemPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin

  override def trigger: PluginTrigger = noTrigger

  object autoImport {
    val golemAgentGuestWasmFile =
      settingKey[File]("Where to write wasm/agent_guest.wasm for golem-cli packaging (default: <base>/wasm/agent_guest.wasm)")
    val golemEnsureAgentGuestWasm =
      taskKey[File]("Ensure wasm/agent_guest.wasm exists (written from the Scala SDK tooling-core resources)")

    // Generic public primitives (Phase 2 of public SDK plan)
    val golemAppRoot           = settingKey[File]("Root dir for generated Golem apps (default .golem-apps)")
    val golemAppName           = settingKey[String]("App name")
    val golemComponent         = settingKey[String]("Qualified component name (e.g. org:component)")

    val golemBundleFileName =
      settingKey[String]("Filename to copy Scala.js bundle to inside component src/ (default scala.js)")
    val golemBridgeSpecManifestPath =
      settingKey[File](
        "Optional BridgeSpec manifest file (BridgeSpecManifest .properties); used when spec/provider are unset"
      )

    // ---------------------------------------------------------------------------
    // Settings-based exports (Scala-only configuration; generates a hidden BridgeSpec manifest)
    // ---------------------------------------------------------------------------

    /** Wire type expression used by the BridgeSpec manifest. */
    object GolemWireType {
      val string: String  = "string"
      val number: String  = "number"
      val boolean: String = "boolean"
      val void: String    = "void"
      val any: String     = "any"
    }

    final case class GolemParam(name: String, wireType: String)

    sealed trait GolemConstructor
    object GolemConstructor {
      case object NoArg extends GolemConstructor
      final case class Scalar(argName: String, wireType: String, scalaFactoryArgs: Seq[String] = Nil)
          extends GolemConstructor
      final case class Positional(params: Seq[GolemParam], scalaFactoryArgs: Seq[String] = Nil) extends GolemConstructor
      final case class Record(
        inputTypeName: String,
        fields: Seq[GolemParam],
        scalaFactoryArgs: Seq[String] = Nil
      ) extends GolemConstructor
    }

    final case class GolemMethodParam(name: String, wireType: String, implArgExpr: String = "")

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
      returnType: String,
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

    // Intentionally no guest export generation here: the Scala.js SDK exports `guest` from library code.

  }

  import autoImport._

  private def defaultClassNameFromTrait(traitClass: String): String = {
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

  private def writeStandaloneGuestSourceFromManifest(manifestFile: File, outFile: File): Unit = {
    val props = new java.util.Properties()
    val in    = new java.io.FileInputStream(manifestFile)
    try props.load(in)
    finally in.close()

    def get(key: String): Option[String] =
      Option(props.getProperty(key)).map(_.trim).filter(_.nonEmpty)

    val agentIndices: Vector[Int] = {
      val keys = props.stringPropertyNames().toArray(new Array[String](0)).toVector
      keys
        .flatMap { k =>
          val prefix = "agents."
          if (k.startsWith(prefix)) {
            val rest = k.substring(prefix.length)
            val dot  = rest.indexOf('.')
            if (dot > 0) {
              val idxStr = rest.substring(0, dot)
              scala.util.Try(idxStr.toInt).toOption
            } else None
          } else None
        }
        .distinct
        .sorted(Ordering.Int)
    }

    def mkCtorExpr(kind: String, ctorParamCount: Int): (String, String) = {
      kind match {
        case "noarg" =>
          ("Unit", "() => ()")
        case "scalar" =>
          val scalaType = get(s"constructor.scalaType").getOrElse("Any")
          (scalaType, "in")
        case "positional" =>
          val types = (0 until ctorParamCount).toVector.map { i =>
            get(s"constructor.param." + i + ".scalaType").getOrElse("Any")
          }
          val ctorType = if (types.length == 1) types.head else types.mkString("(", ", ", ")")
          val args     =
            if (types.length == 1) "in"
            else types.indices.map(i => s"in._${i + 1}").mkString(", ")
          (ctorType, args)
        case other =>
          ("Any", "in")
      }
    }

    val registrations = agentIndices.flatMap { idx =>
      val base         = s"agents.$idx."
      val traitClass   = get(base + "traitClass")
      val implClass    = get(base + "implClass").orElse(get(base + "scalaShimImplClass"))
      val ctorKind     = get(base + "constructor.kind").getOrElse("noarg")
      val ctorParamCnt =
        props
          .stringPropertyNames()
          .toArray(new Array[String](0))
          .count(k => k.startsWith(base + "constructor.param.") && k.endsWith(".scalaType"))

      (traitClass, implClass) match {
        case (Some(traitFqn), Some(implFqn)) =>
          val (ctorType, ctorArgsExpr0) = ctorKind match {
            case "noarg" =>
              ("Unit", "")
            case "scalar" =>
              val t = get(base + "constructor.scalaType").getOrElse("Any")
              (t, "in")
            case "positional" =>
              val types = (0 until ctorParamCnt).toVector.map { i =>
                get(base + s"constructor.param.$i.scalaType").getOrElse("Any")
              }
              val ctorType = if (types.length == 1) types.head else types.mkString("(", ", ", ")")
              val args =
                if (types.length == 1) "in"
                else types.indices.map(i => s"in._${i + 1}").mkString(", ")
              (ctorType, args)
            case _ =>
              ("Any", "in")
          }

          val registration =
            ctorKind match {
              case "noarg" =>
                s"cloud.golem.runtime.autowire.AgentImplementation.register[$traitFqn](new $implFqn())"
              case _ =>
                s"cloud.golem.runtime.autowire.AgentImplementation.register[$traitFqn, $ctorType](in => new $implFqn($ctorArgsExpr0))"
            }
          Some(registration)
        case _ => None
      }
    }

    val registrationBlock =
      if (registrations.isEmpty) ""
      else registrations.map(r => s"      $r").mkString("\n")

    val src =
      s"""package cloud.golem.internal
         |
         |import cloud.golem.runtime.autowire.AgentRegistry
         |import scala.scalajs.js
         |import scala.scalajs.js.Dynamic
         |import scala.scalajs.js.annotation.JSExportTopLevel
         |
         |object GolemStandaloneGuest {
         |  private var registered: Boolean = false
         |
         |  private def ensureRegistered(): Unit =
         |    if (!registered) {
         |      registered = true
         |$registrationBlock
         |    }
         |
         |  private var resolved: js.UndefOr[Resolved] = js.undefined
         |  private final case class Resolved(defn: cloud.golem.runtime.autowire.AgentDefinition[Any], instance: Any)
         |
         |  private def agentError(tag: String, message: String): js.Dynamic =
         |    js.Dynamic.literal("tag" -> tag, "val" -> message)
         |
         |  private def invalidType(message: String): js.Dynamic =
         |    agentError("invalid-type", message)
         |
         |  private def invalidAgentId(message: String): js.Dynamic =
         |    agentError("invalid-agent-id", message)
         |
         |  private def customError(message: String): js.Dynamic = {
         |    val witValue = cloud.golem.runtime.autowire.WitValueBuilder.build(
         |      cloud.golem.data.DataType.StringType,
         |      cloud.golem.data.DataValue.StringValue(message)
         |    ) match {
         |      case Left(_)    => js.Dynamic.literal("tag" -> "string", "val" -> message)
         |      case Right(vit) => vit
         |    }
         |
         |    val node =
         |      js.Dynamic.literal(
         |        "name"  -> (js.undefined: js.UndefOr[String]),
         |        "owner" -> (js.undefined: js.UndefOr[String]),
         |        "type"  -> js.Dynamic.literal("tag" -> "prim-string-type")
         |      )
         |    val witType = js.Dynamic.literal("nodes" -> js.Array(node))
         |
         |    js.Dynamic.literal(
         |      "tag" -> "custom-error",
         |      "val" -> js.Dynamic.literal(
         |        "value" -> witValue,
         |        "typ"   -> witType
         |      )
         |    )
         |  }
         |
         |  private def asAgentError(err: Any, fallbackTag: String): js.Dynamic =
         |    if (err == null) customError("null")
         |    else {
         |      val dyn = err.asInstanceOf[js.Dynamic]
         |      val hasTagVal =
         |        try !js.isUndefined(dyn.selectDynamic("tag")) && !js.isUndefined(dyn.selectDynamic("val"))
         |        catch { case _: Throwable => false }
         |
         |      if (hasTagVal) dyn
         |      else
         |        err match {
         |          case s: String => agentError(fallbackTag, s)
         |          case other     => customError(String.valueOf(other))
         |        }
         |    }
         |
         |  private def normalizeMethodName(methodName: String): String =
         |    if (methodName.contains(".{") && methodName.endsWith("}")) {
         |      val start = methodName.indexOf(".{") + 2
         |      methodName.substring(start, methodName.length - 1)
         |    } else methodName
         |
         |  private def initialize(agentTypeName: String, input: js.Dynamic): js.Promise[Unit] = {
         |    ensureRegistered()
         |    if (!js.isUndefined(resolved)) {
         |      js.Promise.reject(customError("Agent is already initialized in this container")).asInstanceOf[js.Promise[Unit]]
         |    } else {
         |      AgentRegistry.get(agentTypeName) match {
         |        case None =>
         |          js.Promise.reject(invalidType("Invalid agent '" + agentTypeName + "'")).asInstanceOf[js.Promise[Unit]]
         |        case Some(defnAny) =>
         |          defnAny
         |            .initializeAny(input)
         |            .`then`[Unit](
         |              (inst: Any) => { resolved = Resolved(defnAny, inst); () },
         |              (err: Any) => js.Promise.reject(asAgentError(err, "invalid-input")).asInstanceOf[js.Thenable[Unit]]
         |            )
         |      }
         |    }
         |  }
         |
         |  private def invoke(methodName: String, input: js.Dynamic): js.Promise[js.Dynamic] = {
         |    ensureRegistered()
         |    if (js.isUndefined(resolved)) {
         |      js.Promise.reject(invalidAgentId("Agent is not initialized")).asInstanceOf[js.Promise[js.Dynamic]]
         |    } else {
         |      val r  = resolved.asInstanceOf[Resolved]
         |      val mn = normalizeMethodName(methodName)
         |      r.defn
         |        .invokeAny(r.instance, mn, input)
         |        .`catch`[js.Dynamic]((err: Any) =>
         |          js.Promise.reject(asAgentError(err, "invalid-method")).asInstanceOf[js.Thenable[js.Dynamic]]
         |        )
         |    }
         |  }
         |
         |  private def getDefinition(): js.Promise[js.Dynamic] = {
         |    ensureRegistered()
         |    if (js.isUndefined(resolved)) {
         |      js.Promise.reject(invalidAgentId("Agent is not initialized")).asInstanceOf[js.Promise[js.Dynamic]]
         |    } else {
         |      js.Promise.resolve(resolved.asInstanceOf[Resolved].defn.agentType)
         |    }
         |  }
         |
         |  private def discoverAgentTypes(): js.Promise[js.Array[js.Dynamic]] = {
         |    ensureRegistered()
         |    try {
         |      val arr = new js.Array[js.Dynamic]()
         |      AgentRegistry.all.foreach(d => arr.push(d.agentType))
         |      js.Dynamic.global.console.log("[scala.js] discoverAgentTypes.json ->", js.JSON.stringify(arr))
         |      js.Promise.resolve(arr)
         |    } catch {
         |      case t: Throwable =>
         |        js.Promise.reject(asAgentError(t.toString, "custom-error")).asInstanceOf[js.Promise[js.Array[js.Dynamic]]]
         |    }
         |  }
         |
         |  @JSExportTopLevel("guest")
         |  val guest: js.Dynamic =
         |    js.Dynamic.literal(
         |      "initialize"         -> ((agentTypeName: String, input: js.Dynamic) => initialize(agentTypeName, input)),
         |      "invoke"             -> ((methodName: String, input: js.Dynamic) => invoke(methodName, input)),
         |      "getDefinition"      -> (() => getDefinition()),
         |      "discoverAgentTypes" -> (() => discoverAgentTypes())
         |    )
         |}
         |""".stripMargin

    IO.createDirectory(outFile.getParentFile)
    IO.write(outFile, src)
  }

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
              // because schema generation rejects it. As a fallback, infer the boxed primitive return type
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

    private lazy val mGenerateScalaShimFromManifest =
      cls.getMethod(
        "generateScalaShimFromManifest",
        classOf[java.nio.file.Path],
        classOf[String],
        classOf[String],
        classOf[String]
      )

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

  private object BridgeManifest {
    private lazy val cls   = Class.forName("cloud.golem.tooling.bridge.BridgeSpecManifest")
    private lazy val mRead = cls.getMethod("read", classOf[java.nio.file.Path])

    def read(path: java.nio.file.Path): AnyRef =
      mRead.invoke(null, path).asInstanceOf[AnyRef]
  }
  override def projectSettings: Seq[Setting[_]] = Seq(
    golemAgentGuestWasmFile := baseDirectory.value / "wasm" / "agent_guest.wasm",
    golemEnsureAgentGuestWasm := {
      val dest = golemAgentGuestWasmFile.value.toPath
      IO.createDirectory(dest.getParent.toFile)
      val cls = Class.forName("cloud.golem.tooling.GolemTooling")
      val m   = cls.getMethod("ensureAgentGuestWasm", classOf[java.nio.file.Path])
      m.invoke(null, dest)
      dest.toFile
    },

    // Generic primitives defaults
    golemAppRoot           := (ThisBuild / baseDirectory).value / ".golem-apps",
    golemAppName           := name.value,
    golemComponent         := "",
    // Default to `<golemAppName>.js` so most projects don't need to set this explicitly.
    // (Override if you need backwards-compatibility with existing scaffolds that expect e.g. `scala.js`.)
    golemBundleFileName          := s"${golemAppName.value}.js",
    // Default to a managed target location; when golemExports is set, we auto-generate this file.
    golemBridgeSpecManifestPath   := (Compile / resourceManaged).value / "golem" / "bridge-spec.properties",
    golemExports                  := Nil,
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
            else defaultClassNameFromTrait(e.traitClass)
          val scalaFactory =
            if (e.scalaFactory.trim.nonEmpty) e.scalaFactory.trim
            else defaultScalaFactoryFromTrait(e.traitClass)

          sb.append(p).append("traitClass=").append(e.traitClass).append("\n")
          sb.append(p).append("implClass=").append(e.scalaShimImplClass).append("\n")
          sb.append(p).append("agentName=").append(e.agentName).append("\n")
          sb.append(p).append("className=").append(className).append("\n")
          sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")

          e.constructor match {
            case autoImport.GolemConstructor.NoArg =>
              sb.append(p).append("constructor.kind=noarg\n")
            case autoImport.GolemConstructor.Scalar(argName, wireType, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=scalar\n")
              sb.append(p).append("constructor.type=").append(wireType).append("\n")
              sb.append(p).append("constructor.argName=").append(argName).append("\n")
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }
            case autoImport.GolemConstructor.Positional(params, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=positional\n")
              params.zipWithIndex.foreach { case (par, pi) =>
                sb.append(p).append(s"constructor.param.$pi.name=").append(par.name).append("\n")
                sb.append(p).append(s"constructor.param.$pi.type=").append(par.wireType).append("\n")
              }
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }
            case autoImport.GolemConstructor.Record(inputTypeName, fields, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=record\n")
              sb.append(p).append("constructor.inputTypeName=").append(inputTypeName).append("\n")
              fields.zipWithIndex.foreach { case (f, fi) =>
                sb.append(p).append(s"constructor.field.$fi.name=").append(f.name).append("\n")
                sb.append(p).append(s"constructor.field.$fi.type=").append(f.wireType).append("\n")
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
            sb.append(mp).append("returnType=").append(m.returnType).append("\n")
            sb.append(mp).append("implMethodName=").append(m.implMethodName).append("\n")
            m.params.zipWithIndex.foreach { case (par, pi) =>
              val pp = mp + s"param.$pi."
              sb.append(pp).append("name=").append(par.name).append("\n")
              sb.append(pp).append("type=").append(par.wireType).append("\n")
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
      else Def.task(manifest)
    }.value,
    golemScalaShimExportTopLevel := "__golemInternalScalaAgents",
    golemScalaShimObjectName     := "GolemInternalScalaAgents",
    golemScalaShimPackage        := "cloud.golem.internal",
    // Intentionally do not generate any additional sources by default.
    golemGenerateScalaShim := {
      val log = streams.value.log
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
    // No additional wiring helpers are provided by this plugin.
  )
}
