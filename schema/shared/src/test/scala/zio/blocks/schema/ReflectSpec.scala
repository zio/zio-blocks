package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object ReflectSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("ReflectSpec")(
    suite("Reflect.Primitive")(
      test("has consistent equals and hashCode") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]], // should be ignored in equals and hashCode
          typeName = TypeName.long,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val long2 = long1.copy(primitiveType = PrimitiveType.Long(Validation.Numeric.Positive))
        val long3 = long1.copy(typeName = TypeName(Namespace(Seq("zio", "blocks", "schema"), Nil), "Long1"))
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
        assert(long5)(not(equalTo(long1)))
      },
      test("updates primitive default value") {
        assert(Reflect.int[Binding].binding.defaultValue)(isNone) &&
        assert(Reflect.int[Binding].defaultValue(1).binding.defaultValue.get.apply())(equalTo(1))
      },
      test("has access to primitive documentation") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]],
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        assert(long1.doc)(equalTo(Doc("Long (positive)")))
      },
      test("updates primitive documentation") {
        assert(Reflect.int[Binding].doc("Int (updated)").doc)(equalTo(Doc("Int (updated)")))
      },
      test("has access to primitive examples") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = Seq(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        assert(long1.examples)(equalTo(Seq(1L, 2L, 3L)))
      },
      test("updates primitive examples") {
        assert(Reflect.int[Binding].binding.examples(1, 2, 3).examples)(equalTo(Seq(1, 2, 3)))
      }
    ),
    suite("Reflect.Record")(
      test("has consistent equals and hashCode") {
        val record1 = Reflect.tuple2(Reflect.byte[Binding], Reflect.int[Binding])
        val record2 = record1.copy(typeName = TypeName(Namespace(Seq("zio", "blocks", "schema"), Nil), "Record2"))
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
      test("has consistent fields, length, registers and usedRegisters") {
        val record1 = Reflect.some(Reflect.int[Binding])
        val record2 = Reflect.some(Reflect.double[Binding])
        val record3 = Reflect.some(Reflect.string[Binding])
        assert(record1.length)(equalTo(1)) &&
        assert(record1.fields.length)(equalTo(1)) &&
        assert(record1.registers.length)(equalTo(1)) &&
        assert(record1.fields(0).value.asInstanceOf[Primitive[Binding, Int]].primitiveType)(
          equalTo(PrimitiveType.Int(Validation.None))
        ) &&
        assert(record1.registers(0).usedRegisters)(equalTo(RegisterOffset(ints = 1))) &&
        assert(record1.usedRegisters)(equalTo(record1.registers.foldLeft(0)(_ + _.usedRegisters))) &&
        assert(record2.length)(equalTo(1)) &&
        assert(record2.fields.length)(equalTo(1)) &&
        assert(record2.registers.length)(equalTo(1)) &&
        assert(record2.fields(0).value.asInstanceOf[Primitive[Binding, Double]].primitiveType)(
          equalTo(PrimitiveType.Double(Validation.None))
        ) &&
        assert(record2.registers(0).usedRegisters)(equalTo(RegisterOffset(doubles = 1))) &&
        assert(record2.usedRegisters)(equalTo(record2.registers.foldLeft(0)(_ + _.usedRegisters))) &&
        assert(record3.length)(equalTo(1)) &&
        assert(record3.fields.length)(equalTo(1)) &&
        assert(record3.registers.length)(equalTo(1)) &&
        assert(record3.fields(0).value.asInstanceOf[Primitive[Binding, String]].primitiveType)(
          equalTo(PrimitiveType.String(Validation.None))
        ) &&
        assert(record3.registers(0).usedRegisters)(equalTo(RegisterOffset(objects = 1))) &&
        assert(record3.usedRegisters)(equalTo(record3.registers.foldLeft(0)(_ + _.usedRegisters)))
      },
      test("updates record default value") {
        assert(Reflect.some(Reflect.int[Binding]).binding.defaultValue)(isNone) &&
        assert(Reflect.some(Reflect.int[Binding]).binding.defaultValue(Some(0)).defaultValue.get.apply())(
          equalTo(Some(0))
        )
      },
      test("has access to record documentation") {
        assert(Reflect.some(Reflect.int[Binding]).doc)(equalTo(Doc.Empty))
      },
      test("updates record documentation") {
        assert(Reflect.some(Reflect.int[Binding]).doc("Some[Int] (updated)").doc)(equalTo(Doc("Some[Int] (updated)")))
      },
      test("has access to record examples") {
        assert(Reflect.some(Reflect.int[Binding]).binding.examples)(equalTo(Seq.empty))
      },
      test("updates record examples") {
        assert(Reflect.some(Reflect.int[Binding]).binding.examples(Some(2)).examples)(equalTo(Some(2) :: Nil))
      }
    ),
    suite("Reflect.Variant")(
      test("has consistent equals and hashCode") {
        val variant1 = Reflect.option(Reflect.int[Binding])
        val variant2 = Reflect.either(Reflect.byte[Binding], Reflect.long[Binding])

        println(variant1.noBinding)
        println(variant1)
        
        assert(variant1)(equalTo(variant1)) &&
        assert(variant1.hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.noBinding: Any)(equalTo(variant1)) &&
        assert(variant1.noBinding.hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.defaultValue(None))(equalTo(variant1)) &&
        assert(variant1.defaultValue(None).hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant1.examples(Some(2)))(equalTo(variant1)) &&
        assert(variant1.examples(Some(2)).hashCode)(equalTo(variant1.hashCode)) &&
        assert(variant2)(equalTo(variant2)) &&
        assert(variant2.hashCode)(equalTo(variant2.hashCode)) &&
        assert(variant1.doc("Option[Int] (updated)"))(not(equalTo(variant1))) &&
        assert(variant2: Any)(not(equalTo(variant1)))
      },
      test("updates variant default value") {
        assert(Reflect.option(Reflect.int[Binding]).binding.defaultValue)(isNone) &&
        assert(Reflect.option(Reflect.int[Binding]).binding.defaultValue(Some(0)).defaultValue.get.apply())(
          equalTo(Some(0))
        )
      },
      test("has access to variant documentation") {
        assert(Reflect.either(Reflect.byte[Binding], Reflect.long[Binding]).doc)(equalTo(Doc.Empty))
      },
      test("updates variant documentation") {
        assert(Reflect.option(Reflect.int[Binding]).doc("Option[Int] (updated)").doc)(
          equalTo(Doc("Option[Int] (updated)"))
        )
      },
      test("has access to variant examples") {
        assert(Reflect.option(Reflect.int[Binding]).binding.examples)(equalTo(Seq.empty))
      },
      test("updates variant examples") {
        assert(Reflect.option(Reflect.int[Binding]).binding.examples(Some(2)).examples)(equalTo(Seq(Some(2))))
      }
    ),
    suite("Reflect.Sequence")(
      test("has consistent equals and hashCode") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Double]] // should be ignored in equals and hashCode
        )
        val sequence2 = sequence1.copy(element =
          Primitive(PrimitiveType.Double(Validation.None), Binding.Primitive.double, TypeName.double, Doc("text"), Nil)
        )
        val sequence3 = sequence1.copy(typeName = TypeName[List[Double]](Namespace("scala" :: Nil, Nil), "List2"))
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
      test("updates sequence default value") {
        assert(Reflect.vector(Reflect.int[Binding]).binding.defaultValue)(isNone) &&
        assert(Reflect.vector(Reflect.int[Binding]).binding.defaultValue(Vector.empty).defaultValue.get.apply())(
          equalTo(Vector.empty)
        )
      },
      test("has access to sequence documentation") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Double]],
          doc = Doc("List of doubles"),
          modifiers = Nil
        )
        assert(sequence1.doc)(equalTo(Doc("List of doubles")))
      },
      test("updates sequence documentation") {
        assert(Reflect.array(Reflect.int[Binding]).doc("Array (updated)").doc)(equalTo(Doc("Array (updated)")))
      },
      test("has access to record examples") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = Binding.Seq[List, Double](
            constructor = SeqConstructor.listConstructor,
            deconstructor = SeqDeconstructor.listDeconstructor,
            examples = Seq(List(0.1, 0.2, 0.3))
          )
        )
        assert(sequence1.binding.examples)(equalTo(Seq(List(0.1, 0.2, 0.3))))
      },
      test("updates sequence examples") {
        assert(Reflect.set(Reflect.int[Binding]).binding.examples(Set(1, 2, 3)).examples)(equalTo(Seq(Set(1, 2, 3))))
      }
    ),
    suite("Reflect.Map")(
      test("has consistent equals and hashCode") {
        val map1 = Reflect.Map[Binding, Short, Float, Map](
          key = Reflect.short,
          value = Reflect.float,
          typeName = TypeName.map[Short, Float],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Short, Float]] // should be ignored in equals and hashCode
        )
        val map2 = map1.copy(key =
          Primitive(
            PrimitiveType.Short(Validation.Numeric.Positive),
            Binding.Primitive.short,
            TypeName.short
          )
        )
        val map3 = map1.copy(value =
          Primitive(PrimitiveType.Float(Validation.None), Binding.Primitive.float, TypeName.float, Doc("text"), Nil)
        )
        val map4 = map1.copy(typeName = TypeName[Map[Short, Float]](Namespace("scala" :: Nil, Nil), "Map2"))
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
      test("updates map default value") {
        assert(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]).binding.defaultValue)(isNone) &&
        assert(
          Reflect
            .map(Reflect.int[Binding], Reflect.long[Binding])
            .defaultValue(Map.empty)
            .binding
            .defaultValue
            .get
            .apply()
        )(
          equalTo(Map.empty[Int, Long])
        )
      },
      test("has access to map documentation") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]], // should be ignored in equals and hashCode
          doc = Doc("Map of Int to Long"),
          modifiers = Nil
        )
        assert(map1.doc)(equalTo(Doc("Map of Int to Long")))
      },
      test("updates map documentation") {
        assert(Reflect.map(Reflect.int[Binding], Reflect.long[Binding]).doc("Map (updated)").doc)(
          equalTo(Doc("Map (updated)"))
        )
      },
      test("has access to map examples") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map,
            examples = Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil
          )
        )
        assert(map1.binding.examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil))
      },
      test("updates map examples") {
        assert(
          Reflect
            .map(Reflect.int[Binding], Reflect.long[Binding])
            .binding
            .examples(Map(1 -> 1L, 2 -> 2L, 3 -> 3L))
            .examples
        )(
          equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil)
        )
      }
    ),
    suite("Reflect.Dynamic")(
      test("has consistent equals and hashCode") {
        val dynamic1 = Reflect.dynamic[Binding]
        val dynamic2 = dynamic1.copy(dynamicBinding = null.asInstanceOf[Binding.Dynamic])
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
      test("updates dynamic default value") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.binding.defaultValue)(isNone) &&
        assert(
          dynamic1.binding
            .defaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            .defaultValue
            .get
            .apply()
        )(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      },
      test("has access to dynamic documentation") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          doc = Doc("Dynamic"),
          modifiers = Nil
        )
        assert(dynamic1.doc)(equalTo(Doc("Dynamic")))
      },
      test("updates dynamic documentation") {
        assert(Reflect.dynamic[Binding].doc("Dynamic (updated)").doc)(equalTo(Doc("Dynamic (updated)")))
      },
      test("has access to dynamic examples") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(examples = DynamicValue.Primitive(PrimitiveValue.Int(0)) :: Nil)
        )
        assert(dynamic1.binding.examples)(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0)) :: Nil))
      },
      test("updates dynamic examples") {
        val dynamic1 = Reflect.dynamic[Binding]
        assert(dynamic1.binding.examples(DynamicValue.Primitive(PrimitiveValue.Int(1))).examples)(
          equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1)) :: Nil)
        )
      }
    ),
    suite("Reflect.Deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.int[Binding]
        val deferred4 = Primitive(PrimitiveType.Int(Validation.Numeric.Positive), Binding.Primitive.int, TypeName.int)
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
        assert(deferred5)(not(equalTo(deferred1)))
      },
      test("updates deferred default value") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(deferred1.binding.defaultValue)(isNone) &&
        assert(deferred1.binding.defaultValue(1).defaultValue.get.apply())(equalTo(1))
      },
      test("has access to deferred documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive.int,
            TypeName.int,
            Doc("Int (positive)"),
            Nil
          )
        }
        assert(deferred1.doc)(equalTo(Doc("Int (positive)")))
      },
      test("updates sequence documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        assert(deferred1.doc("Deferred (updated)").doc)(equalTo(Doc("Deferred (updated)")))
      },
      test("has access to deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive(examples = Seq(1, 2, 3)),
            TypeName.int
          )
        }
        assert(deferred1.binding.examples)(equalTo(Seq(1, 2, 3)))
      },
      test("updates deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive(examples = Seq(1, 2, 3)),
            TypeName.int
          )
        }
        assert(deferred1.binding.examples(1, 2).examples)(equalTo(Seq(1, 2)))
      }
    )
  )
}
