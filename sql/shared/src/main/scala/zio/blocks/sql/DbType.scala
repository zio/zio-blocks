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

package zio.blocks.sql

import zio.blocks.typeid.TypeId

/**
 * Maps one Scala type to a dialect-specific SQL type and codec.
 *
 * This is the extension point for types the core `DbValue`/derivation set does
 * not know about — for example a PostgreSQL `vector`/pgvector embedding or a
 * driver-specific `PGobject` wrapper. A mapping's [[codec]] is expected to bind
 * values with [[DbValue.Raw]] (so the write stays parameterized) and to decode
 * from the driver's representation.
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.typeid.TypeId
 *
 * case class Embedding(values: List[Double])
 * object Embedding { implicit val schema: Schema[Embedding] = Schema.derived }
 *
 * val embeddingCodec: DbCodec[Embedding] =
 *   DbCodec[String].transform[Embedding] { text =>
 *     Embedding(text.stripPrefix("[").stripSuffix("]").split(",").toList.map(_.toDouble))
 *   }(e => e.values.mkString("[", ",", "]"))
 *
 * val embedding: DbTypeMapping[Embedding] =
 *   DbTypeMapping(TypeId.of[Embedding], "vector", embeddingCodec)
 *
 * val registry = DbTypeRegistry.empty.register(embedding)
 * val deriver  = DbCodecDeriver.withTypeRegistry(registry)
 * }}}
 *
 * Mappings are keyed by `TypeId`; registering the same type again replaces the
 * previous mapping.
 *
 * @param typeId
 *   the Scala type this mapping applies to
 * @param sqlTypeName
 *   dialect type name, e.g. `"vector"` or `"jsonb"`
 * @param codec
 *   codec used for fields (and top-level values) of this type
 */
final case class DbTypeMapping[A](
  typeId: TypeId[A],
  sqlTypeName: String,
  codec: DbCodec[A]
)

/**
 * Immutable registry of [[DbTypeMapping]] values, passed to
 * [[DbCodecDeriver.withTypeRegistry]].
 *
 * A registry is explicit rather than implicit: derivation is schema-driven, so
 * there is no place to summon a per-field `given` while walking the `Reflect`
 * tree. Build a registry, register the dialect mappings, and hand it to the
 * deriver.
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.typeid.TypeId
 *
 * case class Embedding(values: List[Double])
 * object Embedding { implicit val schema: Schema[Embedding] = Schema.derived }
 *
 * val embeddingCodec: DbCodec[Embedding] =
 *   DbCodec[String].transform[Embedding] { text =>
 *     Embedding(text.stripPrefix("[").stripSuffix("]").split(",").toList.map(_.toDouble))
 *   }(e => e.values.mkString("[", ",", "]"))
 *
 * val registry =
 *   DbTypeRegistry.empty
 *     .register(DbTypeMapping(TypeId.of[Embedding], "vector", embeddingCodec))
 *
 * val deriver = DbCodecDeriver.withTypeRegistry(registry)
 * }}}
 */
final class DbTypeRegistry private (private val byTypeId: Map[TypeId[?], DbTypeMapping[?]]) {

  /**
   * Returns a new registry with `mapping` added (replacing any existing entry).
   */
  def register[A](mapping: DbTypeMapping[A]): DbTypeRegistry =
    new DbTypeRegistry(byTypeId.updated(mapping.typeId, mapping))

  /** Returns the mapping registered for `typeId`, if any. */
  def get[A](typeId: TypeId[A]): Option[DbTypeMapping[A]] =
    byTypeId.get(typeId).map(_.asInstanceOf[DbTypeMapping[A]])

  /** All registered mappings, for diagnostics/introspection. */
  def mappings: Iterable[DbTypeMapping[?]] = byTypeId.values

  private[sql] def getAny(typeId: TypeId[?]): Option[DbTypeMapping[?]] = byTypeId.get(typeId)
}

object DbTypeRegistry {

  /** An empty registry. */
  val empty: DbTypeRegistry = new DbTypeRegistry(Map.empty)

  /** Builds a registry from the supplied mappings. */
  def apply(mappings: DbTypeMapping[?]*): DbTypeRegistry =
    mappings.foldLeft(empty)((registry, mapping) => registry.register(mapping))
}
