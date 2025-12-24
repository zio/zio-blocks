package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.Patch.SequenceEdit

object Differ {
  
  def diff[A](schema: Schema[A], x: A, y: A): Patch[A] =
    diffReflect(schema.reflect, x, y, schema)

  private def diffReflect[A](
      reflect: Reflect.Bound[A],
      x: A,
      y: A,
      schema: Schema[A]
  ): Patch[A] = {
    if (x == y) Patch.empty(schema)
    else if (reflect.isPrimitive) {
       Patch.replace(Lens.identity(schema), y)(schema)
    } else {
      reflect match {
        case record: Reflect.Record.Bound[A] =>
          val len = record.fields.length
          var idx = 0
          var patch = Patch.empty(schema)
          while (idx < len) {
             type FieldType = Any
             val lensOption = record.lensByIndex[FieldType](idx)
             
             if (lensOption.isDefined) {
                val lens = lensOption.get
                val fieldValX = lens.get(x)
                val fieldValY = lens.get(y)
                
                val fieldReflect = record.fields(idx).value.asInstanceOf[Reflect.Bound[FieldType]]
                val fieldSchema = new Schema(fieldReflect)
                
                val subPatch = diffReflect(fieldReflect, fieldValX, fieldValY, fieldSchema)
                
                if (subPatch.ops.nonEmpty) {
                   patch = patch ++ subPatch.mapLens(lens)(schema)
                }
             }
             idx += 1
          }
          patch
          
        case variant: Reflect.Variant.Bound[A] =>
             // TODO: Heuristic diff for same-case variants?
             Patch.replace(Lens.identity(schema), y)(schema)
        
        case seq: Reflect.Sequence.Bound[_, _] =>
             // Handle sequence diffing using LCS
             type Elem = Any
             type Col[E] = Iterable[E] 
             
             // We cast to access the sequence logic generically
             val seqTyped = seq.asInstanceOf[Reflect.Sequence.Bound[Elem, Col]]
             val colX = x.asInstanceOf[Col[Elem]]
             val colY = y.asInstanceOf[Col[Elem]]
             
             val vecX = toVector(seqTyped, colX)
             val vecY = toVector(seqTyped, colY)
             
             val edits = LCS.diff(vecX, vecY)
             
             if (edits.forall(_.isInstanceOf[SequenceEdit.Keep[_]])) Patch.empty(schema)
             else {
               // We construct the SequenceUpdate patch. 
               // Note: SequenceUpdate extends LensOp[Nothing], so it fits LensOp[A].
               // The generic type of SequenceUpdate is inferred as the element type?
               // Actually SequenceUpdate[Elem] contains SequenceEdit[Elem].
               val op = Patch.LensOp.SequenceUpdate(edits.asInstanceOf[Vector[Patch.SequenceEdit[A]]])
               Patch(Vector(Patch.LensPair(Lens.identity(schema), op)), schema)
             }
             
        case _ => 
             Patch.replace(Lens.identity(schema), y)(schema)
      }
    }
  }
  
  private def toVector[A, C[_]](seq: Reflect.Sequence.Bound[A, C], col: C[A]): Vector[A] = {
    val iter = seq.seqDeconstructor.deconstruct(col)
    var vec = Vector.empty[A]
    while(iter.hasNext) {
      vec = vec :+ iter.next()
    }
    vec
  }

  private[schema] object LCS {
    def diff[A](original: Vector[A], modified: Vector[A]): Vector[SequenceEdit[A]] = {
      if (original == modified) {
         original.map(SequenceEdit.Keep(_))
      } else {
         var varOriginal = original
         var varModified = modified
         var longestCommonSubstring = getLongestCommonSubsequence(original, modified)
         
         val buffer = Vector.newBuilder[SequenceEdit[A]]
         
         while (longestCommonSubstring.nonEmpty) {
            val headLCS = longestCommonSubstring.head
            longestCommonSubstring = longestCommonSubstring.tail
            
            var headModified = varModified.head
            var loop = true
            while(loop) {
               headModified = varModified.head
               varModified = varModified.tail
               if (headModified != headLCS) {
                  buffer += SequenceEdit.Insert(headModified)
               }
               loop = varModified.nonEmpty && headModified != headLCS
            }
            
            var headOriginal = varOriginal.head
            loop = true
            while(loop) {
               headOriginal = varOriginal.head
               varOriginal = varOriginal.tail
               if (headOriginal != headLCS) {
                  buffer += SequenceEdit.Delete(headOriginal)
               }
               loop = varOriginal.nonEmpty && headOriginal != headLCS
            }
            
            buffer += SequenceEdit.Keep(headLCS)
         }
         
         while(varModified.nonEmpty) {
            buffer += SequenceEdit.Insert(varModified.head)
            varModified = varModified.tail
         }
         while(varOriginal.nonEmpty) {
            buffer += SequenceEdit.Delete(varOriginal.head)
            varOriginal = varOriginal.tail
         }
         
         buffer.result()
      }
    }
    
    private def getLongestCommonSubsequence[A](original: Vector[A], modified: Vector[A]): Vector[A] =
        if (original.isEmpty || modified.isEmpty) Vector.empty
        else if (original == modified) original
        else {
          val myersMatrix = initializeMyersMatrix(original, modified)
          val resultBuilder = Vector.newBuilder[A]
          
          var i = original.length
          var j = modified.length
          
          while (i > 0 && j > 0) {
             if (original(i-1) == modified(j-1)) {
                resultBuilder += original(i-1)
                i -= 1
                j -= 1
             } else if (myersMatrix(i)(j) == myersMatrix(i-1)(j)) {
                i -= 1
             } else {
                j -= 1
             }
          }
          // The result is built backwards, so reverse it
          resultBuilder.result().reverse
        }

      private def initializeMyersMatrix[A](original: Vector[A], modified: Vector[A]): Array[Array[Int]] = {
        val originalLength = original.length
        val modifiedLength = modified.length

        val myersMatrix = Array.fill[Int](originalLength + 1, modifiedLength + 1)(0)

        for (i <- 0 until originalLength) {
          for (j <- 0 until modifiedLength) {
            if (original(i) == modified(j)) {
              myersMatrix(i + 1)(j + 1) = myersMatrix(i)(j) + 1
            } else {
              if (myersMatrix(i)(j + 1) >= myersMatrix(i + 1)(j)) {
                myersMatrix(i + 1)(j + 1) = myersMatrix(i)(j + 1)
              } else {
                myersMatrix(i + 1)(j + 1) = myersMatrix(i + 1)(j)
              }
            }
          }
        }
        myersMatrix
      }
  }
}
