Schema Migration System for ZIO Schema 2 #519OpenLinked Issues: #882, #941jdegoes opened on Dec 18, 2025 · edited by jdegoesOverviewImplement a pure, algebraic migration system for ZIO Schema 2 that represents structural transformations between schema versions as first-class, serializable data.A migration describes how to transform data from one schema version to another, enabling:Schema evolutionBackward / forward compatibilityData versioningOffline migrations (JSON, SQL, data lakes, registries, etc.)The system provides a typed, macro-validated user API (Migration[A, B]) built on a pure, serializable core (DynamicMigration) that operates on DynamicValue.The ADT is fully introspectable and can be used to generate DDL, etc.Motivation & Big PictureWhy structural types?When evolving schemas over time, older versions of data types should not require runtime representations.In this design:Current versions are represented by real case classes / enumsPast versions are represented using:structural types for recordsabstract types + intersection types for sum typesThese types:exist only at compile timehave no runtime representationintroduce zero runtime overheaddo not require optics or instances to be kept aroundThis allows you to describe arbitrarily old versions of data without polluting your runtime or codebase.Typical WorkflowA typical workflow looks like:You have a current type:@schemacase class Person(name: String, age: Int)
You derive and copy its structural shape:type PersonV1 = { def name: String; def age: Int }
You evolve the real type:@schemacase class Person(fullName: String, age: Int, country: String)
You keep only:The current runtime typeThe structural type for the old versionA pure migration between themNo old case classes. No old optics. No runtime baggage.Note there is no requirement that the "current" type actually be a real case class, enum, etc.--so you can work purely with structural types, allowing you to define migrations for data types that are never materialized as runtime structures.Why pure data migrations?Migrations are represented entirely as pure data:no user functionsno closuresno reflectionno runtime code generationAs a result, migrations can be:serializedstored in registriesapplied dynamicallyinspected and transformedused to generate:upgradersdowngradersSQL DDL / DMLoffline data transformsWhile code generation is out of scope for this ticket, this explains many design decisions (invertibility, path-based actions, no functions).Core ArchitectureType Hierarchy// Typed migration (user-facing API)
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A], // These are structural schemas!!!
  targetSchema: Schema[B] // These are structural schemas!!!
) {
  /** Apply migration to transform A to B */
  def apply(value: A): Either[MigrationError, B]

  /** Compose migrations sequentially */
  def ++[C](that: Migration[B, C]): Migration[A, C]

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverse migration (structural inverse; runtime is best-effort) */
  def reverse: Migration[B, A]
}

