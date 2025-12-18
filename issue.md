Schema Migration System for ZIO Schema 2

Overview

Implement a pure, algebraic migration system for ZIO Schema 2 that represents structural transformations between schema versions as first-class, serializable data.

A migration describes how to transform data from one schema version to another, enabling:

schema evolution
backward / forward compatibility
data versioning
offline migrations (JSON, SQL, data lakes, registries, etc.)
The system provides a typed, macro-validated user API (Migration[A, B]) built on a pure, serializable core (DynamicMigration) that operates on DynamicValue.

The ADT is fully introspectable and can be used to generate DDL, etc.

Motivation & Big Picture

Why structural types?

When evolving schemas over time, older versions of data types should not require runtime representations.

In this design:

Current versions are represented by real case classes / enums

Past versions are represented using:

structural types for records
abstract types + intersection types for sum types
These types:

exist only at compile time
have no runtime representation
introduce zero runtime overhead
do not require optics or instances to be kept around
This allows you to describe arbitrarily old versions of data without polluting your runtime or codebase.

Typical Workflow

A typical workflow looks like:

You have a current type:

@schema
case class Person(name: String, age: Int)
You derive and copy its structural shape:

type PersonV1 = { val name: String; val age: Int }
You evolve the real type:

@schema
case class Person(fullName: String, age: Int, country: String)
You keep only:

the current runtime type
the structural type for the old version
a pure migration between them
No old case classes. No old optics. No runtime baggage.

Why pure data migrations?

Migrations are represented entirely as pure data:

no user functions
no closures
no reflection
no runtime code generation
As a result:

migrations can be serialized

stored in registries

applied dynamically

inspected and transformed

used to generate:

upgraders
downgraders
SQL DDL / DML
offline data transforms
While code generation is out of scope for this ticket, this explains many design decisions (invertibility, path-based actions, no functions).

Core Architecture

Type Hierarchy

// Typed migration (user-facing API)
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
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
 Migration[A, B] is introspectable, but not pure data due to bindings inside schemas
DynamicMigration is fully serializable
User-Facing API: Selector Expressions

Selectors, not optics

The user-facing API does not expose optics.

Instead, all locations are specified using selector expressions:

S => A
Examples:

_.name
_.address.street
_.addresses.each.streetNumber
_.country.when[UK]
To see the syntax, one can look at the optic macro, which utilizes the same selector syntax for optic creation (e.g. optic(_.address.street), etc.).

Macro extraction

All builder methods that accept selectors are implemented via macros (or via a macro-generated type class such as ToDynamicOptic).

The macro:

Inspects the selector expression
Validates it is a supported projection
Converts it into a DynamicOptic
Stores that optic in the migration action
Supported projections include:

field access (_.foo.bar)
case selection (_.country.when[UK])
collection traversal (_.items.each)
(future) key access, wrappers, etc.
DynamicOptic is never exposed publicly.

Migration Builder

All selector-accepting methods are implemented via macros. For simplicity, these are shown as functions (e.g. A => Any), but this is NOT the way to implement them. Either all these functions need to be macros, or a macro needs to be used to generate an implicit / given at each call site. Macros may do additional validation to constrain the validity of these different types of transformations.

class MigrationBuilder[A, B](
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
Migration Actions (Untyped Core)

All actions operate at a path, represented by DynamicOptic.

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}
Record Actions

case class AddField(
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
Enum Actions (Supported)

case class RenameCase(
  at: DynamicOptic,
  from: String,
  to: String
) extends MigrationAction

case class TransformCase(
  at: DynamicOptic,
  actions: Vector[MigrationAction]
) extends MigrationAction
Enum case addition / removal is out of scope for this ticket
(requires composite value construction).
Collection / Map Actions

case class TransformElements(
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
SchemaExpr Integration

Used for all value-level transformations

Constraints for this ticket:

primitive → primitive only
joins / splits must produce primitives
no record / enum construction
SchemaExpr.DefaultValue

A special expression that:

uses the macro-captured field schema
calls schema.defaultValue
converts the value to DynamicValue
is stored for reverse migrations
Type Modeling

Records (Structural Types)

type PersonV0 = { val firstName: String; val lastName: String }
type PersonV1 = { val fullName: String; val age: Int }

implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]
Enums (Abstract + Intersection Types)

type OldPaymentMethod
type OldCreditCard =
  { val number: String; val exp: String } & OldPaymentMethod
type OldWireTransfer =
  { val account: String; val routing: String } & OldPaymentMethod
Macros extract:

abstract type → sum identifier
intersection type → case tag
Laws

Identity

Migration.identity[A].apply(a) == Right(a)
Associativity

(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
Structural Reverse

m.reverse.reverse == m
Best-Effort Semantic Inverse

m.apply(a) == Right(b) ⇒ m.reverse.apply(b) == Right(a)
(when sufficient information exists)

Error Handling

All runtime errors return MigrationError
Errors must capture path information (DynamicOptic)
Enables diagnostics such as:
“Failed to apply TransformValue at .addresses.each.streetNumber”
Example

type PersonV0 = { val firstName: String; val lastName: String }

@schema
case class Person(fullName: String, age: Int)

val migration =
  Migration.newBuilder[PersonV0, Person]
    .addField(_.age, 0)
    .build

val old =
  new { val firstName = "John"; val lastName = "Doe" }

migration(old)
// Right(Person("John Doe", 0))
Success Criteria


DynamicMigration fully serializable

Migration[A, B] wraps schemas and actions

All actions path-based via DynamicOptic

User API uses selector functions (S => A) for "optics" on old and new types

Macro validation in .build to confirm "old" has been migrated to "new"

.buildPartial supported

Structural reverse implemented

Identity & associativity laws hold

Enum rename / transform supported

Errors include path information

Comprehensive tests

Scala 2.13 and Scala 3.5+ supported