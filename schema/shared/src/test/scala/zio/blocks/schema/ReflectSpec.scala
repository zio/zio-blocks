package zio.blocks.schema

import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding._
import zio.blocks.typeid.{Owner, TypeId}
import zio.test.Assertion._
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object ReflectSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ReflectSpec")(
    suite("Reflect")(
      test("has consistent asDynamic and isDynamic") {
        assert(Reflect.dynamic[Binding].asDynamic)(isSome(equalTo(Reflect.dynamic[Binding]))) &&
        assert(Reflect.dynamic[Binding].isDynamic)(equalTo(true)) &&
        assert(Reflect.Deferred(() => Reflect.dynamic[Binding]).asDynamic)(isSome(equalTo(Reflect.dynamic[Binding]))) &&
        assert(Reflect.Deferred(() => Reflect.dynamic[Binding]).isDynamic)(equalTo(true)) &&
        assert(Reflect.localTime[Binding].asDynamic)(isNone) &&
        assert(Reflect.localTime[Binding].isDynamic)(equalTo(false)) &&
        assert(tuple4Reflect.asDynamic)(isNone) &&
        assert(tuple4Reflect.isDynamic)(equalTo(false)) &&
        assert(eitherReflect.asDynamic)(isNone) &&
        assert(eitherReflect.isDynamic)(equalTo(false)) &&
        assert(Reflect.set(Reflect.localDateTime[Binding]).asDynamic)(isNone) &&
        assert(Reflect.set(Reflect.localDateTime[Binding]).isDynamic)(equalTo(false)) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).asDynamic)(isNone) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).isDynamic)(equalTo(false))
      },
      test("has consistent asMap, asMapUnknown and isMap") {
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).asMap)(
          isSome(equalTo(Reflect.map(Reflect.int[Binding], Reflect.int[Binding])))
        ) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).asMapUnknown.isDefined)(equalTo(true)) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).isMap)(equalTo(true)) &&
        assert(Reflect.Deferred(() => Reflect.map(Reflect.int[Binding], Reflect.int[Binding])).asMap)(
          isSome(equalTo(Reflect.map(Reflect.int[Binding], Reflect.int[Binding])))
        ) &&
        assert(Reflect.Deferred(() => Reflect.map(Reflect.int[Binding], Reflect.int[Binding])).asMapUnknown.isDefined)(
          equalTo(true)
        ) &&
        assert(Reflect.Deferred(() => Reflect.map(Reflect.int[Binding], Reflect.int[Binding])).isMap)(equalTo(true)) &&
        assert(Reflect.offsetTime[Binding].asMap(null))(isNone) &&
        assert(Reflect.offsetTime[Binding].asMapUnknown)(isNone) &&
        assert(Reflect.offsetTime[Binding].isMap)(equalTo(false)) &&
        assert(tuple4Reflect.asMap(null))(isNone) &&
        assert(tuple4Reflect.asMapUnknown)(isNone) &&
        assert(tuple4Reflect.isMap)(equalTo(false)) &&
        assert(eitherReflect.asMap(null))(isNone) &&
        assert(eitherReflect.asMapUnknown)(isNone) &&
        assert(eitherReflect.isMap)(equalTo(false)) &&
        assert(Reflect.set(Reflect.offsetDateTime[Binding]).asMap(null))(isNone) &&
        assert(Reflect.set(Reflect.offsetDateTime[Binding]).asMapUnknown)(isNone) &&
        assert(Reflect.set(Reflect.offsetDateTime[Binding]).isMap)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asMap(null))(isNone) &&
        assert(Reflect.dynamic[Binding].asMapUnknown)(isNone) &&
        assert(Reflect.dynamic[Binding].isMap)(equalTo(false))
      },
      test("has consistent asRecord and isRecord") {
        assert(tuple4Reflect.asRecord)(isSome(equalTo(tuple4Reflect))) &&
        assert(tuple4Reflect.isRecord)(equalTo(true)) &&
        assert(Reflect.Deferred(() => tuple4Reflect).asRecord)(isSome(equalTo(tuple4Reflect))) &&
        assert(Reflect.Deferred(() => tuple4Reflect).isRecord)(equalTo(true)) &&
        assert(Reflect.unit[Binding].asRecord)(isNone) &&
        assert(Reflect.unit[Binding].isRecord)(equalTo(false)) &&
        assert(eitherReflect.asRecord)(isNone) &&
        assert(eitherReflect.isRecord)(equalTo(false)) &&
        assert(Reflect.set(Reflect.zoneId[Binding]).asRecord)(isNone) &&
        assert(Reflect.set(Reflect.zoneId[Binding]).isRecord)(equalTo(false)) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.bigDecimal[Binding]).asRecord)(isNone) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.bigDecimal[Binding]).isRecord)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asRecord)(isNone) &&
        assert(Reflect.dynamic[Binding].isRecord)(equalTo(false))
      },
      test("has consistent asPrimitive and isPrimitive") {
        assert(Reflect.int[Binding].asPrimitive)(isSome(equalTo(Reflect.int[Binding]))) &&
        assert(Reflect.int[Binding].isPrimitive)(equalTo(true)) &&
        assert(Reflect.Deferred(() => Reflect.int[Binding]).asPrimitive)(isSome(equalTo(Reflect.int[Binding]))) &&
        assert(Reflect.Deferred(() => Reflect.int[Binding]).isPrimitive)(equalTo(true)) &&
        assert(tuple4Reflect.asPrimitive)(isNone) &&
        assert(tuple4Reflect.isPrimitive)(equalTo(false)) &&
        assert(eitherReflect.asPrimitive)(isNone) &&
        assert(eitherReflect.isPrimitive)(equalTo(false)) &&
        assert(Reflect.set(Reflect.period[Binding]).asPrimitive)(isNone) &&
        assert(Reflect.set(Reflect.period[Binding]).isPrimitive)(equalTo(false)) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).asPrimitive)(isNone) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).isPrimitive)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asPrimitive)(isNone) &&
        assert(Reflect.dynamic[Binding].isPrimitive)(equalTo(false))
      },
      test("has consistent asSequence, asSequenceUnknown and isSequence") {
        assert(Reflect.set(Reflect.int[Binding]).asSequence)(isSome(equalTo(Reflect.set(Reflect.int[Binding])))) &&
        assert(Reflect.set(Reflect.int[Binding]).asSequenceUnknown.isDefined)(equalTo(true)) &&
        assert(Reflect.set(Reflect.int[Binding]).isSequence)(equalTo(true)) &&
        assert(Reflect.Deferred(() => Reflect.set(Reflect.int[Binding])).asSequence)(
          isSome(equalTo(Reflect.set(Reflect.int[Binding])))
        ) &&
        assert(Reflect.Deferred(() => Reflect.set(Reflect.int[Binding])).asSequenceUnknown.isDefined)(equalTo(true)) &&
        assert(Reflect.Deferred(() => Reflect.set(Reflect.int[Binding])).isSequence)(equalTo(true)) &&
        assert(Reflect.int[Binding].asSequence(null))(isNone) &&
        assert(Reflect.int[Binding].asSequenceUnknown)(isNone) &&
        assert(Reflect.int[Binding].isSequence)(equalTo(false)) &&
        assert(tuple4Reflect.asSequence(null))(isNone) &&
        assert(tuple4Reflect.asSequenceUnknown)(isNone) &&
        assert(tuple4Reflect.isSequence)(equalTo(false)) &&
        assert(eitherReflect.asSequence(null))(isNone) &&
        assert(eitherReflect.asSequenceUnknown)(isNone) &&
        assert(eitherReflect.isSequence)(equalTo(false)) &&
        assert(Reflect.map(Reflect.dayOfWeek[Binding], Reflect.duration[Binding]).asSequence(null))(isNone) &&
        assert(Reflect.map(Reflect.dayOfWeek[Binding], Reflect.duration[Binding]).asSequenceUnknown)(isNone) &&
        assert(Reflect.map(Reflect.dayOfWeek[Binding], Reflect.duration[Binding]).isSequence)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asSequence(null))(isNone) &&
        assert(Reflect.dynamic[Binding].asSequenceUnknown)(isNone) &&
        assert(Reflect.dynamic[Binding].isSequence)(equalTo(false))
      },
      test("has consistent asVariant and isVariant") {
        assert(eitherReflect.asVariant)(isSome(equalTo(eitherReflect))) &&
        assert(eitherReflect.isVariant)(equalTo(true)) &&
        assert(Reflect.Deferred(() => eitherReflect).asVariant)(isSome(equalTo(eitherReflect))) &&
        assert(Reflect.Deferred(() => eitherReflect).isVariant)(equalTo(true)) &&
        assert(Reflect.int[Binding].asVariant)(isNone) &&
        assert(Reflect.int[Binding].isVariant)(equalTo(false)) &&
        assert(tuple4Reflect.asVariant)(isNone) &&
        assert(tuple4Reflect.isVariant)(equalTo(false)) &&
        assert(Reflect.set(Reflect.monthDay[Binding]).asVariant)(isNone) &&
        assert(Reflect.set(Reflect.monthDay[Binding]).isVariant)(equalTo(false)) &&
        assert(Reflect.map(Reflect.zoneOffset[Binding], Reflect.zonedDateTime[Binding]).asVariant)(isNone) &&
        assert(Reflect.map(Reflect.zoneOffset[Binding], Reflect.zonedDateTime[Binding]).isVariant)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asVariant)(isNone) &&
        assert(Reflect.dynamic[Binding].isVariant)(equalTo(false))
      },
      test("has consistent asWrapperUnknown and isWrapper") {
        assert(wrapperReflect.asWrapperUnknown.isDefined)(equalTo(true)) &&
        assert(wrapperReflect.isWrapper)(equalTo(true)) &&
        assert(Reflect.Deferred(() => wrapperReflect).asWrapperUnknown.isDefined)(equalTo(true)) &&
        assert(Reflect.Deferred(() => wrapperReflect).isWrapper)(equalTo(true)) &&
        assert(Reflect.int[Binding].asWrapperUnknown)(isNone) &&
        assert(Reflect.int[Binding].isWrapper)(equalTo(false)) &&
        assert(tuple4Reflect.asWrapperUnknown)(isNone) &&
        assert(tuple4Reflect.isWrapper)(equalTo(false)) &&
        assert(eitherReflect.asWrapperUnknown)(isNone) &&
        assert(eitherReflect.isWrapper)(equalTo(false)) &&
        assert(Reflect.map(Reflect.dayOfWeek[Binding], Reflect.duration[Binding]).asWrapperUnknown)(isNone) &&
        assert(Reflect.map(Reflect.dayOfWeek[Binding], Reflect.duration[Binding]).isWrapper)(equalTo(false)) &&
        assert(Reflect.dynamic[Binding].asWrapperUnknown)(isNone) &&
        assert(Reflect.dynamic[Binding].isWrapper)(equalTo(false))
      }
    ),
    suite("Reflect.Primitive")(
      test("has consistent equals and hashCode") {
        val long1 = Primitive[Binding, Long](
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null, // should be ignored in equals and hashCode
          typeId = TypeId.long
        )
        val long2 = long1.copy(primitiveType = PrimitiveType.Long(Validation.Numeric.Positive))
        val long3 = long1.copy(typeId = TypeId.nominal[Long]("Long1", Owner.fromPackagePath("zio.blocks.schema")))
        val long4 = long1.copy(doc = Doc("text"))
        val long5 = long1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(long1)(equalTo(long1)) &&
        assert(long1.hashCode)(equalTo(long1.hashCode)) &&
        assert(long1.noBinding: Any)(equalTo(long1)) &&
        assert(long1.noBinding.hashCode)(equalTo(long1.hashCode)) &&
        assert(Reflect.long[Binding])(equalTo(long1)) &&
        assert(Reflect.long[Binding].hashCode)(equalTo(long1.hashCode)) &&
        assert(long2)(not(equalTo(long1))) &&
        assert(long3)(not(equalTo(long1))) &&
        assert(long4)(not(equalTo(long1))) &&
        assert(long5)(not(equalTo(long1))) &&
        assert(long5: Any)(not(equalTo("String")))
      },
      test("has consistent metadata and nodeType") {
        val int1 = Reflect.int[Binding]
        assert(int1.metadata: Any)(equalTo(int1.binding)) &&
        assert(int1.nodeType: Any)(equalTo(Reflect.Type.Primitive))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        val int1 = Reflect.int[Binding]
        assert(int1.fromDynamicValue(int1.toDynamicValue(1)))(isRight(equalTo(1)))
      },
      test("gets and updates primitive type name") {
        val int1 = Reflect.int[Binding]
        assert(int1.typeId)(equalTo(TypeId.int)) &&
        assert(
          int1
            .typeId(TypeId.nominal[Int]("IntWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")))
            .typeId
        )(equalTo(TypeId.nominal[Int]("IntWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))))
      },
      test("updates primitive default value") {
        val int1 = Reflect.int[Binding]
        assert(int1.getDefaultValue)(isNone) &&
        assert(int1.defaultValue(1).getDefaultValue)(isSome(equalTo(1)))
      },
      test("gets and updates primitive documentation") {
        val long1 = Reflect.long[Binding]
        assert(long1.doc)(equalTo(Doc.Empty)) &&
        assert(long1.doc("Long (updated)").doc)(equalTo(Doc("Long (updated)")))
      },
      test("gets and updates primitive examples") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](),
          typeId = TypeId.long,
          doc = Doc("Long (positive)"),
          storedExamples = Seq(1L, 2L, 3L).map(l => DynamicValue.Primitive(PrimitiveValue.Long(l)))
        )
        assert(long1.examples)(equalTo(Seq(1L, 2L, 3L))) &&
        assert(Reflect.int[Binding].examples(1, 2, 3).examples)(equalTo(Seq(1, 2, 3)))
      },
      test("gets and appends primitive modifiers") {
        val int1 = Reflect.int[Binding]
        assert(int1.modifiers)(equalTo(Seq.empty)) &&
        assert(int1.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(int1.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      }
    ),
    suite("Reflect.Record")(
      test("has consistent equals and hashCode") {
        val record1 = tuple4Reflect
        val record2 = record1.copy(typeId =
          TypeId.nominal[(Byte, Short, Int, Long)]("Tuple4", Owner.fromPackagePath("zio.blocks.schema"))
        )
        val record3 = record1.copy(fields = record1.fields.reverse)
        val record4 = record1.copy(doc = Doc("text"))
        val record5 = record1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(record1)(equalTo(record1)) &&
        assert(record1.hashCode)(equalTo(record1.hashCode)) &&
        assert(record1.noBinding: Any)(equalTo(record1)) &&
        assert(record1.noBinding.hashCode)(equalTo(record1.hashCode)) &&
        assert(record2)(not(equalTo(record1))) &&
        assert(record3)(not(equalTo(record1))) &&
        assert(record4)(not(equalTo(record1))) &&
        assert(record5)(not(equalTo(record1)))
      },
      test("has consistent metadata and nodeType") {
        assert(tuple4Reflect.metadata: Any)(equalTo(tuple4Reflect.binding)) &&
        assert(tuple4Reflect.nodeType)(equalTo(Reflect.Type.Record))
      },
      test("has consistent fields, length, registers and usedRegisters") {
        val record1 = tuple4Reflect
        assert(record1.fields.length)(equalTo(4)) &&
        assert(record1.registers.length)(equalTo(4)) &&
        assert(record1.fields(0).value.asPrimitive.map(_.primitiveType): Option[Any])(
          isSome(equalTo(PrimitiveType.Byte(Validation.None)))
        ) &&
        assert(record1.registers(0).usedRegisters)(equalTo(RegisterOffset(bytes = 1))) &&
        assert(record1.fields(1).value.asPrimitive.map(_.primitiveType): Option[Any])(
          isSome(equalTo(PrimitiveType.Short(Validation.None)))
        ) &&
        assert(record1.registers(1).usedRegisters)(equalTo(RegisterOffset(shorts = 1))) &&
        assert(record1.fields(2).value.asPrimitive.map(_.primitiveType): Option[Any])(
          isSome(equalTo(PrimitiveType.Int(Validation.None)))
        ) &&
        assert(record1.registers(2).usedRegisters)(equalTo(RegisterOffset(ints = 1))) &&
        assert(record1.fields(3).value.asPrimitive.map(_.primitiveType): Option[Any])(
          isSome(equalTo(PrimitiveType.Long(Validation.None)))
        ) &&
        assert(record1.registers(3).usedRegisters)(equalTo(RegisterOffset(longs = 1))) &&
        assert(record1.usedRegisters)(equalTo(record1.registers.foldLeft(0L)(_ + _.usedRegisters)))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        assert(tuple4Reflect.fromDynamicValue(tuple4Reflect.toDynamicValue((1: Byte, 2: Short, 3, 4L))))(
          isRight(equalTo((1: Byte, 2: Short, 3, 4L)))
        )
      },
      test("gets and updates record type name") {
        assert(tuple4Reflect.typeId)(
          equalTo(TypeId.of[(Byte, Short, Int, Long)])
        ) &&
        assert(
          tuple4Reflect
            .typeId(
              TypeId.nominal[(Byte, Short, Int, Long)](
                "Tuple4Wrapper",
                Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")
              )
            )
            .typeId
        )(
          equalTo(
            TypeId.nominal[(Byte, Short, Int, Long)](
              "Tuple4Wrapper",
              Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")
            )
          )
        )
      },
      test("gets and updates record default value") {
        assert(tuple4Reflect.getDefaultValue)(isNone) &&
        assert(tuple4Reflect.defaultValue((1: Byte, 2: Short, 3, 4L)).getDefaultValue)(
          isSome(equalTo((1: Byte, 2: Short, 3, 4L)))
        )
      },
      test("gets and updates record documentation") {
        assert(tuple4Reflect.doc)(equalTo(Doc.Empty)) &&
        assert(tuple4Reflect.doc("Tuple4 (updated)").doc)(equalTo(Doc("Tuple4 (updated)")))
      },
      test("gets and updates record examples") {
        assert(tuple4Reflect.examples)(equalTo(Seq.empty)) &&
        assert(tuple4Reflect.examples((1: Byte, 2: Short, 3, 4L)).examples)(
          equalTo((1: Byte, 2: Short, 3, 4L) :: Nil)
        )
      },
      test("gets and appends record modifiers") {
        assert(tuple4Reflect.modifiers)(equalTo(Seq.empty)) &&
        assert(tuple4Reflect.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(tuple4Reflect.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      },
      test("creates lens by name") {
        assert(tuple4Reflect.lensByName("_3"): Option[Any])(
          isSome(equalTo(Lens(tuple4Reflect, Reflect.int[Binding].asTerm[(Byte, Short, Int, Long)]("_3"))))
        ) &&
        assert(tuple4Reflect.lensByName("_5"))(isNone)
      },
      test("creates lens by index") {
        assert(tuple4Reflect.lensByIndex(2): Option[Any])(
          isSome(equalTo(Lens(tuple4Reflect, Reflect.int[Binding].asTerm[(Byte, Short, Int, Long)]("_3"))))
        ) &&
        assert(tuple4Reflect.lensByIndex(4))(isNone)
      },
      test("finds field term by name") {
        assert(tuple4Reflect.fieldByName("_3"): Option[Any])(isSome(equalTo(Reflect.int[Binding].asTerm("_3")))) &&
        assert(tuple4Reflect.fieldByName("_5"))(isNone)
      },
      test("modifies field term by name") {
        assert(
          tuple4Reflect
            .modifyField("_3")(new Term.Updater[Binding] {
              override def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] =
                Some(input.copy(doc = Doc("updated")))
            })
            .flatMap(_.fieldByName("_3")): Option[Any]
        )(isSome(equalTo(Reflect.int[Binding].asTerm("_3").copy(doc = Doc("updated"))))) &&
        assert(tuple4Reflect.modifyField("_3")(new Term.Updater[Binding] {
          override def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] = None
        }): Option[Any])(isNone) &&
        assert(tuple4Reflect.modifyField("_5")(null))(isNone)
      }
    ),
    suite("Reflect.Variant")(
      test("has consistent equals and hashCode") {
        val variant1 = eitherReflect
        assert(variant1)(equalTo(variant1)) &&
        assert(variant1.hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.noBinding: Any)(equalTo(variant1)) &&
        assert(variant1.noBinding.hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.defaultValue(Right(0L)))(equalTo(variant1)) &&
        assert(variant1.defaultValue(Right(0L)).hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.examples(Left(1)))(equalTo(variant1)) &&
        assert(variant1.examples(Left(1)).hashCode)(equalTo(variant1.hashCode))
      },
      test("has consistent metadata and nodeType") {
        assert(eitherReflect.metadata: Any)(equalTo(eitherReflect.binding)) &&
        assert(eitherReflect.nodeType)(equalTo(Reflect.Type.Variant))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        assert(eitherReflect.fromDynamicValue(eitherReflect.toDynamicValue(Left(0))))(isRight(equalTo(Left(0))))
      },
      test("gets and updates variant type name") {
        assert(eitherReflect.typeId)(
          equalTo(TypeId.of[Either[Int, Long]])
        ) &&
        assert(
          eitherReflect
            .typeId(
              TypeId.nominal[Either[Int, Long]](
                "EitherWrapper",
                Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")
              )
            )
            .typeId
        )(
          equalTo(
            TypeId.nominal[Either[Int, Long]](
              "EitherWrapper",
              Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")
            )
          )
        )
      },
      test("gets and updates variant default value") {
        assert(eitherReflect.getDefaultValue)(isNone) &&
        assert(eitherReflect.defaultValue(Left(0)).getDefaultValue)(isSome(equalTo(Left(0))))
      },
      test("gets and updates variant documentation") {
        assert(eitherReflect.doc)(equalTo(Doc.Empty)) &&
        assert(eitherReflect.doc("Option[Int] (updated)").doc)(equalTo(Doc("Option[Int] (updated)")))
      },
      test("gets and updates variant examples") {
        assert(eitherReflect.examples)(equalTo(Seq.empty)) &&
        assert(eitherReflect.examples(Left(1)).examples)(equalTo(Seq(Left(1))))
      },
      test("gets and appends variant modifiers") {
        assert(eitherReflect.modifiers)(equalTo(Seq.empty)) &&
        assert(eitherReflect.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(eitherReflect.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      },
      test("creates prism by name") {
        assert(eitherReflect.prismByName("Left"): Option[Any])(
          isSome(equalTo(Prism(eitherReflect, eitherReflect.cases(0).value.asTerm[Either[Int, Long]]("Left"))))
        ) &&
        assert(eitherReflect.prismByName("Middle"))(isNone)
      },
      test("finds case term by name") {
        assert(eitherReflect.caseByName("Left"): Option[Any])(
          isSome(equalTo(eitherReflect.cases(0).value.asTerm("Left")))
        ) &&
        assert(eitherReflect.caseByName("Middle"))(isNone)
      },
      test("modifies case term by name") {
        assert(
          eitherReflect
            .modifyCase("Left")(new Term.Updater[Binding] {
              override def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] =
                Some(input.copy(doc = Doc("updated")))
            })
            .flatMap(_.caseByName("Left").map(_.doc)): Option[Any]
        )(isSome(equalTo(Doc("updated")))) &&
        assert(eitherReflect.modifyCase("Left")(new Term.Updater[Binding] {
          override def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] = None
        }): Option[Any])(isNone) &&
        assert(eitherReflect.modifyCase("Middle")(null))(isNone)
      }
    ),
    suite("Reflect.Sequence")(
      test("has consistent equals and hashCode") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeId = TypeId.of[List[Double]],
          seqBinding = null // should be ignored in equals and hashCode
        )
        val sequence2 = sequence1.copy(element =
          Primitive(PrimitiveType.Double(Validation.None), TypeId.double, Binding.Primitive.double, Doc("text"))
        )
        val sequence3 = sequence1.copy(typeId = TypeId.nominal[List[Double]]("List2", Owner.fromPackagePath("scala")))
        val sequence4 = sequence1.copy(doc = Doc("text"))
        val sequence5 = sequence1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(sequence1)(equalTo(sequence1)) &&
        assert(sequence1.hashCode)(equalTo(sequence1.hashCode)) &&
        assert(sequence1.noBinding: Any)(equalTo(sequence1)) &&
        assert(sequence1.noBinding.hashCode)(equalTo(sequence1.hashCode)) &&
        assert(sequence2)(not(equalTo(sequence1))) &&
        assert(sequence3)(not(equalTo(sequence1))) &&
        assert(sequence4)(not(equalTo(sequence1))) &&
        assert(sequence5)(not(equalTo(sequence1)))
      },
      test("has consistent metadata and nodeType") {
        val sequence1 = Reflect.set(Reflect.int[Binding])
        assert(sequence1.metadata: Any)(equalTo(sequence1.binding)) &&
        assert(sequence1.nodeType)(equalTo(Reflect.Type.Sequence[Set]()))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        val sequence1 = Reflect.vector(Reflect.int[Binding])
        assert(sequence1.fromDynamicValue(sequence1.toDynamicValue(Vector(1, 2, 3))))(isRight(equalTo(Vector(1, 2, 3))))
      },
      test("has extractors for lists, vectors, and sets") {
        import Reflect.Extractors._

        val bigInt1 = Reflect.bigInt[Binding]
        assert(Option(Reflect.list(bigInt1)).collect { case List(e) => e })(isSome(equalTo(bigInt1))) &&
        assert(Option(Reflect.vector(bigInt1)).collect { case Vector(e) => e })(isSome(equalTo(bigInt1))) &&
        assert(Option(Reflect.set(bigInt1)).collect { case Set(e) => e })(isSome(equalTo(bigInt1))) &&
        assert(Option(Reflect.Deferred(() => Reflect.list(bigInt1))).collect { case List(e) => e })(
          isSome(equalTo(bigInt1))
        ) &&
        assert(Option(Reflect.Deferred(() => Reflect.vector(bigInt1))).collect { case Vector(e) => e })(
          isSome(equalTo(bigInt1))
        ) &&
        assert(Option(Reflect.Deferred(() => Reflect.set(bigInt1))).collect { case Set(e) => e })(
          isSome(equalTo(bigInt1))
        )
      },
      test("gets and updates sequence type name") {
        val sequence1 = Reflect.vector(Reflect.int[Binding])
        assert(sequence1.typeId)(
          equalTo(TypeId.of[Vector[Int]])
        ) &&
        assert(
          sequence1
            .typeId(
              TypeId
                .nominal[Vector[Int]]("VectorWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
            )
            .typeId
        )(
          equalTo(
            TypeId.nominal[Vector[Int]]("VectorWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
          )
        )
      },
      test("gets and updates sequence default value") {
        val sequence1 = Reflect.vector(Reflect.int[Binding])
        assert(sequence1.getDefaultValue)(isNone) &&
        assert(sequence1.defaultValue(Vector.empty).getDefaultValue)(isSome(equalTo(Vector.empty)))
      },
      test("gets and updates sequence documentation") {
        val sequence1 = Reflect.seq(Reflect.int[Binding])
        assert(sequence1.doc)(equalTo(Doc.Empty)) &&
        assert(sequence1.doc("Seq (updated)").doc)(equalTo(Doc("Seq (updated)")))
      },
      test("gets and updates sequence examples") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeId = TypeId.of[List[Double]],
          seqBinding = Binding.Seq[List, Double](
            constructor = SeqConstructor.listConstructor,
            deconstructor = SeqDeconstructor.listDeconstructor
          ),
          storedExamples = Seq(
            DynamicValue.Sequence(
              zio.blocks.chunk.Chunk(
                DynamicValue.Primitive(PrimitiveValue.Double(0.1)),
                DynamicValue.Primitive(PrimitiveValue.Double(0.2)),
                DynamicValue.Primitive(PrimitiveValue.Double(0.3))
              )
            )
          )
        )
        assert(sequence1.examples)(equalTo(Seq(List(0.1, 0.2, 0.3)))) &&
        assert(Reflect.set(Reflect.int[Binding]).examples(Set(1, 2, 3)).examples)(equalTo(Seq(Set(1, 2, 3))))
      },
      test("gets and appends sequence modifiers") {
        val sequence1 = Reflect.set(Reflect.char[Binding])
        assert(sequence1.modifiers)(equalTo(Seq.empty)) &&
        assert(sequence1.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(sequence1.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      }
    ),
    suite("Reflect.Map")(
      test("has consistent equals and hashCode") {
        val map1 = Reflect.Map[Binding, Short, Float, Map](
          key = Reflect.short,
          value = Reflect.float,
          typeId = TypeId.of[Map[Short, Float]],
          mapBinding = null // should be ignored in equals and hashCode
        )
        val map2 = map1.copy(key =
          Primitive(
            PrimitiveType.Short(Validation.Numeric.Positive),
            TypeId.short,
            Binding.Primitive.short
          )
        )
        val map3 = map1.copy(value =
          Primitive(PrimitiveType.Float(Validation.None), TypeId.float, Binding.Primitive.float, Doc("text"))
        )
        val map4 = map1.copy(typeId = TypeId.nominal[Map[Short, Float]]("Map2", Owner.fromPackagePath("scala")))
        val map5 = map1.copy(doc = Doc("text"))
        val map6 = map1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(map1)(equalTo(map1)) &&
        assert(map1.hashCode)(equalTo(map1.hashCode)) &&
        assert(map1.noBinding: Any)(equalTo(map1)) &&
        assert(map1.noBinding.hashCode)(equalTo(map1.hashCode)) &&
        assert(map2)(not(equalTo(map1))) &&
        assert(map3)(not(equalTo(map1))) &&
        assert(map4)(not(equalTo(map1))) &&
        assert(map5)(not(equalTo(map1))) &&
        assert(map6)(not(equalTo(map1)))
      },
      test("has consistent metadata and nodeType") {
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).metadata: Any)(
          equalTo(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).binding)
        ) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.int[Binding]).nodeType)(equalTo(Reflect.Type.Map[Map]()))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        val map1 = Reflect.map(Reflect.int[Binding], Reflect.long[Binding])
        assert(map1.fromDynamicValue(map1.toDynamicValue(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))))(
          isRight(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)))
        )
      },
      test("gets and updates map type name") {
        val map1 = Reflect.map(Reflect.int[Binding], Reflect.long[Binding])
        assert(map1.typeId)(
          equalTo(TypeId.of[Map[Int, Long]])
        ) &&
        assert(
          map1
            .typeId(
              TypeId
                .nominal[Map[Int, Long]]("MapWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
            )
            .typeId
        )(
          equalTo(
            TypeId.nominal[Map[Int, Long]]("MapWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
          )
        )
      },
      test("gets and updates map default value") {
        val map1 = Reflect.map(Reflect.int[Binding], Reflect.long[Binding])
        assert(map1.getDefaultValue)(isNone) &&
        assert(map1.defaultValue(Map.empty).getDefaultValue)(isSome(equalTo(Map.empty[Int, Long])))
      },
      test("gets and updates map documentation") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeId = TypeId.of[Map[Int, Long]],
          mapBinding = null, // should be ignored in equals and hashCode
          doc = Doc("Map of Int to Long")
        )
        assert(map1.doc)(equalTo(Doc("Map of Int to Long"))) &&
        assert(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]).doc("Map (updated)").doc)(
          equalTo(Doc("Map (updated)"))
        )
      },
      test("gets and updates map examples") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeId = TypeId.of[Map[Int, Long]],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map
          ),
          storedExamples = Seq(
            DynamicValue.Map(
              zio.blocks.chunk.Chunk(
                (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Long(1L))),
                (DynamicValue.Primitive(PrimitiveValue.Int(2)), DynamicValue.Primitive(PrimitiveValue.Long(2L))),
                (DynamicValue.Primitive(PrimitiveValue.Int(3)), DynamicValue.Primitive(PrimitiveValue.Long(3L)))
              )
            )
          )
        )
        assert(map1.examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil)) &&
        assert(
          Reflect.map(Reflect.int[Binding], Reflect.long[Binding]).examples(Map(1 -> 1L, 2 -> 2L, 3 -> 3L)).examples
        )(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil))
      },
      test("gets and appends map modifiers") {
        val map1 = Reflect.map(Reflect.int[Binding], Reflect.long[Binding])
        assert(map1.modifiers)(equalTo(Seq.empty)) &&
        assert(map1.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(map1.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      }
    ),
    suite("Reflect.Dynamic")(
      test("has consistent equals and hashCode") {
        val dynamic1 = Reflect.dynamic[Binding]
        val dynamic2 = dynamic1.copy(dynamicBinding = null: Binding.Dynamic)
        val dynamic3 = dynamic1.copy(doc = Doc("text"))
        val dynamic4 = dynamic1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(dynamic1)(equalTo(dynamic1)) &&
        assert(dynamic1.hashCode)(equalTo(dynamic1.hashCode)) &&
        assert(dynamic1.noBinding: Any)(equalTo(dynamic1)) &&
        assert(dynamic1.noBinding.hashCode)(equalTo(dynamic1.hashCode)) &&
        assert(dynamic2)(equalTo(dynamic1)) &&
        assert(dynamic2.hashCode)(equalTo(dynamic1.hashCode)) &&
        assert(dynamic3)(not(equalTo(dynamic1))) &&
        assert(dynamic4)(not(equalTo(dynamic1)))
      },
      test("has consistent metadata and nodeType") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.metadata: Any)(equalTo(dynamic1.binding)) &&
        assert(dynamic1.nodeType)(equalTo(Reflect.Type.Dynamic))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.fromDynamicValue(dynamic1.toDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))))(
          isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        )
      },
      test("gets and updates dynamic type name") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.typeId)(equalTo(TypeId.of[DynamicValue])) &&
        assert(
          dynamic1
            .typeId(
              TypeId
                .nominal[DynamicValue]("DynamicWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
            )
            .typeId
        )(
          equalTo(
            TypeId
              .nominal[DynamicValue]("DynamicWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))
          )
        )
      },
      test("gets and updates dynamic default value") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.getDefaultValue)(isNone) &&
        assert(dynamic1.defaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0))).getDefaultValue)(
          isSome(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        )
      },
      test("gets and updates dynamic documentation") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.doc)(equalTo(Doc.Empty)) &&
        assert(dynamic1.doc("Dynamic (updated)").doc)(equalTo(Doc("Dynamic (updated)")))
      },
      test("gets and updates dynamic examples") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          storedExamples = DynamicValue.Primitive(PrimitiveValue.Int(0)) :: Nil
        )
        assert(dynamic1.examples)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0)) :: Nil)) &&
        assert(dynamic1.examples(DynamicValue.Primitive(PrimitiveValue.Int(1))).examples)(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1)) :: Nil)
        )
      },
      test("gets and appends dynamic modifiers") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.modifiers)(equalTo(Seq.empty)) &&
        assert(dynamic1.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(dynamic1.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      }
    ),
    suite("Reflect.Wrapper")(
      test("has consistent equals and hashCode") {
        val wrapper1 = wrapperReflect
        val wrapper2 =
          wrapper1.copy(typeId = TypeId.nominal[Wrapper]("Tuple4", Owner.fromPackagePath("zio.blocks.schema")))
        val wrapper3 = wrapper1.copy(wrapped = Reflect.long[Binding].doc("Long (updated)"))
        val wrapper4 = wrapper1.copy(doc = Doc("text"))
        val wrapper5 = wrapper1.copy(modifiers = Seq(Modifier.config("key", "value")))
        assert(wrapper1)(equalTo(wrapper1)) &&
        assert(wrapper1.hashCode)(equalTo(wrapper1.hashCode)) &&
        assert(wrapper1.noBinding: Any)(equalTo(wrapper1)) &&
        assert(wrapper1.noBinding.hashCode)(equalTo(wrapper1.hashCode)) &&
        assert(wrapper2)(not(equalTo(wrapper1))) &&
        assert(wrapper3)(not(equalTo(wrapper1))) &&
        assert(wrapper4)(not(equalTo(wrapper1))) &&
        assert(wrapper5)(not(equalTo(wrapper1)))
      },
      test("has consistent metadata and nodeType") {
        assert(wrapperReflect.metadata: Any)(equalTo(wrapperReflect.binding)) &&
        assert(wrapperReflect.nodeType)(equalTo(Reflect.Type.Wrapper[Wrapper, Long]()))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        assert(wrapperReflect.fromDynamicValue(wrapperReflect.toDynamicValue(Wrapper(4L))))(
          isRight(equalTo(Wrapper(4L)))
        )
      },
      test("gets and updates wrapper type name") {
        assert(wrapperReflect.typeId)(
          equalTo(TypeId.nominal[Wrapper]("Wrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")))
        ) &&
        assert(
          wrapperReflect
            .typeId(TypeId.nominal[Wrapper]("Wrapper2", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")))
            .typeId
        )(equalTo(TypeId.nominal[Wrapper]("Wrapper2", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))))
      },
      test("gets and updates wrapper default value") {
        assert(wrapperReflect.getDefaultValue)(isNone) &&
        assert(wrapperReflect.defaultValue(Wrapper(4L)).getDefaultValue)(isSome(equalTo(Wrapper(4L))))
      },
      test("gets and updates wrapper documentation") {
        assert(wrapperReflect.doc)(equalTo(Doc.Empty)) &&
        assert(wrapperReflect.doc("Tuple4 (updated)").doc)(equalTo(Doc("Tuple4 (updated)")))
      },
      test("gets and updates wrapper examples") {
        assert(wrapperReflect.examples)(equalTo(Seq.empty)) &&
        assert(wrapperReflect.examples(Wrapper(4L)).examples)(equalTo(Wrapper(4L) :: Nil))
      },
      test("gets and appends wrapper modifiers") {
        assert(wrapperReflect.modifiers)(equalTo(Seq.empty)) &&
        assert(wrapperReflect.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(wrapperReflect.modifiers(Seq(Modifier.config("key", "value"))).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        )
      }
    ),
    suite("Reflect.Deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.int[Binding]
        val deferred4 = Primitive(PrimitiveType.Int(Validation.Numeric.Positive), TypeId.int, Binding.Primitive.int)
        val deferred5 = Reflect.Deferred[Binding, Int](() => deferred4)
        assert(deferred1)(equalTo(deferred1)) &&
        assert(deferred1.hashCode)(equalTo(deferred1.hashCode)) &&
        assert(deferred1.noBinding: Any)(equalTo(deferred1)) &&
        assert(deferred1.noBinding.hashCode)(equalTo(deferred1.hashCode)) &&
        assert(deferred2)(equalTo(deferred1)) &&
        assert(deferred2.hashCode)(equalTo(deferred1.hashCode)) &&
        assert(deferred3)(equalTo(deferred1)) &&
        assert(deferred3.hashCode)(equalTo(deferred1.hashCode)) &&
        assert(deferred4: Any)(not(equalTo(deferred1))) &&
        assert(deferred5)(not(equalTo(deferred1))) &&
        assert(deferred5: Any)(not(equalTo("String")))
      },
      test("has consistent metadata and nodeType") {
        val deferred1 = Reflect.Deferred[Binding, Year](() => Reflect.year)
        assert(deferred1.metadata: Any)(equalTo(deferred1.binding)) &&
        assert(deferred1.nodeType: Any)(equalTo(Reflect.Type.Primitive))
      },
      test("has consistent fromDynamicValue and toDynamicValue") {
        val deferred1 = Reflect.Deferred[Binding, Year](() => Reflect.year)
        assert(deferred1.fromDynamicValue(deferred1.toDynamicValue(Year.of(2025))))(isRight(equalTo(Year.of(2025))))
      },
      test("gets and updates deferred type name") {
        val deferred1 = Reflect.Deferred[Binding, Year](() => Reflect.year)
        assert(deferred1.typeId)(equalTo(TypeId.year)) &&
        assert(
          deferred1
            .typeId(TypeId.nominal[Year]("YearWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")))
            .typeId
        )(equalTo(TypeId.nominal[Year]("YearWrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec"))))
      },
      test("gets and updates deferred default value") {
        val deferred1 = Reflect.Deferred[Binding, YearMonth](() => Reflect.yearMonth)
        assert(deferred1.getDefaultValue)(isNone) &&
        assert(deferred1.defaultValue(YearMonth.of(2025, 6)).getDefaultValue)(isSome(equalTo(YearMonth.of(2025, 6))))
      },
      test("gets and updates deferred documentation") {
        val deferred1 = Reflect.Deferred[Binding, Currency](() => Reflect.currency)
        assert(deferred1.doc)(equalTo(Doc.Empty)) &&
        assert(deferred1.doc("Currency (updated)").doc)(equalTo(Doc("Currency (updated)")))
      },
      test("gets and updates deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Month](() => Reflect.month)
        assert(deferred1.examples)(equalTo(Seq())) &&
        assert(deferred1.examples(Month.APRIL, Month.MAY).examples)(equalTo(Seq(Month.APRIL, Month.MAY)))
      },
      test("gets and updates modifiers") {
        val deferred1 = Reflect.Deferred[Binding, UUID](() => Reflect.uuid)
        assert(deferred1.modifiers)(equalTo(Seq.empty)) &&
        assert(deferred1.modifier(Modifier.config("key", "value")).modifiers)(
          equalTo(Seq(Modifier.config("key", "value")))
        ) &&
        assert(
          deferred1.modifiers(Seq(Modifier.config("key", "value"))).modifiers
        )(equalTo(Seq(Modifier.config("key", "value"))))
      },
      test("avoids stack overflow for circulary dependent structures") {
        lazy val deferred1: Reflect.Deferred[Binding, Any] = Reflect.Deferred(() => deferred2)
        lazy val deferred2: Reflect.Deferred[Binding, Any] = Reflect.Deferred(() => deferred1)
        assert(deferred1.asDynamic)(isNone) &&
        assert(deferred1.isDynamic)(equalTo(false)) &&
        assert(deferred1.asRecord)(isNone) &&
        assert(deferred1.isRecord)(equalTo(false)) &&
        assert(deferred1.asVariant)(isNone) &&
        assert(deferred1.isVariant)(equalTo(false)) &&
        assert(deferred1.asPrimitive)(isNone) &&
        assert(deferred1.isPrimitive)(equalTo(false)) &&
        assert(deferred1.asSequence(null))(isNone) &&
        assert(deferred1.asSequenceUnknown)(isNone) &&
        assert(deferred1.isSequence)(equalTo(false)) &&
        assert(deferred1.asMap(null))(isNone) &&
        assert(deferred1.asMapUnknown)(isNone) &&
        assert(deferred1.isMap)(equalTo(false)) &&
        assert(deferred1.asWrapperUnknown)(isNone) &&
        assert(deferred1.isWrapper)(equalTo(false)) &&
        assert(deferred1.typeId)(equalTo(TypeId.nominal[Any]("<deferred-cycle>", Owner.Root)))
      }
    )
  )

  val tuple4Reflect: Reflect.Record[Binding, (Byte, Short, Int, Long)] =
    Schema.derived[(Byte, Short, Int, Long)].reflect.asRecord.get
  val eitherReflect: Reflect.Variant[Binding, Either[Int, Long]] =
    Schema.derived[Either[Int, Long]].reflect.asVariant.get
  val wrapperReflect: Reflect.Wrapper[Binding, Wrapper, Long] = new Reflect.Wrapper(
    wrapped = Schema[Long].reflect,
    typeId = TypeId.nominal[Wrapper]("Wrapper", Owner.fromPackagePath("zio.blocks.schema").term("ReflectSpec")),
    wrapperBinding = Binding.Wrapper(
      wrap = (x: Long) => Wrapper(x),
      unwrap = (x: Wrapper) => x.value
    )
  )

  case class Wrapper(value: Long) extends AnyVal
}
