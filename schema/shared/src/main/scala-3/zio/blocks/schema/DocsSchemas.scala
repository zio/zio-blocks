package zio.blocks.schema

/** Schema instances for zio.blocks.docs types (markdown AST).
  * 
  * These instances are needed for implicit search by library users (issue #1030).
  * They are defined in a Scala 3-specific source file because Scala 2's macro
  * implementation cannot derive schemas for types from external modules in the
  * same compilation run.
  */
private[schema] trait DocsSchemas {
  // Leaf types
  implicit lazy val schemaHeadingLevel: Schema[zio.blocks.docs.HeadingLevel] = Schema.derived
  implicit lazy val schemaAlignment: Schema[zio.blocks.docs.Alignment] = Schema.derived
  
  // Inline variants (nested in Inline object)
  implicit lazy val schemaInlineText: Schema[zio.blocks.docs.Inline.Text] = Schema.derived
  implicit lazy val schemaInlineCode: Schema[zio.blocks.docs.Inline.Code] = Schema.derived
  implicit lazy val schemaInlineEmphasis: Schema[zio.blocks.docs.Inline.Emphasis] = Schema.derived
  implicit lazy val schemaInlineStrong: Schema[zio.blocks.docs.Inline.Strong] = Schema.derived
  implicit lazy val schemaInlineStrikethrough: Schema[zio.blocks.docs.Inline.Strikethrough] = Schema.derived
  implicit lazy val schemaInlineLink: Schema[zio.blocks.docs.Inline.Link] = Schema.derived
  implicit lazy val schemaInlineImage: Schema[zio.blocks.docs.Inline.Image] = Schema.derived
  implicit lazy val schemaInlineHtmlInline: Schema[zio.blocks.docs.Inline.HtmlInline] = Schema.derived
  implicit lazy val schemaInlineSoftBreak: Schema[zio.blocks.docs.Inline.SoftBreak.type] = Schema.derived
  implicit lazy val schemaInlineHardBreak: Schema[zio.blocks.docs.Inline.HardBreak.type] = Schema.derived
  implicit lazy val schemaInlineAutolink: Schema[zio.blocks.docs.Inline.Autolink] = Schema.derived
  
  // Top-level Inline variants (compatibility aliases)
  implicit lazy val schemaText: Schema[zio.blocks.docs.Text] = Schema.derived
  implicit lazy val schemaCode: Schema[zio.blocks.docs.Code] = Schema.derived
  implicit lazy val schemaEmphasis: Schema[zio.blocks.docs.Emphasis] = Schema.derived
  implicit lazy val schemaStrong: Schema[zio.blocks.docs.Strong] = Schema.derived
  implicit lazy val schemaStrikethrough: Schema[zio.blocks.docs.Strikethrough] = Schema.derived
  implicit lazy val schemaLink: Schema[zio.blocks.docs.Link] = Schema.derived
  implicit lazy val schemaImage: Schema[zio.blocks.docs.Image] = Schema.derived
  implicit lazy val schemaHtmlInline: Schema[zio.blocks.docs.HtmlInline] = Schema.derived
  implicit lazy val schemaSoftBreak: Schema[zio.blocks.docs.SoftBreak.type] = Schema.derived
  implicit lazy val schemaHardBreak: Schema[zio.blocks.docs.HardBreak.type] = Schema.derived
  implicit lazy val schemaAutolink: Schema[zio.blocks.docs.Autolink] = Schema.derived
  
  // Inline ADT root
  implicit lazy val schemaInline: Schema[zio.blocks.docs.Inline] = Schema.derived
  
  // Supporting types
  implicit lazy val schemaTableRow: Schema[zio.blocks.docs.TableRow] = Schema.derived
  implicit lazy val schemaListItem: Schema[zio.blocks.docs.ListItem] = Schema.derived
  
  // Block types
  implicit lazy val schemaParagraph: Schema[zio.blocks.docs.Paragraph] = Schema.derived
  implicit lazy val schemaHeading: Schema[zio.blocks.docs.Heading] = Schema.derived
  implicit lazy val schemaCodeBlock: Schema[zio.blocks.docs.CodeBlock] = Schema.derived
  implicit lazy val schemaThematicBreak: Schema[zio.blocks.docs.ThematicBreak.type] = Schema.derived
  implicit lazy val schemaBlockQuote: Schema[zio.blocks.docs.BlockQuote] = Schema.derived
  implicit lazy val schemaBulletList: Schema[zio.blocks.docs.BulletList] = Schema.derived
  implicit lazy val schemaOrderedList: Schema[zio.blocks.docs.OrderedList] = Schema.derived
  implicit lazy val schemaHtmlBlock: Schema[zio.blocks.docs.HtmlBlock] = Schema.derived
  implicit lazy val schemaTable: Schema[zio.blocks.docs.Table] = Schema.derived
  
  // Block ADT root
  implicit lazy val schemaBlock: Schema[zio.blocks.docs.Block] = Schema.derived
  
  // Document root
  implicit lazy val schemaDoc: Schema[zio.blocks.docs.Doc] = Schema.derived
}
