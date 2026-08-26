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

package zio.blocks.projection

import scala.compiletime.summonFrom

import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}
import zio.blocks.typeid.AnnotationArg

/**
 * Information about event tag aliasing derived from a Migration chain.
 *
 * @param aliases
 *   Map from old tag name to current tag name (oldTag -> currentTag)
 * @param allTags
 *   Union of current variant names and all old names (queryable tags)
 * @param currentTags
 *   Current variant case names from Schema[E]
 * @param migration
 *   Underlying DynamicMigration for DynamicValue transformation
 */
final case class TagInfo(
  aliases: Map[String, String],
  allTags: Set[String],
  currentTags: Set[String],
  migration: DynamicMigration
) {

  def isOldTag(tag: String): Boolean = aliases.contains(tag)

  def currentTagFor(oldTag: String): Option[String] = aliases.get(oldTag)

  def normalize(tag: String): String = aliases.getOrElse(tag, tag)

  def migrateValue(dv: DynamicValue): Either[zio.blocks.schema.SchemaError, DynamicValue] =
    migration(dv)

  def expandRequested(requested: Set[String]): Set[String] =
    if (requested.isEmpty) allTags
    else
      requested.flatMap { t =>
        val oldsForCurrent = aliases.collect { case (old, cur) if cur == t => old }
        val currentForOld  = aliases.get(t).toSet
        Set(t) ++ oldsForCurrent ++ currentForOld
      }

  def unknownTags(distinctDbTags: Set[String]): Set[String] =
    distinctDbTags -- allTags
}

/**
 * Trait for TagResolver abstraction.
 */
trait TagResolver[E] {
  def tagInfo: TagInfo
  def aliases: Map[String, String] = tagInfo.aliases
  def allTags: Set[String]         = tagInfo.allTags
  def currentTags: Set[String]     = tagInfo.currentTags
}

object TagResolver {

  /**
   * Build TagInfo from a Schema and optional Migration.
   */
  def resolveOpt[E](opt: Option[Migration[E, E]])(using schema: Schema[E]): TagInfo = {
    val current = currentTags[E]
    opt match {
      case None    => TagInfo(Map.empty, current, current, DynamicMigration.empty)
      case Some(m) =>
        val dm    = m.dynamicMigration
        val alias = aliasMapFromDynamic(dm)
        // Resolve transitive chains: if A->B and B->C then A should map to C
        val transitive = transitiveAliasMap(alias)
        val all        = current ++ transitive.keys
        TagInfo(transitive, all, current, dm)
    }
  }

  def resolve[E: Schema](migration: Migration[E, E]): TagInfo =
    resolveOpt[E](Option(migration))

  def resolve[E: Schema]: TagInfo =
    resolveOpt[E](None)

  // Scala 2 compatible implicit discovery with default null
  def resolveImplicit[E: Schema](implicit migration: Migration[E, E]): TagInfo =
    resolveOpt[E](Option(migration))

  def allTags[E: Schema](migration: Migration[E, E] = null): Set[String] =
    resolve[E](migration).allTags

  def aliases[E: Schema](migration: Migration[E, E] = null): Map[String, String] =
    resolve[E](migration).aliases

  /**
   * Inline resolver that discovers Migration[E,E] via given summon if
   * available.
   */
  inline def resolveAuto[E](using schema: Schema[E]): TagInfo =
    summonFrom {
      case m: Migration[E, E] => resolveOpt[E](Some(m))
      case _                  => resolveOpt[E](None)
    }

  private def extractNumericTag(typeId: zio.blocks.typeid.TypeId[?]): Option[Int] = {
    def search(args: List[AnnotationArg]): Option[Int] =
      args.iterator.flatMap {
        case AnnotationArg.Const(v: Int)               => Some(v)
        case AnnotationArg.Const(v: java.lang.Integer) => Some(v.intValue())
        case AnnotationArg.Const(v: Long)              => Some(v.toInt)
        case AnnotationArg.Const(v: java.lang.Long)    => Some(v.intValue())
        case AnnotationArg.Named(_, inner)             => search(List(inner))
        case AnnotationArg.ArrayArg(values)            => search(values)
        case AnnotationArg.Nested(ann)                 => search(ann.args)
        case _                                         => None
      }.nextOption()

    typeId.annotations.collectFirst {
      case ann if ann.name == "eventTag" => search(ann.args)
    }.flatten
  }

  def currentTags[E](using schema: Schema[E]): Set[String] =
    schema.reflect.asVariant match {
      case Some(variant) =>
        variant.cases.map { term =>
          extractNumericTag(term.value.typeId).map(_.toString).getOrElse(term.name)
        }.toSet
      case None =>
        // Non-variant: use typeId name as single tag, respecting numeric annotation
        extractNumericTag(schema.reflect.typeId).map(_.toString) match {
          case Some(numeric) => Set(numeric)
          case None          =>
            val tidName = schema.reflect.typeId.name
            if (tidName.nonEmpty && tidName != "Object" && tidName != "Any") Set(tidName)
            else Set.empty
        }
    }

  def aliasMapFromDynamic(dm: DynamicMigration): Map[String, String] = {
    val renames = dm.actions.collect { case MigrationAction.RenameCase(_, from, to) =>
      from -> to
    }
    // Build map, handling potential duplicate from keys (last wins, but transitivo later)
    renames.foldLeft(Map.empty[String, String]) { case (acc, (from, to)) =>
      acc + (from -> to)
    }
  }

  private def transitiveAliasMap(direct: Map[String, String]): Map[String, String] =
    // For chains A->B, B->C, resolve A->C by iteratively resolving target if target is also a key
    direct.map { case (old, cur) =>
      var resolved = cur
      var visited  = Set(old)
      while (direct.contains(resolved) && !visited.contains(resolved)) {
        visited += resolved
        resolved = direct(resolved)
      }
      old -> resolved
    } ++
      // also keep intermediate mappings that may not be keys of original? Actually direct already contains B->C
      Map.empty

  /**
   * Compute alias map from Migration instance directly.
   */
  def aliasMap[E](migration: Migration[E, E]): Map[String, String] =
    transitiveAliasMap(aliasMapFromDynamic(migration.dynamicMigration))

  /**
   * Validate distinct tags from DB against TagInfo, return unknown tags.
   */
  def unknownTags(tagInfo: TagInfo, distinctDbTags: Set[String]): Set[String] =
    tagInfo.unknownTags(distinctDbTags)
}
