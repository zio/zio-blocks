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

package zio.blocks.schema

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.JsonCodecDeriver
import zio.blocks.schema.migration._

/**
 * JMH benchmark suite for [[DynamicMigration]] hot paths.
 *
 * Scenario coverage:
 *   - 14 single-action `apply()` benchmarks (one per [[MigrationAction]]
 *     variant).
 *   - One realistic 5-action composed `apply()`.
 *   - One 10-action structural `reverse`.
 *   - One 10-action JSON round-trip (encode + decode).
 *   - One no-op `apply()` as allocation-discipline evidence for the hot path.
 *
 * All fixtures are prebuilt in [[setup]]; benchmark methods contain only the
 * single hot-path call plus minimal `Either`/decode result handling.
 */
class DynamicMigrationBenchmark extends BaseBenchmark {

  // -----------------------------------------------------------------------
  // Fixture fields – initialised once in @Setup
  // -----------------------------------------------------------------------

  // 14 single-action fixtures: migration + representative input DynamicValue

  var addFieldMigration: DynamicMigration = scala.compiletime.uninitialized
  var addFieldInput: DynamicValue         = scala.compiletime.uninitialized

  var dropFieldMigration: DynamicMigration = scala.compiletime.uninitialized
  var dropFieldInput: DynamicValue         = scala.compiletime.uninitialized

  var renameMigration: DynamicMigration = scala.compiletime.uninitialized
  var renameInput: DynamicValue         = scala.compiletime.uninitialized

  var transformValueMigration: DynamicMigration = scala.compiletime.uninitialized
  var transformValueInput: DynamicValue         = scala.compiletime.uninitialized

  var changeTypeMigration: DynamicMigration = scala.compiletime.uninitialized
  var changeTypeInput: DynamicValue         = scala.compiletime.uninitialized

  var mandateMigration: DynamicMigration = scala.compiletime.uninitialized
  var mandateInput: DynamicValue         = scala.compiletime.uninitialized

  var optionalizeMigration: DynamicMigration = scala.compiletime.uninitialized
  var optionalizeInput: DynamicValue         = scala.compiletime.uninitialized

  var renameCaseMigration: DynamicMigration = scala.compiletime.uninitialized
  var renameCaseInput: DynamicValue         = scala.compiletime.uninitialized

  var transformCaseMigration: DynamicMigration = scala.compiletime.uninitialized
  var transformCaseInput: DynamicValue         = scala.compiletime.uninitialized

  var transformElementsMigration: DynamicMigration = scala.compiletime.uninitialized
  var transformElementsInput: DynamicValue         = scala.compiletime.uninitialized

  var transformKeysMigration: DynamicMigration = scala.compiletime.uninitialized
  var transformKeysInput: DynamicValue         = scala.compiletime.uninitialized

  var transformValuesMigration: DynamicMigration = scala.compiletime.uninitialized
  var transformValuesInput: DynamicValue         = scala.compiletime.uninitialized

  var joinMigration: DynamicMigration = scala.compiletime.uninitialized
  var joinInput: DynamicValue         = scala.compiletime.uninitialized

  var splitMigration: DynamicMigration = scala.compiletime.uninitialized
  var splitInput: DynamicValue         = scala.compiletime.uninitialized

  // Composed 5-action apply fixture
  var composed5Migration: DynamicMigration = scala.compiletime.uninitialized
  var composed5Input: DynamicValue         = scala.compiletime.uninitialized

  // Structural reverse fixture: 10-action migration
  var reverse10Migration: DynamicMigration = scala.compiletime.uninitialized

  // JSON round-trip fixture: 10-action migration + prebuilt codec + encoded payload
  var jsonRoundTrip10Migration: DynamicMigration                            = scala.compiletime.uninitialized
  var encoded: String                                                       = scala.compiletime.uninitialized
  private var jsonCodec: zio.blocks.schema.json.JsonCodec[DynamicMigration] = scala.compiletime.uninitialized

  // No-op fixture for hot-path discipline allocation-discipline evidence
  var noopMigration: DynamicMigration = scala.compiletime.uninitialized
  var noopInput: DynamicValue         = scala.compiletime.uninitialized

  // -----------------------------------------------------------------------
  // @Setup – precompute all fixtures once
  // -----------------------------------------------------------------------

