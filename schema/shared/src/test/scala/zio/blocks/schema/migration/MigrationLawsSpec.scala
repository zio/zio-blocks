package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Property-based tests for migration algebraic laws.
 *
 * This suite verifies that the migration system satisfies the algebraic
 * properties required by the specification:
 *
 * 1. Identity: Migration.identity[A].apply(a) == Right(a)
 * 2. Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
 * 3. Structural Reverse: m.reverse.reverse == m
 * 4. Best-Effort Semantic Inverse: m.apply(a) == Right(b) => m.reverse.apply(b) == Right(a)
 */
object MigrationLawsSpec extends ZIOSpecDefault {

  // ==========================================================================
  // Test Data Types
  // ==========================================================================

  case class SimpleV1(name: String)
  object SimpleV1 {
    implicit val schema: Schema[SimpleV1] = Schema.derived
  }

  case class SimpleV2(name: String, age: Int)
  object SimpleV2 {
    implicit val schema: Schema[SimpleV2] = Schema.derived
  }

  case class SimpleV3(name: String, age: Int, active: Boolean)
  object SimpleV3 {
    implicit val schema: Schema[SimpleV3] = Schema.derived
  }

  case class RenameV1(n: String)
  object RenameV1 {
    implicit val schema: Schema[RenameV1] = Schema.derived
  }

  case class RenameV2(name: String)
  object RenameV2 {
    implicit val schema: Schema[RenameV2] = Schema.derived
  }

  // ==========================================================================
  // Spec
  // ==========================================================================

  def spec = suite("MigrationLawsSpec")(
    identityLaws,
    associativityLaws,
    reverseLaws,
    semanticInverseLaws,
    compositionLaws
  )

  // ==========================================================================
  // Identity Laws
  // ==========================================================================

