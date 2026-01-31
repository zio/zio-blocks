import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.derive._

object DeriveShowRecursionExample extends App {

  /**
   * A simple type class for converting values to their string representation.
   */
  trait Show[A] {
    def show(value: A): String
  }

  object Show {
    def apply[A](implicit ev: Show[A]): Show[A] = ev

    /**
     * Creates a Show instance from a function.
     */
    def instance[A](f: A => String): Show[A] = new Show[A] {
      def show(value: A): String = f(value)
    }
  }

  /**
   * A deriver for the Show type class that implements the Deriver trait.
   */
  object ShowDeriver extends Deriver[Show] {

    val fieldSeparator          = ", "
    val fieldNameValueSeparator = " = "

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] =
      Lazy(deriveShow(new Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)))

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveShow(
        new Reflect.Record(
          fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
          typeName,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveShow(
        new Reflect.Variant(
          cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
          typeName,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeName: TypeName[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
      deriveShow(
        new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeName, binding, doc, modifiers)
      )
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
      deriveShow(
        new Reflect.Map(
          key.asInstanceOf[Reflect[Binding, K]],
          value.asInstanceOf[Reflect[Binding, V]],
          typeName,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[DynamicValue]] =
      Lazy(deriveShow(new Reflect.Dynamic(binding, TypeName.dynamicValue, doc, modifiers)))

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeName: TypeName[A],
      wrapperPrimitiveType: Option[PrimitiveType[A]],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveShow(
        new Reflect.Wrapper(
          wrapped.asInstanceOf[Reflect[Binding, B]],
          typeName,
          wrapperPrimitiveType,
          binding,
          doc,
          modifiers
        )
      )
    }

    // Internal type alias for cleaner code
    private type TC[A] = Show[A]

    // Private field info class for record handling
    private final class FieldInfo(
      val name: String,
      val show: Show[Any],
      val offset: RegisterOffset,
      val fieldType: Byte
    )

    // Private derivation logic
    private[this] def deriveShow[F[_, _], A](reflect: Reflect[F, A]): Show[A] =
      if (reflect.isPrimitive) {
        derivePrimitiveShow(reflect.asPrimitive.get)
      } else if (reflect.isVariant) {
        deriveVariantShow(reflect.asVariant.get)
      } else if (reflect.isSequence) {
        deriveSequenceShow(reflect.asSequenceUnknown.get.sequence).asInstanceOf[Show[A]]
      } else if (reflect.isMap) {
        deriveMapShow(reflect.asMapUnknown.get.map).asInstanceOf[Show[A]]
      } else if (reflect.isRecord) {
        deriveRecordShow(reflect.asRecord.get)
      } else if (reflect.isWrapper) {
        deriveWrapperShow(reflect.asWrapperUnknown.get.wrapper.asInstanceOf[Reflect.Wrapper[F, A, Any]])
      } else if (reflect.isDynamic) {
        deriveDynamicShow(reflect.asDynamic.get).asInstanceOf[Show[A]]
      } else {
        // Fallback for any other cases (e.g., Deferred)
        Show.instance(_.toString)
      }

    private[this] def derivePrimitiveShow[F[_, _], A](
      primitive: Reflect.Primitive[F, A]
    ): Show[A] =
      // Check if binding is a raw Binding (needs derivation)
      // vs BindingInstance (already has a derived instance)
      if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
        // This is a raw Binding - we need to derive the Show instance ourselves
        Show.instance[A] { a =>
          // Pattern match on the primitive type to format appropriately
          primitive.primitiveType match {
            case PrimitiveType.Unit =>
              // Unit has only one value: ()
              "()"

            case PrimitiveType.Char(_) =>
              // Characters are shown with single quotes: 'a'
              s"'$a'"

            case PrimitiveType.String(_) =>
              // Strings are shown with double quotes: "hello"
              s""""$a""""

            case _ =>
              // All other primitives (Int, Long, Double, etc.)
              // use their natural string representation
              String.valueOf(a)
          }
        }
      } else {
        println("-----")
        // This is a BindingInstance - it wraps an already-derived Show instance
        // This happens when a type manually provides its own Show instance
        // We just extract and return that instance
        //
        // The type parameters of BindingInstance:
        // - TC: The type class (Show in our case)
        // - ?: The binding type marker
        // - A: The value type
        primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
      }

    private[this] def deriveRecordShow[F[_, _], A](
      record: Reflect.Record[F, A]
    ): Show[A] =
      // Check if there's a custom instance provided via BindingInstance
      if (!record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
      } else {
        val binding  = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields   = record.fields
        val typeName = record.typeName
        val len      = fields.length
        val infos    = new Array[FieldInfo](len)

        var offset: RegisterOffset = RegisterOffset.Zero
        var idx                    = 0
        while (idx < len) {
          val field        = fields(idx)
          val fieldReflect = field.value
          val show         = deriveShow(fieldReflect)
          val fieldType    = getFieldType(fieldReflect)
          infos(idx) = new FieldInfo(field.name, show.asInstanceOf[Show[Any]], offset, fieldType)
          offset = RegisterOffset.add(offset, getValueOffset(fieldReflect))
          idx += 1
        }

        new Show[A] {
          private[this] val deconstructor = binding.deconstructor
          private[this] val fieldInfos    = infos
          private[this] val fieldLen      = len
          private[this] val usedRegisters = offset
          private[this] val name          = typeName.name

          def show(value: A): String =
            if (value == null) {
              "null"
            } else {
              val regs = Registers(usedRegisters)
              deconstructor.deconstruct(regs, RegisterOffset.Zero, value)

              val sb = new StringBuilder
              sb.append(name)
              sb.append("(")

              var idx   = 0
              var first = true
              while (idx < fieldLen) {
                val info       = fieldInfos(idx)
                val fieldValue = getFieldValue(regs, info.offset, info.fieldType)
                if (fieldValue != null) {
                  if (!first) {
                    sb.append(fieldSeparator)
                  }
                  first = false
                  sb.append(info.name)
                  sb.append(fieldNameValueSeparator)
                  sb.append(info.show.show(fieldValue))
                }
                idx += 1
              }
              sb.append(")")
              sb.toString
            }
        }
      }

    private[this] def deriveVariantShow[F[_, _], A](
      variant: Reflect.Variant[F, A]
    ): Show[A] =
      // Check if there's a custom instance provided via BindingInstance
      if (!variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
        variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
      } else {
        // Check if it's an Option type for special handling
        option(variant) match {
          case Some(valueReflect) =>
            val valueShow = deriveShow(valueReflect).asInstanceOf[Show[Any]]
            new Show[Option[Any]] {
              def show(value: Option[Any]): String = value match {
                case None    => "None"
                case Some(v) => s"Some(${valueShow.show(v)})"
              }
            }.asInstanceOf[Show[A]]

          case None =>
            val binding   = variant.variantBinding.asInstanceOf[Binding.Variant[A]]
            val cases     = variant.cases
            val len       = cases.length
            val caseShows = new Array[Show[Any]](len)

            var idx = 0
            while (idx < len) {
              val case_       = cases(idx)
              val caseReflect = case_.value
              caseShows(idx) = deriveShow(caseReflect).asInstanceOf[Show[Any]]
              idx += 1
            }

            new Show[A] {
              private[this] val discriminator = binding.discriminator
              private[this] val shows         = caseShows
              private[this] val caseLen       = len

              def show(value: A): String =
                if (value == null) "null"
                else {
                  val caseIdx = discriminator.discriminate(value)
                  if (caseIdx >= 0 && caseIdx < caseLen) {
                    shows(caseIdx).show(value.asInstanceOf[Any])
                  } else {
                    value.toString
                  }
                }
            }
        }
      }

    private[this] def deriveSequenceShow[F[_, _], C[_], A](
      sequence: Reflect.Sequence[F, A, C]
    ): Show[C[A]] =
      // Check if there's a custom instance provided via BindingInstance
      if (!sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, C[A]]].instance.force
      } else {
        val binding     = sequence.seqBinding.asInstanceOf[Binding.Seq[C, A]]
        val elementShow = deriveShow(sequence.element).asInstanceOf[Show[Any]]
        val typeName    = sequence.typeName

        new Show[C[A]] {
          private[this] val deconstructor = binding.deconstructor
          private[this] val elemShow      = elementShow
          private[this] val name          = typeName.name

          def show(value: C[A]): String =
            if (value == null) "null"
            else {
              val sb = new StringBuilder
              sb.append(name)
              sb.append("(")

              val len = deconstructor.size(value)
              var idx = 0
              while (idx < len) {
                if (idx > 0) {
                  sb.append(fieldSeparator)
                }
                val elem = deconstructor.deconstruct(value)
                sb.append(elemShow.show(elem))
                idx += 1
              }
              sb.append(")")
              sb.toString
            }
        }
      }

    private[this] def deriveMapShow[F[_, _], M[_, _], K, V](
      map: Reflect.Map[F, K, V, M]
    ): Show[M[K, V]] =
      // Check if there's a custom instance provided via BindingInstance
      if (!map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        map.mapBinding.asInstanceOf[BindingInstance[TC, ?, M[K, V]]].instance.force
      } else {
        val binding   = map.mapBinding.asInstanceOf[Binding.Map[M, K, V]]
        val keyShow   = deriveShow(map.key).asInstanceOf[Show[Any]]
        val valueShow = deriveShow(map.value).asInstanceOf[Show[Any]]
        val typeName  = map.typeName

        new Show[M[K, V]] {
          private[this] val deconstructor = binding.deconstructor
          private[this] val kShow         = keyShow
          private[this] val vShow         = valueShow
          private[this] val name          = typeName.name

          def show(value: M[K, V]): String =
            if (value == null) "null"
            else {
              val sb = new StringBuilder
              sb.append(name)
              sb.append("(")

              val entries = deconstructor.deconstruct(value)
              var first   = true
              while (entries.hasNext) {
                val entry = entries.next()
                if (!first) {
                  sb.append(fieldSeparator)
                }
                first = false
                val k = deconstructor.getKey(entry)
                val v = deconstructor.getValue(entry)
                sb.append(kShow.show(k))
                sb.append(" -> ")
                sb.append(vShow.show(v))
              }
              sb.append(")")
              sb.toString
            }
        }
      }

    private[this] def deriveWrapperShow[F[_, _], A, B](
      wrapper: Reflect.Wrapper[F, A, B]
    ): Show[A] =
      // Check if there's a custom instance provided via BindingInstance
      if (!wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
        wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
      } else {
        val binding     = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, B]]
        val wrappedShow = deriveShow(wrapper.wrapped).asInstanceOf[Show[B]]
        val typeName    = wrapper.typeName

        new Show[A] {
          private[this] val unwrap = binding.unwrap
          private[this] val inner  = wrappedShow
          private[this] val name   = typeName.name

          def show(value: A): String =
            if (value == null) "null"
            else {
              val unwrapped = unwrap(value)
              if (name.nonEmpty) {
                s"$name(${inner.show(unwrapped)})"
              } else {
                inner.show(unwrapped)
              }
            }
        }
      }

    private[this] def deriveDynamicShow[F[_, _]](
      dynamic: Reflect.Dynamic[F]
    ): Show[DynamicValue] =
      // Check if there's a custom instance provided via BindingInstance
      if (!dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) {
        dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, DynamicValue]].instance.force
      } else {
        Show.instance(_.toString)
      }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Detects if a variant is an Option type and returns the inner value
     * reflect.
     */
    private[this] def option[F[_, _], A](
      variant: Reflect.Variant[F, A]
    ): Option[Reflect[F, ?]] = {
      val cases = variant.cases
      if (cases.length == 2) {
        val case0 = cases(0)
        val case1 = cases(1)
        // Check if this is an Option type (None and Some cases)
        if (case0.name == "None" && case1.name == "Some") {
          case1.value.asRecord.flatMap { record =>
            if (record.fields.length == 1) {
              Some(record.fields(0).value)
            } else None
          }
        } else if (case0.name == "Some" && case1.name == "None") {
          case0.value.asRecord.flatMap { record =>
            if (record.fields.length == 1) {
              Some(record.fields(0).value)
            } else None
          }
        } else None
      } else None
    }

    // Field type constants for register access
    private[this] val ObjectType: Byte  = 0
    private[this] val IntType: Byte     = 1
    private[this] val LongType: Byte    = 2
    private[this] val FloatType: Byte   = 3
    private[this] val DoubleType: Byte  = 4
    private[this] val BooleanType: Byte = 5
    private[this] val ByteType: Byte    = 6
    private[this] val CharType: Byte    = 7
    private[this] val ShortType: Byte   = 8
    private[this] val UnitType: Byte    = 9

    private[this] def getFieldType[F[_, _], A](reflect: Reflect[F, A]): Byte =
      if (reflect.isPrimitive) {
        reflect.asPrimitive.get.primitiveType match {
          case _: PrimitiveType.Int       => IntType
          case _: PrimitiveType.Long      => LongType
          case _: PrimitiveType.Float     => FloatType
          case _: PrimitiveType.Double    => DoubleType
          case _: PrimitiveType.Boolean   => BooleanType
          case _: PrimitiveType.Byte      => ByteType
          case _: PrimitiveType.Char      => CharType
          case _: PrimitiveType.Short     => ShortType
          case _: PrimitiveType.Unit.type => UnitType
          case _                          => ObjectType
        }
      } else {
        ObjectType
      }

    private[this] def getValueOffset[F[_, _], A](reflect: Reflect[F, A]): RegisterOffset =
      if (reflect.isPrimitive) {
        reflect.asPrimitive.get.primitiveType match {
          case _: PrimitiveType.Int       => RegisterOffset(ints = 1)
          case _: PrimitiveType.Long      => RegisterOffset(longs = 1)
          case _: PrimitiveType.Float     => RegisterOffset(floats = 1)
          case _: PrimitiveType.Double    => RegisterOffset(doubles = 1)
          case _: PrimitiveType.Boolean   => RegisterOffset(booleans = 1)
          case _: PrimitiveType.Byte      => RegisterOffset(bytes = 1)
          case _: PrimitiveType.Char      => RegisterOffset(chars = 1)
          case _: PrimitiveType.Short     => RegisterOffset(shorts = 1)
          case _: PrimitiveType.Unit.type => RegisterOffset.Zero
          case _                          => RegisterOffset(objects = 1)
        }
      } else {
        RegisterOffset(objects = 1)
      }

    private[this] def getFieldValue(regs: Registers, offset: RegisterOffset, fieldType: Byte): Any =
      fieldType match {
        case IntType     => regs.getInt(offset)
        case LongType    => regs.getLong(offset)
        case FloatType   => regs.getFloat(offset)
        case DoubleType  => regs.getDouble(offset)
        case BooleanType => regs.getBoolean(offset)
        case ByteType    => regs.getByte(offset)
        case CharType    => regs.getChar(offset)
        case ShortType   => regs.getShort(offset)
        case UnitType    => ()
        case _           => regs.getObject(offset)
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
