package zio.blocks.template

final case class SafeAttrName private (value: String) extends AnyVal

object SafeAttrName {

  def apply(name: String): Option[SafeAttrName] = {
    val lower = name.toLowerCase
    if (allowedAttrs.contains(lower)) Some(new SafeAttrName(lower))
    else if (lower.startsWith("data-") && isValidDataAttr(lower)) Some(new SafeAttrName(lower))
    else if (lower.startsWith("aria-") && isValidAriaAttr(lower)) Some(new SafeAttrName(lower))
    else None
  }

  def unsafe(name: String): SafeAttrName = new SafeAttrName(name)

  private def isValidDataAttr(name: String): Boolean = {
    val suffix = name.substring(5)
    suffix.nonEmpty && suffix.forall(c => c.isLetterOrDigit || c == '-')
  }

  private def isValidAriaAttr(name: String): Boolean = {
    val suffix = name.substring(5)
    suffix.nonEmpty && suffix.forall(c => c.isLetterOrDigit || c == '-')
  }

  private val allowedAttrs: Set[String] = Set(
    "id",
    "class",
    "style",
    "title",
    "href",
    "src",
    "alt",
    "width",
    "height",
    "action",
    "method",
    "name",
    "value",
    "type",
    "placeholder",
    "required",
    "disabled",
    "readonly",
    "checked",
    "selected",
    "multiple",
    "min",
    "max",
    "step",
    "pattern",
    "autofocus",
    "autocomplete",
    "target",
    "rel",
    "download",
    "role",
    "tabindex",
    "hidden",
    "draggable",
    "contenteditable",
    "lang",
    "dir",
    "colspan",
    "rowspan",
    "scope",
    "headers",
    "for",
    "enctype",
    "formaction",
    "formmethod",
    "loading",
    "srcset",
    "sizes",
    "minlength",
    "maxlength",
    "size",
    "cols",
    "rows",
    "wrap",
    "charset",
    "content",
    "http-equiv",
    "media",
    "crossorigin",
    "integrity",
    "referrerpolicy",
    "accept",
    "accept-charset",
    "accesskey",
    "cite",
    "coords",
    "datetime",
    "form",
    "high",
    "low",
    "optimum",
    "label",
    "list",
    "loop",
    "muted",
    "open",
    "poster",
    "preload",
    "sandbox",
    "shape",
    "span",
    "start",
    "summary",
    "translate",
    "usemap"
  )
}
