package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration._

object Scala2BuilderSpec extends ZIOSpecDefault {
  case class Person(firstName: String, lastName: String)
  case class User(fullName: String, age: Int)

  implicit val personSchema: Schema[Person] = Schema.derived[Person]
  implicit val userSchema: Schema[User] = Schema.derived[User]

  def spec = suite("Scala 2.13 Builder Verification")(
    test("Migration building should work in Scala 2.13") {
      
      /**
       * সমাধান: MigrationBuilder-এর নতুন সিগনেচার অনুযায়ী সরাসরি 
       * ToDynamicOptic.derive কল করা হয়েছে একটি মাত্র প্যারামিটার ব্লকের ভেতরে।
       * এটি 'does not take parameters' এররটি সমাধান করবে।
       */
      val builder = MigrationBuilder.make[Person, User]
        .renameField(
          ToDynamicOptic.derive((p: Person) => p.firstName), 
          ToDynamicOptic.derive((u: User) => u.fullName)
        )
        .addField(
          ToDynamicOptic.derive((u: User) => u.age), 
          DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
        .build

      val oldPerson = Person("Al", "Mamun")
      val expected = User("Al", 25)
      
      // মাইগ্রেশন রান করা
      val result = builder.apply(oldPerson)

      /**
       * ওয়ার্নিং সমাধান: সব ভেরিয়েবল অ্যাসানশনে ব্যবহার করা হয়েছে 
       * যাতে 'never used' ওয়ার্নিং না আসে।
       */
      assertTrue(result == Right(expected) && oldPerson.firstName == "Al")
    }
  )
}