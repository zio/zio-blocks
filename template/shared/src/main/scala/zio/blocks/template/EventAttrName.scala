package zio.blocks.template

final case class EventAttrName private (value: String) extends AnyVal

object EventAttrName {

  def apply(name: String): Option[EventAttrName] = {
    val lower = name.toLowerCase
    if (allowedEvents.contains(lower)) Some(new EventAttrName(lower))
    else None
  }

  def unsafe(name: String): EventAttrName = new EventAttrName(name)

  private val allowedEvents: Set[String] = Set(
    "onclick",
    "ondblclick",
    "onmousedown",
    "onmouseup",
    "onmouseover",
    "onmousemove",
    "onmouseout",
    "onmouseenter",
    "onmouseleave",
    "onkeydown",
    "onkeyup",
    "onkeypress",
    "onfocus",
    "onblur",
    "onchange",
    "oninput",
    "onsubmit",
    "onreset",
    "onscroll",
    "onresize",
    "onload",
    "onunload",
    "onerror",
    "onabort",
    "ondrag",
    "ondragend",
    "ondragenter",
    "ondragleave",
    "ondragover",
    "ondragstart",
    "ondrop",
    "oncontextmenu",
    "onwheel",
    "ontouchstart",
    "ontouchend",
    "ontouchmove",
    "ontouchcancel",
    "onanimationstart",
    "onanimationend",
    "onanimationiteration",
    "ontransitionend",
    "onselect",
    "oncopy",
    "oncut",
    "onpaste",
    "onplay",
    "onpause",
    "onended",
    "onvolumechange",
    "ontoggle",
    "oninvalid"
  )
}
