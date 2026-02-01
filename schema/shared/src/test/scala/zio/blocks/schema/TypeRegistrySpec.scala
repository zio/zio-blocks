package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.test._

object TypeRegistrySpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  object Dog {
    implicit val schema: Schema[Dog] = Schema.derived[Dog]
  }
  object Cat {
    implicit val schema: Schema[Cat] = Schema.derived[Cat]
  }
  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived[Animal]
  }

  case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] = Schema[Long].transform((l: Long) => UserId(l), (u: UserId) => u.value)
  }

  def spec: Spec[TestEnvironment, Any] = suite("TypeRegistrySpec")(
    suite("TypeRegistry")(
      suite("empty")(
        test("creates an empty registry") {
          assertTrue(
            TypeRegistry.empty.isEmpty,
            TypeRegistry.empty.size == 0
          )
        }
      ),
      suite("default")(
        test("contains primitive bindings") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupPrimitive[Int].isDefined,
            reg.lookupPrimitive[String].isDefined,
            reg.lookupPrimitive[Boolean].isDefined,
            reg.lookupPrimitive[Long].isDefined,
            reg.lookupPrimitive[Double].isDefined,
            reg.lookupPrimitive[Float].isDefined,
            reg.lookupPrimitive[Short].isDefined,
            reg.lookupPrimitive[Byte].isDefined,
            reg.lookupPrimitive[Char].isDefined,
            reg.lookupPrimitive[Unit].isDefined,
            reg.lookupPrimitive[BigInt].isDefined,
            reg.lookupPrimitive[BigDecimal].isDefined
          )
        },
        test("contains java.time primitive bindings") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupPrimitive[java.time.DayOfWeek].isDefined,
            reg.lookupPrimitive[java.time.Duration].isDefined,
            reg.lookupPrimitive[java.time.Instant].isDefined,
            reg.lookupPrimitive[java.time.LocalDate].isDefined,
            reg.lookupPrimitive[java.time.LocalDateTime].isDefined,
            reg.lookupPrimitive[java.time.LocalTime].isDefined,
            reg.lookupPrimitive[java.time.Month].isDefined,
            reg.lookupPrimitive[java.time.MonthDay].isDefined,
            reg.lookupPrimitive[java.time.OffsetDateTime].isDefined,
            reg.lookupPrimitive[java.time.OffsetTime].isDefined,
            reg.lookupPrimitive[java.time.Period].isDefined,
            reg.lookupPrimitive[java.time.Year].isDefined,
            reg.lookupPrimitive[java.time.YearMonth].isDefined,
            reg.lookupPrimitive[java.time.ZoneId].isDefined,
            reg.lookupPrimitive[java.time.ZoneOffset].isDefined,
            reg.lookupPrimitive[java.time.ZonedDateTime].isDefined
          )
        },
        test("contains java.util primitive bindings") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupPrimitive[java.util.Currency].isDefined,
            reg.lookupPrimitive[java.util.UUID].isDefined
          )
        },
        test("contains dynamic binding") {
          val reg = TypeRegistry.default
          assertTrue(reg.lookupDynamic.isDefined)
        },
        test("contains sequence bindings") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupSeq[List[Int]].isDefined,
            reg.lookupSeq[Vector[String]].isDefined,
            reg.lookupSeq[Set[Long]].isDefined,
            reg.lookupSeq[IndexedSeq[Double]].isDefined,
            reg.lookupSeq[Seq[Boolean]].isDefined,
            reg.lookupSeq[Chunk[Int]].isDefined
          )
        },
        test("contains map bindings") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupMap[Map[String, Int]].isDefined,
            reg.lookupMap[Map[Int, String]].isDefined
          )
        },
        test("is non-empty") {
          assertTrue(TypeRegistry.default.nonEmpty)
        }
      ),
      suite("bind")(
        test("binds record types") {
          val reg = TypeRegistry.default
            .bind(Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]])
          assertTrue(reg.lookupRecord[Person].isDefined)
        },
        test("binds variant types") {
          val reg = TypeRegistry.default
            .bind(Schema[Animal].reflect.binding.asInstanceOf[Binding.Variant[Animal]])
          assertTrue(reg.lookupVariant[Animal].isDefined)
        },
        test("binds wrapper types") {
          val reg = TypeRegistry.default
            .bind(Schema[UserId].reflect.binding.asInstanceOf[Binding.Wrapper[UserId, Long]])
          assertTrue(reg.lookupWrapper[UserId].isDefined)
        },
        test("overwrites existing bindings") {
          val binding1 = Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]]
          val reg1     = TypeRegistry.default.bind(binding1)
          val reg2     = reg1.bind(binding1)
          assertTrue(
            reg1.lookupRecord[Person].isDefined,
            reg2.lookupRecord[Person].isDefined
          )
        },
        test("throws for Seq binding") {
          val binding = Binding.Seq.list[Nothing]
          val result  = try {
            TypeRegistry.empty.bind(binding.asInstanceOf[Binding[Nothing, List[Nothing]]])
            false
          } catch {
            case _: IllegalArgumentException => true
          }
          assertTrue(result)
        },
        test("throws for Map binding") {
          val binding = Binding.Map.map[Nothing, Nothing]
          val result  = try {
            TypeRegistry.empty.bind(binding.asInstanceOf[Binding[Nothing, Map[Nothing, Nothing]]])
            false
          } catch {
            case _: IllegalArgumentException => true
          }
          assertTrue(result)
        }
      ),
      suite("lookup")(
        test("returns None for unbound types") {
          val reg = TypeRegistry.empty
          assertTrue(
            reg.lookupRecord[Person].isEmpty,
            reg.lookupVariant[Animal].isEmpty,
            reg.lookupPrimitive[Int].isEmpty,
            reg.lookupWrapper[UserId].isEmpty,
            reg.lookupDynamic.isEmpty
          )
        },
        test("lookupSeq works with different element types") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupSeq[List[Int]].isDefined,
            reg.lookupSeq[List[String]].isDefined,
            reg.lookupSeq[List[Person]].isDefined
          )
        },
        test("lookupMap works with different key/value types") {
          val reg = TypeRegistry.default
          assertTrue(
            reg.lookupMap[Map[String, Int]].isDefined,
            reg.lookupMap[Map[Int, Person]].isDefined
          )
        }
      ),
      suite("contains")(
        test("returns true for bound types") {
          val reg = TypeRegistry.default
            .bind(Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]])
          assertTrue(reg.contains[Person])
        },
        test("returns false for unbound types") {
          val reg = TypeRegistry.empty
          assertTrue(!reg.contains[Person])
        },
        test("containsSeq returns true for bound sequence types") {
          val reg = TypeRegistry.default
          assertTrue(reg.containsSeq[List[Int]])
        },
        test("containsSeq returns false for unbound sequence types") {
          val reg = TypeRegistry.empty
          assertTrue(!reg.containsSeq[List[Int]])
        },
        test("containsMap returns true for bound map types") {
          val reg = TypeRegistry.default
          assertTrue(reg.containsMap[Map[String, Int]])
        },
        test("containsMap returns false for unbound map types") {
          val reg = TypeRegistry.empty
          assertTrue(!reg.containsMap[Map[String, Int]])
        }
      ),
      suite("equality and hashCode")(
        test("equal registries are equal") {
          val reg1 = TypeRegistry.empty.bind(Binding.Primitive.int)
          val reg2 = TypeRegistry.empty.bind(Binding.Primitive.int)
          assertTrue(reg1 == reg2)
        },
        test("different registries are not equal") {
          val reg1 = TypeRegistry.empty.bind(Binding.Primitive.int)
          val reg2 = TypeRegistry.empty.bind(Binding.Primitive.string)
          assertTrue(reg1 != reg2)
        },
        test("equal registries have same hashCode") {
          val reg1 = TypeRegistry.empty.bind(Binding.Primitive.int)
          val reg2 = TypeRegistry.empty.bind(Binding.Primitive.int)
          assertTrue(reg1.hashCode == reg2.hashCode)
        },
        test("not equal to other types") {
          val reg = TypeRegistry.empty
          assertTrue(!reg.equals("not a registry"))
        }
      ),
      suite("toString")(
        test("includes entry count") {
          val reg = TypeRegistry.empty.bind(Binding.Primitive.int).bind(Binding.Primitive.string)
          assertTrue(reg.toString.contains("2 entries"))
        }
      )
    ),
    suite("TypeId.unapplied")(
      test("returns id with no typeArgs for proper type") {
        val id        = TypeId.of[Int]
        val unapplied = TypeId.unapplied(id)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.fullName == id.fullName
        )
      },
      test("strips type args from applied type") {
        val applied   = TypeId.of[List[Int]]
        val unapplied = TypeId.unapplied(applied)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.name == "List"
        )
      },
      test("different applied types unapply to same constructor") {
        val listInt         = TypeId.of[List[Int]]
        val listString      = TypeId.of[List[String]]
        val unappliedInt    = TypeId.unapplied(listInt)
        val unappliedString = TypeId.unapplied(listString)
        assertTrue(
          unappliedInt.fullName == unappliedString.fullName,
          unappliedInt.typeArgs.isEmpty,
          unappliedString.typeArgs.isEmpty
        )
      },
      test("strips wildcards from type constructor") {
        val listWildcard = TypeId.of[List[_]]
        val unapplied    = TypeId.unapplied(listWildcard)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.name == "List"
        )
      }
    ),
    suite("unified bind method")(
      test("accepts Binding.of result for Record") {
        val binding  = Binding.of[Person]
        val registry = TypeRegistry.default.bind(binding)
        assertTrue(registry.lookupRecord[Person].isDefined)
      },
      test("accepts Binding.of result for Variant") {
        val binding  = Binding.of[Animal]
        val registry = TypeRegistry.default.bind(binding)
        assertTrue(registry.lookupVariant[Animal].isDefined)
      },
      test("accepts Binding.of result for Primitive") {
        val binding  = Binding.of[Int]
        val registry = TypeRegistry.empty.bind(binding)
        assertTrue(registry.lookupPrimitive[Int].isDefined)
      },
      test("Binding.of[List] returns Seq binding") {
        val binding = Binding.of[List]
        assertTrue(binding.isInstanceOf[Binding.Seq[List, Nothing]])
      },
      test("Binding.of[Map] returns Map binding") {
        val binding = Binding.of[Map]
        assertTrue(binding.isInstanceOf[Binding.Map[Map, Nothing, Nothing]])
      },
      test("bind[C[_]] works with Binding.of[List]") {
        val binding  = Binding.of[List]
        val registry = TypeRegistry.empty.bind[List](binding)
        assertTrue(registry.lookupSeq[List[Int]].isDefined)
      },
      test("bind[M[_, _]] works with Binding.of[Map]") {
        val binding  = Binding.of[Map]
        val registry = TypeRegistry.empty.bind[Map](binding)
        assertTrue(registry.lookupMap[Map[String, Int]].isDefined)
      }
    ),
    suite("UnapplySeq")(
      test("UnapplySeq.listInstance provides evidence for List") {
        val u = implicitly[UnapplySeq[List[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.vectorInstance provides evidence for Vector") {
        val u = implicitly[UnapplySeq[Vector[String]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.setInstance provides evidence for Set") {
        val u = implicitly[UnapplySeq[Set[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.indexedSeqInstance provides evidence for IndexedSeq") {
        val u = implicitly[UnapplySeq[IndexedSeq[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.seqInstance provides evidence for Seq") {
        val u = implicitly[UnapplySeq[Seq[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.chunkInstance provides evidence for Chunk") {
        val u = implicitly[UnapplySeq[Chunk[Int]]]
        assertTrue(u != null)
      }
    ),
    suite("UnapplyMap")(
      test("UnapplyMap.mapInstance provides evidence for Map") {
        val u = implicitly[UnapplyMap[Map[String, Int]]]
        assertTrue(u != null)
      }
    )
  )
}
