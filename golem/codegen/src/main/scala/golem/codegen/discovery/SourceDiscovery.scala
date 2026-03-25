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

package golem.codegen.discovery

import scala.meta._
import scala.meta.dialects.Scala213
import scala.meta.parsers._

/**
 * Shared source discovery module for Golem codegen.
 *
 * Scans Scala source text using scalameta for:
 *   - `@agentDefinition` annotated traits
 *   - `@agentImplementation` annotated classes
 *   - Top-level objects (for companion conflict detection)
 *
 * All results are returned as pure data; no code generation is performed here.
 */
object SourceDiscovery {

  final case class SourceInput(path: String, content: String)

  final case class Warning(path: Option[String], message: String)

  /** Discovered `@agentDefinition` trait. */
  final case class AgentTrait(
    path: String,
    pkg: String,
    name: String,
    typeName: Option[String],
    constructorParams: List[ConstructorParam],
    hasDescription: Boolean,
    descriptionValue: Option[String],
    mode: Option[String],
    methods: List[DiscoveredMethod]
  )

  final case class ConstructorParam(name: String, typeExpr: String)

  final case class DiscoveredMethod(
    name: String,
    params: List[ConstructorParam],
    returnTypeExpr: String,
    principalParams: List[Boolean]
  )

  /** Discovered `@agentImplementation` class. */
  final case class AgentImpl(
    pkg: String,
    implClass: String,
    traitType: String,
    ctorTypes: List[String]
  )

  /** Discovered top-level object (for companion conflict detection). */
  final case class ExistingObject(path: String, pkg: String, name: String)

  final case class Result(
    traits: Seq[AgentTrait],
    implementations: Seq[AgentImpl],
    objects: Seq[ExistingObject],
    warnings: Seq[Warning]
  )

  /** Discover all agent traits, implementations, and top-level objects from the given sources. */
  def discover(sources: Seq[SourceInput]): Result = {
    val warnings = List.newBuilder[Warning]
    val traits   = List.newBuilder[AgentTrait]
    val impls    = List.newBuilder[AgentImpl]
    val objects  = List.newBuilder[ExistingObject]

    sources.foreach { src =>
      parseSource(src.content) match {
        case Some(tree) =>
          collect(tree, "", src.path, warnings, traits, impls, objects)
        case None =>
          warnings += Warning(Some(src.path), "Failed to parse source file.")
      }
    }

    Result(
      traits = traits.result().distinct.sortBy(t => (t.pkg, t.name)),
      implementations = impls.result().distinct.sortBy(ai => (ai.pkg, ai.traitType, ai.implClass)),
      objects = objects.result().distinct.sortBy(o => (o.pkg, o.name)),
      warnings = warnings.result()
    )
  }

  // ── Parsing ────────────────────────────────────────────────────────────────

  private def parseSource(source: String): Option[Source] =
    dialects.Scala3(source).parse[Source].toOption
      .orElse(Scala213(source).parse[Source].toOption)

  // ── Annotation detection ───────────────────────────────────────────────────

  private def hasAnnotation(mods: List[Mod], annotName: String): Boolean =
    mods.exists {
      case Mod.Annot(init) =>
        val full = init.tpe.syntax
        full == annotName || full.endsWith(s".$annotName")
      case _ => false
    }

  private def hasAgentDefinition(mods: List[Mod]): Boolean =
    hasAnnotation(mods, "agentDefinition")

  private def hasAgentImplementation(mods: List[Mod]): Boolean =
    hasAnnotation(mods, "agentImplementation")

  /** Flatten all annotation arguments from an Init node. */
  private def flattenArgs(init: Init): List[Term] =
    init.argClauses.toList.flatMap(_.values)

  /** Extract `typeName` from `@agentDefinition(typeName = "Foo")` or `@agentDefinition("Foo")`. */
  private def extractTypeName(mods: List[Mod]): Option[String] =
    mods.collectFirst {
      case Mod.Annot(init) if {
            val full = init.tpe.syntax
            full == "agentDefinition" || full.endsWith(".agentDefinition")
          } =>
        init
    }.flatMap { init =>
      val args = flattenArgs(init)
      // Named argument: @agentDefinition(typeName = "Foo")
      val named = args.collectFirst {
        case Term.Assign(Term.Name("typeName"), Lit.String(v)) if v.nonEmpty => v
      }
      named.orElse {
        // Positional first argument: @agentDefinition("Foo")
        args.headOption.collect {
          case Lit.String(v) if v.nonEmpty => v
        }
      }
    }

  /** Extract `@description("...")` value from a trait's modifiers. */
  private def extractDescription(mods: List[Mod]): (Boolean, Option[String]) =
    mods.collectFirst {
      case Mod.Annot(init) if {
            val full = init.tpe.syntax
            full == "description" || full.endsWith(".description")
          } =>
        init
    } match {
      case Some(init) =>
        val value = flattenArgs(init).headOption.collect {
          case Lit.String(v) => v
        }
        (true, value)
      case None =>
        (false, None)
    }

  /** Extract `mode` from `@agentDefinition(mode = DurabilityMode.Ephemeral)`. */
  private def extractMode(mods: List[Mod]): Option[String] =
    mods.collectFirst {
      case Mod.Annot(init) if {
            val full = init.tpe.syntax
            full == "agentDefinition" || full.endsWith(".agentDefinition")
          } =>
        init
    }.flatMap { init =>
      val args = flattenArgs(init)
      // Named argument: mode = DurabilityMode.Ephemeral
      val named = args.collectFirst {
        case Term.Assign(Term.Name("mode"), term) => extractModeValue(term)
      }.flatten
      named.orElse {
        // Positional second argument (index 1)
        args.lift(1).flatMap(extractModeValue)
      }
    }

