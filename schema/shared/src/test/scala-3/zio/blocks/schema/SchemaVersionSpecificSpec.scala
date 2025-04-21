package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaVersionSpecificSpec")(
    suite("Reflect.Record")(
      test("derives schema using 'derives' keyword") {
        case class Record1(b: Byte, i: Int) derives Schema

        assert(Schema[Record1])(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Nil,
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Record1"
                ),
                recordBinding = null,
                doc = Doc.Empty,
                modifiers = Nil
              )
            )
          )
        )
      }
    )
  )
}
