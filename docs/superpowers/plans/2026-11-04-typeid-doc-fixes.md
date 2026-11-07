# typeid reference: fix four claims that contradict the library

Page: `docs/reference/typeid.md`. Evidence: scala-cli probes against `dev.zio::zio-blocks-typeid:0.0.56`, Scala 3.9.0 (the reproducer plus a second probe for the extra claims).

## 1. Kinds (known bug: derivation records no kind or bounds)

Probe output: `TypeId.of[Runnable].typeParams` gives `(F, Type, arity 0, isTypeConstructor false)`; `Container`/`Cache` params all `Type`, bounds `TypeBounds(None,None)`; `TypeParam.higherKinded("F", 0)` gives `Arrow(List(Type),Type)`, arity 1, `isTypeConstructor` true.

| Place | Old | New |
| --- | --- | --- |
| `TypeParam` section (~:397-430) | "the macro captures each declared parameter ... bounds, and kind" | macros record name, index, variance; bounds `Unbounded` and kind `Kind.Type` for every derived parameter (a known bug in 0.0.56); build by hand with `TypeParam.higherKinded` to record a kind. Prose under the `paramF.kind` block says `Type`/`false`. |
| `Kind` section (:1613-1670) | "TypeId captures kind information at runtime" | `Kind` is the data model; derivation does not compute it. Existing blocks stay (output shows `Type`); add a `TypeParam.higherKinded` block showing a hand-built kind. Table: keep as the list of `Kind` values, say what they mean, not what derivation returns. |
| :1500, :1553, :2048 | "captures ... (variance, bounds, kind)", "records ... bounds, and kind", "Captures variance & kind: Yes" | variance only (plus name/index); say bounds and kind are not recorded by derivation. |

## 2. Opaque normalization

Probe: `normalize(of[Email])` returns the opaque id (`isOpaque` true, equals itself); `of[Email] == of[String]` false; `isOption` is false for `opaque type MaybeName = Option[String]` but true for the alias `type Names = Option[String]`.

| Place | Old | New |
| --- | --- | --- |
| :762 | "resolves type aliases and opaque types ... `opaque type Email = String` normalizes to `String`" | normalization follows aliases only; an opaque type stays as it is, so semantic predicates see through aliases but not opaque types. |
| :1368 | "Opaque types ... normalize to their representation" | opaque type is not unwrapped; this is why it stays distinct from its representation. Add small `mdoc` block (`normalize` on an opaque type). |
| :1370 | "preserve identity even after normalization" | keep meaning, fix wording (never unwrapped). |

## 3. `construct` collection types

Probe: `List`, `Vector`, `Set`, `Seq`, `IndexedSeq` give `Right`; `Chunk` and `Array` give `Left(No matching constructor found ...)` (`ArraySeq` likewise, from the issue entry). Source: `knownTypeConstructors` in `TypeIdPlatformMethods.scala`.

:1331 sentence lists only `List`, `Vector`, `Set`, `Seq`, `IndexedSeq` (and `Map` is covered below); add a block showing `Chunk` and `Array` return `Left`.

## 4. `unapplied`

Probe: `of[List] == unapplied(of[List[Int]])` false on Scala 3; `TypeId.list == unapplied(...)` true.

:1431 "returns the equivalent of `TypeId.of[List]`" becomes: returns the type constructor; on Scala 3 it equals `TypeId.list`, not `TypeId.of[List]` (which derives the `scala.package.List` alias, a known bug in 0.0.56); on Scala 2 it equals `TypeId.of[List[_]]`. Add the `== TypeId.list` check to the block.

Wording follows the Scaladoc on `TypeParam`, `Kind`, `TypeId.normalize`, `TypeId#construct`, `TypeId.unapplied` (#1924).