  private def extractModeValue(term: Term): Option[String] =
    term match {
      case Term.Select(_, Term.Name("Ephemeral")) => Some("ephemeral")
      case Term.Select(_, Term.Name("Durable"))   => Some("durable")
      case Term.Name("Ephemeral")                 => Some("ephemeral")
      case Term.Name("Durable")                   => Some("durable")
      case _                                      => None
    }

  /** Extract `@constructor` method parameters from a trait body. */
  private def extractConstructorParams(templ: Template): List[ConstructorParam] =
    templ.stats.collectFirst {
      case d: Defn.Def if hasAnnotation(d.mods, "constructor") =>
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).flatMap { param =>
          param.decltpe.map(tpe => ConstructorParam(param.name.value, tpe.syntax))
        }
    }.getOrElse(Nil)

  /** Check if a type AST represents `Principal` or `golem.Principal`. */
  private def isPrincipalType(tpe: Type): Boolean =
    tpe match {
      case Type.Name("Principal")                 => true
      case Type.Select(_, Type.Name("Principal")) => true
      case _                                      => false
    }

  /** Extract non-constructor methods from a trait body. */
  private def extractMethods(templ: Template): List[DiscoveredMethod] =
    templ.stats.flatMap {
      case d: Decl.Def if !hasAnnotation(d.mods, "constructor") =>
        val params = d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).flatMap { param =>
          param.decltpe.map(tpe => (ConstructorParam(param.name.value, tpe.syntax), isPrincipalType(tpe)))
        }
        Some(DiscoveredMethod(
          name = d.name.value,
          params = params.map(_._1),
          returnTypeExpr = d.decltpe.syntax,
          principalParams = params.map(_._2)
        ))
      case d: Defn.Def if !hasAnnotation(d.mods, "constructor") =>
        d.decltpe match {
          case Some(retTpe) =>
            val params = d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).flatMap { param =>
              param.decltpe.map(tpe => (ConstructorParam(param.name.value, tpe.syntax), isPrincipalType(tpe)))
            }
            Some(DiscoveredMethod(
              name = d.name.value,
              params = params.map(_._1),
              returnTypeExpr = retTpe.syntax,
              principalParams = params.map(_._2)
            ))
          case None => None // Skip methods with no explicit return type
        }
      case _ => None
    }.toList

  // ── AST walking ────────────────────────────────────────────────────────────

  private def appendPkg(prefix: String, name: String): String =
    if (prefix.isEmpty) name else s"$prefix.$name"

  private def collect(
    tree: Tree,
    pkg: String,
    sourcePath: String,
    warnings: scala.collection.mutable.Builder[Warning, List[Warning]],
    traits: scala.collection.mutable.Builder[AgentTrait, List[AgentTrait]],
    impls: scala.collection.mutable.Builder[AgentImpl, List[AgentImpl]],
    objects: scala.collection.mutable.Builder[ExistingObject, List[ExistingObject]]
  ): Unit =
    tree match {
      case source: Source =>
        source.stats.foreach(collect(_, pkg, sourcePath, warnings, traits, impls, objects))

      case pkgNode: Pkg =>
        val nextPkg = appendPkg(pkg, pkgNode.ref.syntax)
        pkgNode.stats.foreach(collect(_, nextPkg, sourcePath, warnings, traits, impls, objects))

      case Pkg.Object(_, name, templ) =>
        val nextPkg = appendPkg(pkg, name.value)
        templ.stats.foreach(collect(_, nextPkg, sourcePath, warnings, traits, impls, objects))

      case t: Defn.Trait if hasAgentDefinition(t.mods) =>
        val typeName                      = extractTypeName(t.mods)
        val (hasDesc, descVal)            = extractDescription(t.mods)
        val ctorParams                    = extractConstructorParams(t.templ)
        val modeValue                     = extractMode(t.mods)
        val discoveredMethods             = extractMethods(t.templ)
        traits += AgentTrait(
          path = sourcePath,
          pkg = pkg,
          name = t.name.value,
          typeName = typeName,
          constructorParams = ctorParams,
          hasDescription = hasDesc,
          descriptionValue = descVal,
          mode = modeValue,
          methods = discoveredMethods
        )

      case cls: Defn.Class if hasAgentImplementation(cls.mods) =>
        val traitTypeOpt: Option[String] = cls.templ.inits.headOption.map(_.tpe.syntax)
        val ctorParams                   = cls.ctor.paramClauses.flatMap(_.values)
        val ctorTypes: List[String]      = ctorParams.map(_.decltpe.map(_.syntax).getOrElse("")).toList
        traitTypeOpt match {
          case Some(traitType) if pkg.nonEmpty && !ctorTypes.exists(_.isEmpty) =>
            impls += AgentImpl(
              pkg = pkg,
              implClass = cls.name.value,
              traitType = traitType,
              ctorTypes = ctorTypes
            )
          case _ =>
            if (ctorTypes.exists(_.isEmpty))
              warnings += Warning(
                Some(sourcePath),
                s"Skipping @agentImplementation ${cls.name.value} (missing constructor type annotations)."
              )
        }

      case obj: Defn.Object =>
        objects += ExistingObject(
          path = sourcePath,
          pkg = pkg,
          name = obj.name.value
        )

      case _ =>
        ()
    }
}
