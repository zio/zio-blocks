package zio.blocks.template

trait ToElements[-A] {
  def toElements(a: A): Vector[Dom]
}

object ToElements {

  def apply[A](implicit ev: ToElements[A]): ToElements[A] = ev

  implicit val stringToElements: ToElements[String] = new ToElements[String] {
    def toElements(a: String): Vector[Dom] = Vector(Dom.Text(a))
  }

  implicit val intToElements: ToElements[Int] = new ToElements[Int] {
    def toElements(a: Int): Vector[Dom] = Vector(Dom.Text(a.toString))
  }

  implicit val longToElements: ToElements[Long] = new ToElements[Long] {
    def toElements(a: Long): Vector[Dom] = Vector(Dom.Text(a.toString))
  }

  implicit val doubleToElements: ToElements[Double] = new ToElements[Double] {
    def toElements(a: Double): Vector[Dom] = Vector(Dom.Text(a.toString))
  }

  implicit val booleanToElements: ToElements[Boolean] = new ToElements[Boolean] {
    def toElements(a: Boolean): Vector[Dom] = Vector(Dom.Text(a.toString))
  }

  implicit val charToElements: ToElements[Char] = new ToElements[Char] {
    def toElements(a: Char): Vector[Dom] = Vector(Dom.Text(a.toString))
  }

  implicit val domToElements: ToElements[Dom] = new ToElements[Dom] {
    def toElements(a: Dom): Vector[Dom] = Vector(a)
  }

  implicit def optionToElements[A](implicit ev: ToElements[A]): ToElements[Option[A]] =
    new ToElements[Option[A]] {
      def toElements(a: Option[A]): Vector[Dom] = a match {
        case Some(v) => ev.toElements(v)
        case None    => Vector.empty
      }
    }

  implicit def iterableToElements[A](implicit ev: ToElements[A]): ToElements[Iterable[A]] =
    new ToElements[Iterable[A]] {
      def toElements(a: Iterable[A]): Vector[Dom] = {
        val builder = Vector.newBuilder[Dom]
        val it      = a.iterator
        while (it.hasNext) {
          builder ++= ev.toElements(it.next())
        }
        builder.result()
      }
    }

  implicit def vectorToElements[A](implicit ev: ToElements[A]): ToElements[Vector[A]] =
    new ToElements[Vector[A]] {
      def toElements(a: Vector[A]): Vector[Dom] = {
        val builder = Vector.newBuilder[Dom]
        var i       = 0
        while (i < a.length) {
          builder ++= ev.toElements(a(i))
          i += 1
        }
        builder.result()
      }
    }

  implicit def listToElements[A](implicit ev: ToElements[A]): ToElements[List[A]] =
    new ToElements[List[A]] {
      def toElements(a: List[A]): Vector[Dom] = {
        val builder = Vector.newBuilder[Dom]
        var rem     = a
        while (rem.nonEmpty) {
          builder ++= ev.toElements(rem.head)
          rem = rem.tail
        }
        builder.result()
      }
    }
}
