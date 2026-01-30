package zio.blocks.schema.as.compile_errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that As.derived fails at compile time when default values are involved.
 *
 * Default values break the round-trip guarantee because:
 *   1. We can't distinguish between "explicitly set to default" and "missing
 *      field"
 *   2. A → B → A might not preserve the original value if B adds defaults
 *   3. Default values introduce ambiguity in the reverse direction
 */
object DefaultValueSpec extends ZIOSpecDefault {

  def spec = suite("DefaultValueSpec")(
    suite("Target Has Default Values")(
      test("fails when target has field with default value not in source") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ProductA(name: String, price: Double)
          case class ProductB(name: String, price: Double, taxable: Boolean = true)
          
          As.derived[ProductA, ProductB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default value") ||
              result.swap.getOrElse("").contains("taxable") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      },
      test("fails when target has multiple fields with default values") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class SettingsA(host: String)
          case class SettingsB(host: String, port: Int = 8080, ssl: Boolean = false)
          
          As.derived[SettingsA, SettingsB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      }
    ),
    suite("Source Has Default Values")(
      test("fails when source has field with default value not in target") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ConfigA(name: String, debug: Boolean = false)
          case class ConfigB(name: String)
          
          As.derived[ConfigA, ConfigB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default") ||
              result.swap.getOrElse("").contains("debug") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      },
      test("fails when source has multiple fields with default values not in target") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ConnectionA(host: String, port: Int = 3306, timeout: Int = 30)
          case class ConnectionB(host: String)
          
          As.derived[ConnectionA, ConnectionB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      }
    ),
    suite("Both Have Default Values")(
      test("fails when both types have different default value fields") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class EntryA(id: Long, active: Boolean = true)
          case class EntryB(id: Long, visible: Boolean = true)
          
          As.derived[EntryA, EntryB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      },
      test("fails when matching fields both have default values") {
        // Even when fields match by name, defaults break round-trip guarantee
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class CounterA(name: String, count: Int = 0)
          case class CounterB(name: String, count: Long = 0L)
          
          As.derived[CounterA, CounterB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default values")
          )
        )
      }
    ),
    suite("Nested Types with Default Values")(
      test("fails when nested type has default values") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class AddressA(street: String, zip: String = "00000")
          case class AddressB(street: String, zip: String)
          case class PersonA(name: String, address: AddressA)
          case class PersonB(name: String, address: AddressB)
          
          implicit val addressAs: As[AddressA, AddressB] = As.derived[AddressA, AddressB]
          As.derived[PersonA, PersonB]
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.swap.getOrElse("").contains("Cannot derive") ||
              result.swap.getOrElse("").contains("default") ||
              result.swap.getOrElse("").contains("no matching field")
          )
        )
      }
    ),
    suite("Valid Cases Without Default Value Issues")(
      test("succeeds when no default values are present") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ItemA(id: Long, name: String, price: Double)
          case class ItemB(id: Long, name: String, price: Double)
          
          As.derived[ItemA, ItemB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when both have matching fields with Option (not default)") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class ProfileA(name: String, bio: Option[String])
          case class ProfileB(name: String, bio: Option[String])
          
          As.derived[ProfileA, ProfileB]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when types differ but are bidirectionally convertible") {
        typeCheck {
          """
          import zio.blocks.schema.As
          
          case class MetricsA(count: Int, sum: Int)
          case class MetricsB(count: Long, sum: Long)
          
          As.derived[MetricsA, MetricsB]
          """
        }.map(result => assertTrue(result.isRight))
      }
    )
  )
}
