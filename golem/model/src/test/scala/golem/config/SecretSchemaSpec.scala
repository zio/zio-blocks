package golem.config

import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId
import zio.test._

object SecretSchemaSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("SecretSchemaSpec")(
      suite("Secret[String]")(
        test("typeId normalized name contains golem.config.Secret") {
          val tid = TypeId.normalize(Schema[Secret[String]].reflect.typeId)
          assertTrue(tid.fullName.contains("golem.config.Secret"))
        },
        test("reflect is a wrapper") {
          assertTrue(Schema[Secret[String]].reflect.asWrapperUnknown.isDefined)
        },
        test("wrapped reflect corresponds to String") {
          val wrapper = Schema[Secret[String]].reflect.asWrapperUnknown.get
          val wrappedTypeId = TypeId.normalize(wrapper.wrapper.wrapped.typeId)
          assertTrue(wrappedTypeId.fullName.contains("String"))
        }
      ),
      suite("Secret[Int]")(
        test("typeId normalized name contains golem.config.Secret") {
          val tid = TypeId.normalize(Schema[Secret[Int]].reflect.typeId)
          assertTrue(tid.fullName.contains("golem.config.Secret"))
        },
        test("reflect is a wrapper") {
          assertTrue(Schema[Secret[Int]].reflect.asWrapperUnknown.isDefined)
        },
        test("wrapped reflect corresponds to Int") {
          val wrapper = Schema[Secret[Int]].reflect.asWrapperUnknown.get
          val wrappedTypeId = TypeId.normalize(wrapper.wrapper.wrapped.typeId)
          assertTrue(wrappedTypeId.fullName.contains("Int"))
        }
      )
    )
}
