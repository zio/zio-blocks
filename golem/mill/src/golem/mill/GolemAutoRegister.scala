package golem.mill

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import mill.scalajslib.api.ModuleInitializer

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import scala.meta.*
import scala.meta.parsers.*

/**
 * Mill mixin that provides Golem Scala.js agent build wiring.
 *
 * Features (matching sbt GolemPlugin):
 *  - Auto-registration source generation (scans `@agentImplementation` classes)
 *  - `golemPrepare` — writes `agent_guest.wasm` and `scala-js-template.yaml` to `.generated/`
 *  - `golemBuildComponent` — builds the Scala.js bundle for golem-cli to consume
 *  - `scalaJSModuleInitializers` — auto-configured for the generated `RegisterAgents` entrypoint
 *
 * External usage (example):
 *
 * ```scala
 * import $ivy.`dev.zio::zio-golem-mill:<VERSION>`
 * import golem.mill.GolemAutoRegister
 *
 * object demo extends ScalaJSModule with GolemAutoRegister {
 *   def scalaJSVersion = "1.20.0"
 *   def scalaVersion   = "3.3.7"
 *   def golemBasePackage = T(Some("demo"))
 * }
 * ```
 */
trait GolemAutoRegister extends ScalaJSModule {

  // ─── Private helpers ────────────────────────────────────────────────────────

  private def autoRegisterSuffix(basePackage: String): String =
    basePackage
      .replaceAll("[^a-zA-Z0-9_]", "_")
      .stripPrefix("_")
      .stripSuffix("_") match {
      case ""  => "app"
      case out => out
    }

  private def autoRegisterPackage(basePackage: String): String =
    s"golem.runtime.__generated.autoregister.${autoRegisterSuffix(basePackage)}"

