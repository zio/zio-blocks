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
    test("Join merges multiple fields into one") {
      val firstNameExpr = SchemaExpr.DynamicOpticExpr(DynamicOptic.root.at(0))
      val lastNameExpr  = SchemaExpr.DynamicOpticExpr(DynamicOptic.root.at(1))
      val spaceExpr     = SchemaExpr.Literal[DynamicValue, String](" ", Schema.string)
      
      val customExpr = SchemaExpr.StringConcat(
        firstNameExpr.asInstanceOf[SchemaExpr[DynamicValue, String]],
        SchemaExpr.StringConcat(
          spaceExpr,
          lastNameExpr.asInstanceOf[SchemaExpr[DynamicValue, String]]
        )
      ).asInstanceOf[SchemaExpr[DynamicValue, DynamicValue]]

      val migration = MigrationBuilder[PersonV0, PersonV1]
        .join(
          _.fullName,
          customExpr,
          _.firstName,
          _.lastName
        )
        .dropField(_.firstName)
        .dropField(_.lastName)
        .build
        .toOption.get

      val v0 = PersonV0("John", "Doe")
      val result = migration(v0)
       
      assert(result)(isRight(equalTo(PersonV1("John Doe"))))
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
