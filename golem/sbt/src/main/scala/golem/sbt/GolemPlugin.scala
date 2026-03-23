/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package golem.sbt

import sbt.*
import sbt.Keys.*
import sbt.complete.Parsers.spaceDelimited

import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import scala.meta.*
import scala.meta.parsers.*

import org.scalajs.linker.interface.ModuleInitializer
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
        val candidate =
          repoRootFallback / "golem" / "sbt" / "src" / "main" / "resources" / "golem" / "wasm" / "agent_guest.wasm"
        if (candidate.exists()) IO.readBytes(candidate)
        else
          sys.error(
            s"[golem] Missing embedded resource '$resourcePath' (and no repo fallback at ${candidate.getAbsolutePath})."
          )
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
        "Prepares the app directory for golem-cli by ensuring agent_guest.wasm exists and is up-to-date."
      )

    val golemBuildComponent: InputKey[File] =
      inputKey[File](
        "Builds the Scala.js bundle and writes it to the provided output path for golem-cli."
      )
  }

  import autoImport.*

  override def requires: Plugins      = plugins.JvmPlugin && ScalaJSPlugin
  override def trigger: PluginTrigger = noTrigger

  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      golemBasePackage        := None,
      golemAgentGuestWasmFile := {
        // Prefer `.generated/agent_guest.wasm` at the golem *app root* (directory containing `golem.yaml`).
        // This matches the path referenced in golem.yaml manifests (sourceWit, injectToPrebuiltQuickjs).
        //
        // Fallback: `.generated/agent_guest.wasm` adjacent to the *project* being compiled.
        //
        // This supports common layouts:
        // - standalone getting-started: <root> has both build.sbt and golem.yaml
        // - monorepo/crossProject: <root>/golem/examples (manifest) with build at repo root
        val projectRoot = (ThisProject / baseDirectory).value
        val buildRoot   = (ThisBuild / baseDirectory).value

        @annotation.tailrec
        def findAppRoot(dir: File): Option[File] =
          if (dir == null) None
          else {
            val manifest      = dir / "golem.yaml"
            val isAppManifest =
              manifest.exists() && IO.read(manifest).linesIterator.exists(line => line.trim.startsWith("app:"))
            if (isAppManifest) Some(dir) else findAppRoot(dir.getParentFile)
          }

        findAppRoot(projectRoot)
          .map(appRoot => appRoot / ".generated" / "agent_guest.wasm")
          .getOrElse {
            // build-local fallback
            projectRoot / ".generated" / "agent_guest.wasm"
          }
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
          val out              = golemAgentGuestWasmFile.value
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
        golemEnsureAgentGuestWasm.value
        ()
      },
      golemBuildComponent := {
        val args         = spaceDelimited("<component> <outFile> <agentWasmFile?>").parsed
        val component    = args.headOption.getOrElse(sys.error("Missing component name"))
        val outPath      = args.lift(1).getOrElse(".golem/scala.js")
        val agentWasmOpt = args.lift(2)
        val out          = file(outPath)
        val log          = streams.value.log

        agentWasmOpt.foreach { p =>
          val target = file(p)
          val bytes  = embeddedAgentGuestWasmBytes(getClass.getClassLoader, (LocalRootProject / baseDirectory).value)
          val sha    = sha256(bytes)
          if (!sameSha256(target, sha)) {
            IO.createDirectory(target.getParentFile)
            val fos = new FileOutputStream(target)
            try fos.write(bytes)
            finally fos.close()
            log.info(s"[golem] Wrote embedded agent_guest.wasm to ${target.getAbsolutePath}")
          }
        }

        log.info(s"[golem] Building Scala.js bundle for $component ...")
        val report = (Compile / ScalaJSPlugin.autoImport.fullLinkJS).value.data
        val outDir =
          (Compile / ScalaJSPlugin.autoImport.fullLinkJS / ScalaJSPlugin.autoImport.scalaJSLinkerOutputDirectory).value
        val jsName =
          report.publicModules.headOption
            .map(_.jsFileName)
            .getOrElse(sys.error("[golem] No public Scala.js modules were linked."))
        val jsFile = outDir / jsName
        IO.createDirectory(out.getParentFile)
        IO.copyFile(jsFile, out)
        log.info(s"[golem] Wrote Scala.js bundle to ${out.getAbsolutePath}")
        out
      },
      // Make golemPrepare automatic: run before compile/link to ensure .generated/ artifacts exist.
      Compile / compile                             := (Compile / compile).dependsOn(golemPrepare).value,
      Compile / ScalaJSPlugin.autoImport.fastLinkJS :=
        (Compile / ScalaJSPlugin.autoImport.fastLinkJS).dependsOn(golemPrepare).value,
      Compile / ScalaJSPlugin.autoImport.fullLinkJS :=
        (Compile / ScalaJSPlugin.autoImport.fullLinkJS).dependsOn(golemPrepare).value,
      Compile / ScalaJSPlugin.autoImport.scalaJSModuleInitializers ++= {
        golemBasePackage.value.toList.map { basePackage =>
          ModuleInitializer.mainMethod(s"${autoRegisterPackage(basePackage)}.RegisterAgents", "main")
        }
      },
      Compile / sourceGenerators += Def.task {
        val basePackageOpt = golemBasePackage.value
        basePackageOpt match {
          case None =>
            Nil
          case Some(basePackage) =>
            val log          = streams.value.log
            val managedRoot  = (Compile / sourceManaged).value / "golem" / "generated" / "autoregister"
            val scalaSources =
              (Compile / unmanagedSourceDirectories).value
                .flatMap(dir => (dir ** "*.scala").get)
                .distinct

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
                case Pkg.After_4_9_9(ref, stats) =>
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
                        log.warn(
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
                .flatMap(f => parseAgentImpls(IO.read(f)))
                .toList
                .distinct
                .sortBy(ai => (ai.pkg, ai.traitType, ai.implClass))

            if (impls.isEmpty) Nil
            else {
              val genBasePkg = autoRegisterPackage(basePackage)

              val byPkg: Map[String, List[AgentImpl]] =
                impls
                  .groupBy(_.pkg)
                  .map { case (pkg, pkgImpls) =>
                    pkg -> pkgImpls.sortBy(ai => (ai.traitType, ai.implClass))
                  }

              def fileFor(pkg: String, name: String): File =
                managedRoot / pkg.split('.').toList.mkString("/") / name

              val scalaBuiltins: Set[String] = Set(
                "String",
                "Int",
                "Long",
                "Double",
                "Float",
                "Boolean",
                "Byte",
                "Short",
                "Char",
                "Unit",
                "BigInt",
                "BigDecimal",
                "Any",
                "AnyRef",
                "AnyVal",
                "Nothing",
                "Null"
              )

              def fqn(ownerPkg: String, tpeOrTerm: String): String =
                if (tpeOrTerm.contains(".") || scalaBuiltins.contains(tpeOrTerm)) tpeOrTerm
                else s"$ownerPkg.$tpeOrTerm"

              def registrationExpr(ai: AgentImpl): String = {
                val traitFqn = fqn(ai.pkg, ai.traitType)
                val implFqn  = fqn(ai.pkg, ai.implClass)
                s"AgentImplementation.registerClass[$traitFqn, $implFqn]"
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
                    |  def main(): Unit = registerAll()
                    |
                    |  @JSExportTopLevel("__golemRegisterAgents")
                    |  val __golemRegisterAgents: Unit =
                    |    registerAll()
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
