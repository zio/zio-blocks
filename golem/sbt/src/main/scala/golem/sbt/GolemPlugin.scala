package golem.sbt

import sbt.*
import sbt.Keys.*

import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/**
 * sbt plugin for Golem-related build wiring.
 *
 * Currently this provides Scala.js agent auto-registration generation, so
 * user-land code never needs to write/maintain a `RegisterAgents` list.
 *
 * The plugin scans Scala sources for `@agentImplementation` classes and
 * generates an exported Scala.js entrypoint (`__golemRegisterAgents`) that
 * registers them.
 */
object GolemPlugin extends AutoPlugin {

  object autoImport {
    val golemBasePackage: SettingKey[Option[String]] =
      settingKey[Option[String]](
        "Base package whose @agentImplementation classes should be auto-registered (Scala.js)."
      )

    val golemAgentGuestWasmFile: SettingKey[File] =
      settingKey[File](
        "Where to write the embedded base guest runtime WASM (agent_guest.wasm) for use by app manifests."
      )

    val golemWriteAgentGuestWasm: TaskKey[File] =
      taskKey[File]("Writes the embedded base guest runtime WASM (agent_guest.wasm) to golemAgentGuestWasmFile.")
  }

  import autoImport.*

  override def requires: Plugins      = plugins.JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      golemBasePackage := None,
      golemAgentGuestWasmFile := {
        // Default layout assumed by `golem/docs/getting-started.md`:
        // <root>/scala (sbt build root) and <root>/app (golem app manifest).
        val sbtRoot = (ThisBuild / baseDirectory).value
        sbtRoot.getParentFile / "app" / "wasm" / "agent_guest.wasm"
      },
      golemWriteAgentGuestWasm := {
        val out = golemAgentGuestWasmFile.value
        val log = streams.value.log

        val resourcePath = "golem/wasm/agent_guest.wasm"
        val inOpt        = Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
        val bytes: Array[Byte] =
          inOpt match {
            case Some(in) =>
              val bos = new ByteArrayOutputStream()
              try IO.transfer(in, bos)
              finally in.close()
              bos.toByteArray
            case None =>
              // Fallback for monorepo builds, where this plugin can also be sourced from `project/GolemPlugin.scala`.
              // External users will always use the published plugin jar, which includes the resource.
              val repoRoot  = (LocalRootProject / baseDirectory).value
              val candidate = repoRoot / "golem" / "sbt" / "src" / "main" / "resources" / "golem" / "wasm" / "agent_guest.wasm"
              if (candidate.exists()) IO.readBytes(candidate)
              else
                sys.error(
                  s"[golem] Missing embedded resource '$resourcePath' (and no repo fallback at ${candidate.getAbsolutePath})."
                )
          }

        IO.createDirectory(out.getParentFile)

        val fos = new FileOutputStream(out)
        try {
          fos.write(bytes)
        } finally {
          fos.close()
        }

        log.info(s"[golem] Wrote embedded agent_guest.wasm to ${out.getAbsolutePath}")
        out
      },
      Compile / sourceGenerators += Def.task {
        val basePackageOpt = golemBasePackage.value
        basePackageOpt match {
          case None =>
            Nil
          case Some(basePackage) =>
            val log          = streams.value.log
            val managedRoot  = (Compile / sourceManaged).value / "golem" / "generated" / "autoregister"
            val scalaSources = (Compile / unmanagedSources).value.filter(f => f.getName.endsWith(".scala"))

            final case class AgentImpl(pkg: String, implClass: String, traitType: String, ctorTypes: List[String])

            def packageOf(source: String): String = {
              val Pkg = """(?m)^\s*package\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*$""".r
              Pkg.findFirstMatchIn(source).map(_.group(1)).getOrElse("")
            }

            val Ann           = """@agentImplementation(?:\([^\)]*\))?""".r
            val ClassWithCtor =
              (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*extends\s+([^\s\{]+)""").r
            val ClassNoCtor =
              (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*extends\s+([^\s\{]+)""").r

            def ctorTypes(params: String): List[String] = {
              val trimmed = params.trim
              if (trimmed.isEmpty) Nil
              else {
                // Very simple splitting; good enough for our examples/quickstart (no nested commas in types).
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
                  AgentImpl(
                    pkg = pkg,
                    implClass = m.group(1),
                    traitType = m.group(3),
                    ctorTypes = ctorTypes(m.group(2))
                  )
                }
                .toList

              // Avoid double-counting: the "no ctor" regex would also match ctor classes if we ran it blindly.
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
                .flatMap(f => parseAgentImpls(IO.read(f)))
                .toList
                .filter(_.pkg.nonEmpty)
                .distinct
                .sortBy(ai => (ai.pkg, ai.traitType, ai.implClass))

            if (impls.isEmpty) {
              log.info(
                s"[golem] No @agentImplementation classes found; skipping auto RegisterAgents generation for $basePackage."
              )
              Nil
            } else {
              // Generate into an internal golem-owned package so this glue isn't exposed in user land.
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
                impls
                  .groupBy(_.pkg)
                  .map { case (pkg, pkgImpls) =>
                    pkg -> pkgImpls.sortBy(ai => (ai.traitType, ai.implClass))
                  }

              def fileFor(pkg: String, name: String): File =
                managedRoot / pkg.split('.').toList.mkString("/") / name

              def fqn(ownerPkg: String, tpeOrTerm: String): String =
                if (tpeOrTerm.contains(".")) tpeOrTerm
                else s"$ownerPkg.$tpeOrTerm"

              def registrationExpr(ai: AgentImpl): String = {
                // Heuristic: in our examples/quickstart, impl class and its agent trait live in the same package.
                val traitFqn = fqn(ai.pkg, ai.traitType)
                val implFqn  = fqn(ai.pkg, ai.implClass)

                ai.ctorTypes match {
                  case Nil =>
                    s"AgentImplementation.register[$traitFqn](new $implFqn())"
                  case tpe :: Nil =>
                    s"AgentImplementation.register[$traitFqn, $tpe]((in: $tpe) => new $implFqn(in))"
                  case many =>
                    val tupleTpe = many.mkString("(", ", ", ")")
                    val args     = many.indices.map(i => s"in._${i + 1}").mkString(", ")
                    s"AgentImplementation.register[$traitFqn, $tupleTpe]((in: $tupleTpe) => new $implFqn($args))"
                }
              }

              val perPkgFiles: Seq[File] =
                byPkg.toSeq.sortBy(_._1).map { case (pkg, pkgImpls) =>
                  val objSuffix = pkg.replaceAll("[^a-zA-Z0-9_]", "_")
                  val out       = fileFor(genBasePkg, s"__GolemAutoRegister_$objSuffix.scala")
                  val body      = pkgImpls.map(ai => s"    ${registrationExpr(ai)}").mkString("\n")
                  val content   =
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
                  IO.write(out, content)
                  out
                }

              val baseOut = fileFor(genBasePkg, "RegisterAgents.scala")
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
              IO.write(baseOut, baseContent)

              log.info(
                s"[golem] Generated Scala.js agent registration for $basePackage into $genBasePkg (${impls.length} impls, ${byPkg.size} pkgs)."
              )
              perPkgFiles :+ baseOut
            }
        }
      }.taskValue
    )
}