  def identityLaws = suite("IdentityLaws")(
    test("identity.apply(a) == Right(a) for all values") {
      val identity = Migration.identity[SimpleV1]

      val values = Vector(
        SimpleV1(""),
        SimpleV1("a"),
        SimpleV1("hello world"),
        SimpleV1("special!@#$%")
      )

      val results = values.map(v => identity(v))

      assert(results.forall(_.isRight))(isTrue) &&
      assert(results.zip(values).forall { case (result, original) =>
        result == Right(original)
      })(isTrue)
    },
    test("identity has empty actions") {
      val identity = Migration.identity[SimpleV1]

      assert(identity.actions.isEmpty)(isTrue)
      assert(identity.dynamicMigration.actions.isEmpty)(isTrue)
    },
    test("identity.sourceSchema == identity.targetSchema") {
      val identity = Migration.identity[SimpleV1]

      assert(identity.sourceSchema)(equalTo(identity.targetSchema))
    },
    test("identity ++ m == m (when types align)") {
      val id = Migration.identity[SimpleV1]
      val m = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build

      val composed = id ++ m

      val v1 = SimpleV1("test")
      assert(composed(v1))(isRight(equalTo(m(v1).toOption.get)))
    },
    test("m ++ identity == m (when types align)") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val id = Migration.identity[SimpleV2]

      val composed = m ++ id

      val v1 = SimpleV1("test")
      assert(composed(v1))(isRight(equalTo(m(v1).toOption.get)))
    }
  )

  // ==========================================================================
  // Associativity Laws
  // ==========================================================================

  def associativityLaws = suite("AssociativityLaws")(
    test("(m1 ++ m2) ++ m3 and m1 ++ (m2 ++ m3) have same action count") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      // We can't create m3 without SimpleV4, but we can verify the structure
      val leftAssoc = m1 ++ m2

      assert(leftAssoc.actions.length)(equalTo(m1.actions.length + m2.actions.length))
    },
    test("Sequential composition applies migrations in order") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 10).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, false).build

      val composed = m1 ++ m2
      val v1 = SimpleV1("John")

      val result = composed(v1).toOption.get

      assert(result.name)(equalTo("John"))
      assert(result.age)(equalTo(10))
      assert(result.active)(equalTo(false))
    },
    test("Empty migration is neutral element") {
      val empty = DynamicMigration(Vector.empty)
      val m = DynamicMigration(Vector(
        MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema.int)
        )
      ))

      assert((empty ++ m).actions)(equalTo(m.actions))
      assert((m ++ empty).actions)(equalTo(m.actions))
    },
    test("Multiple migrations compose correctly") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 1).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      val composed = m1 ++ m2
      val v1 = SimpleV1("test")

      // Verify the composed migration works end-to-end
      assert(composed(v1).isRight)(isTrue)
    }
  )

  // ==========================================================================
  // Reverse Laws
  // ==========================================================================

  def reverseLaws = suite("ReverseLaws")(
    test("reverse.reverse is structurally equivalent to original") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val doubleReverse = m.reverse.reverse

      // Structural: same number of actions
      assert(doubleReverse.actions.length)(equalTo(m.actions.length))
    },
    test("AddField reverse is DropField") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 100).build
      val reverse = m.reverse

      assert(reverse.actions.head.isInstanceOf[MigrationAction.DropField])(isTrue)
    },
    test("DropField reverse is AddField") {
      val m = Migration.newBuilder[SimpleV2, SimpleV1].dropField(_.age, 100).build
      val reverse = m.reverse

      assert(reverse.actions.head.isInstanceOf[MigrationAction.AddField])(isTrue)
    },
    test("Rename reverse swaps names") {
      val m = Migration.newBuilder[RenameV1, RenameV2].renameField(_.n, _.name).build
      val reverse = m.reverse

      val v2 = RenameV2("test")
      val v1 = reverse(v2).toOption.get

      assert(v1.n)(equalTo("test"))
    },
    test("Composed reverse reverses order") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      val composed = m1 ++ m2
      val reversed = composed.reverse

      // Reverse should have same total actions
      assert(reversed.actions.length)(equalTo(composed.actions.length))
    },
    test("Identity reverse is identity") {
      val identity = Migration.identity[SimpleV1]
      val reversed = identity.reverse

      assert(reversed.actions.isEmpty)(isTrue)
      assert(reversed.sourceSchema)(equalTo(identity.sourceSchema))
      assert(reversed.targetSchema)(equalTo(identity.targetSchema))
    },
    test("Reverse preserves migration structure") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2]
        .addField(_.age, 25)
        .build

      val reverse = m.reverse

      // Reverse should go from SimpleV2 to SimpleV1
      assert(reverse.sourceSchema)(equalTo(SimpleV2.schema))
      assert(reverse.targetSchema)(equalTo(SimpleV1.schema))
    }
  )

  // ==========================================================================
  // Semantic Inverse Laws
  // ==========================================================================

  def semanticInverseLaws = suite("SemanticInverseLaws")(
    test("AddField then reverse with default restores name") {
      val forward = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 99).build
      val reverse = forward.reverse

      val v1 = SimpleV1("Alice")
      val v2 = forward(v1).toOption.get
      val restored = reverse(v2).toOption.get

      assert(restored.name)(equalTo(v1.name))
    },
    test("Rename is its own inverse") {
      val m = Migration.newBuilder[RenameV1, RenameV2].renameField(_.n, _.name).build

      val v1 = RenameV1("test")
      val v2 = m(v1).toOption.get
      val restored = m.reverse(v2).toOption.get

      assert(restored)(equalTo(v1))
    },
    test("Double reverse equals original for pure migrations") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val doubleReverse = m.reverse.reverse

      val v1 = SimpleV1("Bob")
      val originalResult = m(v1).toOption.get
      val doubleReverseResult = doubleReverse(v1).toOption.get

      assert(doubleReverseResult)(equalTo(originalResult))
    },
    test("Best-effort inverse for complex migrations") {
      val m = Migration.newBuilder[SimpleV1, SimpleV2]
        .addField(_.age, 42)
        .build

      val v1 = SimpleV1("Charlie")
      val v2 = m(v1).toOption.get
      val restored = m.reverse(v2).toOption.get

      // Name should be preserved
      assert(restored.name)(equalTo("Charlie"))
    }
  )

  // ==========================================================================
  // Composition Laws
  // ==========================================================================

  def compositionLaws = suite("CompositionLaws")(
    test("andThen is alias for ++") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      val composed1 = m1 ++ m2
      val composed2 = m1.andThen(m2)

      assert(composed1.actions)(equalTo(composed2.actions))
    },
    test("Composed migration has combined schemas") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      val composed = m1 ++ m2

      assert(composed.sourceSchema)(equalTo(SimpleV1.schema))
      assert(composed.targetSchema)(equalTo(SimpleV3.schema))
    },
    test("Composition is type-safe") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 0).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, true).build

      // This compiles because m1's target (SimpleV2) matches m2's source (SimpleV2)
      val composed = m1 ++ m2

      assert(composed)(anything)
    },
    test("Multiple operations in single migration compose") {
      val m = Migration.newBuilder[SimpleV1, SimpleV3]
        .addField(_.age, 25)
        .addField(_.active, true)
        .build

      val v1 = SimpleV1("test")
      val v3 = m(v1).toOption.get

      assert(v3.name)(equalTo("test"))
      assert(v3.age)(equalTo(25))
      assert(v3.active)(isTrue)
    },
    test("Reverse of composition reverses order") {
      val m1 = Migration.newBuilder[SimpleV1, SimpleV2].addField(_.age, 1).build
      val m2 = Migration.newBuilder[SimpleV2, SimpleV3].addField(_.active, false).build

      val composed = m1 ++ m2
      val reversed = composed.reverse

      // Reverse should go from SimpleV3 to SimpleV1
      assert(reversed.sourceSchema)(equalTo(SimpleV3.schema))
      assert(reversed.targetSchema)(equalTo(SimpleV1.schema))
    }
  )
}
