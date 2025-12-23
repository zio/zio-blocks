package zio.blocks.schema

import scala.util.Try
import scala.reflect.ClassTag

class CaseClassMapper(config: DirectMapping.MappingConfig) {
  
  def map[A, B](source: A)(implicit 
    sourceSchema: Schema[A], 
    targetSchema: Schema[B],
    sourceClassTag: ClassTag[A],
    targetClassTag: ClassTag[B]
  ): Either[MappingError, B] = {
    Try {
      mapDirect(source, targetClassTag.runtimeClass.asInstanceOf[Class[B]])
    }.toEither.left.map {
      case e: MappingException => e.error
      case e: Exception => MappingError.UnexpectedError(e.getMessage, Some(e))
    }
  }
  
  private def mapDirect[A, B](source: A, targetClass: Class[B]): B = {
    val targetCtor = targetClass.getDeclaredConstructors.head
    val targetParams = targetCtor.getParameters
    
    val args = new Array[AnyRef](targetParams.length)
    
    for (i <- targetParams.indices) {
      val param = targetParams(i)
      val paramName = param.getName
      
      config.explicitMappings.find(_.targetField == paramName) match {
        case Some(mapping) =>
          val sourceValue = getFieldValue(source, mapping.sourceField)
            .getOrElse(throw MappingException(MappingError.FieldNotFound(mapping.sourceField, source.getClass.getSimpleName)))
          val transformed = mapping.transform.fold(sourceValue)(_(sourceValue))
          args(i) = transformed.asInstanceOf[AnyRef]
        case None =>
          getFieldValue(source, paramName) match {
            case Some(value) => 
              args(i) = convertValue(value, param.getType).asInstanceOf[AnyRef]
            case None =>
              config.defaultValues.get(paramName) match {
                case Some(default) => args(i) = default.asInstanceOf[AnyRef]
                case None => 
                  try {
                    args(i) = createNestedInstance(param.getType).asInstanceOf[AnyRef]
                  } catch {
                    case _: Exception => 
                      throw MappingException(MappingError.FieldNotFound(paramName, source.getClass.getSimpleName))
                  }
              }
          }
      }
    }
    
    targetCtor.newInstance(args: _*).asInstanceOf[B]
  }
  
  private def getFieldValue(source: Any, fieldName: String): Option[Any] = {
    Try {
      val field = source.getClass.getDeclaredField(fieldName)
      field.setAccessible(true)
      field.get(source)
    }.toOption
  }
  
  private def createNestedInstance(targetType: Class[_]): Any = {
    // Detect if it's a case class by checking if it has a single public constructor
    val constructors = targetType.getDeclaredConstructors
    if (constructors.length == 1) {
      val ctor = constructors.head
      val params = ctor.getParameters
      val args = params.map { param =>
        param.getType match {
          case t if t == classOf[String] => ""
          case t if t == classOf[Int] => Int.box(0)
          case t if t == classOf[Long] => Long.box(0L)
          case t if t == classOf[Double] => Double.box(0.0)
          case t if t == classOf[Boolean] => java.lang.Boolean.FALSE
          case _ => null
        }
      }
      ctor.newInstance(args: _*)
    } else {
      null
    }
  }
  
  private def convertValue(value: Any, targetType: Class[_]): Any = {
    if (value == null) null
    else if (targetType.isAssignableFrom(value.getClass)) value
    else targetType match {
      case t if t == classOf[String] => value.toString
      case t if t == classOf[Int] => 
        value match {
          case v: String => v.toInt
          case v: Double => v.toInt
          case v: Long => v.toInt
          case v: Number => v.intValue
          case _ => value
        }
      case t if t == classOf[Long] => 
        value match {
          case v: String => v.toLong
          case v: Int => v.toLong
          case v: Double => v.toLong
          case v: Number => v.longValue
          case _ => value
        }
      case t if t == classOf[Double] => 
        value match {
          case v: String => v.toDouble
          case v: Int => v.toDouble
          case v: Long => v.toDouble
          case v: Number => v.doubleValue
          case _ => value
        }
      case t if t == classOf[BigDecimal] => 
        value match {
          case v: String => BigDecimal(v)
          case v: Int => BigDecimal(v)
          case v: Long => BigDecimal(v)
          case v: Double => BigDecimal(v)
          case v: Number => BigDecimal(v.toString)
          case _ => value
        }
      case t if t == classOf[Boolean] => 
        value match {
          case v: String => v.toBoolean
          case v: Int => v != 0
          case _ => value.toString.toBoolean
        }
      case _ => value
    }
  }
  
  def mapMany[A, B](sources: Iterable[A])(implicit 
    sourceSchema: Schema[A], 
    targetSchema: Schema[B],
    sourceClassTag: ClassTag[A],
    targetClassTag: ClassTag[B]
  ): Either[MappingError, Vector[B]] = {
    // Optimization: process in batches for better performance
    val batchSize = 100
    val batches = sources.grouped(batchSize)
    
    batches.foldLeft(Right(Vector.empty[B]): Either[MappingError, Vector[B]]) {
      case (acc, batch) =>
        val batchResults = batch.map(map[A, B])
        val (successes, failures) = batchResults.partition(_.isRight)
        
        if (failures.nonEmpty) {
          Left(failures.head.left.get) // Return first error
        } else {
          acc.map(existing => existing ++ successes.map(_.right.get))
        }
    }
  }
}

private case class MappingException(error: MappingError) extends Exception

sealed trait MappingError extends Product with Serializable

object MappingError {
  case class FieldNotFound(fieldName: String, sourceType: String) extends MappingError
  case class TypeMismatch(fieldName: String, expectedType: String, actualType: String) extends MappingError
  case class TransformError(fieldName: String, error: String) extends MappingError
  case class FieldMappingError(fieldName: String, targetType: String, details: String) extends MappingError
  case class UnexpectedError(message: String, cause: Option[Throwable] = None) extends MappingError
}