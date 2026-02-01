import zio.blocks.schema.binding._
import zio.blocks.schema._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

object DeriveShowExperimental extends App {
  trait Show[A] {
    def show(value: A): String
  }

  object DeriveShow extends Deriver[Show] {

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] = Lazy {
      new Show[A] {
        def show(value: A): String =
          primitiveType match {
            // For demonstration purposes, we only handle a few primitive types specially
            // For the rest, we just use toString of primitive value
            case _: PrimitiveType.String => "\"" + value + "\""
            case _: PrimitiveType.Char   => "'" + value + "'"
            case _                       => String.valueOf(value)
          }
      }
    }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Get Show instances for all fields eagerly (force Lazy evaluation)
      val fieldShowInstances: IndexedSeq[(String, Show[Any])] = fields.map { field =>
        val fieldName         = field.name
        val fieldShowInstance = D.instance(field.value.metadata).force.asInstanceOf[Show[Any]]
        (fieldName, fieldShowInstance)
      }

      // Cast fields to use Binding as F (we are going to create Reflect.Record with Binding as F)
      val recordFields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]

      // Cast to Binding.Record to access constructor/deconstructor
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]

      // Build a Reflect.Record to get access to the computed registers for each field
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

      new Show[A] {
        // Implement show by deconstructing value into registers and showing each field
        // The `value` parameter is of type A (the record type), e.g. Person(name: String, age: Int)
        def show(value: A): String = {
          // Create registers with space for all used registers to hold deconstructed field values
          val registers = Registers(recordReflect.usedRegisters)

          // Deconstruct field values of the record into the registers
          recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)

          // Build string representations for all fields
          val fieldStrings = fields.indices.map { i =>
            val (fieldName, showInstance) = fieldShowInstances(i)
            // Get field value from its computed register position
            val fieldValue = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
            s"$fieldName = ${showInstance.show(fieldValue)}"
          }

          // Combine field strings into final record representation
          s"${typeId.name}(${fieldStrings.mkString(", ")})"
        }
      }

    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, _]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Get Show instances for all cases LAZILY (don't force yet!)
      val caseShowInstances: IndexedSeq[Lazy[Show[Any]]] = cases.map { case_ =>
        D.instance(case_.value.metadata).asInstanceOf[Lazy[Show[Any]]]
      }

      // Cast binding to Binding.Variant to access discriminator and matchers
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
      val discriminator  = variantBinding.discriminator
      val matchers       = variantBinding.matchers

      new Show[A] {
        // Implement show by using discriminator and matchers to find the right case
        // The `value` parameter is of type A (the variant type), e.g. an Option[Int] value
        def show(value: A): String = {
          // Use discriminator to determine which case this value belongs to
          val caseIndex = discriminator.discriminate(value)

          // Use matcher to downcast to the specific case type
          val caseValue = matchers(caseIndex).downcastOrNull(value)

          // Just delegate to the case's Show instance - it already knows its own name
          caseShowInstances(caseIndex).force.show(caseValue)
        }
      }
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[C[A]]] = ???

    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeId: TypeId[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[M[K, V]]] = ???

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[DynamicValue]] = ???

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = ???
  }

  case class Person(name: String, age: Int, bestFriend: Option[Person])
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(DeriveShow)
  }

  val person = Person("Alice", 30, Some(Person(name = "Foo", 29, None)))
  println(Person.show.show(person))

//  val person = Person("Alice", 30, bestFriend = Some(Person("Bob", 25, bestFriend = None)))
//  println(Person.show.show(person)) // Expected output: Person(name="Alice", age=30)
}
