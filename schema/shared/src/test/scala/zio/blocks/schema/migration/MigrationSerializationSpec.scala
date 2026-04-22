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

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.json.JsonCodecDeriver
import zio.test._

/**
 * Shared serialization contract for the migration system.
 *
 * Proves two things about the real derived migration schema:
 *   1. JSON round-trip: `JSON.decode(JSON.encode(m)) == Right(m)` over the
 *      exhaustive `genDynamicMigration` generator and over readable named
 *      fixtures.
 *   2. The zero-`Schema[_]` invariant proven two ways:
 *      - direct payload scan over concrete `MigrationAction` instances
 *        (restates the existing `MigrationActionStructuralSpec` guardrail in
 *        the serialization context);
 *      - derived-schema walk over `Schema[DynamicMigration]` asserting no
 *        `zio.blocks.schema.Schema` appears as any encountered `TypeId`.
 *
 * JSON is the primary proof target; MessagePack and Toon are covered by
 * module-local smoke specs.
 */
object MigrationSerializationSpec extends SchemaBaseSpec {

  import MigrationSerializationFixtures._

  // The JSON codec is derived once per spec; re-derivation per sample would
  // pessimise property execution time without adding coverage.
  private lazy val jsonCodec = Schema[DynamicMigration].deriving(JsonCodecDeriver).derive

  private def jsonRoundTrip(m: DynamicMigration): TestResult = {
    val encoded = jsonCodec.encodeToString(m)
    val decoded = jsonCodec.decode(encoded)
    assertTrue(decoded == Right(m))
  }

  // -- Direct payload scan ------------------------------------
  //
  // Enumerate one concrete instance of every `MigrationAction` variant and
  // assert that every case-class field has a type we allow in a pure-data
  // migration ADT: DynamicOptic / String / SchemaExpr / SchemaRepr / Chunk.
  // This remains the explicit guardrail against obvious field regressions
  // (new `Schema[_]` or closure field appearing in a case class).

  private val defaultExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  private val concreteVariants: List[MigrationAction] = List(
    MigrationAction.AddField(DynamicOptic.root, "f", defaultExpr),
    MigrationAction.DropField(DynamicOptic.root, "f", defaultExpr),
    MigrationAction.Rename(DynamicOptic.root.field("f"), "g"),
    MigrationAction.TransformValue(DynamicOptic.root, defaultExpr),
    MigrationAction.ChangeType(DynamicOptic.root, defaultExpr),
    MigrationAction.Mandate(DynamicOptic.root, defaultExpr),
    MigrationAction.Optionalize(DynamicOptic.root, SchemaRepr.Primitive("int")),
    MigrationAction.RenameCase(DynamicOptic.root.caseOf("Foo"), "Foo", "Bar"),
    MigrationAction.TransformCase(DynamicOptic.root.caseOf("Foo"), Chunk.empty),
    MigrationAction.TransformElements(DynamicOptic.root.elements, defaultExpr),
    MigrationAction.TransformKeys(DynamicOptic.root.mapKeys, defaultExpr),
    MigrationAction.TransformValues(DynamicOptic.root.mapValues, defaultExpr),
    MigrationAction.Join(DynamicOptic.root, Chunk.empty, defaultExpr),
    MigrationAction.Split(DynamicOptic.root, Chunk.empty, defaultExpr)
  )

  private def isAllowedPayloadType(v: Any): Boolean = v match {
    case _: DynamicOptic     => true
    case _: String           => true
    case _: SchemaExpr[_, _] => true
    case _: SchemaRepr       => true
    case _: Chunk[_]         => true
    case _                   => false
  }

  private def scanConcretePayload(a: MigrationAction): Boolean = {
    val elements = a.asInstanceOf[Product].productIterator.toList
    elements.forall(isAllowedPayloadType)
  }

