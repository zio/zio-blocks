package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.docs._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.{Owner, TypeId}

private[schema] trait DocsSchemas {
  private[this] val docsOwner: Owner         = Owner.fromPackagePath("zio.blocks.docs")
  private[this] val headingLevelOwner: Owner = docsOwner.tpe("HeadingLevel")
  private[this] val alignmentOwner: Owner    = docsOwner.tpe("Alignment")
  private[this] val inlineOwner: Owner       = docsOwner.tpe("Inline")

  // ===========================================================================
  // HeadingLevel (sealed abstract class with H1-H6 case objects)
  // ===========================================================================

  private[this] lazy val headingLevelH1Schema: Schema[HeadingLevel.H1.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H1.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H1.type]("H1", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H1.type](HeadingLevel.H1),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H1.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val headingLevelH2Schema: Schema[HeadingLevel.H2.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H2.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H2.type]("H2", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H2.type](HeadingLevel.H2),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H2.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val headingLevelH3Schema: Schema[HeadingLevel.H3.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H3.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H3.type]("H3", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H3.type](HeadingLevel.H3),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H3.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val headingLevelH4Schema: Schema[HeadingLevel.H4.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H4.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H4.type]("H4", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H4.type](HeadingLevel.H4),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H4.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val headingLevelH5Schema: Schema[HeadingLevel.H5.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H5.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H5.type]("H5", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H5.type](HeadingLevel.H5),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H5.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val headingLevelH6Schema: Schema[HeadingLevel.H6.type] = new Schema(
    reflect = new Reflect.Record[Binding, HeadingLevel.H6.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HeadingLevel.H6.type]("H6", headingLevelOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HeadingLevel.H6.type](HeadingLevel.H6),
        deconstructor = new ConstantDeconstructor[HeadingLevel.H6.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaHeadingLevel: Schema[HeadingLevel] = new Schema(
    reflect = new Reflect.Variant[Binding, HeadingLevel](
      cases = Chunk(
        headingLevelH1Schema.reflect.asTerm("H1"),
        headingLevelH2Schema.reflect.asTerm("H2"),
        headingLevelH3Schema.reflect.asTerm("H3"),
        headingLevelH4Schema.reflect.asTerm("H4"),
        headingLevelH5Schema.reflect.asTerm("H5"),
        headingLevelH6Schema.reflect.asTerm("H6")
      ),
      typeId = TypeId.nominal[HeadingLevel]("HeadingLevel", docsOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[HeadingLevel] {
          def discriminate(a: HeadingLevel): Int = a match {
            case HeadingLevel.H1 => 0
            case HeadingLevel.H2 => 1
            case HeadingLevel.H3 => 2
            case HeadingLevel.H4 => 3
            case HeadingLevel.H5 => 4
            case HeadingLevel.H6 => 5
          }
        },
        matchers = Matchers(
          new Matcher[HeadingLevel.H1.type] {
            def downcastOrNull(a: Any): HeadingLevel.H1.type = a match {
              case HeadingLevel.H1 => HeadingLevel.H1
              case _               => null.asInstanceOf[HeadingLevel.H1.type]
            }
          },
          new Matcher[HeadingLevel.H2.type] {
            def downcastOrNull(a: Any): HeadingLevel.H2.type = a match {
              case HeadingLevel.H2 => HeadingLevel.H2
              case _               => null.asInstanceOf[HeadingLevel.H2.type]
            }
          },
          new Matcher[HeadingLevel.H3.type] {
            def downcastOrNull(a: Any): HeadingLevel.H3.type = a match {
              case HeadingLevel.H3 => HeadingLevel.H3
              case _               => null.asInstanceOf[HeadingLevel.H3.type]
            }
          },
          new Matcher[HeadingLevel.H4.type] {
            def downcastOrNull(a: Any): HeadingLevel.H4.type = a match {
              case HeadingLevel.H4 => HeadingLevel.H4
              case _               => null.asInstanceOf[HeadingLevel.H4.type]
            }
          },
          new Matcher[HeadingLevel.H5.type] {
            def downcastOrNull(a: Any): HeadingLevel.H5.type = a match {
              case HeadingLevel.H5 => HeadingLevel.H5
              case _               => null.asInstanceOf[HeadingLevel.H5.type]
            }
          },
          new Matcher[HeadingLevel.H6.type] {
            def downcastOrNull(a: Any): HeadingLevel.H6.type = a match {
              case HeadingLevel.H6 => HeadingLevel.H6
              case _               => null.asInstanceOf[HeadingLevel.H6.type]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Alignment (sealed trait with Left, Center, Right, None case objects)
  // ===========================================================================

  private[this] lazy val alignmentLeftSchema: Schema[Alignment.Left.type] = new Schema(
    reflect = new Reflect.Record[Binding, Alignment.Left.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Alignment.Left.type]("Left", alignmentOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Alignment.Left.type](Alignment.Left),
        deconstructor = new ConstantDeconstructor[Alignment.Left.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val alignmentCenterSchema: Schema[Alignment.Center.type] = new Schema(
    reflect = new Reflect.Record[Binding, Alignment.Center.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Alignment.Center.type]("Center", alignmentOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Alignment.Center.type](Alignment.Center),
        deconstructor = new ConstantDeconstructor[Alignment.Center.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val alignmentRightSchema: Schema[Alignment.Right.type] = new Schema(
    reflect = new Reflect.Record[Binding, Alignment.Right.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Alignment.Right.type]("Right", alignmentOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Alignment.Right.type](Alignment.Right),
        deconstructor = new ConstantDeconstructor[Alignment.Right.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val alignmentNoneSchema: Schema[Alignment.None.type] = new Schema(
    reflect = new Reflect.Record[Binding, Alignment.None.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Alignment.None.type]("None", alignmentOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Alignment.None.type](Alignment.None),
        deconstructor = new ConstantDeconstructor[Alignment.None.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaAlignment: Schema[Alignment] = new Schema(
    reflect = new Reflect.Variant[Binding, Alignment](
      cases = Chunk(
        alignmentLeftSchema.reflect.asTerm("Left"),
        alignmentCenterSchema.reflect.asTerm("Center"),
        alignmentRightSchema.reflect.asTerm("Right"),
        alignmentNoneSchema.reflect.asTerm("None")
      ),
      typeId = TypeId.nominal[Alignment]("Alignment", docsOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Alignment] {
          def discriminate(a: Alignment): Int = a match {
            case Alignment.Left   => 0
            case Alignment.Center => 1
            case Alignment.Right  => 2
            case Alignment.None   => 3
          }
        },
        matchers = Matchers(
          new Matcher[Alignment.Left.type] {
            def downcastOrNull(a: Any): Alignment.Left.type = a match {
              case Alignment.Left => Alignment.Left
              case _              => null.asInstanceOf[Alignment.Left.type]
            }
          },
          new Matcher[Alignment.Center.type] {
            def downcastOrNull(a: Any): Alignment.Center.type = a match {
              case Alignment.Center => Alignment.Center
              case _                => null.asInstanceOf[Alignment.Center.type]
            }
          },
          new Matcher[Alignment.Right.type] {
            def downcastOrNull(a: Any): Alignment.Right.type = a match {
              case Alignment.Right => Alignment.Right
              case _               => null.asInstanceOf[Alignment.Right.type]
            }
          },
          new Matcher[Alignment.None.type] {
            def downcastOrNull(a: Any): Alignment.None.type = a match {
              case Alignment.None => Alignment.None
              case _              => null.asInstanceOf[Alignment.None.type]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Inline leaf types (inside Inline companion)
  // ===========================================================================

  implicit lazy val schemaInlineText: Schema[Inline.Text] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Text](
      fields = Chunk(Schema[String].reflect.asTerm("value")),
      typeId = TypeId.nominal[Inline.Text]("Text", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Text] {
          def usedRegisters: RegisterOffset                                 = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.Text =
            Inline.Text(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Inline.Text] {
          def usedRegisters: RegisterOffset                                              = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Text): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaInlineCode: Schema[Inline.Code] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Code](
      fields = Chunk(Schema[String].reflect.asTerm("value")),
      typeId = TypeId.nominal[Inline.Code]("Code", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Code] {
          def usedRegisters: RegisterOffset                                 = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.Code =
            Inline.Code(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Inline.Code] {
          def usedRegisters: RegisterOffset                                              = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Code): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaInlineHtmlInline: Schema[Inline.HtmlInline] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.HtmlInline](
      fields = Chunk(Schema[String].reflect.asTerm("content")),
      typeId = TypeId.nominal[Inline.HtmlInline]("HtmlInline", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.HtmlInline] {
          def usedRegisters: RegisterOffset                                       = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.HtmlInline =
            Inline.HtmlInline(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Inline.HtmlInline] {
          def usedRegisters: RegisterOffset                                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.HtmlInline): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaInlineSoftBreak: Schema[Inline.SoftBreak.type] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.SoftBreak.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Inline.SoftBreak.type]("SoftBreak", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Inline.SoftBreak.type](Inline.SoftBreak),
        deconstructor = new ConstantDeconstructor[Inline.SoftBreak.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaInlineHardBreak: Schema[Inline.HardBreak.type] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.HardBreak.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[Inline.HardBreak.type]("HardBreak", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Inline.HardBreak.type](Inline.HardBreak),
        deconstructor = new ConstantDeconstructor[Inline.HardBreak.type]
      ),
      modifiers = Chunk.empty
    )
  )

  // Autolink(url: String, isEmail: Boolean) — 1 object + 1 boolean
  implicit lazy val schemaInlineAutolink: Schema[Inline.Autolink] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Autolink](
      fields = Chunk(
        Schema[String].reflect.asTerm("url"),
        Schema[Boolean].reflect.asTerm("isEmail")
      ),
      typeId = TypeId.nominal[Inline.Autolink]("Autolink", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Autolink] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset.add(1L, 0x100000000L)
          def construct(in: Registers, offset: RegisterOffset): Inline.Autolink =
            Inline.Autolink(
              in.getObject(offset).asInstanceOf[String],
              in.getBoolean(offset)
            )
        },
        deconstructor = new Deconstructor[Inline.Autolink] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset.add(1L, 0x100000000L)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Autolink): Unit = {
            out.setObject(offset, in.url)
            out.setBoolean(offset, in.isEmail)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Image(alt: String, url: String, title: Option[String]) — 3 objects
  implicit lazy val schemaInlineImage: Schema[Inline.Image] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Image](
      fields = Chunk(
        Schema[String].reflect.asTerm("alt"),
        Schema[String].reflect.asTerm("url"),
        Schema[Option[String]].reflect.asTerm("title")
      ),
      typeId = TypeId.nominal[Inline.Image]("Image", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Image] {
          def usedRegisters: RegisterOffset                                  = 3
          def construct(in: Registers, offset: RegisterOffset): Inline.Image =
            Inline.Image(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Option[String]]
            )
        },
        deconstructor = new Deconstructor[Inline.Image] {
          def usedRegisters: RegisterOffset                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Image): Unit = {
            out.setObject(offset, in.alt)
            out.setObject(offset + 1, in.url)
            out.setObject(offset + 2, in.title)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Inline recursive types (contain Chunk[Inline], must use Deferred)
  // ===========================================================================

  // Emphasis(content: Chunk[Inline]) — 1 object, uses Deferred
  implicit lazy val schemaInlineEmphasis: Schema[Inline.Emphasis] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Emphasis](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Inline.Emphasis]("Emphasis", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Emphasis] {
          def usedRegisters: RegisterOffset                                     = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.Emphasis =
            Inline.Emphasis(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Inline.Emphasis] {
          def usedRegisters: RegisterOffset                                                  = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Emphasis): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Strong(content: Chunk[Inline]) — 1 object, uses Deferred
  implicit lazy val schemaInlineStrong: Schema[Inline.Strong] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Strong](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Inline.Strong]("Strong", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Strong] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.Strong =
            Inline.Strong(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Inline.Strong] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Strong): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Strikethrough(content: Chunk[Inline]) — 1 object, uses Deferred
  implicit lazy val schemaInlineStrikethrough: Schema[Inline.Strikethrough] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Strikethrough](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Inline.Strikethrough]("Strikethrough", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Strikethrough] {
          def usedRegisters: RegisterOffset                                          = 1
          def construct(in: Registers, offset: RegisterOffset): Inline.Strikethrough =
            Inline.Strikethrough(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Inline.Strikethrough] {
          def usedRegisters: RegisterOffset                                                       = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Strikethrough): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Link(text: Chunk[Inline], url: String, title: Option[String]) — 3 objects, uses Deferred for text
  implicit lazy val schemaInlineLink: Schema[Inline.Link] = new Schema(
    reflect = new Reflect.Record[Binding, Inline.Link](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("text"),
        Schema[String].reflect.asTerm("url"),
        Schema[Option[String]].reflect.asTerm("title")
      ),
      typeId = TypeId.nominal[Inline.Link]("Link", inlineOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Inline.Link] {
          def usedRegisters: RegisterOffset                                 = 3
          def construct(in: Registers, offset: RegisterOffset): Inline.Link =
            Inline.Link(
              in.getObject(offset).asInstanceOf[Chunk[Inline]],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Option[String]]
            )
        },
        deconstructor = new Deconstructor[Inline.Link] {
          def usedRegisters: RegisterOffset                                              = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Inline.Link): Unit = {
            out.setObject(offset, in.text)
            out.setObject(offset + 1, in.url)
            out.setObject(offset + 2, in.title)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Inline sealed trait (Variant with 11 cases)
  // ===========================================================================

  implicit lazy val schemaInline: Schema[Inline] = new Schema(
    reflect = new Reflect.Variant[Binding, Inline](
      cases = Chunk(
        schemaInlineText.reflect.asTerm("Text"),
        schemaInlineCode.reflect.asTerm("Code"),
        schemaInlineEmphasis.reflect.asTerm("Emphasis"),
        schemaInlineStrong.reflect.asTerm("Strong"),
        schemaInlineStrikethrough.reflect.asTerm("Strikethrough"),
        schemaInlineLink.reflect.asTerm("Link"),
        schemaInlineImage.reflect.asTerm("Image"),
        schemaInlineHtmlInline.reflect.asTerm("HtmlInline"),
        schemaInlineSoftBreak.reflect.asTerm("SoftBreak"),
        schemaInlineHardBreak.reflect.asTerm("HardBreak"),
        schemaInlineAutolink.reflect.asTerm("Autolink")
      ),
      typeId = TypeId.nominal[Inline]("Inline", docsOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Inline] {
          def discriminate(a: Inline): Int = a match {
            case _: Inline.Text          => 0
            case _: Text                 => 0
            case _: Inline.Code          => 1
            case _: Code                 => 1
            case _: Inline.Emphasis      => 2
            case _: Emphasis             => 2
            case _: Inline.Strong        => 3
            case _: Strong               => 3
            case _: Inline.Strikethrough => 4
            case _: Strikethrough        => 4
            case _: Inline.Link          => 5
            case _: Link                 => 5
            case _: Inline.Image         => 6
            case _: Image                => 6
            case _: Inline.HtmlInline    => 7
            case _: HtmlInline           => 7
            case Inline.SoftBreak        => 8
            case SoftBreak               => 8
            case Inline.HardBreak        => 9
            case HardBreak               => 9
            case _: Inline.Autolink      => 10
            case _: Autolink             => 10
          }
        },
        matchers = Matchers(
          new Matcher[Inline.Text] {
            def downcastOrNull(a: Any): Inline.Text = a match {
              case x: Inline.Text => x
              case Text(v)        => Inline.Text(v)
              case _              => null.asInstanceOf[Inline.Text]
            }
          },
          new Matcher[Inline.Code] {
            def downcastOrNull(a: Any): Inline.Code = a match {
              case x: Inline.Code => x
              case Code(v)        => Inline.Code(v)
              case _              => null.asInstanceOf[Inline.Code]
            }
          },
          new Matcher[Inline.Emphasis] {
            def downcastOrNull(a: Any): Inline.Emphasis = a match {
              case x: Inline.Emphasis => x
              case Emphasis(c)        => Inline.Emphasis(c)
              case _                  => null.asInstanceOf[Inline.Emphasis]
            }
          },
          new Matcher[Inline.Strong] {
            def downcastOrNull(a: Any): Inline.Strong = a match {
              case x: Inline.Strong => x
              case Strong(c)        => Inline.Strong(c)
              case _                => null.asInstanceOf[Inline.Strong]
            }
          },
          new Matcher[Inline.Strikethrough] {
            def downcastOrNull(a: Any): Inline.Strikethrough = a match {
              case x: Inline.Strikethrough => x
              case Strikethrough(c)        => Inline.Strikethrough(c)
              case _                       => null.asInstanceOf[Inline.Strikethrough]
            }
          },
          new Matcher[Inline.Link] {
            def downcastOrNull(a: Any): Inline.Link = a match {
              case x: Inline.Link => x
              case Link(t, u, ti) => Inline.Link(t, u, ti)
              case _              => null.asInstanceOf[Inline.Link]
            }
          },
          new Matcher[Inline.Image] {
            def downcastOrNull(a: Any): Inline.Image = a match {
              case x: Inline.Image => x
              case Image(a2, u, t) => Inline.Image(a2, u, t)
              case _               => null.asInstanceOf[Inline.Image]
            }
          },
          new Matcher[Inline.HtmlInline] {
            def downcastOrNull(a: Any): Inline.HtmlInline = a match {
              case x: Inline.HtmlInline => x
              case HtmlInline(c)        => Inline.HtmlInline(c)
              case _                    => null.asInstanceOf[Inline.HtmlInline]
            }
          },
          new Matcher[Inline.SoftBreak.type] {
            def downcastOrNull(a: Any): Inline.SoftBreak.type = a match {
              case Inline.SoftBreak => Inline.SoftBreak
              case SoftBreak        => Inline.SoftBreak
              case _                => null.asInstanceOf[Inline.SoftBreak.type]
            }
          },
          new Matcher[Inline.HardBreak.type] {
            def downcastOrNull(a: Any): Inline.HardBreak.type = a match {
              case Inline.HardBreak => Inline.HardBreak
              case HardBreak        => Inline.HardBreak
              case _                => null.asInstanceOf[Inline.HardBreak.type]
            }
          },
          new Matcher[Inline.Autolink] {
            def downcastOrNull(a: Any): Inline.Autolink = a match {
              case x: Inline.Autolink => x
              case Autolink(u, e)     => Inline.Autolink(u, e)
              case _                  => null.asInstanceOf[Inline.Autolink]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Top-level Inline aliases (separate classes with identical structure)
  // ===========================================================================

  implicit lazy val schemaText: Schema[Text] = new Schema(
    reflect = new Reflect.Record[Binding, Text](
      fields = Chunk(Schema[String].reflect.asTerm("value")),
      typeId = TypeId.nominal[Text]("Text", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Text] {
          def usedRegisters: RegisterOffset                          = 1
          def construct(in: Registers, offset: RegisterOffset): Text =
            Text(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Text] {
          def usedRegisters: RegisterOffset                                       = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Text): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaCode: Schema[Code] = new Schema(
    reflect = new Reflect.Record[Binding, Code](
      fields = Chunk(Schema[String].reflect.asTerm("value")),
      typeId = TypeId.nominal[Code]("Code", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Code] {
          def usedRegisters: RegisterOffset                          = 1
          def construct(in: Registers, offset: RegisterOffset): Code =
            Code(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Code] {
          def usedRegisters: RegisterOffset                                       = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Code): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaEmphasis: Schema[Emphasis] = new Schema(
    reflect = new Reflect.Record[Binding, Emphasis](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Emphasis]("Emphasis", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Emphasis] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Emphasis =
            Emphasis(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Emphasis] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Emphasis): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaStrong: Schema[Strong] = new Schema(
    reflect = new Reflect.Record[Binding, Strong](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Strong]("Strong", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Strong] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): Strong =
            Strong(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Strong] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Strong): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaStrikethrough: Schema[Strikethrough] = new Schema(
    reflect = new Reflect.Record[Binding, Strikethrough](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Strikethrough]("Strikethrough", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Strikethrough] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Strikethrough =
            Strikethrough(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Strikethrough] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Strikethrough): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaLink: Schema[Link] = new Schema(
    reflect = new Reflect.Record[Binding, Link](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("text"),
        Schema[String].reflect.asTerm("url"),
        Schema[Option[String]].reflect.asTerm("title")
      ),
      typeId = TypeId.nominal[Link]("Link", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Link] {
          def usedRegisters: RegisterOffset                          = 3
          def construct(in: Registers, offset: RegisterOffset): Link =
            Link(
              in.getObject(offset).asInstanceOf[Chunk[Inline]],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Option[String]]
            )
        },
        deconstructor = new Deconstructor[Link] {
          def usedRegisters: RegisterOffset                                       = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Link): Unit = {
            out.setObject(offset, in.text)
            out.setObject(offset + 1, in.url)
            out.setObject(offset + 2, in.title)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaImage: Schema[Image] = new Schema(
    reflect = new Reflect.Record[Binding, Image](
      fields = Chunk(
        Schema[String].reflect.asTerm("alt"),
        Schema[String].reflect.asTerm("url"),
        Schema[Option[String]].reflect.asTerm("title")
      ),
      typeId = TypeId.nominal[Image]("Image", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Image] {
          def usedRegisters: RegisterOffset                           = 3
          def construct(in: Registers, offset: RegisterOffset): Image =
            Image(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Option[String]]
            )
        },
        deconstructor = new Deconstructor[Image] {
          def usedRegisters: RegisterOffset                                        = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Image): Unit = {
            out.setObject(offset, in.alt)
            out.setObject(offset + 1, in.url)
            out.setObject(offset + 2, in.title)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaHtmlInline: Schema[HtmlInline] = new Schema(
    reflect = new Reflect.Record[Binding, HtmlInline](
      fields = Chunk(Schema[String].reflect.asTerm("content")),
      typeId = TypeId.nominal[HtmlInline]("HtmlInline", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[HtmlInline] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): HtmlInline =
            HtmlInline(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[HtmlInline] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: HtmlInline): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaSoftBreak: Schema[SoftBreak.type] = new Schema(
    reflect = new Reflect.Record[Binding, SoftBreak.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[SoftBreak.type]("SoftBreak", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[SoftBreak.type](SoftBreak),
        deconstructor = new ConstantDeconstructor[SoftBreak.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaHardBreak: Schema[HardBreak.type] = new Schema(
    reflect = new Reflect.Record[Binding, HardBreak.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[HardBreak.type]("HardBreak", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[HardBreak.type](HardBreak),
        deconstructor = new ConstantDeconstructor[HardBreak.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaAutolink: Schema[Autolink] = new Schema(
    reflect = new Reflect.Record[Binding, Autolink](
      fields = Chunk(
        Schema[String].reflect.asTerm("url"),
        Schema[Boolean].reflect.asTerm("isEmail")
      ),
      typeId = TypeId.nominal[Autolink]("Autolink", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Autolink] {
          def usedRegisters: RegisterOffset                              = RegisterOffset.add(1L, 0x100000000L)
          def construct(in: Registers, offset: RegisterOffset): Autolink =
            Autolink(
              in.getObject(offset).asInstanceOf[String],
              in.getBoolean(offset)
            )
        },
        deconstructor = new Deconstructor[Autolink] {
          def usedRegisters: RegisterOffset                                           = RegisterOffset.add(1L, 0x100000000L)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Autolink): Unit = {
            out.setObject(offset, in.url)
            out.setBoolean(offset, in.isEmail)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Supporting types
  // ===========================================================================

  // TableRow(cells: Chunk[Chunk[Inline]]) — 1 object
  implicit lazy val schemaTableRow: Schema[TableRow] = new Schema(
    reflect = new Reflect.Record[Binding, TableRow](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Chunk[Inline]]](() => Schema[Chunk[Chunk[Inline]]].reflect).asTerm("cells")
      ),
      typeId = TypeId.nominal[TableRow]("TableRow", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TableRow] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): TableRow =
            TableRow(in.getObject(offset).asInstanceOf[Chunk[Chunk[Inline]]])
        },
        deconstructor = new Deconstructor[TableRow] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: TableRow): Unit =
            out.setObject(offset, in.cells)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ListItem(content: Chunk[Block], checked: Option[Boolean]) — 2 objects
  implicit lazy val schemaListItem: Schema[ListItem] = new Schema(
    reflect = new Reflect.Record[Binding, ListItem](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Block]](() => Schema[Chunk[Block]].reflect).asTerm("content"),
        Schema[Option[Boolean]].reflect.asTerm("checked")
      ),
      typeId = TypeId.nominal[ListItem]("ListItem", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[ListItem] {
          def usedRegisters: RegisterOffset                              = 2
          def construct(in: Registers, offset: RegisterOffset): ListItem =
            ListItem(
              in.getObject(offset).asInstanceOf[Chunk[Block]],
              in.getObject(offset + 1).asInstanceOf[Option[Boolean]]
            )
        },
        deconstructor = new Deconstructor[ListItem] {
          def usedRegisters: RegisterOffset                                           = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: ListItem): Unit = {
            out.setObject(offset, in.content)
            out.setObject(offset + 1, in.checked)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Block types
  // ===========================================================================

  // Paragraph(content: Chunk[Inline]) — 1 object
  implicit lazy val schemaParagraph: Schema[Paragraph] = new Schema(
    reflect = new Reflect.Record[Binding, Paragraph](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Paragraph]("Paragraph", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Paragraph] {
          def usedRegisters: RegisterOffset                               = 1
          def construct(in: Registers, offset: RegisterOffset): Paragraph =
            Paragraph(in.getObject(offset).asInstanceOf[Chunk[Inline]])
        },
        deconstructor = new Deconstructor[Paragraph] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Paragraph): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Heading(level: HeadingLevel, content: Chunk[Inline]) — 2 objects
  implicit lazy val schemaHeading: Schema[Heading] = new Schema(
    reflect = new Reflect.Record[Binding, Heading](
      fields = Chunk(
        schemaHeadingLevel.reflect.asTerm("level"),
        new Reflect.Deferred[Binding, Chunk[Inline]](() => Schema[Chunk[Inline]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[Heading]("Heading", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Heading] {
          def usedRegisters: RegisterOffset                             = 2
          def construct(in: Registers, offset: RegisterOffset): Heading =
            Heading(
              in.getObject(offset).asInstanceOf[HeadingLevel],
              in.getObject(offset + 1).asInstanceOf[Chunk[Inline]]
            )
        },
        deconstructor = new Deconstructor[Heading] {
          def usedRegisters: RegisterOffset                                          = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Heading): Unit = {
            out.setObject(offset, in.level)
            out.setObject(offset + 1, in.content)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // CodeBlock(info: Option[String], code: String) — 2 objects
  implicit lazy val schemaCodeBlock: Schema[CodeBlock] = new Schema(
    reflect = new Reflect.Record[Binding, CodeBlock](
      fields = Chunk(
        Schema[Option[String]].reflect.asTerm("info"),
        Schema[String].reflect.asTerm("code")
      ),
      typeId = TypeId.nominal[CodeBlock]("CodeBlock", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[CodeBlock] {
          def usedRegisters: RegisterOffset                               = 2
          def construct(in: Registers, offset: RegisterOffset): CodeBlock =
            CodeBlock(
              in.getObject(offset).asInstanceOf[Option[String]],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[CodeBlock] {
          def usedRegisters: RegisterOffset                                            = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: CodeBlock): Unit = {
            out.setObject(offset, in.info)
            out.setObject(offset + 1, in.code)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ThematicBreak — case object
  implicit lazy val schemaThematicBreak: Schema[ThematicBreak.type] = new Schema(
    reflect = new Reflect.Record[Binding, ThematicBreak.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[ThematicBreak.type]("ThematicBreak", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[ThematicBreak.type](ThematicBreak),
        deconstructor = new ConstantDeconstructor[ThematicBreak.type]
      ),
      modifiers = Chunk.empty
    )
  )

  // BlockQuote(content: Chunk[Block]) — 1 object, uses Deferred for Chunk[Block]
  implicit lazy val schemaBlockQuote: Schema[BlockQuote] = new Schema(
    reflect = new Reflect.Record[Binding, BlockQuote](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Block]](() => Schema[Chunk[Block]].reflect).asTerm("content")
      ),
      typeId = TypeId.nominal[BlockQuote]("BlockQuote", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[BlockQuote] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): BlockQuote =
            BlockQuote(in.getObject(offset).asInstanceOf[Chunk[Block]])
        },
        deconstructor = new Deconstructor[BlockQuote] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: BlockQuote): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // BulletList(items: Chunk[ListItem], tight: Boolean) — 1 object + 1 boolean
  implicit lazy val schemaBulletList: Schema[BulletList] = new Schema(
    reflect = new Reflect.Record[Binding, BulletList](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[ListItem]](() => Schema[Chunk[ListItem]].reflect).asTerm("items"),
        Schema[Boolean].reflect.asTerm("tight")
      ),
      typeId = TypeId.nominal[BulletList]("BulletList", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[BulletList] {
          def usedRegisters: RegisterOffset                                = RegisterOffset.add(1L, 0x100000000L)
          def construct(in: Registers, offset: RegisterOffset): BulletList =
            BulletList(
              in.getObject(offset).asInstanceOf[Chunk[ListItem]],
              in.getBoolean(offset)
            )
        },
        deconstructor = new Deconstructor[BulletList] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset.add(1L, 0x100000000L)
          def deconstruct(out: Registers, offset: RegisterOffset, in: BulletList): Unit = {
            out.setObject(offset, in.items)
            out.setBoolean(offset, in.tight)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean) — 1 int + 1 object + 1 boolean
  implicit lazy val schemaOrderedList: Schema[OrderedList] = new Schema(
    reflect = new Reflect.Record[Binding, OrderedList](
      fields = Chunk(
        Schema[Int].reflect.asTerm("start"),
        new Reflect.Deferred[Binding, Chunk[ListItem]](() => Schema[Chunk[ListItem]].reflect).asTerm("items"),
        Schema[Boolean].reflect.asTerm("tight")
      ),
      typeId = TypeId.nominal[OrderedList]("OrderedList", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[OrderedList] {
          // 1 int (4 bytes) + 1 object + 1 boolean (1 byte) = 5 bytes + 1 object
          def usedRegisters: RegisterOffset                                 = RegisterOffset.add(RegisterOffset.add(0x400000000L, 1L), 0x100000000L)
          def construct(in: Registers, offset: RegisterOffset): OrderedList =
            OrderedList(
              in.getInt(offset),
              in.getObject(offset).asInstanceOf[Chunk[ListItem]],
              in.getBoolean(RegisterOffset.add(offset, 0x400000000L))
            )
        },
        deconstructor = new Deconstructor[OrderedList] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset.add(RegisterOffset.add(0x400000000L, 1L), 0x100000000L)
          def deconstruct(out: Registers, offset: RegisterOffset, in: OrderedList): Unit = {
            out.setInt(offset, in.start)
            out.setObject(offset, in.items)
            out.setBoolean(RegisterOffset.add(offset, 0x400000000L), in.tight)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // HtmlBlock(content: String) — 1 object
  implicit lazy val schemaHtmlBlock: Schema[HtmlBlock] = new Schema(
    reflect = new Reflect.Record[Binding, HtmlBlock](
      fields = Chunk(Schema[String].reflect.asTerm("content")),
      typeId = TypeId.nominal[HtmlBlock]("HtmlBlock", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[HtmlBlock] {
          def usedRegisters: RegisterOffset                               = 1
          def construct(in: Registers, offset: RegisterOffset): HtmlBlock =
            HtmlBlock(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[HtmlBlock] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: HtmlBlock): Unit =
            out.setObject(offset, in.content)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow]) — 3 objects
  implicit lazy val schemaTable: Schema[Table] = new Schema(
    reflect = new Reflect.Record[Binding, Table](
      fields = Chunk(
        schemaTableRow.reflect.asTerm("header"),
        Schema[Chunk[Alignment]].reflect.asTerm("alignments"),
        Schema[Chunk[TableRow]].reflect.asTerm("rows")
      ),
      typeId = TypeId.nominal[Table]("Table", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Table] {
          def usedRegisters: RegisterOffset                           = 3
          def construct(in: Registers, offset: RegisterOffset): Table =
            Table(
              in.getObject(offset).asInstanceOf[TableRow],
              in.getObject(offset + 1).asInstanceOf[Chunk[Alignment]],
              in.getObject(offset + 2).asInstanceOf[Chunk[TableRow]]
            )
        },
        deconstructor = new Deconstructor[Table] {
          def usedRegisters: RegisterOffset                                        = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Table): Unit = {
            out.setObject(offset, in.header)
            out.setObject(offset + 1, in.alignments)
            out.setObject(offset + 2, in.rows)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Block sealed trait (Variant with 10 cases)
  // ===========================================================================

  implicit lazy val schemaBlock: Schema[Block] = new Schema(
    reflect = new Reflect.Variant[Binding, Block](
      cases = Chunk(
        schemaParagraph.reflect.asTerm("Paragraph"),
        schemaHeading.reflect.asTerm("Heading"),
        schemaCodeBlock.reflect.asTerm("CodeBlock"),
        schemaThematicBreak.reflect.asTerm("ThematicBreak"),
        schemaBlockQuote.reflect.asTerm("BlockQuote"),
        schemaBulletList.reflect.asTerm("BulletList"),
        schemaOrderedList.reflect.asTerm("OrderedList"),
        schemaListItem.reflect.asTerm("ListItem"),
        schemaHtmlBlock.reflect.asTerm("HtmlBlock"),
        schemaTable.reflect.asTerm("Table")
      ),
      typeId = TypeId.nominal[Block]("Block", docsOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Block] {
          def discriminate(a: Block): Int = a match {
            case _: Paragraph   => 0
            case _: Heading     => 1
            case _: CodeBlock   => 2
            case ThematicBreak  => 3
            case _: BlockQuote  => 4
            case _: BulletList  => 5
            case _: OrderedList => 6
            case _: ListItem    => 7
            case _: HtmlBlock   => 8
            case _: Table       => 9
          }
        },
        matchers = Matchers(
          new Matcher[Paragraph] {
            def downcastOrNull(a: Any): Paragraph = a match {
              case x: Paragraph => x
              case _            => null.asInstanceOf[Paragraph]
            }
          },
          new Matcher[Heading] {
            def downcastOrNull(a: Any): Heading = a match {
              case x: Heading => x
              case _          => null.asInstanceOf[Heading]
            }
          },
          new Matcher[CodeBlock] {
            def downcastOrNull(a: Any): CodeBlock = a match {
              case x: CodeBlock => x
              case _            => null.asInstanceOf[CodeBlock]
            }
          },
          new Matcher[ThematicBreak.type] {
            def downcastOrNull(a: Any): ThematicBreak.type = a match {
              case ThematicBreak => ThematicBreak
              case _             => null.asInstanceOf[ThematicBreak.type]
            }
          },
          new Matcher[BlockQuote] {
            def downcastOrNull(a: Any): BlockQuote = a match {
              case x: BlockQuote => x
              case _             => null.asInstanceOf[BlockQuote]
            }
          },
          new Matcher[BulletList] {
            def downcastOrNull(a: Any): BulletList = a match {
              case x: BulletList => x
              case _             => null.asInstanceOf[BulletList]
            }
          },
          new Matcher[OrderedList] {
            def downcastOrNull(a: Any): OrderedList = a match {
              case x: OrderedList => x
              case _              => null.asInstanceOf[OrderedList]
            }
          },
          new Matcher[ListItem] {
            def downcastOrNull(a: Any): ListItem = a match {
              case x: ListItem => x
              case _           => null.asInstanceOf[ListItem]
            }
          },
          new Matcher[HtmlBlock] {
            def downcastOrNull(a: Any): HtmlBlock = a match {
              case x: HtmlBlock => x
              case _            => null.asInstanceOf[HtmlBlock]
            }
          },
          new Matcher[Table] {
            def downcastOrNull(a: Any): Table = a match {
              case x: Table => x
              case _        => null.asInstanceOf[Table]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // Document root
  // ===========================================================================

  // Doc(blocks: Chunk[Block], metadata: Map[String, String]) — 2 objects
  implicit lazy val schemaDoc: Schema[Doc] = new Schema(
    reflect = new Reflect.Record[Binding, Doc](
      fields = Chunk(
        new Reflect.Deferred[Binding, Chunk[Block]](() => Schema[Chunk[Block]].reflect).asTerm("blocks"),
        Schema[Map[String, String]].reflect.asTerm("metadata")
      ),
      typeId = TypeId.nominal[Doc]("Doc", docsOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Doc] {
          def usedRegisters: RegisterOffset                         = 2
          def construct(in: Registers, offset: RegisterOffset): Doc =
            Doc(
              in.getObject(offset).asInstanceOf[Chunk[Block]],
              in.getObject(offset + 1).asInstanceOf[Map[String, String]]
            )
        },
        deconstructor = new Deconstructor[Doc] {
          def usedRegisters: RegisterOffset                                      = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Doc): Unit = {
            out.setObject(offset, in.blocks)
            out.setObject(offset + 1, in.metadata)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
