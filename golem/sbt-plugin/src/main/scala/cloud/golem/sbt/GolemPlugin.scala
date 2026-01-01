package cloud.golem.sbt

import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

import java.io.File

object GolemPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin
  override def trigger: PluginTrigger = noTrigger

  object autoImport {
    val golemAgentGuestWasmFile =
      settingKey[File]("Where to write wasm/agent_guest.wasm for golem-cli packaging (default: <base>/wasm/agent_guest.wasm)")
    val golemEnsureAgentGuestWasm =
      taskKey[File]("Ensure wasm/agent_guest.wasm exists (written from the Scala SDK tooling-core resources)")

    val golemAppRoot   = settingKey[File]("Root dir for generated Golem apps (default .golem-apps)")
    val golemAppName   = settingKey[String]("App name")
    val golemComponent = settingKey[String]("Qualified component name (e.g. org:component)")

    val golemBundleFileName =
      settingKey[String]("Filename to copy Scala.js bundle to inside component src/ (default: <appName>.js)")

    val golemBridgeSpecManifestPath =
      settingKey[File](
        "Optional BridgeSpec manifest file (BridgeSpecManifest .properties); used for shim generation when golemExports is configured"
      )

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
      final case class Scalar(argName: String, wireType: String, scalaFactoryArgs: Seq[String] = Nil) extends GolemConstructor
      final case class Positional(params: Seq[GolemParam], scalaFactoryArgs: Seq[String] = Nil)      extends GolemConstructor
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

    val golemExports =
      settingKey[Seq[GolemExport]]("List of exported agents (Scala-only); used to auto-generate a BridgeSpec manifest.")

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

    private lazy val mEnsureAgentGuestWasm =
      cls.getMethod("ensureAgentGuestWasm", classOf[java.nio.file.Path])

    def generateScalaShimFromManifest(
      manifest: java.nio.file.Path,
      exportTopLevel: String,
      objectName: String,
      packageName: String
    ): String =
      mGenerateScalaShimFromManifest
        .invoke(null, manifest, exportTopLevel, objectName, packageName)
        .asInstanceOf[String]

    def ensureAgentGuestWasm(dest: java.nio.file.Path): Unit =
      mEnsureAgentGuestWasm.invoke(null, dest)
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    golemAgentGuestWasmFile := baseDirectory.value / "wasm" / "agent_guest.wasm",
    golemEnsureAgentGuestWasm := {
      val dest = golemAgentGuestWasmFile.value.toPath
      IO.createDirectory(dest.getParent.toFile)
      Tooling.ensureAgentGuestWasm(dest)
      dest.toFile
    },

    golemAppRoot   := (ThisBuild / baseDirectory).value / ".golem-apps",
    golemAppName   := name.value,
    golemComponent := "",

    golemBundleFileName := s"${golemAppName.value}.js",

    golemBridgeSpecManifestPath := (Compile / resourceManaged).value / "golem" / "bridge-spec.properties",
    golemExports                := Nil,

    golemEnsureBridgeSpecManifest := Def.taskDyn {
      val log      = streams.value.log
      val exports  = golemExports.value
      val manifest = golemBridgeSpecManifestPath.value

      def writeManifestFromExports(exports: Seq[GolemExport]): File = {
        IO.createDirectory(manifest.getParentFile)

        val scalaBundleImport = s"./${golemBundleFileName.value}"
        val sb                = new StringBuilder()
        sb.append("scalaBundleImport=").append(scalaBundleImport).append("\n")
        sb.append("scalaAgentsExpr=").append(scalaAgentsExprForExportTopLevel(golemScalaShimExportTopLevel.value)).append("\n\n")

        exports.zipWithIndex.foreach { case (e, idx) =>
          val p = s"agents.$idx."
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
            case GolemConstructor.NoArg =>
              sb.append(p).append("constructor.kind=noarg\n")

            case GolemConstructor.Scalar(argName, wireType, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=scalar\n")
              sb.append(p).append("constructor.type=").append(wireType).append("\n")
              sb.append(p).append("constructor.argName=").append(argName).append("\n")
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }

            case GolemConstructor.Positional(params, scalaFactoryArgs) =>
              sb.append(p).append("constructor.kind=positional\n")
              params.zipWithIndex.foreach { case (par, pi) =>
                sb.append(p).append(s"constructor.param.$pi.name=").append(par.name).append("\n")
                sb.append(p).append(s"constructor.param.$pi.type=").append(par.wireType).append("\n")
              }
              scalaFactoryArgs.zipWithIndex.foreach { case (arg, ai) =>
                sb.append(p).append(s"constructor.scalaFactoryArg.$ai=").append(arg).append("\n")
              }

            case GolemConstructor.Record(inputTypeName, fields, scalaFactoryArgs) =>
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

    golemGenerateScalaShim := {
      val log      = streams.value.log
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
    }
  )
}