  // -- Derived-schema walk ------------------------------------
  //
  // Walk `Schema[DynamicMigration].reflect` recursively, collecting every
  // encountered `TypeId.fullName`. Assert that none of them refers to
  // `zio.blocks.schema.Schema` — the one type whose accidental appearance
  // would break  by embedding schema-carrying payload into the serialized
  // graph. `SchemaExpr` and `SchemaRepr` are pure-data ADTs and are expected.

  private def collectTypeIds(r: Reflect[Binding, _], seen: Set[Reflect[Binding, _]]): List[String] =
    if (seen.contains(r)) Nil
    else {
      val seen2 = seen + r
      r match {
        case rec: Reflect.Record[Binding, _] @unchecked =>
          rec.typeId.fullName :: rec.fields.iterator
            .flatMap(term => collectTypeIds(term.value.asInstanceOf[Reflect[Binding, _]], seen2))
            .toList
        case v: Reflect.Variant[Binding, _] @unchecked =>
          v.typeId.fullName :: v.cases.iterator
            .flatMap(term => collectTypeIds(term.value.asInstanceOf[Reflect[Binding, _]], seen2))
            .toList
        case s: Reflect.Sequence[Binding, _, c] @unchecked =>
          s.typeId.fullName :: collectTypeIds(s.element.asInstanceOf[Reflect[Binding, _]], seen2)
        case mp: Reflect.Map[Binding, _, _, m] @unchecked =>
          mp.typeId.fullName ::
            collectTypeIds(mp.key.asInstanceOf[Reflect[Binding, _]], seen2) :::
            collectTypeIds(mp.value.asInstanceOf[Reflect[Binding, _]], seen2)
        case w: Reflect.Wrapper[Binding, _, _] @unchecked =>
          w.typeId.fullName :: collectTypeIds(w.wrapped.asInstanceOf[Reflect[Binding, _]], seen2)
        case p: Reflect.Primitive[Binding, _] @unchecked =>
          p.typeId.fullName :: Nil
        case d: Reflect.Dynamic[Binding] @unchecked =>
          // `Reflect.Dynamic` describes the DynamicValue leaf — not the
          // Schema class. Record it for completeness but it is not a
          //  violation.
          d.typeId.fullName :: Nil
        case def0: Reflect.Deferred[Binding, _] @unchecked =>
          collectTypeIds(def0.value.asInstanceOf[Reflect[Binding, _]], seen2)
      }
    }

  private val forbiddenTypeFullName: String = "zio.blocks.schema.Schema"

  // -- Suite -----------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSerializationSpec")(
    suite("jsonRoundTrip")(
      test("property: JSON round-trip over exhaustive genDynamicMigration (>= 100 samples)") {
        check(genDynamicMigration)(jsonRoundTrip)
      } @@ TestAspect.samples(100),
      test("fixture: simpleRenameAndAdd") {
        jsonRoundTrip(simpleRenameAndAdd)
      },
      test("fixture: nestedTransformCase (depth >= 2)") {
        jsonRoundTrip(nestedTransformCase)
      },
      test("fixture: joinSplit3Paths (3-path vector)") {
        jsonRoundTrip(joinSplit3Paths)
      },
      test("fixture: stringConcatTransform (recursive SchemaExpr)") {
        jsonRoundTrip(stringConcatTransform)
      },
      test("fixture: literalTransform (canonical post-roundtrip shape)") {
        jsonRoundTrip(literalTransform)
      },
      test("empty migration round-trips") {
        jsonRoundTrip(DynamicMigration.empty)
      }
    ),
    suite("noSchemaFields")(
      test("direct payload scan: every concrete MigrationAction variant carries only allowed field types") {
        val allAllowed: Boolean = concreteVariants.forall(a => scanConcretePayload(a))
        val variantCount: Int   = concreteVariants.length
        assertTrue(allAllowed) && assertTrue(variantCount == 14)
      },
      test("derived-schema walk: Schema[DynamicMigration] does not embed Schema[_] anywhere") {
        val ids       = collectTypeIds(Schema[DynamicMigration].reflect, Set.empty)
        val violation = ids.find(_ == forbiddenTypeFullName)
        assertTrue(violation.isEmpty)
      }
    )
  )
}
