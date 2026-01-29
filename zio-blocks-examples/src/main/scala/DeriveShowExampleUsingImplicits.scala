import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.BindingType.Wrapper
import zio.blocks.schema.derive.Deriver

object DeriveShowExampleUsingImplicits extends App {

  trait Show[A] {
    def show(value: A): String
  }

  object ShowDeriver extends Deriver[Show] {

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] = Lazy {
      new Show[A] {
        def show(value: A): String =
          primitiveType match {
            case _: PrimitiveType.String => "\"" + value + "\""
            case _: PrimitiveType.Char   => "'" + value + "'"
            case _                       => String.valueOf(value)
          }
      }
    }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, _]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Cast to Binding.Record to access constructor/deconstructor
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]

      // Build a Reflect.Record to get access to the computed registers for each field
      val record = new Reflect.Record[Binding, A](
        fields.asInstanceOf[IndexedSeq[Term[Binding, A, _]]],
        typeName,
        recordBinding,
        doc,
        modifiers
      )

      // Get Show instances for all fields eagerly (force Lazy evaluation)
      val fieldShowInstances: IndexedSeq[(String, Show[Any])] = fields.map { field =>
        val fieldName    = field.name
        val showInstance = D.instance(field.value.metadata).force.asInstanceOf[Show[Any]]
        (fieldName, showInstance)
      }

      new Show[A] {
        def show(value: A): String = {
          // Create registers and deconstruct the value into them
          val regs = Registers(record.usedRegisters)
          recordBinding.deconstructor.deconstruct(regs, RegisterOffset.Zero, value)

          // Read each field from its computed register position
          val fieldStrings = fields.indices.map { i =>
            val (fieldName, showInstance) = fieldShowInstances(i)
            val fieldValue                = record.registers(i).get(regs, RegisterOffset.Zero)
            s"$fieldName = ${showInstance.show(fieldValue)}"
          }

          s"${typeName.name}(${fieldStrings.mkString(", ")})"
        }
      }
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, _]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Cast to Binding.Variant to access matchers
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]

      // Get Show instances for all cases eagerly
      val caseShowInstances: IndexedSeq[Show[Any]] = cases.map { c =>
        D.instance(c.value.metadata).force.asInstanceOf[Show[Any]]
      }

      // Get matchers from the variant binding
      val matchers: Matchers[A] = variantBinding.matchers

      new Show[A] {
        def show(value: A): String = {
          var i              = 0
          var result: String = null
          while (i < matchers.matchers.length && result == null) {
            val matcher   = matchers(i).asInstanceOf[Matcher[Any]]
            val caseValue = matcher.downcastOrNull(value)
            if (caseValue != null) {
              result = caseShowInstances(i).show(caseValue)
            } else {
              i += 1
            }
          }
          if (result == null) {
            throw new IllegalStateException(s"No matching case found for value: $value")
          }
          result
        }
      }
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeName: TypeName[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
      // Cast to Binding.Seq to access deconstructor
      val seqBinding  = binding.asInstanceOf[Binding.Seq[C, A]]
      val elementShow = D.instance(element.metadata).force

      new Show[C[A]] {
        def show(value: C[A]): String = {
          val elements = seqBinding.deconstructor.deconstruct(value).map(elementShow.show).toList
          s"${typeName.name}(${elements.mkString(", ")})"
        }
      }
    }

    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeName: TypeName[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
      // Cast to Binding.Map to access deconstructor
      val mapBinding = binding.asInstanceOf[Binding.Map[M, K, V]]
      val keyShow    = D.instance(key.metadata).force
      val valueShow  = D.instance(value.metadata).force
      val decon      = mapBinding.deconstructor

      new Show[M[K, V]] {
        def show(mapValue: M[K, V]): String = {
          val entries = decon
            .deconstruct(mapValue)
            .map { kv =>
              val k = decon.getKey(kv)
              val v = decon.getValue(kv)
              s"${keyShow.show(k)} -> ${valueShow.show(v)}"
            }
            .toList
          s"${typeName.name}(${entries.mkString(", ")})"
        }
      }
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[DynamicValue]] = Lazy {
      new Show[DynamicValue] {
        def show(value: DynamicValue): String = value.toString
      }
    }

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeName: TypeName[A],
      wrapperPrimitiveType: Option[PrimitiveType[A]],
      binding: Binding[Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: ShowDeriver.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Cast to Binding.Wrapper to access unwrap
      // Binding.Wrapper[A, B] has unwrap: A => B (wrapped => underlying)
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      val wrappedShow    = D.instance(wrapped.metadata).force

      new Show[A] {
        def show(value: A): String = {
          val unwrapped = wrapperBinding.unwrap(value)
          wrappedShow.show(unwrapped)
        }
      }
    }
  }

  // Test with a simple case class
  case class Person(name: String, age: Int)

  implicit val personSchema: Schema[Person] = Schema.derived[Person]
  implicit val personShow: Show[Person]     = Schema[Person].derive(ShowDeriver)

  val str = personShow.show(Person("Alice", 30))
  println(str) // Expected: Person(name = "Alice", age = 30)

  // Test with nested case class
  case class Address(street: String, city: String)

  case class Employee(name: String, age: Int, address: Address)

  implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
  implicit val employeeSchema: Schema[Employee] = Schema.derived[Employee]
  implicit val addressShow: Show[Address]       = Schema[Address].derive(ShowDeriver)
  implicit val employeeShow: Show[Employee]     = Schema[Employee].derive(ShowDeriver)

  val emp = Employee("Bob", 35, Address("123 Main St", "New York"))
  println(employeeShow.show(emp))
  // Expected: Employee(name = "Bob", age = 35, address = Address(street = "123 Main St", city = "New York"))

  // Test with List
  case class Team(name: String, members: List[String])

  implicit val teamSchema: Schema[Team] = Schema.derived[Team]
  implicit val teamShow: Show[Team]     = Schema[Team].derive(ShowDeriver)

  val team = Team("Engineering", List("Alice", "Bob", "Charlie"))
  println(teamShow.show(team))
  // Expected: Team(name = "Engineering", members = List("Alice", "Bob", "Charlie"))
}
