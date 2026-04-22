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
import zio.blocks.schema.DynamicValueGen.genPrimitiveValue
import zio.test.Gen

/**
 * Shared serialization fixtures for the migration property suite.
 *
 * Exports an exhaustive `genDynamicMigration` property generator that covers
 * all 14 `MigrationAction` variants, with:
 *   - nested `TransformCase` at depth >= 2,
 *   - 3-path `Chunk[DynamicOptic]` vectors for `Join` / `Split`,
 *   - the full migration-used `SchemaExpr` subset (DefaultValue, Literal,
 *     StringConcat) bridged by `SchemaExpr.migrationSchema`.
 *
 * Also exports a curated named-fixture catalog so JSON round-trip failures
 * are diagnosable without relying solely on generator shrinking.
 *
 * Literal roundtrip note: `SchemaExpr.Literal` round-trip is intentionally
 * lossy — the reconstructed `Literal` is always
 * `Literal[Any, DynamicValue](dv, Schema[DynamicValue])`. To keep the
 * property-driven round-trip a *structural* equality check, `genLiteralExpr`
 * only emits values that already match that canonical post-roundtrip shape.
 */
object MigrationSerializationFixtures {

  // -- primitive leaf generators ---------------------------------------------

  private val genDynamicOpticLeaf: Gen[Any, DynamicOptic] = Gen.elements(
    DynamicOptic.root,
    DynamicOptic.root.field("a"),
    DynamicOptic.root.field("a").field("b"),
    DynamicOptic.root.elements,
    DynamicOptic.root.field("m").mapKeys,
    DynamicOptic.root.field("m").mapValues
  )

  private val genCaseOptic: Gen[Any, DynamicOptic] = Gen.elements(
    DynamicOptic.root.caseOf("Foo"),
    DynamicOptic.root.caseOf("Bar"),
    DynamicOptic.root.field("v").caseOf("Baz")
  )

  val genSchemaRepr: Gen[Any, SchemaRepr] = Gen.elements(
    SchemaRepr.Primitive("int"),
    SchemaRepr.Primitive("string"),
    SchemaRepr.Optional(SchemaRepr.Primitive("int"))
  )

  private val genDynamicValue: Gen[Any, DynamicValue] = Gen.elements(
    DynamicValue.Primitive(PrimitiveValue.Int(7)),
    DynamicValue.Primitive(PrimitiveValue.String("hello")),
    DynamicValue.Primitive(PrimitiveValue.Boolean(true))
  )

  // -- migration-used SchemaExpr subset --------------------------------------

  /** `SchemaExpr.DefaultValue` is pure data and round-trips verbatim. */
  val genDefaultValueExpr: Gen[Any, SchemaExpr[_, _]] =
    for {
      opt <- genDynamicOpticLeaf
      s   <- genSchemaRepr
    } yield SchemaExpr.DefaultValue(opt, s)

  /**
   * `SchemaExpr.Literal` is intentionally reconstructed as
   * `Literal[Any, DynamicValue](dv, Schema[DynamicValue])` by the migration
   * schema bridge. This generator emits values in exactly that canonical
   * shape so the shared JSON round-trip property check is a strict equality
   * proof.
   */
  val genLiteralExpr: Gen[Any, SchemaExpr[_, _]] =
    for {
      dv <- genDynamicValue
    } yield SchemaExpr.Literal[Any, DynamicValue](dv, Schema[DynamicValue])

  /**
   * Recursive `StringConcat` over the non-recursive migration-used leaves. The
   * StringConcat field types are `SchemaExpr[A, String]`, but at runtime the
   * interpreter / migrationSchema bridge carries arbitrary `SchemaExpr[_, _]`
   * leaves and projects them through `Schema[DynamicValue]`. The property
   * suite only checks *structural* round-trip of the persisted data, so
   * injecting canonical leaves here is safe.
   */
  val genStringConcatExpr: Gen[Any, SchemaExpr[_, _]] =
    for {
      l <- Gen.oneOf(genDefaultValueExpr, genLiteralExpr)
      r <- Gen.oneOf(genDefaultValueExpr, genLiteralExpr)
    } yield SchemaExpr
      .StringConcat[Any](
        l.asInstanceOf[SchemaExpr[Any, String]],
        r.asInstanceOf[SchemaExpr[Any, String]]
      )
      .asInstanceOf[SchemaExpr[_, _]]

