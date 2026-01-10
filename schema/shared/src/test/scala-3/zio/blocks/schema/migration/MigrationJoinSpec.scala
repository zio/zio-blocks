package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationJoinSpec extends ZIOSpecDefault {

  final case class PersonV0(firstName: String, lastName: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived[PersonV0]
  }

  final case class PersonV1(fullName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  def spec = suite("MigrationJoinSpec")(
    test("Join merges fields in specified order (verifying extracted values are used)") {
      // We explicitly join lastName THEN firstName.
      // If Join works correctly, it passes Sequence(lastName, firstName) to the expression.
      // So index 0 = lastName, index 1 = firstName.
      // We want to form "Doe, John".
      
      val firstArgExpr  = SchemaExpr.DynamicOpticExpr(DynamicOptic.root.at(0)) // Expects lastName
      val secondArgExpr = SchemaExpr.DynamicOpticExpr(DynamicOptic.root.at(1)) // Expects firstName
      val commaExpr     = SchemaExpr.Literal[DynamicValue, String](", ", Schema.string)
      
      val combinerExpr = SchemaExpr.StringConcat(
        firstArgExpr.asInstanceOf[SchemaExpr[DynamicValue, String]],
        SchemaExpr.StringConcat(
          commaExpr,
          secondArgExpr.asInstanceOf[SchemaExpr[DynamicValue, String]]
        )
      ).asInstanceOf[SchemaExpr[DynamicValue, DynamicValue]]

      // PersonV1 is single field, so we map to fullName.
      // PersonV0 is (firstName, lastName).
      
      val migration = MigrationBuilder[PersonV0, PersonV1]
        .join(
          _.fullName,
          combinerExpr,
          _.lastName,  // Source 1 (becomes index 0)
          _.firstName  // Source 2 (becomes index 1)
        )
        .dropField(_.firstName)
        .dropField(_.lastName)
        .build
        .toOption.get

      val v0 = PersonV0("John", "Doe")
      val result = migration(v0)
       
      // If Join uses extracted fields: "Doe, John"
      // If Join uses root record: "John, Doe" (because index 0 is first name in record)
      assert(result)(isRight(equalTo(PersonV1("Doe, John"))))
    },
    test("Best-Effort Reversibility: DropField -> AddField") {
       final case class UserV0(id: Int, name: String)
       object UserV0 {
         implicit val schema: Schema[UserV0] = Schema.derived[UserV0]
       }
       final case class UserV1(id: Int)
       object UserV1 {
         implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
       }
       
       // Use a default value for name in s0 to test reversibility
       val s0 = UserV0.schema.defaultValue(UserV0(1, "default"))
       val s1 = UserV1.schema
       
       val migration = MigrationBuilder[UserV0, UserV1](Vector.empty)(s0, s1)
         .dropField(_.name)
         .build
         .toOption.get
         
       val reverseMigration = migration.reverse.toOption.get
              val v1 = UserV1(123)
        val result = reverseMigration(v1)
         
        // Should result in UserV0(123, "default")
        assert(result)(isRight(equalTo(UserV0(123, "default"))))
    }
  )
}