// Untyped migration (pure data, fully serializable)
case class DynamicMigration(
  actions: Vector[MigrationAction]
) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def ++(that: DynamicMigration): DynamicMigration
  def reverse: DynamicMigration
}
Migration[A, B] is introspectable, but not pure data due to bindings inside schemas.DynamicMigration is fully serializable.User-Facing API: Selector ExpressionsSelectors, not opticsThe user-facing API does not expose optics. Instead, all locations are specified using selector expressions: S => A.Examples:_.name_.address.street_.addresses.each.streetNumber_.country.when[UK]To see the syntax, one can look at the optic macro, which utilizes the same selector syntax for optic creation (e.g. optic(_.address.street), etc.).Macro extractionAll builder methods that accept selectors are implemented via macros (or via a macro-generated type class such as ToDynamicOptic).The macro:Inspects the selector expressionValidates it is a supported projectionConverts it into a DynamicOpticStores that optic in the migration actionSupported projections include:field access (_.foo.bar)case selection (_.country.when[UK])collection traversal (_.items.each)(future) key access, wrappers, etc.DynamicOptic is never exposed publicly.Migration BuilderAll selector-accepting methods are implemented via macros. For simplicity, these are shown as functions (e.g. A => Any), but this is NOT the way to implement them. Either all these functions need to be macros, or a macro needs to be used to generate an implicit / given at each call site. Macros may do additional validation to constrain the validity of these different types of transformations.class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // ----- Record operations -----

  def addField(
    target: B => Any,
    default: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[B, ?] = SchemaExpr.DefaultValue
  ): MigrationBuilder[A, B]

  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B]

  def transformField(
    from: A => Any,
    to: B => Any,
    transform: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  def mandateField(
    source: A => Option[?],
    target: B => Any,
    default: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  def optionalizeField(
    source: A => Any,
    target: B => Option[?]
  ): MigrationBuilder[A, B]

  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: SchemaExpr[A, ?]  // primitive-to-primitive only
  ): MigrationBuilder[A, B]

  // ----- Enum operations (limited) -----

  def renameCase[SumA, SumB](
    from: String,
    to: String
  ): MigrationBuilder[A, B]

  def transformCase[SumA, CaseA, SumB, CaseB](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  ): MigrationBuilder[A, B]

  // ----- Collections -----

  def transformElements(
    at: A => Vector[?],
    transform: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  // ----- Maps -----

  def transformKeys(
    at: A => Map[?, ?],
    transform: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  def transformValues(
    at: A => Map[?, ?],
    transform: SchemaExpr[A, ?]
  ): MigrationBuilder[A, B]

  /** Build migration with full macro validation */
  def build: Migration[A, B]

  /** Build migration without full validation */
  def buildPartial: Migration[A, B]
}
Migration Actions (Untyped Core)All actions operate at a path, represented by DynamicOptic.sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}
Record Actionscase class AddField(
  at: DynamicOptic,
  default: SchemaExpr[?]
) extends MigrationAction

case class DropField(
  at: DynamicOptic,
  defaultForReverse: SchemaExpr[?]
) extends MigrationAction

case class Rename(
  at: DynamicOptic,
  to: String
) extends MigrationAction

case class TransformValue(
  at: DynamicOptic,
  transform: SchemaExpr[?]
) extends MigrationAction

case class Mandate(
  at: DynamicOptic,
  default: SchemaExpr[?]
) extends MigrationAction

case class Optionalize(
  at: DynamicOptic
) extends MigrationAction

case class Join(
  at: DynamicOptic,
  sourcePaths: Vector[DynamicOptic],
  combiner: SchemaExpr[?]
) extends MigrationAction

case class Split(
  at: DynamicOptic,
  targetPaths: Vector[DynamicOptic],
  splitter: SchemaExpr[?]
) extends MigrationAction

case class ChangeType(
  at: DynamicOptic,
  converter: SchemaExpr[?]
) extends MigrationAction
Enum Actions (Supported)case class RenameCase(
  at: DynamicOptic,
  from: String,
  to: String
) extends MigrationAction

case class TransformCase(
  at: DynamicOptic,
  actions: Vector[MigrationAction]
) extends MigrationAction
Enum case addition / removal is out of scope for this ticket (requires composite value construction).Collection / Map Actionscase class TransformElements(
  at: DynamicOptic,
  transform: SchemaExpr[?]
) extends MigrationAction

case class TransformKeys(
  at: DynamicOptic,
  transform: SchemaExpr[?]
) extends MigrationAction

case class TransformValues(
  at: DynamicOptic,
  transform: SchemaExpr[?]
) extends MigrationAction
SchemaExpr IntegrationUsed for all value-level transformations.Constraints for this ticket:primitive → primitive onlyjoins / splits must produce primitivesno record / enum constructionSchemaExpr.DefaultValueA special expression that:uses the macro-captured field schemacalls schema.defaultValueconverts the value to DynamicValueis stored for reverse migrationsType ModelingRecords (Structural Types)type PersonV0 = { val firstName: String; val lastName: String }
type PersonV1 = { val fullName: String; val age: Int }

implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]
Enums (Union of Structural Types with Tags)Enums are encoded into structural types by using union types, together with singleton types (string literals, which represent the name of the case of the enum).In structural types, the names of the type aliases shown below are not relevant, nor are they used.type OldCreditCard =
  { type Tag = "CreditCard"; def number: String; def exp: String }

type OldWireTransfer =
  { type Tag = "WireTransfer"; def account: String; def routing: String }

type OldPaymentMethod = OldCreditCard | OldWireTransfer
Macros extract:refinement type → structure of the casetype Tag with singleton type → case tagLawsIdentityMigration.identity[A].apply(a) == Right(a)
Associativity(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
Structural Reversem.reverse.reverse == m
Best-Effort Semantic Inversem.apply(a) == Right(b) ⇒ m.reverse.apply(b) == Right(a)
(when sufficient information exists)Error HandlingAll runtime errors return MigrationErrorErrors must capture path information (DynamicOptic)Enables diagnostics such as:"Failed to apply TransformValue at .addresses.each.streetNumber"Exampletype PersonV0 = { val firstName: String; val lastName: String }

@schemacase class Person(fullName: String, age: Int)

val migration =
  Migration.newBuilder[PersonV0, Person]
    .addField(_.age, 0)
    .build

val old =
  new { val firstName = "John"; val lastName = "Doe" }

migration(old)
// Right(Person("John Doe", 0))
Success Criteria[ ] DynamicMigration fully serializable[ ] Migration[A, B] wraps schemas and actions[ ] All actions path-based via DynamicOptic[ ] User API uses selector functions (S => A) for "optics" on old and new types[ ] Macro validation in .build to confirm "old" has been migrated to "new"[ ] .buildPartial supported[ ] Structural reverse implemented[ ] Identity & associativity laws hold[ ] Enum rename / transform supported[ ] Errors include path information[ ] Comprehensive tests[ ] Scala 2.13 and Scala 3.5+ supported