  /** Full migration-used `SchemaExpr` subset. */
  val genSchemaExpr: Gen[Any, SchemaExpr[_, _]] = Gen.oneOf(
    genDefaultValueExpr,
    genLiteralExpr,
    genStringConcatExpr
  )

  // -- MigrationAction leaf generators (11 of 14) ----------------------------

  private val genAddField: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; n <- Gen.alphaNumericStringBounded(1, 8); d <- genSchemaExpr }
      yield MigrationAction.AddField(at, n, d)

  private val genDropField: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; n <- Gen.alphaNumericStringBounded(1, 8); d <- genSchemaExpr }
      yield MigrationAction.DropField(at, n, d)

  private val genRename: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; s <- Gen.alphaNumericStringBounded(1, 8) }
      yield MigrationAction.Rename(at.field("orig"), s)

  private val genTransformValue: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; d <- genSchemaExpr } yield MigrationAction.TransformValue(at, d)

  private val genChangeType: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; d <- genSchemaExpr } yield MigrationAction.ChangeType(at, d)

  private val genMandate: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; s <- genSchemaRepr }
      yield MigrationAction.Mandate(at, SchemaExpr.DefaultValue(at, s))

  private val genOptionalize: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; s <- genSchemaRepr } yield MigrationAction.Optionalize(at, s)

  private val genRenameCase: Gen[Any, MigrationAction] =
    for { at <- genCaseOptic; from <- Gen.alphaNumericStringBounded(1, 6) }
      yield MigrationAction.RenameCase(at, from, from + "New")

  private val genTransformElements: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; d <- genSchemaExpr } yield MigrationAction.TransformElements(at, d)

  private val genTransformKeys: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; d <- genSchemaExpr } yield MigrationAction.TransformKeys(at, d)

  private val genTransformValues: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; d <- genSchemaExpr } yield MigrationAction.TransformValues(at, d)

  /** Produces a 3-path `Chunk[DynamicOptic]` ( / ). */
  private val gen3Paths: Gen[Any, Chunk[DynamicOptic]] =
    for {
      p1 <- genDynamicOpticLeaf
      p2 <- genDynamicOpticLeaf
      p3 <- genDynamicOpticLeaf
    } yield Chunk(p1, p2, p3)

  private val genJoin: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; paths <- gen3Paths; d <- genSchemaExpr }
      yield MigrationAction.Join(at, paths, d)

  private val genSplit: Gen[Any, MigrationAction] =
    for { at <- genDynamicOpticLeaf; paths <- gen3Paths; d <- genSchemaExpr }
      yield MigrationAction.Split(at, paths, d)

  /**
   * All non-recursive `MigrationAction` leaf variants. `TransformCase` is
   * handled by the recursive top-level generator below.
   */
  private val genLeafAction: Gen[Any, MigrationAction] = Gen.oneOf(
    genAddField,
    genDropField,
    genRename,
    genTransformValue,
    genChangeType,
    genMandate,
    genOptionalize,
    genRenameCase,
    genTransformElements,
    genTransformKeys,
    genTransformValues,
    genJoin,
    genSplit
  )

  /**
   * Recursive `TransformCase` generator parameterized on nesting depth. Depth
   * 2 is enough to satisfy 's "nested >= 2" requirement; we cap recursion
   * to keep property runs bounded.
   */
  private def genTransformCase(depth: Int): Gen[Any, MigrationAction] =
    if (depth <= 0) {
      // Terminal TransformCase wraps only leaves.
      for {
        at <- genCaseOptic
        xs <- Gen.listOfBounded(1, 3)(genLeafAction)
      } yield MigrationAction.TransformCase(at, Chunk.from(xs))
    } else {
      for {
        at   <- genCaseOptic
        leaf <- Gen.listOfBounded(1, 2)(genLeafAction)
        // At least one inner TransformCase keeps nesting depth >= 2.
        inner = genTransformCase(depth - 1)
        nested <- Gen.listOfBounded(1, 2)(inner)
      } yield MigrationAction.TransformCase(at, Chunk.from(leaf ++ nested))
    }

  /** 14-variant `MigrationAction` generator (13 leaves + recursive TransformCase). */
  val genMigrationAction: Gen[Any, MigrationAction] =
    Gen.oneOf(genLeafAction, genTransformCase(2))

  /**
   * Top-level `DynamicMigration` generator. Chunks 1..5 actions so every
   * sample carries multiple action variants and exercises the `Chunk`
   * envelope serialized by `Schema[DynamicMigration]`.
   */
  val genDynamicMigration: Gen[Any, DynamicMigration] =
    Gen.listOfBounded(1, 5)(genMigrationAction).map(xs => new DynamicMigration(Chunk.from(xs)))

  // -- Deep DynamicValue coverage --------------------------------------------

  private val genDynamicValueLeaf: Gen[Any, DynamicValue] =
    Gen.oneOf(
      genPrimitiveValue.map(DynamicValue.Primitive(_)),
      Gen.const(DynamicValue.Null)
    )

  private val genFieldName: Gen[Any, String] = Gen.alphaNumericStringBounded(1, 8)

  private val genCaseNameValue: Gen[Any, String] = Gen.alphaNumericStringBounded(1, 8)

  private def distinctFields(fields: List[(String, DynamicValue)]): Chunk[(String, DynamicValue)] =
    Chunk.from(fields.distinctBy(_._1))

  private def distinctEntries(entries: List[(DynamicValue, DynamicValue)]): Chunk[(DynamicValue, DynamicValue)] =
    Chunk.from(entries.distinctBy(_._1))

  private def genDynamicValueAtMost(maxDepth: Int): Gen[Any, DynamicValue] =
    if (maxDepth <= 0) genDynamicValueLeaf
    else
      Gen.oneOf(
        genDynamicValueLeaf,
        genRecordDynamicValueAtMost(maxDepth - 1),
        genVariantDynamicValueAtMost(maxDepth - 1),
        genSequenceDynamicValueAtMost(maxDepth - 1),
        genMapDynamicValueAtMost(maxDepth - 1)
      )

  private def genRecordDynamicValueAtMost(maxDepth: Int): Gen[Any, DynamicValue] =
    Gen
      .listOfBounded(0, 3) {
        for {
          key   <- genFieldName
          value <- genDynamicValueAtMost(maxDepth)
        } yield key -> value
      }
      .map(fields => DynamicValue.Record(distinctFields(fields)))

  private def genVariantDynamicValueAtMost(maxDepth: Int): Gen[Any, DynamicValue] =
    for {
      caseName <- genCaseNameValue
      value    <- genDynamicValueAtMost(maxDepth)
    } yield DynamicValue.Variant(caseName, value)

  private def genSequenceDynamicValueAtMost(maxDepth: Int): Gen[Any, DynamicValue] =
    Gen
      .listOfBounded(0, 3)(genDynamicValueAtMost(maxDepth))
      .map(values => DynamicValue.Sequence(Chunk.from(values)))

  private def genMapDynamicValueAtMost(maxDepth: Int): Gen[Any, DynamicValue] =
    Gen
      .listOfBounded(0, 3) {
        for {
          key   <- Gen.alphaNumericStringBounded(1, 8).map(s => DynamicValue.Primitive(PrimitiveValue.String(s)))
          value <- genDynamicValueAtMost(maxDepth)
        } yield key -> value
      }
      .map(entries => DynamicValue.Map(distinctEntries(entries)))

  private def genRecordDynamicValueAtLeast(minDepth: Int): Gen[Any, DynamicValue] =
    for {
      required <- genDynamicValueAtLeast(minDepth - 1)
      extras <- Gen.listOfBounded(0, 2) {
        for {
          key   <- genFieldName.filter(_ != "required")
          value <- genDynamicValueAtMost(minDepth - 1)
        } yield key -> value
      }
    } yield DynamicValue.Record(distinctFields(("required", required) :: extras))

  private def genVariantDynamicValueAtLeast(minDepth: Int): Gen[Any, DynamicValue] =
    for {
      caseName <- genCaseNameValue.map(name => s"${name}Case")
      value    <- genDynamicValueAtLeast(minDepth - 1)
    } yield DynamicValue.Variant(caseName, value)

  private def genSequenceDynamicValueAtLeast(minDepth: Int): Gen[Any, DynamicValue] =
    for {
      required <- genDynamicValueAtLeast(minDepth - 1)
      extras   <- Gen.listOfBounded(0, 2)(genDynamicValueAtMost(minDepth - 1))
    } yield DynamicValue.Sequence(Chunk.from(required :: extras))

  private def genMapDynamicValueAtLeast(minDepth: Int): Gen[Any, DynamicValue] =
    for {
      required <- genDynamicValueAtLeast(minDepth - 1)
      extras <- Gen.listOfBounded(0, 2) {
        for {
          key   <- Gen.alphaNumericStringBounded(1, 8).map(s => DynamicValue.Primitive(PrimitiveValue.String(s)))
          value <- genDynamicValueAtMost(minDepth - 1)
        } yield key -> value
      }
    } yield {
      val requiredKey = DynamicValue.Primitive(PrimitiveValue.String("required"))
      DynamicValue.Map(distinctEntries((requiredKey, required) :: extras))
    }

  private def genDynamicValueAtLeast(minDepth: Int): Gen[Any, DynamicValue] =
    if (minDepth <= 0) genDynamicValueLeaf
    else
      Gen.oneOf(
        genRecordDynamicValueAtLeast(minDepth),
        genVariantDynamicValueAtLeast(minDepth),
        genSequenceDynamicValueAtLeast(minDepth),
        genMapDynamicValueAtLeast(minDepth)
      )

  val genRecordDynamicValueDepth3: Gen[Any, DynamicValue] = genRecordDynamicValueAtLeast(3)

  val genVariantDynamicValueDepth3: Gen[Any, DynamicValue] = genVariantDynamicValueAtLeast(3)

  val genSequenceDynamicValueDepth3: Gen[Any, DynamicValue] = genSequenceDynamicValueAtLeast(3)

  val genMapDynamicValueDepth3: Gen[Any, DynamicValue] = genMapDynamicValueAtLeast(3)

  /**
   * Local deep-value generator. Unlike the repo-wide default
   * `DynamicValueGen.genDynamicValue`, every sample here reaches a structural
   * depth of at least 3 so the law suite can prove nested-shape coverage.
   */
  val genDynamicValueDepth3: Gen[Any, DynamicValue] = Gen.oneOf(
    genRecordDynamicValueDepth3,
    genVariantDynamicValueDepth3,
    genSequenceDynamicValueDepth3,
    genMapDynamicValueDepth3
  )

  def dynamicValueDepth(value: DynamicValue): Int = value match {
    case _: DynamicValue.Primitive => 0
    case DynamicValue.Null         => 0
    case DynamicValue.Record(fields) =>
      if (fields.isEmpty) 1
      else 1 + fields.iterator.map(_._2).map(dynamicValueDepth).max
    case DynamicValue.Variant(_, inner) =>
      1 + dynamicValueDepth(inner)
    case DynamicValue.Sequence(elements) =>
      if (elements.isEmpty) 1
      else 1 + elements.iterator.map(dynamicValueDepth).max
    case DynamicValue.Map(entries) =>
      if (entries.isEmpty) 1
      else
        1 + entries.iterator
          .flatMap { case (key, value) => Iterator(key, value) }
          .map(dynamicValueDepth)
          .max
  }

  private def containsShape(value: DynamicValue, predicate: DynamicValue => Boolean): Boolean =
    predicate(value) || (value match {
      case DynamicValue.Record(fields) =>
        fields.iterator.exists { case (_, inner) => containsShape(inner, predicate) }
      case DynamicValue.Variant(_, inner) =>
        containsShape(inner, predicate)
      case DynamicValue.Sequence(elements) =>
        elements.iterator.exists(containsShape(_, predicate))
      case DynamicValue.Map(entries) =>
        entries.iterator.exists { case (key, inner) =>
          containsShape(key, predicate) || containsShape(inner, predicate)
        }
      case _: DynamicValue.Primitive =>
        false
      case DynamicValue.Null =>
        false
    })

  def containsRecord(value: DynamicValue): Boolean =
    containsShape(value, _.isInstanceOf[DynamicValue.Record])

  def containsVariant(value: DynamicValue): Boolean =
    containsShape(value, _.isInstanceOf[DynamicValue.Variant])

  def containsSequence(value: DynamicValue): Boolean =
    containsShape(value, _.isInstanceOf[DynamicValue.Sequence])

  def containsMap(value: DynamicValue): Boolean =
    containsShape(value, _.isInstanceOf[DynamicValue.Map])

  // -- Reversible witness catalog --------------------------------------------

  private def literalDynamicValueExpr(value: DynamicValue): SchemaExpr[_, _] =
    SchemaExpr.Literal[Any, DynamicValue](value, Schema[DynamicValue])

  private val reversiblePaths: Chunk[DynamicOptic] =
    Chunk(DynamicOptic.root.field("left"), DynamicOptic.root.field("right"))

  private val genReversibleRenameWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    for {
      payload <- genDynamicValueDepth3
      stable  <- genDynamicValueAtMost(1)
    } yield {
      val input = DynamicValue.Record(
        Chunk(
          "orig"   -> payload,
          "stable" -> stable
        )
      )
      input -> new DynamicMigration(
        Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("orig"), "renamed"))
      )
    }

  private val genReversibleRenameCaseWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    genDynamicValueDepth3.map { payload =>
      DynamicValue.Variant("Before", payload) ->
        new DynamicMigration(
          Chunk.single(MigrationAction.RenameCase(DynamicOptic.root.caseOf("Before"), "Before", "After"))
        )
    }

  private val genReversibleOptionalizeWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    genDynamicValueDepth3.map { value =>
      value -> new DynamicMigration(
        Chunk.single(MigrationAction.Optionalize(DynamicOptic.root, SchemaRepr.Primitive("string")))
      )
    }

  private val genReversibleMandateWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    genDynamicValueDepth3.map { value =>
      DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> value))) ->
        new DynamicMigration(
          Chunk.single(
            MigrationAction.Mandate(
              DynamicOptic.root,
              SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
            )
          )
        )
    }

  private val genReversibleTransformValueWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    genDynamicValueDepth3.map { value =>
      value -> new DynamicMigration(
        Chunk.single(MigrationAction.TransformValue(DynamicOptic.root, literalDynamicValueExpr(value)))
      )
    }

  private val genReversibleChangeTypeWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    genDynamicValueDepth3.map { value =>
      value -> new DynamicMigration(
        Chunk.single(MigrationAction.ChangeType(DynamicOptic.root, literalDynamicValueExpr(value)))
      )
    }

  private val genReversibleJoinWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    for {
      left  <- genDynamicValueDepth3
      right <- genDynamicValueDepth3
    } yield {
      val input = DynamicValue.Record(
        Chunk(
          "left"  -> left,
          "right" -> right
        )
      )
      input -> new DynamicMigration(
        Chunk.single(MigrationAction.Join(DynamicOptic.root, reversiblePaths, literalDynamicValueExpr(input)))
      )
    }

  private val genReversibleSplitWitness: Gen[Any, (DynamicValue, DynamicMigration)] =
    for {
      left  <- genDynamicValueDepth3
      right <- genDynamicValueDepth3
    } yield {
      val input = DynamicValue.Record(
        Chunk(
          "left"  -> left,
          "right" -> right
        )
      )
      input -> new DynamicMigration(
        Chunk.single(MigrationAction.Split(DynamicOptic.root, reversiblePaths, literalDynamicValueExpr(input)))
      )
    }

  /**
   * Witness-backed reversible subset for .
   *
   * The generator catalog is explicit and deliberately small: Rename,
   * RenameCase, Optionalize, Mandate, root-pinned TransformValue / ChangeType,
   * and identity-shaped Join / Split witnesses. No arbitrary lossy transforms,
   * free-form literal rewrites, or path-sensitive actions are admitted.
   */
  val genReversibleMigrationWitness: Gen[Any, (DynamicValue, DynamicMigration)] = Gen.oneOf(
    genReversibleRenameWitness,
    genReversibleRenameCaseWitness,
    genReversibleOptionalizeWitness,
    genReversibleMandateWitness,
    genReversibleTransformValueWitness,
    genReversibleChangeTypeWitness,
    genReversibleJoinWitness,
    genReversibleSplitWitness
  )

  val genReversibleMigration: Gen[Any, DynamicMigration] =
    genReversibleMigrationWitness.map(_._2)

  // -- Named fixtures --------------------------------------------------

  /** Fixture 1: a straightforward rename + add-field flow. */
  val simpleRenameAndAdd: DynamicMigration = new DynamicMigration(
    Chunk(
      MigrationAction.Rename(DynamicOptic.root.field("orig"), "renamed"),
      MigrationAction.AddField(
        DynamicOptic.root,
        "added",
        SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))
      )
    )
  )

  /**
   * Fixture 2: a nested TransformCase at depth 2 — outer TransformCase wraps
   * an inner TransformCase that wraps a concrete Rename. Diagnoses
   * recursive-Reflect.Deferred round-trip regressions.
   */
  val nestedTransformCase: DynamicMigration = new DynamicMigration(
    Chunk.single(
      MigrationAction.TransformCase(
        DynamicOptic.root.caseOf("Outer"),
        Chunk(
          MigrationAction.TransformCase(
            DynamicOptic.root.caseOf("Inner"),
            Chunk.single(
              MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
            )
          )
        )
      )
    )
  )

  /**
   * Fixture 3: Join + Split with a 3-path vector and a non-trivial combiner /
   * splitter expression. Diagnoses `Chunk[DynamicOptic]` round-trip
   * regressions and SchemaExpr-field serialization for both multi-path
   * actions.
   */
  val joinSplit3Paths: DynamicMigration = {
    val paths = Chunk(
      DynamicOptic.root.field("first"),
      DynamicOptic.root.field("middle"),
      DynamicOptic.root.field("last")
    )
    new DynamicMigration(
      Chunk(
        MigrationAction.Join(
          DynamicOptic.root.field("joined"),
          paths,
          SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
        ),
        MigrationAction.Split(
          DynamicOptic.root.field("joined"),
          paths,
          SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
        )
      )
    )
  }

  /**
   * Fixture 4: a StringConcat combiner inside a TransformValue action — proves
   * the recursive `StringConcat` bridge through `Reflect.Deferred` round-trips.
   */
  val stringConcatTransform: DynamicMigration = new DynamicMigration(
    Chunk.single(
      MigrationAction.TransformValue(
        DynamicOptic.root.field("concatenated"),
        SchemaExpr.StringConcat[Any](
          SchemaExpr
            .DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
            .asInstanceOf[SchemaExpr[Any, String]],
          SchemaExpr
            .DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
            .asInstanceOf[SchemaExpr[Any, String]]
        )
      )
    )
  )

  /** Fixture 5: a Literal transform in its canonical (post-roundtrip) form. */
  val literalTransform: DynamicMigration = new DynamicMigration(
    Chunk.single(
      MigrationAction.TransformValue(
        DynamicOptic.root,
        SchemaExpr.Literal[Any, DynamicValue](
          DynamicValue.Primitive(PrimitiveValue.Int(42)),
          Schema[DynamicValue]
        )
      )
    )
  )

  /**
   * Curated named-fixture catalog used by both the shared JSON round-trip
   * spec and downstream codec-module smoke tests.
   */
  val namedFixtures: List[(String, DynamicMigration)] = List(
    "simpleRenameAndAdd"    -> simpleRenameAndAdd,
    "nestedTransformCase"   -> nestedTransformCase,
    "joinSplit3Paths"       -> joinSplit3Paths,
    "stringConcatTransform" -> stringConcatTransform,
    "literalTransform"      -> literalTransform
  )
}