  @Setup
  def setup(): Unit = {
    // Shared expression helpers (pure data, no closures)
    val intRepr    = SchemaRepr.Primitive("int")
    val strRepr    = SchemaRepr.Primitive("string")
    val defaultInt = SchemaExpr.DefaultValue(DynamicOptic.root, intRepr)
    val litInt42   = SchemaExpr.Literal[Any, DynamicValue](
      DynamicValue.Primitive(PrimitiveValue.Int(42)),
      Schema[DynamicValue]
    )

    // A simple nested record used as a representative DynamicValue for record actions.
    //   { "orig": 1, "extra": "hello" }
    val simpleRecord = DynamicValue.Record(
      Chunk(
        "orig"  -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        "extra" -> DynamicValue.Primitive(PrimitiveValue.String("hello"))
      )
    )

    // --- AddField ---
    // Input: { "orig": 1, "extra": "hello" }
    // Action: add field "added" with defaultInt
    addFieldMigration = new DynamicMigration(
      Chunk.single(MigrationAction.AddField(DynamicOptic.root, "added", defaultInt))
    )
    addFieldInput = simpleRecord

    // --- DropField ---
    // Input: { "orig": 1, "extra": "hello" }
    // Action: drop field "extra"
    dropFieldMigration = new DynamicMigration(
      Chunk.single(MigrationAction.DropField(DynamicOptic.root, "extra", defaultInt))
    )
    dropFieldInput = simpleRecord

    // --- Rename ---
    // Input: { "orig": 1, "extra": "hello" }
    // Action: rename field "orig" → "renamed"
    renameMigration = new DynamicMigration(
      Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("orig"), "renamed"))
    )
    renameInput = simpleRecord

    // --- TransformValue ---
    // Input: int 42 at root
    // Action: transform root value via literal(42) (identity on the hot path)
    transformValueMigration = new DynamicMigration(
      Chunk.single(MigrationAction.TransformValue(DynamicOptic.root, litInt42))
    )
    transformValueInput = DynamicValue.Primitive(PrimitiveValue.Int(42))

    // --- ChangeType ---
    // Input: int 42 at root
    // Action: change type of root via literal(42)
    changeTypeMigration = new DynamicMigration(
      Chunk.single(MigrationAction.ChangeType(DynamicOptic.root, litInt42))
    )
    changeTypeInput = DynamicValue.Primitive(PrimitiveValue.Int(42))

    // --- Mandate ---
    // Input: Some(42) as Variant("Some", Record(("value", 42)))
    // Action: mandate root with defaultInt (unwraps the Option)
    mandateMigration = new DynamicMigration(
      Chunk.single(MigrationAction.Mandate(DynamicOptic.root, defaultInt))
    )
    mandateInput = DynamicValue.Variant(
      "Some",
      DynamicValue.Record(Chunk.single("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
    )

    // --- Optionalize ---
    // Input: int 42 at root
    // Action: wrap in Some (Optionalize)
    optionalizeMigration = new DynamicMigration(
      Chunk.single(MigrationAction.Optionalize(DynamicOptic.root, intRepr))
    )
    optionalizeInput = DynamicValue.Primitive(PrimitiveValue.Int(42))

    // --- RenameCase ---
    // Input: Variant("Before", int 1)
    // Action: rename case "Before" → "After"
    renameCaseMigration = new DynamicMigration(
      Chunk.single(MigrationAction.RenameCase(DynamicOptic.root.caseOf("Before"), "Before", "After"))
    )
    renameCaseInput = DynamicValue.Variant("Before", DynamicValue.Primitive(PrimitiveValue.Int(1)))

    // --- TransformCase ---
    // Input: Variant("Target", { "x": 1 })
    // Action: for case "Target", rename field "x" → "y".
    // Inner actions operate on the full currentDv (the Variant), so the optic
    // for the inner Rename must navigate through the case node to reach the field.
    transformCaseMigration = new DynamicMigration(
      Chunk.single(
        MigrationAction.TransformCase(
          DynamicOptic.root.caseOf("Target"),
          Chunk.single(MigrationAction.Rename(DynamicOptic.root.caseOf("Target").field("x"), "y"))
        )
      )
    )
    transformCaseInput = DynamicValue.Variant(
      "Target",
      DynamicValue.Record(Chunk.single("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
    )

    // --- TransformElements ---
    // Input: Sequence of ints at root.
    // `at` is the optic that reaches the Sequence node itself (root), so the
    // interpreter does currentDv.get(root) → Sequence; then transforms elements.
    transformElementsMigration = new DynamicMigration(
      Chunk.single(MigrationAction.TransformElements(DynamicOptic.root, litInt42))
    )
    transformElementsInput = DynamicValue.Sequence(
      Chunk(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2)),
        DynamicValue.Primitive(PrimitiveValue.Int(3))
      )
    )

    // --- TransformKeys ---
    // Input: Map at root.
    // `at` is the optic that reaches the Map node itself (root).
    val litStr = SchemaExpr.Literal[Any, DynamicValue](
      DynamicValue.Primitive(PrimitiveValue.String("k")),
      Schema[DynamicValue]
    )
    transformKeysMigration = new DynamicMigration(
      Chunk.single(MigrationAction.TransformKeys(DynamicOptic.root, litStr))
    )
    transformKeysInput = DynamicValue.Map(
      Chunk.single(
        DynamicValue.Primitive(PrimitiveValue.String("key")) ->
          DynamicValue.Primitive(PrimitiveValue.Int(99))
      )
    )

    // --- TransformValues ---
    // Input: Map at root.
    // `at` reaches the Map node itself (root); interpreter extracts Map entries.
    transformValuesMigration = new DynamicMigration(
      Chunk.single(MigrationAction.TransformValues(DynamicOptic.root, litInt42))
    )
    transformValuesInput = DynamicValue.Map(
      Chunk.single(
        DynamicValue.Primitive(PrimitiveValue.String("k")) ->
          DynamicValue.Primitive(PrimitiveValue.Int(1))
      )
    )

    // --- Join ---
    // Input: Record with "left", "right", and "joined" fields.
    // Join evaluates combiner (litInt42) against root → Primitive(42); then
    // setOrFail sets "joined" to that value. The "joined" field must already
    // exist in the record so setOrFail can navigate and overwrite it.
    val joinPaths = Chunk(
      DynamicOptic.root.field("left"),
      DynamicOptic.root.field("right")
    )
    joinMigration = new DynamicMigration(
      Chunk.single(
        MigrationAction.Join(
          DynamicOptic.root.field("joined"),
          joinPaths,
          litInt42
        )
      )
    )
    joinInput = DynamicValue.Record(
      Chunk(
        "left"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        "right"  -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
        "joined" -> DynamicValue.Primitive(PrimitiveValue.Int(0))
      )
    )

    // --- Split ---
    // Input: Record with "joined", "left", and "right" fields.
    // Split evaluates splitter against root; splitter must return a Record
    // whose fields count == targetPaths.length. We use a literal Record
    // combiner that matches the 2 targetPaths exactly.
    val splitCombiner = SchemaExpr.Literal[Any, DynamicValue](
      DynamicValue.Record(
        Chunk(
          "p0" -> DynamicValue.Primitive(PrimitiveValue.Int(10)),
          "p1" -> DynamicValue.Primitive(PrimitiveValue.Int(20))
        )
      ),
      Schema[DynamicValue]
    )
    splitMigration = new DynamicMigration(
      Chunk.single(
        MigrationAction.Split(
          DynamicOptic.root.field("joined"),
          joinPaths,
          splitCombiner
        )
      )
    )
    splitInput = DynamicValue.Record(
      Chunk(
        "joined" -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
        "left"   -> DynamicValue.Primitive(PrimitiveValue.Int(0)),
        "right"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
      )
    )

    // --- Composed 5-action apply: realistic nested-record migration ---
    // Uses 5 actions that all succeed on the same record-shaped input:
    //   Rename("orig" → "renamed") ++ AddField("added") ++ DropField("extra") ++
    //   Optionalize("status") ++ TransformValue("count")
    composed5Migration = new DynamicMigration(
      Chunk(
        MigrationAction.Rename(DynamicOptic.root.field("orig"), "renamed"),
        MigrationAction.AddField(DynamicOptic.root, "added", defaultInt),
        MigrationAction.DropField(DynamicOptic.root, "extra", defaultInt),
        MigrationAction.Optionalize(DynamicOptic.root.field("status"), strRepr),
        MigrationAction.TransformValue(DynamicOptic.root.field("count"), litInt42)
      )
    )
    // Input record with all fields referenced by the 5 actions
    composed5Input = DynamicValue.Record(
      Chunk(
        "orig"   -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        "extra"  -> DynamicValue.Primitive(PrimitiveValue.String("drop-me")),
        "status" -> DynamicValue.Primitive(PrimitiveValue.String("active")),
        "count"  -> DynamicValue.Primitive(PrimitiveValue.Int(7))
      )
    )

    // --- Structural reverse: 10-action migration ---
    // Build a 10-action migration; reverse() is the measured call.
    reverse10Migration = new DynamicMigration(
      Chunk(
        MigrationAction.Rename(DynamicOptic.root.field("f0"), "r0"),
        MigrationAction.Rename(DynamicOptic.root.field("f1"), "r1"),
        MigrationAction.AddField(DynamicOptic.root, "a2", defaultInt),
        MigrationAction.DropField(DynamicOptic.root, "d3", defaultInt),
        MigrationAction.Optionalize(DynamicOptic.root.field("f4"), intRepr),
        MigrationAction.Mandate(DynamicOptic.root.field("f5"), defaultInt),
        MigrationAction.RenameCase(DynamicOptic.root.caseOf("C6"), "C6", "C6New"),
        MigrationAction.TransformValue(DynamicOptic.root.field("f7"), litInt42),
        MigrationAction.ChangeType(DynamicOptic.root.field("f8"), litInt42),
        MigrationAction.TransformValue(DynamicOptic.root.field("f9"), litInt42)
      )
    )

    // --- JSON round-trip: 10-action migration ---
    // Derive codec once; precompute the encoded form so the benchmark measures
    // only encode + decode (not codec derivation).
    jsonRoundTrip10Migration = new DynamicMigration(
      Chunk(
        MigrationAction.Rename(DynamicOptic.root.field("g0"), "h0"),
        MigrationAction.AddField(DynamicOptic.root, "added1", defaultInt),
        MigrationAction.DropField(DynamicOptic.root, "dropped2", defaultInt),
        MigrationAction.Optionalize(DynamicOptic.root.field("g3"), strRepr),
        MigrationAction.Mandate(DynamicOptic.root.field("g4"), defaultInt),
        MigrationAction.RenameCase(DynamicOptic.root.caseOf("D5"), "D5", "D5New"),
        MigrationAction.TransformCase(
          DynamicOptic.root.caseOf("D6"),
          Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("x"), "y"))
        ),
        MigrationAction.TransformValue(DynamicOptic.root.field("g7"), litInt42),
        MigrationAction.ChangeType(DynamicOptic.root.field("g8"), litInt42),
        MigrationAction.TransformValue(DynamicOptic.root.field("g9"), litInt42)
      )
    )
    jsonCodec = Schema[DynamicMigration].deriving(JsonCodecDeriver).derive
    encoded = jsonCodec.encodeToString(jsonRoundTrip10Migration)

    // --- No-op: empty migration ---
    noopMigration = DynamicMigration.empty
    noopInput = DynamicValue.Primitive(PrimitiveValue.Int(0))
  }

  // -----------------------------------------------------------------------
  // @Benchmark methods – 14 single-action apply paths
  // -----------------------------------------------------------------------

  @Benchmark
  def addFieldApply(): Either[MigrationError, DynamicValue] =
    addFieldMigration(addFieldInput)

  @Benchmark
  def dropFieldApply(): Either[MigrationError, DynamicValue] =
    dropFieldMigration(dropFieldInput)

  @Benchmark
  def renameApply(): Either[MigrationError, DynamicValue] =
    renameMigration(renameInput)

  @Benchmark
  def transformValueApply(): Either[MigrationError, DynamicValue] =
    transformValueMigration(transformValueInput)

  @Benchmark
  def changeTypeApply(): Either[MigrationError, DynamicValue] =
    changeTypeMigration(changeTypeInput)

  @Benchmark
  def mandateApply(): Either[MigrationError, DynamicValue] =
    mandateMigration(mandateInput)

  @Benchmark
  def optionalizeApply(): Either[MigrationError, DynamicValue] =
    optionalizeMigration(optionalizeInput)

  @Benchmark
  def renameCaseApply(): Either[MigrationError, DynamicValue] =
    renameCaseMigration(renameCaseInput)

  @Benchmark
  def transformCaseApply(): Either[MigrationError, DynamicValue] =
    transformCaseMigration(transformCaseInput)

  @Benchmark
  def transformElementsApply(): Either[MigrationError, DynamicValue] =
    transformElementsMigration(transformElementsInput)

  @Benchmark
  def transformKeysApply(): Either[MigrationError, DynamicValue] =
    transformKeysMigration(transformKeysInput)

  @Benchmark
  def transformValuesApply(): Either[MigrationError, DynamicValue] =
    transformValuesMigration(transformValuesInput)

  @Benchmark
  def joinApply(): Either[MigrationError, DynamicValue] =
    joinMigration(joinInput)

  @Benchmark
  def splitApply(): Either[MigrationError, DynamicValue] =
    splitMigration(splitInput)

  // -----------------------------------------------------------------------
  // @Benchmark methods – composite scenarios
  // -----------------------------------------------------------------------

  /** Realistic 5-action composed apply. */
  @Benchmark
  def composedApply(): Either[MigrationError, DynamicValue] =
    composed5Migration(composed5Input)

  /** Structural reverse of a 10-action migration. */
  @Benchmark
  def structuralReverse10Action(): DynamicMigration =
    reverse10Migration.reverse

  /** Full JSON encode + decode round-trip for a 10-action migration. */
  @Benchmark
  def jsonRoundTrip10Action(): Either[SchemaError, DynamicMigration] =
    jsonCodec.decode(jsonCodec.encodeToString(jsonRoundTrip10Migration))

  /** No-op apply on an empty migration. */
  @Benchmark
  def noopApply(): Either[MigrationError, DynamicValue] =
    noopMigration(noopInput)
}
