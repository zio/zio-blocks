package zio.blocks.template

class InlineTag(name: String) extends Tag(name) {

  inline def optimizedApply(inline modifier: Modifier, inline modifiers: Modifier*): Dom.Element =
    ${ TagMacro.applyImpl('this, 'modifier, 'modifiers) }
}
