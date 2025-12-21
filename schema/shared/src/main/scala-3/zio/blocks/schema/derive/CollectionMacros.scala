package zio.blocks.schema.derive

import scala.quoted.*

/**
 * Macros for handling collection type conversions in Into derivation.
 * 
 * Handles Option, Either, Map, Array, List, Vector, Set, and Seq conversions.
 */
object CollectionMacros {
  
  /**
   * Attempts to derive an Into[A, B] instance for collection type conversions.
   * 
   * @return Some(expr) if conversion is possible, None otherwise
   */
  def collectionConversion[A: Type, B: Type](using Quotes)(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr
  ): Option[Expr[zio.blocks.schema.Into[A, B]]] = {
    import quotes.reflect.*
    
    // Check if both are Option types
    if (source <:< TypeRepr.of[Option[?]] && target <:< TypeRepr.of[Option[?]]) {
      val sourceElemType = getTypeArg(source, 0)
      val targetElemType = getTypeArg(target, 0)
      
      return Some(generateOptionConversion[A, B](sourceElemType, targetElemType))
    }
    
    // Check for Either types
    if (source <:< TypeRepr.of[Either[?, ?]] && target <:< TypeRepr.of[Either[?, ?]]) {
      val sourceLeftType = getTypeArg(source, 0)
      val sourceRightType = getTypeArg(source, 1)
      val targetLeftType = getTypeArg(target, 0)
      val targetRightType = getTypeArg(target, 1)
      
      return Some(generateEitherConversion[A, B](
        sourceLeftType, sourceRightType,
        targetLeftType, targetRightType
      ))
    }
    
    // Check for Map types
    if (source <:< TypeRepr.of[Map[?, ?]] && target <:< TypeRepr.of[Map[?, ?]]) {
      val sourceKeyType = getTypeArg(source, 0)
      val sourceValueType = getTypeArg(source, 1)
      val targetKeyType = getTypeArg(target, 0)
      val targetValueType = getTypeArg(target, 1)
      
      return Some(generateMapConversion[A, B](
        sourceKeyType, sourceValueType,
        targetKeyType, targetValueType
      ))
    }
    
    // Check for Array types
    val sourceIsArray = source.typeSymbol.fullName == "scala.Array"
    val targetIsArray = target.typeSymbol.fullName == "scala.Array"
    
    if (sourceIsArray && targetIsArray) {
      val sourceElemType = source.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
      val targetElemType = target.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
      
      return Some(generateArrayToArrayConversion[A, B](sourceElemType, targetElemType))
    } else if (sourceIsArray && !targetIsArray) {
      val sourceElemType = source.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
      val targetElemType = getTypeArg(target, 0)
      
      return Some(generateArrayToCollectionConversion[A, B](
        sourceElemType, targetElemType,
        target <:< TypeRepr.of[List[?]],
        target <:< TypeRepr.of[Vector[?]],
        target <:< TypeRepr.of[Set[?]]
      ))
    } else if (!sourceIsArray && targetIsArray) {
      val sourceElemType = getTypeArg(source, 0)
      val targetElemType = target.typeArgs.headOption.getOrElse(TypeRepr.of[Any])
      
      return Some(generateCollectionToArrayConversion[A, B](sourceElemType, targetElemType))
    }
    
    // Check for common collection types
    val sourceIsSeq = source <:< TypeRepr.of[Seq[?]]
    val sourceIsList = source <:< TypeRepr.of[List[?]]
    val sourceIsVector = source <:< TypeRepr.of[Vector[?]]
    val sourceIsSet = source <:< TypeRepr.of[Set[?]]
    
    val targetIsSeq = target <:< TypeRepr.of[Seq[?]]
    val targetIsList = target <:< TypeRepr.of[List[?]]
    val targetIsVector = target <:< TypeRepr.of[Vector[?]]
    val targetIsSet = target <:< TypeRepr.of[Set[?]]
    
    val isSourceCollection = sourceIsSeq || sourceIsList || sourceIsVector || sourceIsSet
    val isTargetCollection = targetIsSeq || targetIsList || targetIsVector || targetIsSet
    
    if (!isSourceCollection || !isTargetCollection) {
      return None
    }
    
    // Extract element types
    val sourceElemType = getTypeArg(source, 0)
    val targetElemType = getTypeArg(target, 0)
    
    Some(generateCollectionConversion[A, B](
      source, target,
      sourceElemType, targetElemType,
      targetIsList, targetIsVector, targetIsSet
    ))
  }
  
