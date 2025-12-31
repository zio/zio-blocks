package cloud.golem.mill

import mill._
import mill.api.PathRef
import mill.scalajslib._
import os.Path

object GolemExports {
  object WireType {
    val string: String  = "string"
    val number: String  = "number"
    val boolean: String = "boolean"
    val void: String    = "void"
    val any: String     = "any"
  }

  final case class Field(name: String, wireType: String)

  sealed trait Constructor
  object Constructor {
    case object NoArg                                                                             extends Constructor
    final case class Scalar(argName: String, wireType: String, scalaFactoryArgs: Seq[String] = Nil) extends Constructor
    final case class Positional(params: Seq[Field], scalaFactoryArgs: Seq[String] = Nil)          extends Constructor
    final case class Record(inputTypeName: String, fields: Seq[Field], scalaFactoryArgs: Seq[String] = Nil)
        extends Constructor
  }

  final case class MethodParam(name: String, wireType: String, implArgExpr: String = "")

  final case class Method(
    name: String,
    returnType: String,
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

  /**
   * Filename to copy Scala.js bundle to inside component `src/` (default:
   * `<golemAppName>.js`).
   */
  def golemBundleFileName: String = s"${golemAppName}.js"

  /**
   * Optional BridgeSpec manifest file (BridgeSpecManifest .properties); used
   * when golemExports is empty and golemAutoExports cannot detect any exports.
   *
   * Can be absolute or relative to the Mill workspace.
   */
  def golemBridgeSpecManifestPath: String = ""

  /**
   * Scala-only export configuration; when non-empty, tooling generates a
   * BridgeSpec manifest automatically (written under the module's Task.dest).
   */
  def golemExports: Seq[GolemExports.Export] = Seq.empty

  /**
   * When `golemExports` is empty, attempt to auto-detect exports from compiled
   * classes annotated with `@agentDefinition` / `@agentImplementation`.
   *
   * Currently this auto mode supports only "primitive-only" agents
   * (String/Boolean/numbers, Option[T], List[T]). More complex shapes still
   * require explicit `golemExports`.
   */
  def golemAutoExports: Boolean = true

  /**
   * JS export name for the generated Scala shim object (default:
   * __golemInternalScalaAgents).
   */
  def golemScalaShimExportTopLevel: String = "__golemInternalScalaAgents"

  /** Scala object name for the generated Scala shim. */
  def golemScalaShimObjectName: String = "GolemInternalScalaAgents"

  /** Scala package for the generated Scala shim (internal). */
  def golemScalaShimPackage: String = "cloud.golem.internal"

  /** Generates the internal Scala shim into managed sources (compile-time). */
  def golemGenerateScalaShim: T[Seq[PathRef]] = Task {
    val log        = Task.log
    val compileRes = compile()
    val compileCp  = compileClasspath().map(_.path).toSeq
    val manifest   = ensureBridgeSpecManifest(Task.dest, log, compileRes.classes.path, compileCp)
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

  // No additional wiring helpers are provided by this plugin.

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
      val cl = new java.net.URLClassLoader(cpUrls, null)

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

      def wireTypeOf(tpe: java.lang.reflect.Type): Either[String, String] = {
        def mapClass(c: Class[?]): Option[String] =
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

        tpe match {
          case c: Class[_] =>
            mapClass(c).toRight(s"Unsupported type for auto exports: ${c.getName}")
          case p: java.lang.reflect.ParameterizedType =>
            val raw  = p.getRawType.asInstanceOf[Class[?]].getName
            val args = p.getActualTypeArguments.toVector
            raw match {
              case "scala.Option" =>
                if (args.length != 1) Left(s"Unsupported Option arity for auto exports: ${p.getTypeName}")
                else wireTypeOf(args.head).map(inner => s"$inner | null")
              case "scala.collection.immutable.List" | "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
                if (args.length != 1) Left(s"Unsupported collection arity for auto exports: ${p.getTypeName}")
                else wireTypeOf(args.head).map(inner => s"$inner[]")
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
        def mapClass(c: Class[?]): String =
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

      val classDir   = classesDir
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
        loaded.filter(c =>
          !c.isInterface && baseAgentCls.isAssignableFrom(c) && !java.lang.reflect.Modifier.isAbstract(c.getModifiers)
        )

      val detected: Vector[Detected] =
        impls.flatMap { impl =>
          val implementedTraits = impl.getInterfaces.toVector.map(_.getName).filter(traits.contains)
          implementedTraits match {
            case Vector(traitName) =>
              val agentName = traits(traitName)
              val ctor      = impl.getConstructors.toVector.sortBy(_.getParameterCount).headOption.orNull
              if (ctor == null) None
              else {
                val ctorParamsE =
                  ctor.getGenericParameterTypes.toVector.zipWithIndex.map { case (tpe, idx) =>
                    wireTypeOf(tpe).map(wt => (s"arg$idx", wt, scalaParamTypeOf(tpe)))
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
                          val outTpe  = p.getActualTypeArguments.headOption.getOrElse(classOf[Object])
                          val outWireE  = wireTypeOf(outTpe)
                          val paramsE =
                            m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt, i) =>
                              wireTypeOf(pt).map(wt => (s"arg$i", wt, scalaParamTypeOf(pt)))
                            }
                          if (outWireE.isLeft) Left(outWireE.swap.getOrElse(""))
                          else if (paramsE.exists(_.isLeft)) Left(paramsE.collectFirst { case Left(e) => e }.get)
                          else Right((m.getName, true, outWireE.toOption.get, paramsE.collect { case Right(v) => v }))

                        case c: Class[?] if c.getName == "scala.runtime.BoxedUnit" || c == java.lang.Void.TYPE =>
                          val paramsE =
                            m.getGenericParameterTypes.toVector.zipWithIndex.map { case (pt, i) =>
                              wireTypeOf(pt).map(wt => (s"arg$i", wt, scalaParamTypeOf(pt)))
                            }
                          if (paramsE.exists(_.isLeft)) Left(paramsE.collectFirst { case Left(e) => e }.get)
                          else Right((m.getName, false, "void", paramsE.collect { case Right(v) => v }))

                        case other =>
                          Left(
                            s"Auto exports only supports Future[...] or Unit return types; found ${other.getTypeName} on $traitName.${m.getName}"
                          )
                      }
                    }

                  if (methodsE.exists(_.isLeft)) None
                  else
                    Some(
                      Detected(agentName, traitName, impl.getName, ctorParams, methodsE.collect { case Right(v) => v })
                    )
                }
              }
            case _ => None
          }
        }.toVector

      if (detected.isEmpty) None
      else {
        val scalaBundleImport = s"./${golemBundleFileName}"
        val sb                = new StringBuilder()
        sb.append("scalaBundleImport=").append(scalaBundleImport).append("\n")
        sb.append("scalaAgentsExpr=")
          .append(
            s"(scalaExports as any).${golemScalaShimExportTopLevel.trim} ?? (globalThis as any).${golemScalaShimExportTopLevel.trim}"
          )
          .append("\n\n")

        detected.zipWithIndex.foreach { case (e, idx) =>
          val p            = s"agents.$idx."
          val className    = "Scala" + e.traitClass.split('.').lastOption.getOrElse("Agent").stripSuffix("$")
          val scalaFactory = "new" + e.traitClass.split('.').lastOption.getOrElse("Agent").stripSuffix("$")

          sb.append(p).append("agentName=").append(e.agentName).append("\n")
          sb.append(p).append("className=").append(className).append("\n")
          sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")
          sb.append(p).append("scalaShimImplClass=").append(e.implClass).append("\n")

          e.ctor match {
            case Vector() =>
              sb.append(p).append("constructor.kind=noarg\n")
            case Vector((argName, wireType, scalaType)) =>
              sb.append(p).append("constructor.kind=scalar\n")
              sb.append(p).append("constructor.argName=").append(argName).append("\n")
              sb.append(p).append("constructor.type=").append(wireType).append("\n")
              sb.append(p).append("constructor.scalaType=").append(scalaType).append("\n")
            case params =>
              sb.append(p).append("constructor.kind=positional\n")
              params.zipWithIndex.foreach { case ((n, t, st), pi) =>
                sb.append(p).append(s"constructor.param.$pi.name=").append(n).append("\n")
                sb.append(p).append(s"constructor.param.$pi.type=").append(t).append("\n")
                sb.append(p).append(s"constructor.param.$pi.scalaType=").append(st).append("\n")
              }
          }

          e.methods.zipWithIndex.foreach { case ((mName, isAsync, wireRet, params), mi) =>
            val mp = p + s"method.$mi."
            sb.append(mp).append("name=").append(mName).append("\n")
            sb.append(mp).append("isAsync=").append(if (isAsync) "true" else "false").append("\n")
            sb.append(mp).append("returnType=").append(wireRet).append("\n")
            sb.append(mp).append("implMethodName=").append(mName).append("\n")
            params.zipWithIndex.foreach { case ((pn, pt, pst), pi) =>
              val pp = mp + s"param.$pi."
              sb.append(pp).append("name=").append(pn).append("\n")
              sb.append(pp).append("type=").append(pt).append("\n")
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
    def defaultClassNameFromTrait(traitClass: String): String = {
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
      val p         = s"agents.$idx."
      val className =
        if (e.className.trim.nonEmpty) e.className.trim
        else defaultClassNameFromTrait(e.traitClass)
      val scalaFactory =
        if (e.scalaFactory.trim.nonEmpty) e.scalaFactory.trim
        else defaultScalaFactoryFromTrait(e.traitClass)

      sb.append(p).append("agentName=").append(e.agentName).append("\n")
      sb.append(p).append("className=").append(className).append("\n")
      sb.append(p).append("scalaFactory=").append(scalaFactory).append("\n")

      e.constructor match {
        case GolemExports.Constructor.NoArg =>
          sb.append(p).append("constructor.kind=noarg\n")
        case GolemExports.Constructor.Scalar(argName, wireType, scalaFactoryArgs) =>
          sb.append(p).append("constructor.kind=scalar\n")
          sb.append(p).append("constructor.type=").append(wireType).append("\n")
          sb.append(p).append("constructor.argName=").append(argName).append("\n")
          scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
            sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
          }
        case GolemExports.Constructor.Positional(params, scalaFactoryArgs) =>
          sb.append(p).append("constructor.kind=positional\n")
          params.zipWithIndex.foreach { case (par, pi) =>
            sb.append(p).append(s"constructor.param.$pi.name=").append(par.name).append("\n")
            sb.append(p).append(s"constructor.param.$pi.type=").append(par.wireType).append("\n")
          }
          scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
            sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
          }
        case GolemExports.Constructor.Record(inputTypeName, fields, scalaFactoryArgs) =>
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

    os.write.over(os.Path(file), sb.result(), createFolders = true)
  }

  // Tooling-core access is done via reflection to keep the Mill plugin classpath stable.
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

  // Harness/demo flows live outside the plugin; this trait exposes only the generic primitives.
}
