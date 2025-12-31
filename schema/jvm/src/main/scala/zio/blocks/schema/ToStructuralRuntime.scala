package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

/**
 * Runtime helpers for transforming nominal schemas to structural schemas.
 * JVM only - requires reflection for structural type access.
 */
object ToStructuralRuntime {

  /**
   * Transform a product schema (case class) to its structural equivalent.
   */
  def transformProductSchema[A, S](schema: Schema[A]): Schema[S] = {
    schema.reflect match {
      case record: Reflect.Record[Binding, A] @unchecked =>
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
        
        val fieldInfos = record.fields.map { field =>
          (field.name, field.value.asInstanceOf[Reflect.Bound[Any]])
        }

        // Calculate register usage - same as original
        val totalRegisters = binding.constructor.usedRegisters

        // Generate normalized type name
        val typeName = normalizeTypeName(fieldInfos.toList.map { case (name, reflect) =>
          (name, reflect.typeName.name)
        })

        // Create structural schema with new bindings
        new Schema[S](
          new Reflect.Record[Binding, S](
            fields = record.fields.map { field =>
              field.value.asInstanceOf[Reflect.Bound[Any]].asTerm[S](field.name)
            },
            typeName = new TypeName[S](new Namespace(Nil, Nil), typeName, Nil),
            recordBinding = new Binding.Record[S](
              constructor = new Constructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def construct(in: Registers, baseOffset: RegisterOffset): S = {
                  // Use the original constructor to build the nominal value,
                  // then cast to structural (works on JVM because case class methods
                  // satisfy structural type requirements)
                  val nominal = binding.constructor.construct(in, baseOffset)
                  nominal.asInstanceOf[S]
                }
              },
              deconstructor = new Deconstructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def deconstruct(out: Registers, baseOffset: RegisterOffset, in: S): Unit = {
                  // Cast structural back to nominal and use original deconstructor
                  binding.deconstructor.deconstruct(out, baseOffset, in.asInstanceOf[A])
                }
              }
            ),
            doc = record.doc,
            modifiers = record.modifiers
          )
        )

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }
  }

  /**
   * Transform a tuple schema to its structural equivalent.
   */
  def transformTupleSchema[A, S](schema: Schema[A]): Schema[S] = {
    schema.reflect match {
      case _: Reflect.Record[Binding, A] @unchecked =>
        // Tuples are also represented as Record in the schema
        transformProductSchema[A, S](schema)

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }
  }

  /**
   * Generate a normalized type name for a structural type.
   * Fields are sorted alphabetically for deterministic naming.
   */
  private def normalizeTypeName(fields: List[(String, String)]): String = {
    val sorted = fields.sortBy(_._1)
    sorted.map { case (name, typeName) =>
      s"$name:${simplifyTypeName(typeName)}"
    }.mkString("{", ",", "}")
  }

  /**
   * Simplify type names for display (e.g., "scala.Int" -> "Int")
   */
  private def simplifyTypeName(typeName: String): String = {
    typeName
      .replace("scala.", "")
      .replace("java.lang.", "")
      .replace("Predef.", "")
  }
}

