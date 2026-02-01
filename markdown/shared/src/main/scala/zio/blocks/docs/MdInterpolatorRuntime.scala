package zio.blocks.docs

object MdInterpolatorRuntime {

  def parseAndBuild(sc: StringContext, args: Seq[Inline]): Doc = {
    val combined = buildMarkdownString(sc.parts, args)
    Parser.parse(combined) match {
      case Right(doc) => doc
      case Left(err)  => throw new IllegalArgumentException(s"Invalid markdown: ${err.message}")
    }
  }

  private def buildMarkdownString(parts: Seq[String], args: Seq[Inline]): String = {
    val sb        = new StringBuilder
    val partsIter = parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) {
        sb.append(Renderer.renderInline(argsIter.next()))
      }
    }
    sb.toString
  }
}
