package cloud.golem.runtime.autowire

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Reflect, Schema, TypeName}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

/**
 * Encode/decode between **plain JS values** (as produced/consumed by the generated TS bridge) and Scala values
 * using `zio.blocks.schema.Schema`.
 *
 * JS representation conventions:
 * - **records**: JS objects with fields
 * - **sequences**: JS arrays
 * - **options**: `null` for None, inner value for Some
 */
// Used by plugin-generated Scala shims (in package `cloud.golem.internal`), so it must be visible
// outside `cloud.golem.runtime.autowire` but still not part of the public API surface.
private[golem] object JsPlainSchemaCodec {
  def decode[A](value: js.Any)(implicit schema: Schema[A]): Either[String, A] =
    schema.fromDynamicValue(fromJs(schema.reflect.asInstanceOf[Reflect.Bound[Any]], value)).left.map(_.toString)

  def encode[A](value: A)(implicit schema: Schema[A]): js.Any =
    toJs(schema.reflect.asInstanceOf[Reflect.Bound[Any]], schema.toDynamicValue(value))

  private def fromJs(reflect0: Reflect.Bound[Any], value0: js.Any): DynamicValue = {
    // Wrapper: treat as underlying.
    reflect0.asWrapperUnknown match {
      case Some(w) =>
        return fromJs(w.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]], value0)
      case None => ()
    }

    // Option-like: Variant(None/Some(value)).
    optionInfo(reflect0) match {
      case Some((innerRef, usesRecordWrapper)) =>
        if (js.isUndefined(value0) || value0 == null) {
          // Option(None): payload is ignored by our detection; use empty record.
          return DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
        } else {
          val inner = fromJs(innerRef, value0)
          val payload =
            if (usesRecordWrapper) DynamicValue.Record(Vector("value" -> inner))
            else inner
          return DynamicValue.Variant("Some", payload)
        }
      case None => ()
    }

    reflect0.asPrimitive match {
      case Some(p) =>
        val tn = p.primitiveType.typeName
        val pv: PrimitiveValue =
          if (tn == TypeName.unit) PrimitiveValue.Unit
          else if (tn == TypeName.string) PrimitiveValue.String(value0.asInstanceOf[String])
          else if (tn == TypeName.boolean) PrimitiveValue.Boolean(value0.asInstanceOf[Boolean])
          else if (tn == TypeName.byte) PrimitiveValue.Byte(value0.asInstanceOf[Double].toByte)
          else if (tn == TypeName.short) PrimitiveValue.Short(value0.asInstanceOf[Double].toShort)
          else if (tn == TypeName.int) PrimitiveValue.Int(value0.asInstanceOf[Double].toInt)
          else if (tn == TypeName.long) PrimitiveValue.Long(value0.asInstanceOf[Double].toLong)
          else if (tn == TypeName.float) PrimitiveValue.Float(value0.asInstanceOf[Double].toFloat)
          else if (tn == TypeName.double) PrimitiveValue.Double(value0.asInstanceOf[Double])
          else throw new IllegalArgumentException(s"Unsupported primitive for JS codec: $tn")
        DynamicValue.Primitive(pv)

      case None =>
        reflect0.asRecord match {
          case Some(rec) =>
            val dyn = value0.asInstanceOf[js.Dynamic]
            val fields =
              rec.fields.map { f =>
                val fv = dyn.selectDynamic(f.name).asInstanceOf[js.Any]
                f.name -> fromJs(f.value.asInstanceOf[Reflect.Bound[Any]], fv)
              }.toVector
            DynamicValue.Record(fields)

          case None =>
            reflect0.asSequenceUnknown match {
              case Some(seq) =>
                val arr = value0.asInstanceOf[js.Array[js.Any]]
                val elems =
                  arr.toVector.map(v => fromJs(seq.sequence.element.asInstanceOf[Reflect.Bound[Any]], v)).toVector
                DynamicValue.Sequence(elems)

              case None =>
                reflect0.asMapUnknown match {
                  case Some(map) =>
                    val obj = value0.asInstanceOf[js.Dictionary[js.Any]]
                    val keyRef   = map.map.key.asInstanceOf[Reflect.Bound[Any]]
                    val valueRef = map.map.value.asInstanceOf[Reflect.Bound[Any]]
                    val entries =
                      obj.toVector.map { case (k, v) =>
                        val kd = fromJs(keyRef, k.asInstanceOf[js.Any])
                        val vd = fromJs(valueRef, v)
                        (kd, vd)
                      }.toVector
                    DynamicValue.Map(entries)

                  case None =>
                    throw new IllegalArgumentException(s"Unsupported schema reflect for JS codec: ${reflect0.nodeType}")
                }
            }
        }
    }
  }

  private def toJs(reflect0: Reflect.Bound[Any], value: DynamicValue): js.Any = {
    // Wrapper: treat as underlying.
    reflect0.asWrapperUnknown match {
      case Some(w) =>
        return toJs(w.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]], value)
      case None => ()
    }

    // Option-like: Variant(None/Some(value)).
    optionInfo(reflect0) match {
      case Some((innerRef, usesRecordWrapper)) =>
        value match {
          case DynamicValue.Variant("None", _) => null
          case DynamicValue.Variant("Some", payload) =>
            val inner =
              if (usesRecordWrapper)
                payload match {
                  case DynamicValue.Record(fields) =>
                    fields.find(_._1 == "value").map(_._2).getOrElse(DynamicValue.Record(Vector.empty))
                  case other => other
                }
              else payload
            return toJs(innerRef, inner)
          case _ =>
            // Best-effort: treat unknown as null
            return null
        }
      case None => ()
    }

    (reflect0.asPrimitive, reflect0.asRecord, reflect0.asSequenceUnknown, reflect0.asMapUnknown) match {
      case (Some(_), _, _, _) =>
        value match {
          case DynamicValue.Primitive(pv) =>
            pv match {
              case PrimitiveValue.Unit         => null
              case PrimitiveValue.String(v)    => v
              case PrimitiveValue.Boolean(v)   => v
              case PrimitiveValue.Byte(v)      => v.toDouble
              case PrimitiveValue.Short(v)     => v.toDouble
              case PrimitiveValue.Int(v)       => v.toDouble
              case PrimitiveValue.Long(v)      => v.toDouble
              case PrimitiveValue.Float(v)     => v.toDouble
              case PrimitiveValue.Double(v)    => v
              case PrimitiveValue.BigInt(v)    => v.toString
              case PrimitiveValue.BigDecimal(v)=> v.toString
              case PrimitiveValue.Char(v)      => v.toString
              case PrimitiveValue.UUID(v)      => v.toString
              case other                       => other.toString
            }
          case _ => null
        }

      case (_, Some(rec), _, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val obj = js.Dictionary.empty[js.Any]
            // Keep schema field order
            val map = fields.toMap
            rec.fields.foreach { f =>
              map.get(f.name).foreach { dv =>
                obj.update(f.name, toJs(f.value.asInstanceOf[Reflect.Bound[Any]], dv))
              }
            }
            obj.asInstanceOf[js.Any]
          case _ => js.Dictionary.empty[js.Any].asInstanceOf[js.Any]
        }

      case (_, _, Some(seq), _) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            elements.map(e => toJs(seq.sequence.element.asInstanceOf[Reflect.Bound[Any]], e)).toJSArray
          case _ => new js.Array[js.Any]()
        }

      case (_, _, _, Some(map)) =>
        value match {
          case DynamicValue.Map(entries) =>
            val dict = js.Dictionary.empty[js.Any]
            entries.foreach { case (k, v) =>
              val keyJs = toJs(map.map.key.asInstanceOf[Reflect.Bound[Any]], k)
              val keyStr = keyJs.asInstanceOf[String]
              dict.update(keyStr, toJs(map.map.value.asInstanceOf[Reflect.Bound[Any]], v))
            }
            dict.asInstanceOf[js.Any]
          case _ => js.Dictionary.empty[js.Any].asInstanceOf[js.Any]
        }

      case _ =>
        null
    }
  }

  // Ported (lightly) from DataInterop: detect Option-like schema (Variant(None/Some(value))).
  private def optionInfo(reflect: Reflect.Bound[Any]): Option[(Reflect.Bound[Any], Boolean)] =
    reflect.asVariant.flatMap { variant =>
      def simpleCaseName(name: String): String = {
        val afterDot =
          name.lastIndexOf('.') match {
            case -1 => name
            case i  => name.substring(i + 1)
          }
        if (afterDot.endsWith("$")) afterDot.dropRight(1) else afterDot
      }

      val noneCase = variant.cases.find(t => simpleCaseName(t.name) == "None")
      val someCase = variant.cases.find(t => simpleCaseName(t.name) == "Some")
      if (noneCase.isEmpty || someCase.isEmpty) None
      else {
        val someValue = someCase.get.value.asInstanceOf[Reflect.Bound[Any]]
        someValue.asRecord match {
          case Some(someRec) =>
            someRec.fieldByName("value") match {
              case Some(valueField) => Some((valueField.value.asInstanceOf[Reflect.Bound[Any]], true))
              case None             => Some((someValue, false))
            }
          case None =>
            Some((someValue, false))
        }
      }
    }
}


