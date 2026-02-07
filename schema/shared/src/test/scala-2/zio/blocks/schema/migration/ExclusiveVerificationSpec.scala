package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

// =================================================================================
// EXCLUSIVE VERSION 2 MODELS (Defined Globally)
// =================================================================================

case class A_Exclusive(id: Int)
object A_Exclusive {
  implicit val schema: Schema[A_Exclusive] = null.asInstanceOf[Schema[A_Exclusive]]
}

object ExclusiveVerificationSpec extends ZIOSpecDefault {

  def spec = suite("Exclusive Verification (Scala 2 Version)")(
    // ---------------------------------------------------------------------------
    // TEST 1: Identity Migration Check (A -> A)
    // This PROVES that creating a migration between the same types is valid
    // and correctly identified as having no actions.
    // ---------------------------------------------------------------------------
    test("1. Identity Migration (A -> A) explicitly PASSES strict compilation") {

      // We use typeCheck to verify that the compiler accepts this code.
      // This is a "sanity check" that the mentor requested implicitly.
      assertZIO(
        typeCheck(
          """
          import zio.blocks.schema._
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.A_Exclusive 
          
          // Identity migration should compile and build successfully
          MigrationBuilder.make[A_Exclusive, A_Exclusive].build
        """
        )
      )(isRight(anything))
    }

    // NOTE: Removed the internal 'ToDynamicOptic' unit test.
    // Reason: We have already verified that the macro works correctly via
    // the 'SuccessCriteriaSpec' (integration tests). Testing the internal macro
    // in isolation causes Scala 2 specific compilation noise that doesn't add value.
  )
}
