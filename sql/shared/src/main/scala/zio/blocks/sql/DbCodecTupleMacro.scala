/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.sql

import scala.quoted._

private[sql] object DbCodecTupleMacro {

  def tupleCodecImpl[T <: Tuple: Type](using Quotes): Expr[DbCodec[T]] = {
    import quotes.reflect._

    val elementTypes = flattenTupleTypes[T]
    if (elementTypes.isEmpty)
      return '{ emptyTupleCodec.asInstanceOf[DbCodec[T]] }

    val codecExprs: List[Expr[DbCodec[?]]] = elementTypes.map { tpe =>
      tpe match {
        case '[t] =>
          Expr.summon[DbCodec[t]] match {
            case Some(codec) => codec
            case None        =>
              report.errorAndAbort(s"No DbCodec instance found for tuple element type ${Type.show[t]}")
          }
      }
    }

    val codecsArray = '{ IArray(${ Varargs(codecExprs) }: _*) }
    val arity       = elementTypes.size

    '{
      val codecs = $codecsArray
      new DbCodec[T] {
        val columns: IndexedSeq[String] = {
          val builder = IndexedSeq.newBuilder[String]
          var i       = 0
          while (i < codecs.length) {
            val c = codecs(i).asInstanceOf[DbCodec[Any]]
            if (c.columnCount == 1) builder += s"_${i + 1}"
            else {
              val cols = c.columns
              var j    = 0
              while (j < cols.length) {
                builder += s"_${i + 1}_${cols(j)}"
                j += 1
              }
            }
            i += 1
          }
          builder.result()
        }

        def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): T = {
          val arr    = new Array[Any](${ Expr(arity) })
          var offset = 0
          var i      = 0
          while (i < codecs.length) {
            val c    = codecs(i).asInstanceOf[DbCodec[Any]]
            val cols = c.columnCount
            arr(i) = c.readValue(reader, columnLabels.slice(offset, offset + cols))
            offset += cols
            i += 1
          }
          Tuple.fromArray(arr).asInstanceOf[T]
        }

        override def readValue(reader: DbResultReader, startIndex: Int): T = {
          val arr    = new Array[Any](${ Expr(arity) })
          var offset = startIndex
          var i      = 0
          while (i < codecs.length) {
            val c    = codecs(i).asInstanceOf[DbCodec[Any]]
            val cols = c.columnCount
            arr(i) = c.readValue(reader, offset)
            offset += cols
            i += 1
          }
          Tuple.fromArray(arr).asInstanceOf[T]
        }

        def writeValue(writer: DbParamWriter, startIndex: Int, value: T): Unit = {
          val product = value.asInstanceOf[Product]
          var offset  = startIndex
          var i       = 0
          while (i < codecs.length) {
            val c = codecs(i).asInstanceOf[DbCodec[Any]]
            c.writeValue(writer, offset, product.productElement(i))
            offset += c.columnCount
            i += 1
          }
        }

        def toDbValues(value: T): IndexedSeq[DbValue] = {
          val product = value.asInstanceOf[Product]
          val builder = IndexedSeq.newBuilder[DbValue]
          var i       = 0
          while (i < codecs.length) {
            val c = codecs(i).asInstanceOf[DbCodec[Any]]
            builder ++= c.toDbValues(product.productElement(i))
            i += 1
          }
          builder.result()
        }
      }
    }
  }

  private def flattenTupleTypes[T <: Tuple: Type](using Quotes): List[Type[?]] =
    Type.of[T] match {
      case '[EmptyTuple] => Nil
      case '[h *: t]     => Type.of[h] :: flattenTupleTypes[t]
    }

  private val emptyTupleCodec: DbCodec[EmptyTuple] = new DbCodec[EmptyTuple] {
    val columns: IndexedSeq[String]                                                        = IndexedSeq.empty
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): EmptyTuple    = EmptyTuple
    override def readValue(reader: DbResultReader, startIndex: Int): EmptyTuple            = EmptyTuple
    def writeValue(writer: DbParamWriter, startIndex: Int, value: EmptyTuple): Unit        = ()
    def toDbValues(value: EmptyTuple): IndexedSeq[DbValue]                                 = IndexedSeq.empty
  }
}