  private def sha256(bytes: Array[Byte]): Array[Byte] = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes)
    md.digest()
  }

  private def embeddedAgentGuestWasmBytes(): Array[Byte] = {
    val resourcePath = "golem/wasm/agent_guest.wasm"
    Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match {
      case Some(in) =>
        val bos = new ByteArrayOutputStream()
        try {
          val buf = new Array[Byte](64 * 1024)
          var n   = in.read(buf)
          while (n >= 0) {
            if (n > 0) bos.write(buf, 0, n)
            n = in.read(buf)
          }
        } finally in.close()
        bos.toByteArray
      case None =>
        throw new RuntimeException(
          s"[golem] Missing embedded resource '$resourcePath'. This should be packaged in the zio-golem-mill plugin."
        )
    }
  }

  // ─── Settings ───────────────────────────────────────────────────────────────

  /** Base package whose `@agentImplementation` classes should be auto-registered. */
  def golemBasePackage: T[Option[String]] = T(None)

  /**
   * Relative path from the component directory to the golem app root.
   *
   * Use `"."` for standalone layouts where the component dir IS the app root,
   * `"../.."` for monorepo layouts where components live in `components-js/<name>/`.
   *
   * Default: `"."` (standalone).
   */
  def golemComponentPathPrefix: T[String] = T(".")

  /**
   * Where the base guest runtime wasm should be written.
   *
   * Default: searches up from `millSourcePath` for a `golem.yaml` containing an `app:` directive,
   * then places `agent_guest.wasm` in `.generated/` under that app root. Falls back to
   * `millSourcePath / ".generated" / "agent_guest.wasm"`.
   */
  def golemAgentGuestWasmFile: T[os.Path] = T {
    @annotation.tailrec
    def findAppRoot(dir: os.Path): Option[os.Path] = {
      val manifest = dir / "golem.yaml"
      val isAppManifest =
        os.exists(manifest) && os.read(manifest).linesIterator.exists(_.trim.startsWith("app:"))
      if (isAppManifest) Some(dir)
      else {
        val parent = dir / os.up
        if (parent == dir) None // filesystem root
        else findAppRoot(parent)
      }
    }

    findAppRoot(millSourcePath)
      .map(_ / ".generated" / "agent_guest.wasm")
      .getOrElse(millSourcePath / ".generated" / "agent_guest.wasm")
  }

  // ─── Tasks ──────────────────────────────────────────────────────────────────

  /** Ensures the base guest runtime wasm exists; writes the embedded resource if missing or out-of-date. */
  def golemEnsureAgentGuestWasm: T[PathRef] = T {
    val out         = golemAgentGuestWasmFile()
    val bytes       = embeddedAgentGuestWasmBytes()
    val expectedSha = sha256(bytes)
    val currentSha  = if (os.exists(out) && os.size(out) > 0) Some(sha256(os.read.bytes(out))) else None

    if (currentSha.exists(java.util.Arrays.equals(_, expectedSha))) PathRef(out)
    else {
      os.makeDir.all(out / os.up)
      os.write.over(out, bytes)
      T.log.info(s"[golem] Wrote embedded agent_guest.wasm to $out")
      PathRef(out)
    }
  }

  /** Generates the `scala-js-template.yaml` content for golem.yaml includes. */
  private def scalaJsTemplateYaml(
    prefix: String,
    buildCommand: String
  ): String = {
    def p(path: String): String =
      if (prefix == ".") path
      else s"$prefix/$path"

    val cn = "{{ component_name | to_snake_case }}"

    s"""|# Generated by GolemAutoRegister. Do not edit.
        |componentTemplates:
        |  scala.js:
        |    build:
        |    - command: $buildCommand
        |      sources:
        |      - src
        |      targets:
        |      - .golem/scala.js
        |    - injectToPrebuiltQuickjs: ${p(".generated/agent_guest.wasm")}
        |      module: .golem/scala.js
        |      moduleWasm: ${p(s".golem/agents/$cn.module.wasm")}
        |      into: ${p(s".golem/agents/$cn.dynamic.wasm")}
        |    - generateAgentWrapper: ${p(s".golem/agents/$cn.wrapper.wasm")}
        |      basedOnCompiledWasm: ${p(s".golem/agents/$cn.dynamic.wasm")}
        |    - composeAgentWrapper: ${p(s".golem/agents/$cn.wrapper.wasm")}
        |      withAgent: ${p(s".golem/agents/$cn.dynamic.wasm")}
        |      to: ${p(s".golem/agents/$cn.static.wasm")}
        |    sourceWit: ${p(".generated/agent_guest.wasm")}
        |    generatedWit: ${p(s".golem/agents/$cn/wit-generated")}
        |    componentWasm: ${p(s".golem/agents/$cn.static.wasm")}
        |    linkedWasm: ${p(s".golem/agents/$cn.wasm")}
        |""".stripMargin
  }

  /**
   * Prepares the app directory for golem-cli by ensuring required artifacts
   * (`agent_guest.wasm`, `scala-js-template.yaml`) exist and are up-to-date.
   */
  def golemPrepare: T[Unit] = T {
    val wasmRef      = golemEnsureAgentGuestWasm()
    val generatedDir = wasmRef.path / os.up // .generated/
    val templateFile = generatedDir / "scala-js-template.yaml"
    val prefix       = golemComponentPathPrefix()
    val workspace    = T.workspace

    // Compute the mill build command for the template
    val modulePath = millModuleSegments.render
    val appRoot    = generatedDir / os.up

    val isStandalone = appRoot.toNIO.toRealPath() == workspace.toNIO.toRealPath()

    val buildCommand = if (isStandalone) {
      // Standalone: component dir may differ from build root. Mill must run from build root.
      // For prefix "." the component dir IS the app root (= build root), so cd is a no-op.
      if (prefix == ".")
        s"""mill $modulePath.golemBuildComponent {{ component_name }} .golem/scala.js"""
      else
        s"""bash -c 'COMP_DIR="$$PWD" && cd $prefix && mill $modulePath.golemBuildComponent {{ component_name }} $$COMP_DIR/.golem/scala.js'"""
    } else {
      // Monorepo: compute relative path from component dir to build root.
      val appRelToBuild   = workspace.toNIO.relativize(appRoot.toNIO).toString
      val appDepth        = appRelToBuild.split('/').count(_.nonEmpty)
      val appToRootPrefix = (1 to appDepth).map(_ => "..").mkString("/")
      val compToRoot      = if (prefix == ".") appToRootPrefix else s"$prefix/$appToRootPrefix"

      s"""bash -c 'COMP_DIR="$$PWD" && cd $compToRoot && mill $modulePath.golemBuildComponent {{ component_name }} $$COMP_DIR/.golem/scala.js'"""
    }

    val content = scalaJsTemplateYaml(prefix, buildCommand)
    if (!os.exists(templateFile) || os.read(templateFile) != content) {
      os.write.over(templateFile, content, createFolders = true)
      T.log.info(s"[golem] Wrote scala-js-template.yaml to $templateFile")
    }
    ()
  }

  /**
   * Builds the Scala.js bundle and writes it to the provided output path for golem-cli.
   *
   * Called by golem-cli during `golem build` via the command in `scala-js-template.yaml`:
   * {{{
   *   mill <module>.golemBuildComponent <component-name> <output-path>
   * }}}
   */
  def golemBuildComponent(component: String, outPath: String): Command[PathRef] = T.command {
    T.log.info(s"[golem] Building Scala.js bundle for $component ...")
    val report = fastLinkJS()
    val jsName =
      report.publicModules.headOption
        .map(_.jsFileName)
        .getOrElse(throw new RuntimeException("[golem] No public Scala.js modules were linked."))

    val jsFile = report.dest.path / jsName
    // outPath is typically absolute (from golem-cli's $COMP_DIR expansion), but handle relative too
    val out =
      if (outPath.startsWith("/")) os.Path(outPath)
      else T.workspace / os.SubPath(outPath)

    os.makeDir.all(out / os.up)
    os.copy.over(jsFile, out)
    T.log.info(s"[golem] Wrote Scala.js bundle to $out")
    PathRef(out)
  }

  // ─── Module initializer auto-configuration ──────────────────────────────────

  override def scalaJSModuleInitializers: T[Seq[ModuleInitializer]] = T {
    val base = super.scalaJSModuleInitializers()
    golemBasePackage() match {
      case Some(basePackage) =>
        base ++ Seq(
          ModuleInitializer.mainMethod(s"${autoRegisterPackage(basePackage)}.RegisterAgents", "main")
        )
      case None => base
    }
  }

  // ─── Auto-register source generation ────────────────────────────────────────

  /** Generates Scala sources under `T.dest` and returns them as generated sources. */
  def golemGeneratedAutoRegisterSources: T[Seq[PathRef]] = T {
    golemBasePackage() match {
      case None =>
        Seq.empty
      case Some(basePackage) =>
        val managedRoot = T.dest / "golem" / "generated" / "autoregister"

        val scalaSources: Seq[os.Path] =
          os.walk(millSourcePath / "src")
            .filter(p => os.isFile(p) && p.ext == "scala")

        final case class AgentImpl(pkg: String, implClass: String, traitType: String, ctorTypes: List[String])

        def parseWithDialect(input: Input.String, dialect: Dialect): Option[Source] = {
          implicit val d: Dialect = dialect
          input.parse[Source].toOption
        }

        def parseSource(source: String): Option[Source] = {
          val input = Input.String(source)
          parseWithDialect(input, dialects.Scala3).orElse(parseWithDialect(input, dialects.Scala213))
        }

        def hasAgentImplementation(mods: List[Mod]): Boolean =
          mods.exists {
            case Mod.Annot(init) =>
              val full = init.tpe.syntax
              full == "agentImplementation" || full.endsWith(".agentImplementation")
            case _ => false
          }

        def appendPkg(prefix: String, name: String): String =
          if (prefix.isEmpty) name else s"$prefix.$name"

        def collect(tree: Tree, pkg: String): List[AgentImpl] =
          tree match {
            case source: Source =>
              source.stats.flatMap(collect(_, pkg))
            case Pkg(ref, stats) =>
              val nextPkg = appendPkg(pkg, ref.syntax)
              stats.flatMap(collect(_, nextPkg))
            case Pkg.Object(_, name, templ) =>
              val nextPkg = appendPkg(pkg, name.value)
              templ.stats.flatMap(collect(_, nextPkg))
            case cls: Defn.Class if hasAgentImplementation(cls.mods) =>
              val traitTypeOpt: Option[String] = cls.templ.inits.headOption.map(_.tpe.syntax)
              val ctorParams                   = cls.ctor.paramss.flatten
              val ctorTypes: List[String]      = ctorParams.map(_.decltpe.map(_.syntax).getOrElse("")).toList
              traitTypeOpt match {
                case Some(traitType) if pkg.nonEmpty && !ctorTypes.exists(_.isEmpty) =>
                  List(
                    AgentImpl(
                      pkg = pkg,
                      implClass = cls.name.value,
                      traitType = traitType,
                      ctorTypes = ctorTypes
                    )
                  )
                case _ =>
                  if (ctorTypes.exists(_.isEmpty))
                    T.log.error(
                      s"[golem] Skipping @agentImplementation ${cls.name.value} (missing constructor type annotations)."
                    )
                  Nil
              }
            case _ =>
              Nil
          }

        def parseAgentImpls(source: String): List[AgentImpl] =
          parseSource(source).toList.flatMap(tree => collect(tree, ""))

        val impls: List[AgentImpl] =
          scalaSources
            .flatMap(p => parseAgentImpls(os.read(p)))
            .distinct
            .sortBy(ai => (ai.pkg, ai.traitType, ai.implClass))
            .toList

        if (impls.isEmpty) Seq.empty
        else {
          val genBasePkg = autoRegisterPackage(basePackage)

          val byPkg: Map[String, List[AgentImpl]] =
            impls.groupBy(_.pkg).view.mapValues(_.sortBy(ai => (ai.traitType, ai.implClass))).toMap

          def fileFor(pkg: String, name: String): os.Path =
            managedRoot / os.SubPath(pkg.split('.').toList) / name

          def fqn(ownerPkg: String, tpeOrTerm: String): String =
            if (tpeOrTerm.contains(".")) tpeOrTerm else s"$ownerPkg.$tpeOrTerm"

          def registrationExpr(ai: AgentImpl): String = {
            val traitFqn = fqn(ai.pkg, ai.traitType)
            val implFqn  = fqn(ai.pkg, ai.implClass)
            ai.ctorTypes match {
              case Nil =>
                s"AgentImplementation.register[$traitFqn](new $implFqn())"
              case tpe :: Nil =>
                s"AgentImplementation.register[$traitFqn, $tpe]((in: $tpe) => new $implFqn(in))"
              case many =>
                val args    = many.indices.map(i => s"in._${i + 1}").mkString(", ")
                val ctorTpe = s"(${many.mkString(", ")})"
                s"AgentImplementation.register[$traitFqn, $ctorTpe]((in: $ctorTpe) => new $implFqn($args))"
            }
          }

          val perPkgFiles: Seq[os.Path] =
            byPkg.toSeq.sortBy(_._1).map { case (pkg, pkgImpls) =>
              val objSuffix = pkg.replaceAll("[^a-zA-Z0-9_]", "_")
              val out       = fileFor(genBasePkg, s"__GolemAutoRegister_$objSuffix.scala")
              os.makeDir.all(out / os.up)
              val body    = pkgImpls.map(ai => s"    ${registrationExpr(ai)}").mkString("\n")
              val content =
                s"""|package $genBasePkg
                    |
                    |import golem.runtime.autowire.AgentImplementation
                    |import $pkg._
                    |
                    |/** Generated. Do not edit. */
                    |private[golem] object __GolemAutoRegister_$objSuffix {
                    |  def register(): Unit = {
                    |$body
                    |    ()
                    |  }
                    |}
                    |""".stripMargin
              os.write.over(out, content)
              out
            }

          val baseOut = fileFor(genBasePkg, "RegisterAgents.scala")
          os.makeDir.all(baseOut / os.up)
          val touches =
            byPkg.keys.toSeq.sorted.map { pkg =>
              val objSuffix = pkg.replaceAll("[^a-zA-Z0-9_]", "_")
              s"      __GolemAutoRegister_$objSuffix.register()"
            }.mkString("\n")
          val baseContent =
            s"""|package $genBasePkg
                |
                |import scala.scalajs.js.annotation.JSExportTopLevel
                |
                |/** Generated. Do not edit. */
                |private[golem] object RegisterAgents {
                |  private var registered = false
                |
                |  private def registerAll(): Unit =
                |    if (!registered) {
                |      registered = true
                |$touches
                |      ()
                |    }
                |
                |  def init(): Unit =
                |    registerAll()
                |
                |  def main(): Unit =
                |    registerAll()
                |
                |  @JSExportTopLevel("__golemRegisterAgents")
                |  val __golemRegisterAgents: Unit =
                |    registerAll()
                |}
                |""".stripMargin
          os.write.over(baseOut, baseContent)

          T.log.info(
            s"[golem] Generated Scala.js agent registration for $basePackage into $genBasePkg (${impls.length} impls, ${byPkg.size} pkgs)."
          )
          (perPkgFiles :+ baseOut).map(PathRef(_))
        }
    }
  }

  // ─── Compile hooks ──────────────────────────────────────────────────────────

  override def compile: T[mill.scalalib.api.CompilationResult] = T {
    golemPrepare()
    super.compile()
  }

  override def generatedSources: T[Seq[PathRef]] =
    T { super.generatedSources() ++ golemGeneratedAutoRegisterSources() }
}
