package zio.blocks.schema.json

import zio.Chunk

sealed trait Json { self =>
  def typeIndex: Int

  def isNull: Boolean = this == Json.Null
  def isBoolean: Boolean = this.isInstanceOf[Json.Bool]
  def isNumber: Boolean = this.isInstanceOf[Json.Num]
  def isString: Boolean = this.isInstanceOf[Json.Str]
  def isArray: Boolean = this.isInstanceOf[Json.Arr]
  def isObject: Boolean = this.isInstanceOf[Json.Obj]

  def asBoolean: Option[Boolean] = this match {
    case Json.Bool(value) => Some(value)
    case _                => None
  }

  def asNumber: Option[java.math.BigDecimal] = this match {
    case Json.Num(value) => Some(value)
    case _               => None
  }

  def asString: Option[String] = this match {
    case Json.Str(value) => Some(value)
    case _               => None
  }

  def asArray: Option[Chunk[Json]] = this match {
    case Json.Arr(elements) => Some(elements)
    case _                  => None
  }

  def asObject: Option[Chunk[(String, Json)]] = this match {
    case Json.Obj(fields) => Some(fields)
    case _                => None
  }

  def apply(key: String): Option[Json] = this match {
    case Json.Obj(fields) => fields.find(_._1 == key).map(_._2)
    case _                => None
  }

  def apply(index: Int): Option[Json] = this match {
    case Json.Arr(elements) if index >= 0 && index < elements.length => Some(elements(index))
    case _ => None
  }

  def merge(that: Json): Json = (this, that) match {
    case (Json.Obj(fields1), Json.Obj(fields2)) =>
      val merged = fields1.foldLeft(fields2.toMap) { case (acc, (k, v)) =>
        acc.get(k) match {
          case Some(existing) => acc.updated(k, v.merge(existing))
          case None           => acc.updated(k, v)
        }
      }
      Json.Obj(Chunk.fromIterable(merged.toSeq))
    case (_, that) => that
  }

  def foldDown[A](z: A)(f: (A, Json) => A): A = {
    val acc = f(z, this)
    this match {
      case Json.Arr(elements) => elements.foldLeft(acc)((a, j) => j.foldDown(a)(f))
      case Json.Obj(fields)   => fields.foldLeft(acc)((a, kv) => kv._2.foldDown(a)(f))
      case _                  => acc
    }
  }

  def foldUp[A](z: A)(f: (A, Json) => A): A = {
    val inner = this match {
      case Json.Arr(elements) => elements.foldLeft(z)((a, j) => j.foldUp(a)(f))
      case Json.Obj(fields)   => fields.foldLeft(z)((a, kv) => kv._2.foldUp(a)(f))
      case _                  => z
    }
    f(inner, this)
  }

  def transformDown(f: Json => Json): Json = {
    val transformed = f(this)
    transformed match {
      case Json.Arr(elements) => Json.Arr(elements.map(_.transformDown(f)))
      case Json.Obj(fields)   => Json.Obj(fields.map { case (k, v) => (k, v.transformDown(f)) })
      case other              => other
    }
  }

  def transformUp(f: Json => Json): Json = {
    val inner = this match {
      case Json.Arr(elements) => Json.Arr(elements.map(_.transformUp(f)))
      case Json.Obj(fields)   => Json.Obj(fields.map { case (k, v) => (k, v.transformUp(f)) })
      case other              => other
    }
    f(inner)
  }

  def dropNulls: Json = transformDown {
    case Json.Obj(fields) => Json.Obj(fields.filter(_._2 != Json.Null))
    case other            => other
  }

  def sortKeys: Json = transformDown {
    case Json.Obj(fields) => Json.Obj(Chunk.fromIterable(fields.sortBy(_._1)))
    case other            => other
  }

  def normalize: Json = dropNulls.sortKeys
}

object Json {
  case object Null extends Json {
    def typeIndex: Int = 0
  }

  final case class Bool(value: Boolean) extends Json {
    def typeIndex: Int = 1
  }

  final case class Num(value: java.math.BigDecimal) extends Json {
    def typeIndex: Int = 2
  }

  final case class Str(value: String) extends Json {
    def typeIndex: Int = 3
  }

  final case class Arr(elements: Chunk[Json]) extends Json {
    def typeIndex: Int = 4
  }

  final case class Obj(fields: Chunk[(String, Json)]) extends Json {
    def typeIndex: Int = 5

    def toMap: Map[String, Json] = fields.toMap
  }

  def obj(fields: (String, Json)*): Obj = Obj(Chunk.fromIterable(fields))
  def arr(elements: Json*): Arr = Arr(Chunk.fromIterable(elements))
  def str(value: String): Str = Str(value)
  def num(value: Int): Num = Num(java.math.BigDecimal.valueOf(value.toLong))
  def num(value: Long): Num = Num(java.math.BigDecimal.valueOf(value))
  def num(value: Double): Num = Num(java.math.BigDecimal.valueOf(value))
  def num(value: java.math.BigDecimal): Num = Num(value)
  def bool(value: Boolean): Bool = Bool(value)
  val `null`: Null.type = Null
  val `true`: Bool = Bool(true)
  val `false`: Bool = Bool(false)
}
