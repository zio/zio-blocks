package zio.blocks.typeid

import zio.blocks.chunk.Chunk

import scala.util.Try
import scala.util.control.NonFatal

/**
 * JVM-specific implementation of TypeId platform methods.
 */
private[typeid] object TypeIdPlatformMethods {

  private val primitiveClasses: Map[String, Class[?]] = Map(
    "scala.Boolean" -> classOf[Boolean],
    "scala.Byte"    -> classOf[Byte],
    "scala.Short"   -> classOf[Short],
    "scala.Int"     -> classOf[Int],
    "scala.Long"    -> classOf[Long],
    "scala.Float"   -> classOf[Float],
    "scala.Double"  -> classOf[Double],
    "scala.Char"    -> classOf[Char],
    "scala.Unit"    -> classOf[Unit]
  )

  private val scalaToJavaMapping: Map[String, String] = Map(
    "scala.collection.immutable.Nil" -> "scala.collection.immutable.Nil$",
    "scala.None"                     -> "scala.None$",
    "scala.BigInt"                   -> "scala.math.BigInt",
    "scala.BigDecimal"               -> "scala.math.BigDecimal"
  )

  def getClass(id: TypeId[_]): Option[Class[_]] =
    if (id.aliasedTo.isDefined || id.representation.isDefined) None
    else {
      val fullName = id.fullName
      primitiveClasses.get(fullName).orElse {
        val className = toJvmClassName(id.owner, id.name, id.defKind)
        Try(Class.forName(className)).toOption
      }
    }

  def construct(id: TypeId[_], args: Chunk[AnyRef]): Either[String, Any] =
    knownTypeConstructors.get(id.fullName) match {
      case Some(ctor) => ctor(args)
      case None       => reflectiveConstruct(id, args)
    }

  private type Constructor = Chunk[AnyRef] => Either[String, Any]

  private val knownTypeConstructors: Map[String, Constructor] = Map(
    "scala.collection.immutable.List"       -> constructList,
    "scala.collection.immutable.Vector"     -> constructVector,
    "scala.collection.immutable.Set"        -> constructSet,
    "scala.collection.immutable.Map"        -> constructMap,
    "scala.collection.immutable.Seq"        -> constructSeq,
    "scala.collection.immutable.IndexedSeq" -> constructIndexedSeq,
    "scala.Option"                          -> constructOption,
    "scala.Some"                            -> constructSome,
    "scala.None"                            -> constructNone,
    "scala.util.Either"                     -> constructEither,
    "scala.Tuple2"                          -> constructTuple2,
    "scala.Tuple3"                          -> constructTuple3,
    "java.time.LocalDate"                   -> constructLocalDate,
    "java.time.LocalTime"                   -> constructLocalTime,
    "java.time.LocalDateTime"               -> constructLocalDateTime,
    "java.time.Year"                        -> constructYear,
    "java.time.YearMonth"                   -> constructYearMonth,
    "java.time.MonthDay"                    -> constructMonthDay,
    "java.time.Duration"                    -> constructDuration,
    "java.time.Period"                      -> constructPeriod,
    "java.time.Instant"                     -> constructInstant,
    "java.time.ZoneOffset"                  -> constructZoneOffset
  )

  private def constructList(args: Chunk[AnyRef]): Either[String, Any]       = Right(args.toList)
  private def constructVector(args: Chunk[AnyRef]): Either[String, Any]     = Right(args.toVector)
  private def constructSet(args: Chunk[AnyRef]): Either[String, Any]        = Right(args.toSet)
  private def constructSeq(args: Chunk[AnyRef]): Either[String, Any]        = Right(args.toSeq)
  private def constructIndexedSeq(args: Chunk[AnyRef]): Either[String, Any] = Right(args.toIndexedSeq)

  private def constructMap(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length % 2 != 0) Left("Map requires an even number of arguments (key-value pairs)")
    else Right(args.grouped(2).map(pair => pair(0) -> pair(1)).toMap)

  private def constructOption(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.isEmpty) Right(None)
    else if (args.length == 1) Right(Some(args(0)))
    else Left("Option requires 0 or 1 arguments")

  private def constructSome(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 1) Right(Some(args(0)))
    else Left("Some requires exactly 1 argument")

  private def constructNone(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.isEmpty) Right(None)
    else Left("None requires 0 arguments")

  private def constructEither(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length != 2) Left("Either requires exactly 2 arguments: (isRight: Boolean, value)")
    else
      args(0) match {
        case isRight: java.lang.Boolean =>
          if (isRight) Right(scala.util.Right(args(1)))
          else Right(scala.util.Left(args(1)))
        case _ => Left("Either's first argument must be a Boolean indicating isRight")
      }

  private def constructTuple2(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 2) Right((args(0), args(1)))
    else Left("Tuple2 requires exactly 2 arguments")

  private def constructTuple3(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 3) Right((args(0), args(1), args(2)))
    else Left("Tuple3 requires exactly 3 arguments")

  private def constructLocalDate(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 3)
      try Right(java.time.LocalDate.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int], args(2).asInstanceOf[Int]))
      catch { case NonFatal(e) => Left(s"Failed to construct LocalDate: ${e.getMessage}") }
    else Left("LocalDate requires 3 arguments: (year, month, day)")

  private def constructLocalTime(args: Chunk[AnyRef]): Either[String, Any] =
    args.length match {
      case 2 =>
        try Right(java.time.LocalTime.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int]))
        catch { case NonFatal(e) => Left(s"Failed to construct LocalTime: ${e.getMessage}") }
      case 3 =>
        try
          Right(
            java.time.LocalTime.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int], args(2).asInstanceOf[Int])
          )
        catch { case NonFatal(e) => Left(s"Failed to construct LocalTime: ${e.getMessage}") }
      case _ => Left("LocalTime requires 2 or 3 arguments: (hour, minute) or (hour, minute, second)")
    }

  private def constructLocalDateTime(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 6)
      try
        Right(
          java.time.LocalDateTime.of(
            args(0).asInstanceOf[Int],
            args(1).asInstanceOf[Int],
            args(2).asInstanceOf[Int],
            args(3).asInstanceOf[Int],
            args(4).asInstanceOf[Int],
            args(5).asInstanceOf[Int]
          )
        )
      catch { case NonFatal(e) => Left(s"Failed to construct LocalDateTime: ${e.getMessage}") }
    else Left("LocalDateTime requires 6 arguments: (year, month, day, hour, minute, second)")

  private def constructYear(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 1)
      try Right(java.time.Year.of(args(0).asInstanceOf[Int]))
      catch { case NonFatal(e) => Left(s"Failed to construct Year: ${e.getMessage}") }
    else Left("Year requires 1 argument: (year)")

  private def constructYearMonth(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 2)
      try Right(java.time.YearMonth.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int]))
      catch { case NonFatal(e) => Left(s"Failed to construct YearMonth: ${e.getMessage}") }
    else Left("YearMonth requires 2 arguments: (year, month)")

  private def constructMonthDay(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 2)
      try Right(java.time.MonthDay.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int]))
      catch { case NonFatal(e) => Left(s"Failed to construct MonthDay: ${e.getMessage}") }
    else Left("MonthDay requires 2 arguments: (month, day)")

  private def constructDuration(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 1)
      try Right(java.time.Duration.ofSeconds(args(0).asInstanceOf[Long]))
      catch { case NonFatal(e) => Left(s"Failed to construct Duration: ${e.getMessage}") }
    else Left("Duration requires 1 argument: (seconds)")

  private def constructPeriod(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 3)
      try
        Right(
          java.time.Period.of(args(0).asInstanceOf[Int], args(1).asInstanceOf[Int], args(2).asInstanceOf[Int])
        )
      catch { case NonFatal(e) => Left(s"Failed to construct Period: ${e.getMessage}") }
    else Left("Period requires 3 arguments: (years, months, days)")

  private def constructInstant(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 1)
      try Right(java.time.Instant.ofEpochSecond(args(0).asInstanceOf[Long]))
      catch { case NonFatal(e) => Left(s"Failed to construct Instant: ${e.getMessage}") }
    else Left("Instant requires 1 argument: (epochSecond)")

  private def constructZoneOffset(args: Chunk[AnyRef]): Either[String, Any] =
    if (args.length == 1)
      try Right(java.time.ZoneOffset.ofTotalSeconds(args(0).asInstanceOf[Int]))
      catch { case NonFatal(e) => Left(s"Failed to construct ZoneOffset: ${e.getMessage}") }
    else Left("ZoneOffset requires 1 argument: (totalSeconds)")

  private def reflectiveConstruct(id: TypeId[_], args: Chunk[AnyRef]): Either[String, Any] =
    id.clazz match {
      case None        => Left(s"Cannot construct ${id.fullName}: class not available")
      case Some(clazz) =>
        try {
          val constructors = clazz.getConstructors
          if (constructors.isEmpty) Left(s"No public constructors found for ${id.fullName}")
          else {
            val argsArray = args.toArray
            val ctor      = findMatchingConstructor(constructors, argsArray)
            ctor match {
              case Some(c) => Right(c.newInstance(argsArray: _*))
              case None    =>
                Left(
                  s"No matching constructor found for ${id.fullName} with ${args.length} arguments of types: ${argsArray.map(_.getClass.getName).mkString(", ")}"
                )
            }
          }
        } catch {
          case NonFatal(e) => Left(s"Failed to construct ${id.fullName}: ${e.getMessage}")
        }
    }

  private def findMatchingConstructor(
    constructors: Array[java.lang.reflect.Constructor[_]],
    args: Array[AnyRef]
  ): Option[java.lang.reflect.Constructor[_]] =
    constructors.find { ctor =>
      val paramTypes = ctor.getParameterTypes
      paramTypes.length == args.length && paramTypes.zip(args).forall { case (paramType, arg) =>
        arg == null || isAssignable(paramType, arg.getClass)
      }
    }

  private def isAssignable(paramType: Class[_], argType: Class[_]): Boolean =
    if (paramType.isAssignableFrom(argType)) true
    else if (paramType.isPrimitive) boxedType(paramType).isAssignableFrom(argType)
    else if (argType.isPrimitive) paramType.isAssignableFrom(boxedType(argType))
    else false

  private def boxedType(primitive: Class[_]): Class[_] =
    primitive match {
      case java.lang.Boolean.TYPE   => classOf[java.lang.Boolean]
      case java.lang.Byte.TYPE      => classOf[java.lang.Byte]
      case java.lang.Short.TYPE     => classOf[java.lang.Short]
      case java.lang.Integer.TYPE   => classOf[java.lang.Integer]
      case java.lang.Long.TYPE      => classOf[java.lang.Long]
      case java.lang.Float.TYPE     => classOf[java.lang.Float]
      case java.lang.Double.TYPE    => classOf[java.lang.Double]
      case java.lang.Character.TYPE => classOf[java.lang.Character]
      case _                        => primitive
    }

  private def toJvmClassName(owner: Owner, name: String, defKind: TypeDefKind): String = {
    val fullName = if (owner.isRoot) name else s"${owner.asString}.$name"
    scalaToJavaMapping.getOrElse(
      fullName, {
        val (ownerPath, lastWasTermOrType) = ownerToJvmPath(owner)
        val isObject                       = defKind.isInstanceOf[TypeDefKind.Object]
        val separator                      = if (lastWasTermOrType) "$" else if (ownerPath.isEmpty) "" else "."
        val className                      = s"$ownerPath$separator$name"
        if (isObject && !className.endsWith("$")) className + "$"
        else className
      }
    )
  }

  private def ownerToJvmPath(owner: Owner): (String, Boolean) =
    if (owner.segments.isEmpty) ("", false)
    else {
      val parts = owner.segments.map {
        case Owner.Package(name) => (name, false)
        case Owner.Term(name)    => (name, true)
        case Owner.Type(name)    => (name, true)
      }
      val sb = new StringBuilder
      parts.zipWithIndex.foreach { case ((name, _), idx) =>
        if (idx > 0) {
          val prevWasTermOrType = parts(idx - 1)._2
          sb.append(if (prevWasTermOrType) '$' else '.')
        }
        sb.append(name)
      }
      (sb.toString, parts.last._2)
    }
}
