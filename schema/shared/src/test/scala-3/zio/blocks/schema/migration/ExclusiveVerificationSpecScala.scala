package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

// =================================================================================
// EXCLUSIVE VERSION 3 MODELS (Defined Globally)
// =================================================================================

case class Source_S3(id: Int)
object Source_S3 {
  implicit val schema: Schema[Source_S3] = null.asInstanceOf[Schema[Source_S3]]
}

case class Target_S3(id: Int, newField: String)
object Target_S3 {
  implicit val schema: Schema[Target_S3] = null.asInstanceOf[Schema[Target_S3]]
}

object ExclusiveVerificationSpecScala3 extends ZIOSpecDefault {

  def spec = suite("Exclusive Verification (Scala 3 Version)")(
    // ---------------------------------------------------------------------------
    // TEST 1: Identity Migration (Scala 3 Syntax Check)
    // ---------------------------------------------------------------------------
    test("1. [Scala 3] Identity Migration (A -> A) PASSES compilation") {
      assertZIO(
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.Source_S3
          
          // [FIX] Explicit Type Annotation added to prevent Recursive Value Error in Scala 3
          val migration: Migration[Source_S3, Source_S3] = 
            MigrationBuilder.make[Source_S3, Source_S3].build
        """
        )
      )(isRight(anything))
    },

    // ---------------------------------------------------------------------------
    // TEST 2: Compile-Time Safety (Strict Failure)
    // ---------------------------------------------------------------------------
    test("2. [Scala 3] Empty Migration (A -> B) FAILS compilation (Safety Check)") {
      assertZIO(
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.migration._
          
          case class A3(id: Int)
          case class B3(id: Int, extra: String)
          
          given Schema[A3] = null.asInstanceOf[Schema[A3]]
          given Schema[B3] = null.asInstanceOf[Schema[B3]]
          
          // This MUST FAIL because 'extra' field is missing
          MigrationBuilder.make[A3, B3].build
        """
        )
      )(isLeft(anything))
    },

    // ---------------------------------------------------------------------------
    // TEST 3: Valid Migration with Actions PASSES compilation
    // ---------------------------------------------------------------------------
    test("3. [Scala 3] Valid Migration with Actions PASSES compilation") {
      assertZIO(
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.migration._
          
          case class A3(id: Int)
          case class B3(id: Int, extra: String)
          
          given Schema[A3] = null.asInstanceOf[Schema[A3]]
          given Schema[B3] = null.asInstanceOf[Schema[B3]]
          
          // Using Fully Qualified Name to avoid ambiguous import error
          val mockExpr: zio.blocks.schema.migration.SchemaExpr[_] = 
            null.asInstanceOf[zio.blocks.schema.migration.SchemaExpr[_]]
          
          // Valid migration should compile
          MigrationBuilder.make[A3, B3]
            .addField((b: B3) => b.extra, mockExpr)
            .build
        """
        )
      )(isRight(anything))
    }
  )
}
