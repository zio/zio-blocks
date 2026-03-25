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

package golem.codegen.autoregister

import scala.meta._
import scala.meta.dialects.Scala213
import scala.meta.parsers._

/**
 * Pure, build-tool-agnostic code generator for Golem agent auto-registration.
 *
 * Scans Scala source text for `@agentImplementation` annotated classes and
 * produces Scala source files that register them via
 * `AgentImplementation.registerClass`.
 *
 * All generated code is constructed as scalameta AST nodes and pretty-printed
 * via `.syntax`, following the project's code-generation conventions.
 */
object AutoRegisterCodegen {

  final case class SourceInput(path: String, content: String)

  final case class Warning(path: Option[String], message: String)

  final case class GeneratedFile(relativePath: String, content: String)

  final case class Result(
    generatedPackage: String,
    files: Seq[GeneratedFile],
    warnings: Seq[Warning],
    implCount: Int,
    packageCount: Int
  )

  /** Computes the generated package name for a given base package. */
  def generatedPackage(basePackage: String): String =
    s"golem.runtime.__generated.autoregister.${autoRegisterSuffix(basePackage)}"

  /**
   * Generates auto-registration source files for all `@agentImplementation`
   * classes found in the given sources.
   *
   * @param basePackage
   *   the user's base package (e.g. `"example"`)
   * @param sources
   *   Scala source inputs to scan
   * @return
   *   a [[Result]] with generated files (relative paths) and any warnings
   */
  def generate(basePackage: String, sources: Seq[SourceInput]): Result = {
    val genBasePkg = generatedPackage(basePackage)
    val warnings   = List.newBuilder[Warning]

    val impls: List[AgentImpl] =
      sources.toList
        .flatMap { src =>
          parseAgentImpls(src.content, src.path, warnings)
        }
        .distinct
        .sortBy(ai => (ai.pkg, ai.traitType, ai.implClass))

    if (impls.isEmpty) {
      Result(
        generatedPackage = genBasePkg,
        files = Seq.empty,
        warnings = warnings.result(),
        implCount = 0,
        packageCount = 0
      )
    } else {
      val byPkg: Map[String, List[AgentImpl]] =
        impls
          .groupBy(_.pkg)
          .map { case (pkg, pkgImpls) =>
            pkg -> pkgImpls.sortBy(ai => (ai.traitType, ai.implClass))
          }

      val perPkgFiles: Seq[GeneratedFile] =
        byPkg.toSeq.sortBy(_._1).map { case (pkg, pkgImpls) =>
          val objSuffix = sanitizeSuffix(pkg)
          val tree      = buildPerPkgSource(genBasePkg, objSuffix, pkgImpls)
          GeneratedFile(
            relativePath = packagePath(genBasePkg, s"__GolemAutoRegister_$objSuffix.scala"),
            content = tree.syntax
          )
        }

      val registerCallExprs: List[Stat] =
        byPkg.keys.toSeq.sorted.toList.map { pkg =>
          val objSuffix = sanitizeSuffix(pkg)
          val objRef    = Term.Name(s"__GolemAutoRegister_$objSuffix")
          q"$objRef.register()"
        }

      val baseTree = buildRegisterAgentsSource(genBasePkg, registerCallExprs)
      val baseFile = GeneratedFile(
        relativePath = packagePath(genBasePkg, "RegisterAgents.scala"),
        content = baseTree.syntax
      )

      Result(
        generatedPackage = genBasePkg,
        files = perPkgFiles :+ baseFile,
        warnings = warnings.result(),
        implCount = impls.length,
        packageCount = byPkg.size
      )
    }
  }

  // ── AST construction ───────────────────────────────────────────────────────

  private def buildPerPkgSource(
    genBasePkg: String,
    objSuffix: String,
    pkgImpls: List[AgentImpl]
  ): Source = {
    val pkgRef  = parseTermRef(genBasePkg)
    val objName = Term.Name(s"__GolemAutoRegister_$objSuffix")

    val registrations: List[Stat] = pkgImpls.map { ai =>
      buildRegistrationCall(ai)
    }

    val agentImplImport = parseImporter("golem.runtime.autowire.AgentImplementation")

    source"""
      package $pkgRef {
        import ..$agentImplImport

        /** Generated. Do not edit. */
        private[golem] object $objName {
          def register(): Unit = {
            ..$registrations
            ()
          }
        }
      }
    """
  }

