package golem.mill

import mill.*
import mill.scalalib.*

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.MessageDigest
import scala.util.matching.Regex

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
   * - app/wasm/agent_guest.wasm (golem manifest expects this)
   */
  def golemAgentGuestWasmFile: T[os.Path] = T { millSourcePath / "app" / "wasm" / "agent_guest.wasm" }

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

        def packageOf(source: String): String = {
          val Pkg: Regex = """(?m)^\s*package\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*$""".r
          Pkg.findFirstMatchIn(source).map(_.group(1)).getOrElse("")
        }

        val Ann: Regex = """@agentImplementation(?:\([^\)]*\))?""".r
        val ClassWithCtor: Regex =
          (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*extends\s+([^\s\{]+)""").r
        val ClassNoCtor: Regex =
          (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*extends\s+([^\s\{]+)""").r

        def ctorTypes(params: String): List[String] = {
          val trimmed = params.trim
          if (trimmed.isEmpty) Nil
          else {
            trimmed
              .split(",")
              .toList
              .map(_.trim)
              .filter(_.nonEmpty)
              .flatMap { p =>
                val colonIdx = p.indexOf(':')
                if (colonIdx < 0) Nil
                else {
                  val afterColon = p.substring(colonIdx + 1).trim
                  val tpe        = afterColon.takeWhile(_ != '=').trim
                  if (tpe.isEmpty) Nil else List(tpe)
                }
              }
          }
        }

        def parseAgentImpls(source: String): List[AgentImpl] = {
          val pkg      = packageOf(source)
          val withCtor = ClassWithCtor
            .findAllMatchIn(source)
            .map { m =>
              AgentImpl(pkg = pkg, implClass = m.group(1), traitType = m.group(3), ctorTypes = ctorTypes(m.group(2)))
            }
            .toList

          val ctorClassNames = withCtor.map(_.implClass).toSet
          val noCtor         = ClassNoCtor
            .findAllMatchIn(source)
            .map(m => AgentImpl(pkg = pkg, implClass = m.group(1), traitType = m.group(2), ctorTypes = Nil))
            .filterNot(ai => ctorClassNames.contains(ai.implClass))
            .toList

          withCtor ++ noCtor
        }

        val impls: List[AgentImpl] =
          scalaSources
            .flatMap(p => parseAgentImpls(os.read(p)))
            .filter(_.pkg.nonEmpty)
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
            val ctorTpe  = s"$traitFqn#AgentInput"
            ai.ctorTypes match {
              case Nil =>
                s"AgentImplementation.register[$traitFqn](new $implFqn())"
              case tpe :: Nil =>
                s"AgentImplementation.register[$traitFqn, $ctorTpe]((in: $ctorTpe) => new $implFqn(in))"
              case many =>
                val args     = many.indices.map(i => s"in._${i + 1}").mkString(", ")
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
              s"    __GolemAutoRegister_$objSuffix.register()"
            }.mkString("\n")
          val baseContent =
            s"""|package $genBasePkg
                |
                |import scala.scalajs.js.annotation.JSExportTopLevel
                |
                |/** Generated. Do not edit. */
                |private[golem] object RegisterAgents {
                |  @JSExportTopLevel("__golemRegisterAgents")
                |  val __golemRegisterAgents: Unit = {
                |$touches
                |    ()
                |  }
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

