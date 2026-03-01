package zio.blocks.template

object InterpolatorRuntime {

  def buildCss(sc: StringContext, args: Seq[String]): Css = {
    val sb        = new java.lang.StringBuilder
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) sb.append(argsIter.next())
    }
    Css(sb.toString)
  }

  def buildJs(sc: StringContext, args: Seq[String]): Js = {
    val sb        = new java.lang.StringBuilder
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) sb.append(argsIter.next())
    }
    Js(sb.toString)
  }

  def buildHtml(sc: StringContext, args: Seq[Vector[Dom]]): Dom = {
    val children  = Vector.newBuilder[Dom]
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      val part = partsIter.next()
      if (part.nonEmpty) children += Dom.RawHtml(part)
      if (argsIter.hasNext) children ++= argsIter.next()
    }
    Dom.fragment(children.result())
  }
}
