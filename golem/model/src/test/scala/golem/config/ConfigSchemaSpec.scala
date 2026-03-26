package golem.config

import golem.data.{DataType, DataValue, ElementSchema}
import zio.test._

object ConfigSchemaSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("ConfigSchemaSpec")(
      suite("primitive leaf instances")(
        test("String produces local declaration") {
          val decls = ConfigSchema[String].describe(List("key"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Local,
            decls.head.path == List("key"),
            decls.head.valueType == ElementSchema.Component(DataType.StringType)
          )
        },
        test("Int produces local declaration") {
          val decls = ConfigSchema[Int].describe(List("count"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Local,
            decls.head.path == List("count"),
            decls.head.valueType == ElementSchema.Component(DataType.IntType)
          )
        },
        test("Long produces local declaration") {
          val decls = ConfigSchema[Long].describe(List("id"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Local,
            decls.head.path == List("id"),
            decls.head.valueType == ElementSchema.Component(DataType.LongType)
          )
        },
        test("Double produces local declaration") {
          val decls = ConfigSchema[Double].describe(List("rate"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Local,
            decls.head.path == List("rate"),
            decls.head.valueType == ElementSchema.Component(DataType.DoubleType)
          )
        },
        test("Boolean produces local declaration") {
          val decls = ConfigSchema[Boolean].describe(List("enabled"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Local,
            decls.head.path == List("enabled"),
            decls.head.valueType == ElementSchema.Component(DataType.BoolType)
          )
        }
      ),
      suite("Secret instance")(
        test("Secret[String] produces secret declaration") {
          val decls = ConfigSchema[Secret[String]].describe(List("apiKey"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Secret,
            decls.head.path == List("apiKey"),
            decls.head.valueType == ElementSchema.Component(DataType.StringType)
          )
        },
        test("Secret[Int] produces secret declaration with int type") {
          val decls = ConfigSchema[Secret[Int]].describe(List("pin"))
          assertTrue(
            decls.size == 1,
            decls.head.source == AgentConfigSource.Secret,
            decls.head.path == List("pin"),
            decls.head.valueType == ElementSchema.Component(DataType.IntType)
          )
        }
      ),
      suite("path propagation")(
        test("empty path produces empty path in declaration") {
          val decls = ConfigSchema[String].describe(Nil)
          assertTrue(decls.head.path == Nil)
        },
        test("multi-segment path is preserved") {
          val decls = ConfigSchema[String].describe(List("a", "b", "c"))
          assertTrue(decls.head.path == List("a", "b", "c"))
        }
      ),
      suite("ConfigOverride (internal)")(
        test("String override has correct path and type") {
          val ov = ConfigOverride[String](List("key"), "hello")
          assertTrue(
            ov.path == List("key"),
            ov.value == DataValue.StringValue("hello"),
            ov.valueType == ElementSchema.Component(DataType.StringType)
          )
        },
        test("Int override has correct path and value") {
          val ov = ConfigOverride[Int](List("count"), 42)
          assertTrue(
            ov.path == List("count"),
            ov.value == DataValue.IntValue(42),
            ov.valueType == ElementSchema.Component(DataType.IntType)
          )
        },
        test("Boolean override encodes correctly") {
          val ov = ConfigOverride[Boolean](List("flag"), true)
          assertTrue(
            ov.value == DataValue.BoolValue(true),
            ov.valueType == ElementSchema.Component(DataType.BoolType)
          )
        }
      )
    )
}