  private def getTypeArg(using Quotes)(tpe: quotes.reflect.TypeRepr, index: Int): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    tpe match {
      case AppliedType(_, args) if args.size > index => args(index)
      case _ => TypeRepr.of[Any]
    }
  }
  
  private def generateOptionConversion[A: Type, B: Type](using Quotes)(
    sourceElemType: quotes.reflect.TypeRepr,
    targetElemType: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    if (sourceElemType =:= targetElemType) {
      // Same element type - simple identity
      '{ zio.blocks.schema.Into.identity[A].asInstanceOf[zio.blocks.schema.Into[A, B]] }
    } else {
      // Need element conversion
      sourceElemType.asType match {
        case '[s] =>
          targetElemType.asType match {
            case '[t] =>
              '{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                    val opt = input.asInstanceOf[Option[s]]
                    opt match {
                      case None => Right(None.asInstanceOf[B])
                      case Some(value) =>
                        ${
                          // Try to get or derive Into for elements
                          Expr.summon[zio.blocks.schema.Into[s, t]] match {
                            case Some(elemInto) =>
                              '{ $elemInto.into(value).map(v => Some(v).asInstanceOf[B]) }
                            case None =>
                              '{ zio.blocks.schema.Into.derived[s, t].into(value).map(v => Some(v).asInstanceOf[B]) }
                          }
                        }
                    }
                  }
                }
              }
          }
      }
    }
  }
  
  private def generateCollectionConversion[A: Type, B: Type](using Quotes)(
    source: quotes.reflect.TypeRepr,
    target: quotes.reflect.TypeRepr,
    sourceElemType: quotes.reflect.TypeRepr,
    targetElemType: quotes.reflect.TypeRepr,
    targetIsList: Boolean,
    targetIsVector: Boolean,
    targetIsSet: Boolean
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    if (sourceElemType =:= targetElemType) {
      // Same element type - just convert collection structure
      // Performance optimization: Use efficient builders for better performance
      '{
        new zio.blocks.schema.Into[A, B] {
          @inline def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
            val coll = input.asInstanceOf[Iterable[Any]]
            val result = ${
              if (targetIsList) {
                // Optimize: Use ListBuilder or direct conversion if source is already List
                '{ 
                  coll match {
                    case l: List[Any] => l.asInstanceOf[B]
                    case _ => coll.toList.asInstanceOf[B]
                  }
                }
              } else if (targetIsVector) {
                // Optimize: Use VectorBuilder or direct conversion
                '{ 
                  coll match {
                    case v: Vector[Any] => v.asInstanceOf[B]
                    case _ => coll.toVector.asInstanceOf[B]
                  }
                }
              } else if (targetIsSet) {
                '{ coll.toSet.asInstanceOf[B] }
              } else {
                '{ coll.toSeq.asInstanceOf[B] }
              }
            }
            Right(result)
          }
        }
      }
    } else {
      // Need element conversion
      sourceElemType.asType match {
        case '[s] =>
          targetElemType.asType match {
            case '[t] =>
              '{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                    val coll = input.asInstanceOf[Iterable[s]]
                    
                    ${
                      // Try to get or derive Into for elements
                      val elemIntoExpr = Expr.summon[zio.blocks.schema.Into[s, t]] match {
                        case Some(elemInto) => elemInto
                        case None => '{ zio.blocks.schema.Into.derived[s, t] }
                      }
                      
                      '{
                        // Performance optimization: Use efficient builders instead of foldLeft + reverse
                        // This reduces allocations and improves performance
                        ${
                          if (targetIsList) {
                            '{
                              val builder = List.newBuilder[t]
                              var error: Option[zio.blocks.schema.SchemaError] = None
                              
                              val iter = coll.iterator
                              while (iter.hasNext && error.isEmpty) {
                                $elemIntoExpr.into(iter.next()) match {
                                  case Right(converted) => builder += converted
                                  case Left(err) => error = Some(err)
                                }
                              }
                              
                              error match {
                                case Some(err) => Left(err)
                                case None => Right(builder.result().asInstanceOf[B])
                              }
                            }
                          } else if (targetIsVector) {
                            '{
                              val builder = Vector.newBuilder[t]
                              var error: Option[zio.blocks.schema.SchemaError] = None
                              
                              val iter = coll.iterator
                              while (iter.hasNext && error.isEmpty) {
                                $elemIntoExpr.into(iter.next()) match {
                                  case Right(converted) => builder += converted
                                  case Left(err) => error = Some(err)
                                }
                              }
                              
                              error match {
                                case Some(err) => Left(err)
                                case None => Right(builder.result().asInstanceOf[B])
                              }
                            }
                          } else if (targetIsSet) {
                            '{
                              val builder = Set.newBuilder[t]
                              var error: Option[zio.blocks.schema.SchemaError] = None
                              
                              val iter = coll.iterator
                              while (iter.hasNext && error.isEmpty) {
                                $elemIntoExpr.into(iter.next()) match {
                                  case Right(converted) => builder += converted
                                  case Left(err) => error = Some(err)
                                }
                              }
                              
                              error match {
                                case Some(err) => Left(err)
                                case None => Right(builder.result().asInstanceOf[B])
                              }
                            }
                          } else {
                            '{
                              val builder = scala.collection.mutable.ArrayBuffer.newBuilder[t]
                              var error: Option[zio.blocks.schema.SchemaError] = None
                              
                              val iter = coll.iterator
                              while (iter.hasNext && error.isEmpty) {
                                $elemIntoExpr.into(iter.next()) match {
                                  case Right(converted) => builder += converted
                                  case Left(err) => error = Some(err)
                                }
                              }
                              
                              error match {
                                case Some(err) => Left(err)
                                case None => Right(builder.result().toSeq.asInstanceOf[B])
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
          }
      }
    }
  }
  
  private def generateEitherConversion[A: Type, B: Type](using Quotes)(
    sourceLeftType: quotes.reflect.TypeRepr,
    sourceRightType: quotes.reflect.TypeRepr,
    targetLeftType: quotes.reflect.TypeRepr,
    targetRightType: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    val leftSame = sourceLeftType =:= targetLeftType
    val rightSame = sourceRightType =:= targetRightType
    
    if (leftSame && rightSame) {
      // Identity
      '{ zio.blocks.schema.Into.identity[A].asInstanceOf[zio.blocks.schema.Into[A, B]] }
    } else {
      sourceLeftType.asType match {
        case '[sl] =>
          sourceRightType.asType match {
            case '[sr] =>
              targetLeftType.asType match {
                case '[tl] =>
                  targetRightType.asType match {
                    case '[tr] =>
                      '{
                        new zio.blocks.schema.Into[A, B] {
                          def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                            val either = input.asInstanceOf[Either[sl, sr]]
                            either match {
                              case Left(left) =>
                                ${
                                  if (leftSame) {
                                    '{ Right(Left(left).asInstanceOf[B]) }
                                  } else {
                                    val leftInto = Expr.summon[zio.blocks.schema.Into[sl, tl]] match {
                                      case Some(into) => into
                                      case None => '{ zio.blocks.schema.Into.derived[sl, tl] }
                                    }
                                    '{ $leftInto.into(left).map(l => Left(l).asInstanceOf[B]) }
                                  }
                                }
                              case Right(right) =>
                                ${
                                  if (rightSame) {
                                    '{ Right(Right(right).asInstanceOf[B]) }
                                  } else {
                                    val rightInto = Expr.summon[zio.blocks.schema.Into[sr, tr]] match {
                                      case Some(into) => into
                                      case None => '{ zio.blocks.schema.Into.derived[sr, tr] }
                                    }
                                    '{ $rightInto.into(right).map(r => Right(r).asInstanceOf[B]) }
                                  }
                                }
                            }
                          }
                        }
                      }
                  }
              }
          }
      }
    }
  }
  
  private def generateMapConversion[A: Type, B: Type](using Quotes)(
    sourceKeyType: quotes.reflect.TypeRepr,
    sourceValueType: quotes.reflect.TypeRepr,
    targetKeyType: quotes.reflect.TypeRepr,
    targetValueType: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    val keySame = sourceKeyType =:= targetKeyType
    val valueSame = sourceValueType =:= targetValueType
    
    if (keySame && valueSame) {
      // Identity
      '{ zio.blocks.schema.Into.identity[A].asInstanceOf[zio.blocks.schema.Into[A, B]] }
    } else {
      sourceKeyType.asType match {
        case '[sk] =>
          sourceValueType.asType match {
            case '[sv] =>
              targetKeyType.asType match {
                case '[tk] =>
                  targetValueType.asType match {
                    case '[tv] =>
                      '{
                        new zio.blocks.schema.Into[A, B] {
                          def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                            val map = input.asInstanceOf[Map[sk, sv]]
                            
                            ${
                              val keyIntoExpr = if (keySame) {
                                None
                              } else {
                                Some(Expr.summon[zio.blocks.schema.Into[sk, tk]] match {
                                  case Some(into) => into
                                  case None => '{ zio.blocks.schema.Into.derived[sk, tk] }
                                })
                              }
                              
                              val valueIntoExpr = if (valueSame) {
                                None
                              } else {
                                Some(Expr.summon[zio.blocks.schema.Into[sv, tv]] match {
                                  case Some(into) => into
                                  case None => '{ zio.blocks.schema.Into.derived[sv, tv] }
                                })
                              }
                              
                              (keyIntoExpr, valueIntoExpr) match {
                                case (None, None) =>
                                  // Both same - identity
                                  '{ Right(map.asInstanceOf[B]) }
                                case (Some(keyInto), None) =>
                                  // Only keys need conversion
                                  '{
                                    map.foldLeft[Either[zio.blocks.schema.SchemaError, Map[tk, sv]]](Right(Map.empty)) {
                                      case (Right(acc), (k, v)) =>
                                        $keyInto.into(k) match {
                                          case Right(convertedKey) => Right(acc + (convertedKey -> v))
                                          case Left(err) => Left(err)
                                        }
                                      case (left @ Left(_), _) => left
                                    }.map(_.asInstanceOf[B])
                                  }
                                case (None, Some(valueInto)) =>
                                  // Only values need conversion
                                  '{
                                    map.foldLeft[Either[zio.blocks.schema.SchemaError, Map[sk, tv]]](Right(Map.empty)) {
                                      case (Right(acc), (k, v)) =>
                                        $valueInto.into(v) match {
                                          case Right(convertedValue) => Right(acc + (k -> convertedValue))
                                          case Left(err) => Left(err)
                                        }
                                      case (left @ Left(_), _) => left
                                    }.map(_.asInstanceOf[B])
                                  }
                                case (Some(keyInto), Some(valueInto)) =>
                                  // Both need conversion
                                  '{
                                    map.foldLeft[Either[zio.blocks.schema.SchemaError, Map[tk, tv]]](Right(Map.empty)) {
                                      case (Right(acc), (k, v)) =>
                                        for {
                                          convertedKey <- $keyInto.into(k)
                                          convertedValue <- $valueInto.into(v)
                                        } yield acc + (convertedKey -> convertedValue)
                                      case (left @ Left(_), _) => left
                                    }.map(_.asInstanceOf[B])
                                  }
                              }
                            }
                          }
                        }
                      }
                  }
              }
          }
      }
    }
  }
  
  private def generateArrayToArrayConversion[A: Type, B: Type](using Quotes)(
    sourceElemType: quotes.reflect.TypeRepr,
    targetElemType: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    if (sourceElemType =:= targetElemType) {
      '{ zio.blocks.schema.Into.identity[A].asInstanceOf[zio.blocks.schema.Into[A, B]] }
    } else {
      sourceElemType.asType match {
        case '[s] =>
          targetElemType.asType match {
            case '[t] =>
              '{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                    val arr = input.asInstanceOf[Array[s]]
                    val elemInto = zio.blocks.schema.Into.derived[s, t]
                    
                    val result = scala.collection.mutable.ArrayBuffer.empty[t]
                    var idx = 0
                    var error: Option[zio.blocks.schema.SchemaError] = None
                    
                    while (idx < arr.length && error.isEmpty) {
                      elemInto.into(arr(idx)) match {
                        case Right(converted) => 
                          result += converted
                          idx += 1
                        case Left(err) =>
                          error = Some(err)
                      }
                    }
                    
                    error match {
                      case Some(err) => Left(err)
                      case None => 
                        // Convert ArrayBuffer to Array using reflection
                        // Since ClassTag is not available for generic type B, we use runtime reflection
                        try {
                          val array = if (result.isEmpty) {
                            // Empty array - use Any as base type
                            Array.empty[Any].asInstanceOf[B]
                          } else {
                            // Use first element to determine type at runtime
                            val firstElem = result.head
                            val elemClass = firstElem.getClass
                            val arrayInstance = java.lang.reflect.Array.newInstance(elemClass, result.size)
                            val arrayObj = arrayInstance.asInstanceOf[Array[Object]]
                            
                            result.zipWithIndex.foreach { case (elem, idx) =>
                              arrayObj(idx) = elem.asInstanceOf[Object]
                            }
                            
                            arrayInstance.asInstanceOf[B]
                          }
                          Right(array)
                        } catch {
                          case e: Exception =>
                            Left(zio.blocks.schema.SchemaError.expectationMismatch(Nil, s"Cannot create array: ${e.getMessage}"))
                        }
                    }
                  }
                }
              }
          }
      }
    }
  }
  
  private def generateArrayToCollectionConversion[A: Type, B: Type](using Quotes)(
    sourceElemType: quotes.reflect.TypeRepr,
    targetElemType: quotes.reflect.TypeRepr,
    targetIsList: Boolean,
    targetIsVector: Boolean,
    targetIsSet: Boolean
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    if (sourceElemType =:= targetElemType) {
      '{
        new zio.blocks.schema.Into[A, B] {
          def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
            val arr = input.asInstanceOf[Array[Any]]
            val result = ${
              if (targetIsList) '{ arr.toList }
              else if (targetIsVector) '{ arr.toVector }
              else if (targetIsSet) '{ arr.toSet }
              else '{ arr.toSeq }
            }
            Right(result.asInstanceOf[B])
          }
        }
      }
    } else {
      sourceElemType.asType match {
        case '[s] =>
          targetElemType.asType match {
            case '[t] =>
              '{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                    val arr = input.asInstanceOf[Array[s]]
                    val elemInto = zio.blocks.schema.Into.derived[s, t]
                    
                    val converted = arr.foldLeft[Either[zio.blocks.schema.SchemaError, List[t]]](Right(Nil)) {
                      case (Right(acc), elem) =>
                        elemInto.into(elem) match {
                          case Right(converted) => Right(converted :: acc)
                          case Left(err) => Left(err)
                        }
                      case (left @ Left(_), _) => left
                    }
                    
                    converted.map { list =>
                      val reversed = list.reverse
                      ${
                        if (targetIsList) '{ reversed.asInstanceOf[B] }
                        else if (targetIsVector) '{ reversed.toVector.asInstanceOf[B] }
                        else if (targetIsSet) '{ reversed.toSet.asInstanceOf[B] }
                        else '{ reversed.toSeq.asInstanceOf[B] }
                      }
                    }
                  }
                }
              }
          }
      }
    }
  }
  
  private def generateCollectionToArrayConversion[A: Type, B: Type](using Quotes)(
    sourceElemType: quotes.reflect.TypeRepr,
    targetElemType: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.schema.Into[A, B]] = {
    
    if (sourceElemType =:= targetElemType) {
      '{
        new zio.blocks.schema.Into[A, B] {
          def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
            val coll = input.asInstanceOf[Iterable[Any]]
            Right(coll.toArray.asInstanceOf[B])
          }
        }
      }
    } else {
      sourceElemType.asType match {
        case '[s] =>
          targetElemType.asType match {
            case '[t] =>
              '{
                new zio.blocks.schema.Into[A, B] {
                  def into(input: A): Either[zio.blocks.schema.SchemaError, B] = {
                    val coll = input.asInstanceOf[Iterable[s]]
                    val elemInto = zio.blocks.schema.Into.derived[s, t]
                    
                    val converted = coll.foldLeft[Either[zio.blocks.schema.SchemaError, List[t]]](Right(Nil)) {
                      case (Right(acc), elem) =>
                        elemInto.into(elem) match {
                          case Right(converted) => Right(converted :: acc)
                          case Left(err) => Left(err)
                        }
                      case (left @ Left(_), _) => left
                    }
                    
                    converted.map { list =>
                      val reversed = list.reverse
                      // Convert List to Array using reflection
                      // Since ClassTag is not available for generic type B, we use runtime reflection
                      try {
                        if (reversed.isEmpty) {
                          // Empty array - use Any as base type
                          Array.empty[Any].asInstanceOf[B]
                        } else {
                          // Use first element to determine type at runtime
                          val firstElem = reversed.head
                          val elemClass = firstElem.getClass
                          val arrayInstance = java.lang.reflect.Array.newInstance(elemClass, reversed.size)
                          val arrayObj = arrayInstance.asInstanceOf[Array[Object]]
                          
                          reversed.zipWithIndex.foreach { case (elem, idx) =>
                            arrayObj(idx) = elem.asInstanceOf[Object]
                          }
                          
                          arrayInstance.asInstanceOf[B]
                        }
                      } catch {
                        case e: Exception =>
                          throw new RuntimeException(s"Cannot create array: ${e.getMessage}", e)
                      }
                    }
                  }
                }
              }
          }
      }
    }
  }
}

