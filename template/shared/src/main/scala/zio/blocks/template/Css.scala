package zio.blocks.template

import zio.blocks.chunk.Chunk

sealed trait Css extends Product with Serializable {
  def render: String
  def render(indent: Int): String
}

object Css {

  final case class Rule(selector: CssSelector, declarations: Chunk[Declaration]) extends Css {
    def render: String = {
      val sb = new java.lang.StringBuilder
      sb.append(selector.render)
      sb.append('{')
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(d.property)
        sb.append(':')
        sb.append(d.value)
        sb.append(';')
        i += 1
      }
      sb.append('}')
      sb.toString
    }

    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb        = new java.lang.StringBuilder
      val indentStr = " " * indent
      sb.append(selector.render)
      sb.append(" {\n")
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(indentStr)
        sb.append(d.property)
        sb.append(": ")
        sb.append(d.value)
        sb.append(";\n")
        i += 1
      }
      sb.append('}')
      sb.toString
    }
  }

  final case class Declaration(property: String, value: String)

  final case class Sheet(rules: Chunk[Css]) extends Css {
    def render: String = {
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        sb.append(rules(i).render)
        i += 1
      }
      sb.toString
    }

    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        if (i > 0) sb.append('\n')
        sb.append(rules(i).render(indent))
        i += 1
      }
      sb.toString
    }
  }

  final case class Raw(value: String) extends Css {
    def render: String              = value
    def render(indent: Int): String = value
    def stripMargin: Raw            = Raw(value.stripMargin)
  }

  final case class Comment(text: String) extends Css {
    def render: String              = "/*" + text.replace("*/", "* /") + "*/"
    def render(indent: Int): String = "/* " + text.replace("*/", "* /") + " */"
  }

  def apply(value: String): Css = Raw(value)
}
