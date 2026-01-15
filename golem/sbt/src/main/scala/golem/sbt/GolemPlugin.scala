package golem.sbt

import sbt.*
import sbt.Keys.*

import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.security.MessageDigest

import org.scalajs.sbtplugin.ScalaJSPlugin

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

  private def sha256(bytes: Array[Byte]): Array[Byte] = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bytes)
    md.digest()
  }

  private def sameSha256(file: File, expectedSha: Array[Byte]): Boolean =
    file.exists() && file.length() > 0 && java.util.Arrays.equals(sha256(IO.readBytes(file)), expectedSha)

  private def embeddedAgentGuestWasmBytes(cl: ClassLoader, repoRootFallback: File): Array[Byte] = {
    val resourcePath = "golem/wasm/agent_guest.wasm"
    Option(cl.getResourceAsStream(resourcePath)) match {
      case Some(in) =>
        val bos = new ByteArrayOutputStream()
        try IO.transfer(in, bos)
        finally in.close()
        bos.toByteArray
      case None =>
        // Fallback for monorepo builds, where the plugin source is compiled into the meta-build.
        val candidate = repoRootFallback / "golem" / "sbt" / "src" / "main" / "resources" / "golem" / "wasm" / "agent_guest.wasm"
        if (candidate.exists()) IO.readBytes(candidate)
        else sys.error(s"[golem] Missing embedded resource '$resourcePath' (and no repo fallback at ${candidate.getAbsolutePath}).")
    }
  }

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

    val golemEnsureAgentGuestWasm: TaskKey[File] =
      taskKey[File](
        "Ensures the base guest runtime WASM (agent_guest.wasm) exists at golemAgentGuestWasmFile; writes it if missing."
      )

    val golemPrepare: TaskKey[Unit] =
      taskKey[Unit](
        "Prepares the app directory for golem-cli by ensuring required artifacts (e.g. agent_guest.wasm) exist and are up-to-date."
      )
  }

  import autoImport.*

  override def requires: Plugins      = plugins.JvmPlugin && ScalaJSPlugin
  override def trigger: PluginTrigger = noTrigger

  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      golemBasePackage := None,
      golemAgentGuestWasmFile := {
        // Prefer an `app/wasm/agent_guest.wasm` adjacent to the *project* being compiled.
        //
        // This supports common layouts:
        // - standalone getting-started: <root>/scala (build) + <root>/app (manifest)
        // - monorepo/crossProject: <root>/golem/examples/js (build) + <root>/golem/examples/app (manifest)
        val projectRoot = (ThisProject / baseDirectory).value
        val buildRoot   = (ThisBuild / baseDirectory).value

        val candidates: List[File] =
          List(
            // Project-local app/
            projectRoot / "app" / "wasm" / "agent_guest.wasm",
            // If project is a nested build dir (e.g. .../examples/js), prefer sibling ../app
            projectRoot.getParentFile / "app" / "wasm" / "agent_guest.wasm",
            // getting-started layout: build root is `scala/`, app is sibling in parent
            if (projectRoot.getName == "scala") projectRoot.getParentFile / "app" / "wasm" / "agent_guest.wasm"
            else null,
            // build-local fallback (keeps writes inside the build root)
            buildRoot / "app" / "wasm" / "agent_guest.wasm"
          ).filter(_ != null)

        // Pick the first candidate whose parent `app/` directory exists; otherwise default to project-local path.
        candidates.find(f => f.getParentFile.getParentFile.exists()).getOrElse(candidates.head)
      },
      golemWriteAgentGuestWasm := {
        val out = golemAgentGuestWasmFile.value
        val log = streams.value.log

        val repoRootFallback = (LocalRootProject / baseDirectory).value
        val bytes            = embeddedAgentGuestWasmBytes(getClass.getClassLoader, repoRootFallback)

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
      golemEnsureAgentGuestWasm := {
        // Use dynamic tasks so we don't depend on `golemWriteAgentGuestWasm` unless needed.
        Def.taskDyn {
          val out = golemAgentGuestWasmFile.value
          val repoRootFallback = (LocalRootProject / baseDirectory).value
          val bytes            = embeddedAgentGuestWasmBytes(getClass.getClassLoader, repoRootFallback)
          val expectedSha      = sha256(bytes)

          if (sameSha256(out, expectedSha)) Def.task(out)
          else
            Def.task {
              val reason = if (!out.exists() || out.length() == 0) "missing" else "out-of-date"
              streams.value.log.info(
                s"[golem] agent_guest.wasm is $reason at ${out.getAbsolutePath}; writing embedded copy."
              )
              golemWriteAgentGuestWasm.value
            }
        }.value
      },
      golemPrepare := {
        // Today this primarily ensures the base guest runtime wasm is present/up-to-date.
        // (We intentionally keep this lightweight; build/link remains user-controlled.)
        golemEnsureAgentGuestWasm.value
        ()
      },
      // Make the embedded wasm fully automatic for typical usage: if the app manifest expects `app/wasm/agent_guest.wasm`
      // and it isn't there, create it as part of normal compilation / linking flows.
      Compile / compile := (Compile / compile).dependsOn(golemPrepare).value,
      Compile / ScalaJSPlugin.autoImport.fastLinkJS :=
        (Compile / ScalaJSPlugin.autoImport.fastLinkJS).dependsOn(golemPrepare).value,
      Compile / ScalaJSPlugin.autoImport.fullLinkJS :=
        (Compile / ScalaJSPlugin.autoImport.fullLinkJS).dependsOn(golemPrepare).value,
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
              def stripComments(s: String): String = {
                // Remove block comments and line comments. This intentionally operates on the raw source snippet.
                val noBlock = s.replaceAll("(?s)/\\*.*?\\*/", "")
                noBlock.replaceAll("(?m)//.*$", "")
              }

              def splitTopLevelCommas(s: String): List[String] = {
                val out = scala.collection.mutable.ListBuffer.empty[String]
                val buf = new java.lang.StringBuilder
                var paren = 0
                var bracket = 0
                var brace = 0
                var i = 0
                while (i < s.length) {
                  val ch = s.charAt(i)
                  ch match {
                    case '(' => paren += 1; buf.append(ch)
                    case ')' => if (paren > 0) paren -= 1; buf.append(ch)
                    case '[' => bracket += 1; buf.append(ch)
                    case ']' => if (bracket > 0) bracket -= 1; buf.append(ch)
                    case '{' => brace += 1; buf.append(ch)
                    case '}' => if (brace > 0) brace -= 1; buf.append(ch)
                    case ',' if paren == 0 && bracket == 0 && brace == 0 =>
                      out += buf.toString
                      buf.setLength(0)
                    case _ =>
                      buf.append(ch)
                  }
                  i += 1
                }
                out += buf.toString
                out.toList
              }

              val cleaned = stripComments(params).trim
              if (cleaned.isEmpty) Nil
              else {
                splitTopLevelCommas(cleaned)
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