  private def buildRegisterAgentsSource(
    genBasePkg: String,
    registerCalls: List[Stat]
  ): Source = {
    val pkgRef = parseTermRef(genBasePkg)

    val jsExportImport = parseImporter("scala.scalajs.js.annotation.JSExportTopLevel")

    val jsExportAnnot = parseMeta[Mod]("""@JSExportTopLevel("__golemRegisterAgents")""")

    source"""
      package $pkgRef {
        import ..$jsExportImport

        /** Generated. Do not edit. */
        private[golem] object RegisterAgents {
          private var registered = false

          private def registerAll(): Unit =
            if (!registered) {
              registered = true
              ..$registerCalls
              ()
            }

          def main(): Unit = registerAll()

          $jsExportAnnot val __golemRegisterAgents: Unit =
            registerAll()
        }
      }
    """
  }

  private def buildRegistrationCall(ai: AgentImpl): Stat = {
    val traitTpe = parseType(fqn(ai.pkg, ai.traitType))
    val implTpe  = parseType(fqn(ai.pkg, ai.implClass))
    q"AgentImplementation.registerClass[$traitTpe, $implTpe]"
  }

  // ── Type/term reference helpers ────────────────────────────────────────────

  private def parseMeta[T](code: String)(implicit parse: Parse[T]): T =
    Scala213(code).parse[T].get

  private def parseTermRef(dotted: String): Term.Ref =
    parseMeta[Term](dotted).asInstanceOf[Term.Ref]

  private def parseType(tpe: String): Type =
    parseMeta[Type](tpe)

  private def parseImporter(dotted: String): List[Importer] = {
    val importStat = parseMeta[Stat](s"import $dotted").asInstanceOf[Import]
    importStat.importers
  }

  // ── Implementation details ─────────────────────────────────────────────────

  private final case class AgentImpl(pkg: String, implClass: String, traitType: String, ctorTypes: List[String])

  private def autoRegisterSuffix(basePackage: String): String =
    basePackage
      .replaceAll("[^a-zA-Z0-9_]", "_")
      .stripPrefix("_")
      .stripSuffix("_") match {
      case ""  => "app"
      case out => out
    }

  private def sanitizeSuffix(pkg: String): String =
    pkg.replaceAll("[^a-zA-Z0-9_]", "_")

  private def packagePath(pkg: String, fileName: String): String =
    pkg.replace('.', '/') + "/" + fileName

  private val scalaBuiltins: Set[String] = Set(
    "String", "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char", "Unit",
    "BigInt", "BigDecimal", "Any", "AnyRef", "AnyVal", "Nothing", "Null"
  )

  private def fqn(ownerPkg: String, tpeOrTerm: String): String =
    if (tpeOrTerm.contains(".") || scalaBuiltins.contains(tpeOrTerm)) tpeOrTerm
    else s"$ownerPkg.$tpeOrTerm"

  private def parseSource(source: String): Option[Source] =
    dialects.Scala3(source).parse[Source].toOption
      .orElse(Scala213(source).parse[Source].toOption)

  private def hasAgentImplementation(mods: List[Mod]): Boolean =
    mods.exists {
      case Mod.Annot(init) =>
        val full = init.tpe.syntax
        full == "agentImplementation" || full.endsWith(".agentImplementation")
      case _ => false
    }

  private def appendPkg(prefix: String, name: String): String =
    if (prefix.isEmpty) name else s"$prefix.$name"

  private def collect(
    tree: Tree,
    pkg: String,
    sourcePath: String,
    warnings: scala.collection.mutable.Builder[Warning, List[Warning]]
  ): List[AgentImpl] =
    tree match {
      case source: Source =>
        source.stats.flatMap(collect(_, pkg, sourcePath, warnings))
      case pkgNode: Pkg =>
        val nextPkg = appendPkg(pkg, pkgNode.ref.syntax)
        pkgNode.stats.flatMap(collect(_, nextPkg, sourcePath, warnings))
      case Pkg.Object(_, name, templ) =>
        val nextPkg = appendPkg(pkg, name.value)
        templ.stats.flatMap(collect(_, nextPkg, sourcePath, warnings))
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
              warnings += Warning(
                Some(sourcePath),
                s"Skipping @agentImplementation ${cls.name.value} (missing constructor type annotations)."
              )
            Nil
        }
      case _ =>
        Nil
    }

  private def parseAgentImpls(
    source: String,
    sourcePath: String,
    warnings: scala.collection.mutable.Builder[Warning, List[Warning]]
  ): List[AgentImpl] =
    parseSource(source).toList.flatMap(tree => collect(tree, "", sourcePath, warnings))
}
