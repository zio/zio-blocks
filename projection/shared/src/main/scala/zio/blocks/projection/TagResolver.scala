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
 * Which event tags exist for an event type, and how old tags map to current
 * ones.
 *
 * Event tags are stored as strings, and a renamed case leaves old events in the
 * store under its old tag. A `TagInfo` records which tags are current, which
 * are old aliases of them, and the migration that rewrites old events. An
 * [[EventStore]] uses it to read old events as if they carried the current tag.
 *
 * Build one with [[TagResolver]] rather than by hand.
 *
 * @param aliases
 *   map from an old tag to the current tag it was renamed to. Chains of renames
 *   are already followed, so every old tag maps straight to its final name
 * @param allTags
 *   every tag that may legitimately be in the store: the current tags plus all
 *   old ones
 * @param currentTags
 *   the tags the event type's schema produces now
 * @param migration
 *   the migration used to transform old events
 */
final case class TagInfo(
  aliases: Map[String, String],
  allTags: Set[String],
  currentTags: Set[String],
  migration: DynamicMigration
) {

  /** Whether `tag` is an old name that has been renamed. */
  def isOldTag(tag: String): Boolean = aliases.contains(tag)

  /** The current tag that the old tag `oldTag` was renamed to, if any. */
  def currentTagFor(oldTag: String): Option[String] = aliases.get(oldTag)

  /**
   * Returns the current tag for an old tag, and any other tag unchanged. Use it
   * to compare tags without caring whether an event was stored before or after
   * a rename.
   */
  def normalize(tag: String): String = aliases.getOrElse(tag, tag)

  /**
   * Runs the migration on an old event's value, so that it can then be decoded
   * with the current schema. Fails if the migration cannot be applied.
   */
  def migrateValue(dv: DynamicValue): Either[zio.blocks.schema.SchemaError, DynamicValue] =
    migration(dv)

  /**
   * Widens a set of tags to be read so that a rename does not hide events. Each
   * requested tag is joined by the old tags that were renamed to it, and by the
   * current tag it was renamed to. An empty request means all tags.
   *
   * For example, with `UserCreated` renamed to `UserRegistered`, asking for
   * `UserRegistered` gives both names.
   */
  def expandRequested(requested: Set[String]): Set[String] =
    if (requested.isEmpty) allTags
    else
      requested.flatMap { t =>
        val oldsForCurrent = aliases.collect { case (old, cur) if cur == t => old }
        val currentForOld  = aliases.get(t).toSet
        Set(t) ++ oldsForCurrent ++ currentForOld
      }

  /**
   * The tags in `distinctDbTags` that are neither current nor a known old name.
   * Such events were probably written under a name that no migration mentions.
   */
  def unknownTags(distinctDbTags: Set[String]): Set[String] =
    distinctDbTags -- allTags
}

/**
 * A view of the [[TagInfo]] for an event type `E`, with its parts as methods.
 *
 * Instances are made by implementing `tagInfo`. Most code uses the functions on
 * the companion object to get a [[TagInfo]] directly.
 */
trait TagResolver[E] {

  /** The tag information for `E`. */
  def tagInfo: TagInfo

  /** Old tag to current tag, as in [[TagInfo.aliases]]. */
  def aliases: Map[String, String] = tagInfo.aliases

  /** Every tag that may be in the store, as in [[TagInfo.allTags]]. */
  def allTags: Set[String] = tagInfo.allTags

  /** The tags `E` produces now, as in [[TagInfo.currentTags]]. */
  def currentTags: Set[String] = tagInfo.currentTags
}

/**
 * Works out the tags of an event type and the aliases left behind by renamed
 * cases. It reads the current tags from `Schema[E]`, and the old ones from the
 * `RenameCase` actions of an optional `Migration[E, E]`.
 *
 * Without a migration there are no aliases. With one, each renamed case becomes
 * an alias, so old events can still be read and queried. The names are used
 * exactly as written in the migration.
 *
 * {{{
 * val migration: Migration[UserEvent, UserEvent] =
 *   Migration.newBuilder[UserEvent, UserEvent]
 *     .renameCase("UserCreated", "UserRegistered")
 *     .build
 *
 * val info = TagResolver.resolve[UserEvent](migration)
 * // info.aliases == Map("UserCreated" -> "UserRegistered")
 * }}}
 *
 * The event stores call these functions when they are created, so applications
 * normally only pass a migration and do not call them directly.
 */
object TagResolver {

  /**
   * Builds a [[TagInfo]] from `Schema[E]` and an optional migration. This is
   * the function the others call.
   *
   * Chains of renames are followed, so if `A` was renamed to `B` and `B` to
   * `C`, then `A` maps to `C`.
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

  /** Builds a [[TagInfo]] with the aliases of the given migration. */
  def resolve[E: Schema](migration: Migration[E, E]): TagInfo =
    resolveOpt[E](Option(migration))

  /** Builds a [[TagInfo]] with no aliases, for an event type never renamed. */
  def resolve[E: Schema]: TagInfo =
    resolveOpt[E](None)

  /**
   * Builds a [[TagInfo]], using a `Migration[E, E]` if one is in implicit scope
   * and none otherwise. This form also works in Scala 2.
   */
  def resolveImplicit[E: Schema](implicit migration: Migration[E, E] = null): TagInfo =
    resolveOpt[E](Option(migration))

  /**
   * All tags of `E`, current and old. Pass `null` for no migration, which is
   * the default.
   */
  def allTags[E: Schema](migration: Migration[E, E] = null): Set[String] =
    resolve[E](migration).allTags

  /**
   * The old-to-current tag aliases of the given migration. Pass `null` for no
   * migration, which is the default.
   */
  def aliases[E: Schema](migration: Migration[E, E] = null): Map[String, String] =
    resolve[E](migration).aliases

  /**
   * Builds a [[TagInfo]], using a `Migration[E, E]` if a given instance is
   * available and none otherwise. It is Scala 3 only, and resolves the instance
   * at the call site.
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

  /**
   * The tags that events of type `E` are stored under now.
   *
   * For a sealed event type there is one tag per case: the number from its
   * `@eventTag`, or the case name if it has none. For a single, non-sealed type
   * the tag is its `@eventTag` number or its type name, and there is none if
   * the name is empty, `Object` or `Any`.
   */
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

  /**
   * Reads the `RenameCase` actions of a migration into a map from the old name
   * to the new one. Other actions are ignored. If one old name is renamed more
   * than once, the last rename wins. Chains are not followed here, see
   * [[aliasMap]].
   */
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
   * The old-to-current aliases of a migration, with chains of renames followed:
   * if `A` was renamed to `B` and `B` to `C`, then `A` maps to `C`. A cycle of
   * renames stops instead of looping.
   */
  def aliasMap[E](migration: Migration[E, E]): Map[String, String] =
    transitiveAliasMap(aliasMapFromDynamic(migration.dynamicMigration))

  /**
   * The tags found in the store that `tagInfo` does not know about. The same as
   * [[TagInfo.unknownTags]].
   */
  def unknownTags(tagInfo: TagInfo, distinctDbTags: Set[String]): Set[String] =
    tagInfo.unknownTags(distinctDbTags)
}
