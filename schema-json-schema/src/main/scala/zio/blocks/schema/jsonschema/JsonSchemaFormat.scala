package zio.blocks.schema.jsonschema

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive._
import zio.blocks.schema.jsonschema.JsonSchemaValue._

/**
 * JSON Schema format for deriving JSON Schema from ZIO Blocks Schema.
 *
 * This format generates JSON Schema Draft 2020-12 compliant schemas from
 * any Scala type that has a ZIO Blocks Schema.
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.jsonschema._
 *
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * val jsonSchema: JsonSchema[Person] = Schema[Person].derive(JsonSchemaFormat.deriver)
 * println(jsonSchema.toPrettyJson)
 * }}}
 */
object JsonSchemaFormat {

  /**
   * The deriver for generating JSON Schema from ZIO Blocks Schema.
   */
  val deriver: Deriver[JsonSchema] = new Deriver[JsonSchema] {

    override def derivePrimitive[F[_, _], A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    ): Lazy[JsonSchema[A]] = Lazy {
      val baseSchema = primitiveTypeToSchema(primitiveType)
      val withDoc = addDocumentation(baseSchema, doc)
      JsonSchema(withDoc)
    }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[A]] = Lazy {
      val properties = fields.map { field =>
        val fieldSchema = instance(field.value).force.schema
        val fieldName = getFieldName(field)
        fieldName -> fieldSchema
      }

      val required = fields.collect {
        case field if !isOptional(field) => getFieldName(field)
      }

      val baseSchema = JsonSchema.`object`(properties, required, Some(Bool(false)))
      val withTitle = JsonSchema.withTitle(baseSchema, typeName.name)
      val withDoc = addDocumentation(withTitle, doc)
      JsonSchema(withDoc)
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[A]] = Lazy {
      // Check if this is an enum (all cases are singletons/units)
      val isEnum = cases.forall { c =>
        c.value match {
          case r: Reflect.Record[F, ?] @unchecked => r.fields.isEmpty
          case _                                   => false
        }
      }

      val baseSchema = if (isEnum) {
        // For enums, use enum with string values
        val enumValues = cases.map(c => Str(getCaseName(c)): JsonSchemaValue)
        JsonSchema.enum(enumValues)
      } else {
        // For sealed traits with data, use oneOf with discriminator
        val caseSchemas = cases.map { c =>
          val caseSchema = instance(c.value).force.schema
          val caseName = getCaseName(c)
          // Wrap each case in an object with the case name as discriminator
          Obj(
            "type"       -> Str("object"),
            "properties" -> Obj(caseName -> caseSchema),
            "required"   -> Arr(IndexedSeq(Str(caseName)))
          )
        }
        JsonSchema.oneOf(caseSchemas)
      }

      val withTitle = JsonSchema.withTitle(baseSchema, typeName.name)
      val withDoc = addDocumentation(withTitle, doc)
      JsonSchema(withDoc)
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeName: TypeName[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[C[A]]] = Lazy {
      val elementReflect = element.asInstanceOf[Reflect[F, A]]
      val elementSchema = deriveFromReflect(elementReflect).schema
      val baseSchema = JsonSchema.array(elementSchema)
      val withDoc = addDocumentation(baseSchema, doc)
      JsonSchema(withDoc)
    }

    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeName: TypeName[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[M[K, V]]] = Lazy {
      // JSON Schema only supports string keys for objects
      val valueSchema = deriveFromReflect(value.asInstanceOf[Reflect[F, V]]).schema
      val baseSchema = JsonSchema.map(valueSchema)
      val withDoc = addDocumentation(baseSchema, doc)
      JsonSchema(withDoc)
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[DynamicValue]] = Lazy {
      // Dynamic values can be any JSON value
      val baseSchema = Obj.empty // Empty schema allows any value
      val withDoc = addDocumentation(baseSchema, doc)
      JsonSchema(withDoc)
    }

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeName: TypeName[A],
      wrapperPrimitiveType: Option[PrimitiveType[A]],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[JsonSchema[A]] = Lazy {
      // For wrappers, use the wrapped type's schema but with the wrapper's name
      val wrappedSchema = deriveFromReflect(wrapped.asInstanceOf[Reflect[F, B]]).schema
      val withTitle = JsonSchema.withTitle(wrappedSchema, typeName.name)
      val withDoc = addDocumentation(withTitle, doc)
      JsonSchema(withDoc)
    }

    private def deriveFromReflect[F[_, _], A](reflect: Reflect[F, A])(implicit
      F: HasBinding[F],
      D: HasInstance[F]
    ): JsonSchema[A] = reflect match {
      case p: Reflect.Primitive[F, A] @unchecked =>
        derivePrimitive(p.primitiveType, p.typeName, p.binding.asInstanceOf[Binding[BindingType.Primitive, A]], p.doc, p.modifiers).force

      case r: Reflect.Record[F, A] @unchecked =>
        deriveRecord(r.fields, r.typeName, r.binding.asInstanceOf[Binding[BindingType.Record, A]], r.doc, r.modifiers).force

      case v: Reflect.Variant[F, A] @unchecked =>
        deriveVariant(v.cases, v.typeName, v.binding.asInstanceOf[Binding[BindingType.Variant, A]], v.doc, v.modifiers).force

      case s: Reflect.Sequence[F, c, a] @unchecked =>
        deriveSequence(s.element, s.typeName, s.binding.asInstanceOf[Binding[BindingType.Seq[c], c[a]]], s.doc, s.modifiers).force.asInstanceOf[JsonSchema[A]]

      case m: Reflect.Map[F, map, k, v] @unchecked =>
        deriveMap(m.key, m.value, m.typeName, m.binding.asInstanceOf[Binding[BindingType.Map[map], map[k, v]]], m.doc, m.modifiers).force.asInstanceOf[JsonSchema[A]]

      case d: Reflect.Dynamic[F] @unchecked =>
        deriveDynamic(d.binding.asInstanceOf[Binding[BindingType.Dynamic, DynamicValue]], d.doc, d.modifiers).force.asInstanceOf[JsonSchema[A]]

      case w: Reflect.Wrapper[F, A, b] @unchecked =>
        deriveWrapper(w.wrapped, w.typeName, w.wrapperPrimitiveType, w.binding.asInstanceOf[Binding[BindingType.Wrapper[A, b], A]], w.doc, w.modifiers).force

      case deferred: Reflect.Deferred[F, A] @unchecked =>
        deriveFromReflect(deferred.value)
    }

    private def primitiveTypeToSchema[A](primitiveType: PrimitiveType[A]): Obj = primitiveType match {
      case _: PrimitiveType.Unit.type    => JsonSchema.`null`
      case _: PrimitiveType.Boolean.type => JsonSchema.boolean
      case _: PrimitiveType.Byte.type    => JsonSchema.withNumericConstraints(JsonSchema.integer, minimum = Some(-128), maximum = Some(127))
      case _: PrimitiveType.Short.type   => JsonSchema.withNumericConstraints(JsonSchema.integer, minimum = Some(-32768), maximum = Some(32767))
      case _: PrimitiveType.Int.type     => JsonSchema.integer
      case _: PrimitiveType.Long.type    => JsonSchema.integer
      case _: PrimitiveType.Float.type   => JsonSchema.number
      case _: PrimitiveType.Double.type  => JsonSchema.number
      case _: PrimitiveType.Char.type    => JsonSchema.withStringConstraints(JsonSchema.string, minLength = Some(1), maxLength = Some(1))
      case _: PrimitiveType.String.type  => JsonSchema.string
      case _: PrimitiveType.BigInt.type  => JsonSchema.integer
      case _: PrimitiveType.BigDecimal.type => JsonSchema.number
      case _: PrimitiveType.Binary.type  => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("byte")) // base64 encoded
      case _: PrimitiveType.UUID.type    => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("uuid"))
      case _: PrimitiveType.Currency.type => JsonSchema.string
      case _: PrimitiveType.DayOfWeek.type => JsonSchema.enum(IndexedSeq(
        Str("MONDAY"), Str("TUESDAY"), Str("WEDNESDAY"), Str("THURSDAY"),
        Str("FRIDAY"), Str("SATURDAY"), Str("SUNDAY")
      ))
      case _: PrimitiveType.Duration.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("duration"))
      case _: PrimitiveType.Instant.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("date-time"))
      case _: PrimitiveType.LocalDate.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("date"))
      case _: PrimitiveType.LocalDateTime.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("date-time"))
      case _: PrimitiveType.LocalTime.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("time"))
      case _: PrimitiveType.Month.type => JsonSchema.enum(IndexedSeq(
        Str("JANUARY"), Str("FEBRUARY"), Str("MARCH"), Str("APRIL"), Str("MAY"), Str("JUNE"),
        Str("JULY"), Str("AUGUST"), Str("SEPTEMBER"), Str("OCTOBER"), Str("NOVEMBER"), Str("DECEMBER")
      ))
      case _: PrimitiveType.MonthDay.type => JsonSchema.string
      case _: PrimitiveType.OffsetDateTime.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("date-time"))
      case _: PrimitiveType.OffsetTime.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("time"))
      case _: PrimitiveType.Period.type => JsonSchema.string
      case _: PrimitiveType.Year.type => JsonSchema.integer
      case _: PrimitiveType.YearMonth.type => JsonSchema.string
      case _: PrimitiveType.ZonedDateTime.type => JsonSchema.withStringConstraints(JsonSchema.string, format = Some("date-time"))
      case _: PrimitiveType.ZoneId.type => JsonSchema.string
      case _: PrimitiveType.ZoneOffset.type => JsonSchema.string
    }

    private def addDocumentation(schema: Obj, doc: Doc): Obj = doc match {
      case Doc.Empty => schema
      case Doc.Text(value) => schema + ("description" -> Str(value))
      case Doc.Concat(leaves) =>
        val text = leaves.collect { case Doc.Text(v) => v }.mkString(" ")
        if (text.isEmpty) schema
        else schema + ("description" -> Str(text))
    }

    private def getFieldName[F[_, _], A, B](field: Term[F, A, B]): String =
      field.modifiers.collectFirst {
        case Modifier.rename(name) => name
      }.getOrElse(field.name)

    private def getCaseName[F[_, _], A, B](c: Term[F, A, B]): String =
      c.modifiers.collectFirst {
        case Modifier.rename(name) => name
      }.getOrElse(c.name)

    private def isOptional[F[_, _], A, B](field: Term[F, A, B]): Boolean =
      field.value match {
        case v: Reflect.Variant[F, B] @unchecked =>
          v.typeName.namespace.packages == Seq("scala") && v.typeName.name == "Option"
        case _ => false
      }
  }
}
