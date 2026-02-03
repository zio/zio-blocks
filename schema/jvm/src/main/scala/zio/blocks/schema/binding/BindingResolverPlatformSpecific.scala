package zio.blocks.schema.binding

import zio.blocks.schema.DynamicValue
import zio.blocks.typeid.TypeId

private[binding] trait BindingResolverPlatformSpecific {

  val reflection: BindingResolver = ReflectionResolver

  private object ReflectionResolver extends BindingResolver {
    import scala.util.Try

    private val recordCache = new java.util.concurrent.ConcurrentHashMap[TypeId[_], Option[Binding.Record[Any]]]()

    private val BooleanType: Class[_] = java.lang.Boolean.TYPE
    private val ByteType: Class[_]    = java.lang.Byte.TYPE
    private val CharType: Class[_]    = java.lang.Character.TYPE
    private val ShortType: Class[_]   = java.lang.Short.TYPE
    private val IntType: Class[_]     = java.lang.Integer.TYPE
    private val LongType: Class[_]    = java.lang.Long.TYPE
    private val FloatType: Class[_]   = java.lang.Float.TYPE
    private val DoubleType: Class[_]  = java.lang.Double.TYPE

    private def registerIncrement(clazz: Class[_]): RegisterOffset.RegisterOffset =
      if (clazz == BooleanType) RegisterOffset(booleans = 1)
      else if (clazz == ByteType) RegisterOffset(bytes = 1)
      else if (clazz == CharType) RegisterOffset(chars = 1)
      else if (clazz == ShortType) RegisterOffset(shorts = 1)
      else if (clazz == IntType) RegisterOffset(ints = 1)
      else if (clazz == LongType) RegisterOffset(longs = 1)
      else if (clazz == FloatType) RegisterOffset(floats = 1)
      else if (clazz == DoubleType) RegisterOffset(doubles = 1)
      else RegisterOffset(objects = 1)

    private def computeTotalRegisters(paramTypes: Array[Class[_]]): RegisterOffset.RegisterOffset = {
      var total: RegisterOffset.RegisterOffset = RegisterOffset.Zero
      var i                                    = 0
      while (i < paramTypes.length) {
        total = RegisterOffset.add(total, registerIncrement(paramTypes(i)))
        i += 1
      }
      total
    }

    private def computeFieldOffsets(paramTypes: Array[Class[_]]): Array[RegisterOffset.RegisterOffset] = {
      val offsets                                      = new Array[RegisterOffset.RegisterOffset](paramTypes.length)
      var currentOffset: RegisterOffset.RegisterOffset = RegisterOffset.Zero
      var i                                            = 0
      while (i < paramTypes.length) {
        offsets(i) = currentOffset
        currentOffset = RegisterOffset.add(currentOffset, registerIncrement(paramTypes(i)))
        i += 1
      }
      offsets
    }

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] = {
      val normalizedId = TypeId.normalize(typeId)
      recordCache
        .computeIfAbsent(normalizedId, _ => deriveRecordBinding(normalizedId))
        .asInstanceOf[Option[Binding.Record[A]]]
    }

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] = None

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] = None

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] = None

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] = None

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] = None

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] = None

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] = None

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] = None

    private def deriveRecordBinding(typeId: TypeId[_]): Option[Binding.Record[Any]] =
      if (!typeId.isCaseClass) None
      else
        typeId.clazz.flatMap { clazz =>
          Try {
            val constructors = clazz.getConstructors
            if (constructors.isEmpty) None
            else {
              val primaryCtor = constructors.head
              val paramTypes  = primaryCtor.getParameterTypes
              val paramCount  = paramTypes.length

              val fields = clazz.getDeclaredFields.take(paramCount)
              fields.foreach(_.setAccessible(true))

              val totalRegs    = computeTotalRegisters(paramTypes)
              val fieldOffsets = computeFieldOffsets(paramTypes)

              val constructor = new Constructor[Any] {
                def usedRegisters: RegisterOffset.RegisterOffset = totalRegs

                def construct(in: Registers, baseOffset: RegisterOffset.RegisterOffset): Any = {
                  val args = new Array[AnyRef](paramCount)
                  var i    = 0
                  while (i < paramCount) {
                    val paramType = paramTypes(i)
                    val offset    = RegisterOffset.add(baseOffset, fieldOffsets(i))
                    if (paramType == BooleanType) {
                      args(i) = java.lang.Boolean.valueOf(in.getBoolean(offset))
                    } else if (paramType == ByteType) {
                      args(i) = java.lang.Byte.valueOf(in.getByte(offset))
                    } else if (paramType == CharType) {
                      args(i) = java.lang.Character.valueOf(in.getChar(offset))
                    } else if (paramType == ShortType) {
                      args(i) = java.lang.Short.valueOf(in.getShort(offset))
                    } else if (paramType == IntType) {
                      args(i) = java.lang.Integer.valueOf(in.getInt(offset))
                    } else if (paramType == LongType) {
                      args(i) = java.lang.Long.valueOf(in.getLong(offset))
                    } else if (paramType == FloatType) {
                      args(i) = java.lang.Float.valueOf(in.getFloat(offset))
                    } else if (paramType == DoubleType) {
                      args(i) = java.lang.Double.valueOf(in.getDouble(offset))
                    } else {
                      args(i) = in.getObject(offset)
                    }
                    i += 1
                  }
                  primaryCtor.newInstance(args: _*)
                }
              }

              val deconstructor = new Deconstructor[Any] {
                def usedRegisters: RegisterOffset.RegisterOffset = totalRegs

                def deconstruct(out: Registers, baseOffset: RegisterOffset.RegisterOffset, in: Any): Unit = {
                  var i = 0
                  while (i < paramCount) {
                    val field     = fields(i)
                    val paramType = paramTypes(i)
                    val offset    = RegisterOffset.add(baseOffset, fieldOffsets(i))
                    if (paramType == BooleanType) {
                      out.setBoolean(offset, field.getBoolean(in))
                    } else if (paramType == ByteType) {
                      out.setByte(offset, field.getByte(in))
                    } else if (paramType == CharType) {
                      out.setChar(offset, field.getChar(in))
                    } else if (paramType == ShortType) {
                      out.setShort(offset, field.getShort(in))
                    } else if (paramType == IntType) {
                      out.setInt(offset, field.getInt(in))
                    } else if (paramType == LongType) {
                      out.setLong(offset, field.getLong(in))
                    } else if (paramType == FloatType) {
                      out.setFloat(offset, field.getFloat(in))
                    } else if (paramType == DoubleType) {
                      out.setDouble(offset, field.getDouble(in))
                    } else {
                      out.setObject(offset, field.get(in).asInstanceOf[AnyRef])
                    }
                    i += 1
                  }
                }
              }

              Some(Binding.Record(constructor, deconstructor))
            }
          }.toOption.flatten
        }

    override def toString: String = "BindingResolver.Reflection"
  }
}
