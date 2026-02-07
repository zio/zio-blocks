package golem.mill

import mill.*
import mill.scalalib.*

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.MessageDigest
import scala.meta.*
import scala.meta.parsers.*

/**
 * Mill mixin that generates Scala.js agent auto-registration sources.
 *
 * External usage (example):
 *
 * ```scala
 * import $ivy.`dev.zio::zio-golem-mill:<VERSION>`
 * import golem.mill.GolemAutoRegister
 *
 * object demo extends ScalaJSModule with GolemAutoRegister {
 *   def golemBasePackage = T(Some("demo"))
 * }
 * ```
 */
trait GolemAutoRegister extends ScalaModule {

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

  /** Base package whose `@agentImplementation` classes should be auto-registered. */
  def golemBasePackage: T[Option[String]] = T(None)

  /**
   * Where the base guest runtime wasm should be written.
   *
   * Default assumes a project structure like:
   * - build.sc (mill root)
   * - app/.generated/agent_guest.wasm (golem manifest expects this)
   */
  def golemAgentGuestWasmFile: T[os.Path] = T { millSourcePath / "app" / ".generated" / "agent_guest.wasm" }

  /** Ensures the base guest runtime wasm exists; writes the embedded resource if missing. */
  def golemEnsureAgentGuestWasm: T[PathRef] = T {
    val out = golemAgentGuestWasmFile()
    val bytes       = embeddedAgentGuestWasmBytes()
    val expectedSha = sha256(bytes)
    val currentSha  = if (os.exists(out) && os.size(out) > 0) Some(sha256(os.read.bytes(out))) else None

    if (currentSha.exists(java.util.Arrays.equals(_, expectedSha))) PathRef(out)
    else {
      os.makeDir.all(out / os.up)
      os.write.over(out, bytes)
      PathRef(out)
    }
  }

  /** Prepares the app directory for golem-cli by ensuring required artifacts exist and are up-to-date. */
  def golemPrepare: T[Unit] = T {
    golemEnsureAgentGuestWasm()
    ()
  }

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
              val ctorParams                  = cls.ctor.paramss.flatten
              val ctorTypes: List[String]     = ctorParams.map(_.decltpe.map(_.syntax).getOrElse("")).toList
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
                    T.log.warn(
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
          val moduleSuffix =
            basePackage
              .replaceAll("[^a-zA-Z0-9_]", "_")
              .stripPrefix("_")
              .stripSuffix("_") match {
              case ""  => "app"
              case out => out
            }
          val genBasePkg = s"golem.runtime.__generated.autoregister.$moduleSuffix"

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
                val args     = many.indices.map(i => s"in._${i + 1}").mkString(", ")
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

          (perPkgFiles :+ baseOut).map(PathRef(_))
        }
    }
  }

  override def compile: T[mill.scalalib.api.CompilationResult] = T {
    // Make guest runtime wasm fully automatic: write it if missing during normal compilation / linking workflows.
    golemPrepare()
    super.compile()
  }

  override def generatedSources: T[Seq[PathRef]] =
    T { super.generatedSources() ++ golemGeneratedAutoRegisterSources() }
}

