package golem.config

import golem.data.{DataType, ElementSchema}
import zio.test._

object ConfigSchemaDerivedSpec extends ZIOSpecDefault {

  final case class SimpleConfig(name: String, count: Int)
  object SimpleConfig {
    implicit val cs: ConfigSchema[SimpleConfig] = ConfigSchemaDerived.derived
  }

  final case class DbConfig(host: String, port: Int, password: Secret[String])
  object DbConfig {
    implicit val cs: ConfigSchema[DbConfig] = ConfigSchemaDerived.derived
  }

  final case class NestedConfig(appName: String, db: DbConfig)
  object NestedConfig {
    implicit val cs: ConfigSchema[NestedConfig] = ConfigSchemaDerived.derived
  }

  final case class AllLocalConfig(a: String, b: Int, c: Boolean, d: Long, e: Double)
  object AllLocalConfig {
    implicit val cs: ConfigSchema[AllLocalConfig] = ConfigSchemaDerived.derived
  }

  final case class MultiSecretConfig(token: Secret[String], key: Secret[String], name: String)
  object MultiSecretConfig {
    implicit val cs: ConfigSchema[MultiSecretConfig] = ConfigSchemaDerived.derived
  }

  final case class DeeplyNested(outer: NestedConfig)
  object DeeplyNested {
    implicit val cs: ConfigSchema[DeeplyNested] = ConfigSchemaDerived.derived
  }

  override def spec: Spec[TestEnvironment, Any] =
    suite("ConfigSchemaDerivedSpec")(
      suite("simple case class")(
        test("produces correct number of declarations") {
          val decls = SimpleConfig.cs.describe(Nil)
          assertTrue(decls.size == 2)
        },
        test("produces local declarations for all fields") {
          val decls = SimpleConfig.cs.describe(Nil)
          assertTrue(
            decls.forall(_.source == AgentConfigSource.Local)
          )
        },
        test("field names become paths") {
          val decls = SimpleConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d => d.path == List("name") && d.valueType == ElementSchema.Component(DataType.StringType)),
            decls.exists(d => d.path == List("count") && d.valueType == ElementSchema.Component(DataType.IntType))
          )
        }
      ),
      suite("case class with secret field")(
        test("produces correct number of declarations") {
          val decls = DbConfig.cs.describe(Nil)
          assertTrue(decls.size == 3)
        },
        test("local fields have Local source") {
          val decls = DbConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d => d.path == List("host") && d.source == AgentConfigSource.Local),
            decls.exists(d => d.path == List("port") && d.source == AgentConfigSource.Local)
          )
        },
        test("secret field has Secret source") {
          val decls = DbConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d =>
              d.path == List("password") &&
                d.source == AgentConfigSource.Secret &&
                d.valueType == ElementSchema.Component(DataType.StringType)
            )
          )
        }
      ),
      suite("nested case class")(
        test("produces declarations for all leaf fields") {
          val decls = NestedConfig.cs.describe(Nil)
          assertTrue(decls.size == 4)
        },
        test("top-level field has single-segment path") {
          val decls = NestedConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d => d.path == List("appName") && d.source == AgentConfigSource.Local)
          )
        },
        test("nested fields have multi-segment paths") {
          val decls = NestedConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d => d.path == List("db", "host") && d.source == AgentConfigSource.Local),
            decls.exists(d => d.path == List("db", "port") && d.source == AgentConfigSource.Local),
            decls.exists(d => d.path == List("db", "password") && d.source == AgentConfigSource.Secret)
          )
        }
      ),
      suite("path prefix propagation")(
        test("describe with prefix prepends to all paths") {
          val decls = SimpleConfig.cs.describe(List("app"))
          assertTrue(
            decls.exists(_.path == List("app", "name")),
            decls.exists(_.path == List("app", "count"))
          )
        },
        test("nested config with prefix produces correct deep paths") {
          val decls = NestedConfig.cs.describe(List("root"))
          assertTrue(
            decls.exists(_.path == List("root", "appName")),
            decls.exists(_.path == List("root", "db", "host")),
            decls.exists(_.path == List("root", "db", "port")),
            decls.exists(_.path == List("root", "db", "password"))
          )
        }
      ),
      suite("all-local config")(
        test("five fields produce five local declarations") {
          val decls = AllLocalConfig.cs.describe(Nil)
          assertTrue(
            decls.size == 5,
            decls.forall(_.source == AgentConfigSource.Local)
          )
        },
        test("field types are correct") {
          val decls = AllLocalConfig.cs.describe(Nil)
          assertTrue(
            decls.exists(d => d.path == List("a") && d.valueType == ElementSchema.Component(DataType.StringType)),
            decls.exists(d => d.path == List("b") && d.valueType == ElementSchema.Component(DataType.IntType)),
            decls.exists(d => d.path == List("c") && d.valueType == ElementSchema.Component(DataType.BoolType)),
            decls.exists(d => d.path == List("d") && d.valueType == ElementSchema.Component(DataType.LongType)),
            decls.exists(d => d.path == List("e") && d.valueType == ElementSchema.Component(DataType.DoubleType))
          )
        }
      ),
      suite("multiple secrets")(
        test("each secret field gets Secret source") {
          val decls = MultiSecretConfig.cs.describe(Nil)
          assertTrue(
            decls.size == 3,
            decls.count(_.source == AgentConfigSource.Secret) == 2,
            decls.count(_.source == AgentConfigSource.Local) == 1
          )
        }
      ),
      suite("deeply nested")(
        test("three levels of nesting produce correct paths") {
          val decls = DeeplyNested.cs.describe(Nil)
          assertTrue(
            decls.exists(_.path == List("outer", "appName")),
            decls.exists(_.path == List("outer", "db", "host")),
            decls.exists(_.path == List("outer", "db", "port")),
            decls.exists(_.path == List("outer", "db", "password"))
          )
        }
      )
    )
}
