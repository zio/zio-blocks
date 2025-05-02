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
        case class Record1(c: Char, d: Double) derives Schema

        object Record1 extends CompanionOptics[Record1] {
          val c = field(x => x.c)
          val d = field(_.d)
        }

        val schema = summon[Schema[Record1]]
        val record = schema.reflect.asInstanceOf[Reflect.Record[Binding, Record1]]
        val field1 = record.fields(0).asInstanceOf[Term.Bound[Record1, Char]]
        val field2 = record.fields(1).asInstanceOf[Term.Bound[Record1, Double]]
        assert(field1.value.binding.defaultValue)(isNone) &&
        assert(field2.value.binding.defaultValue)(isNone) &&
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(chars = 1, doubles = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(chars = 1, doubles = 1))) &&
        assert(Record1.c.get(Record1('1', 2.0)))(equalTo('1')) &&
        assert(Record1.d.get(Record1('1', 2.0)))(equalTo(2.0)) &&
        assert(Record1.c.replace(Record1('1', 2.0), '3'))(equalTo(Record1('3', 2.0))) &&
        assert(Record1.d.replace(Record1('1', 2.0), 3.0))(equalTo(Record1('1', 3.0))) &&
        assert(schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Seq(
                  Schema[Char].reflect.asTerm("c"),
                  Schema[Double].reflect.asTerm("d")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Record1"
                ),
                recordBinding = null
              )
            )
          )
        )
      }
    ),
    suite("Reflect.Variant")(
      test("derives schema using 'derives' keyword") {
        sealed trait Variant1 derives Schema

        case class Case1(d: Double) extends Variant1 derives Schema

        case class Case2(f: Float) extends Variant1 derives Schema

        object Variant1 extends CompanionOptics[Variant1] {
          val case1 = caseOf[Case1]
          val case2 = caseOf[Case2]
        }

        val schema = Schema.derived[Variant1]
        assert(Variant1.case1.getOption(Case1(0.1)))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.case2.getOption(Case2(0.2f)))(isSome(equalTo(Case2(0.2f)))) &&
        assert(Variant1.case1.replace(Case1(0.1), Case1(0.2)))(equalTo(Case1(0.2))) &&
        assert(Variant1.case2.replace(Case2(0.2f), Case2(0.3f)))(equalTo(Case2(0.3f))) &&
        assert(schema)(
          equalTo(
            new Schema[Variant1](
              reflect = Reflect.Variant[Binding, Variant1](
                cases = Seq(
                  Schema[Case1].reflect.asTerm("Case1"),
                  Schema[Case2].reflect.asTerm("Case2")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Variant1"
                ),
                variantBinding = null
              )
            )
          )
        )
      }
    )
  )
}
