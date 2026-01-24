# Plan: Enhance toString of Core Data Types (Issue #802)

**Related Issue**: #802
**Date**: 2026-01-24
**Status**: Planning

## Objective

Implement custom `toString` methods across the zio-blocks schema library to provide human-readable, debugger-friendly representations. Each type's format should match established conventions and prior art.

## Current State Analysis

### Files to Modify

| File | Current toString | Target Format |
|------|-----------------|---------------|
| `TypeName.scala` | Default case class | Valid Scala type syntax |
| `DynamicOptic.scala` | Custom (needs update) | Path interpolator syntax |
| `Optic.scala` | None | `$` macro syntax |
| `Reflect.scala` | None | SDL-style recursive expansion |
| `Schema.scala` | None | Delegates to Reflect |
| `Term.scala` | None | Field name with expanded type |
| `DynamicValue.scala` | None | EJSON format |
| `DynamicPatch.scala` | None | Line-oriented diff |
| `Patch.scala` | None | Delegates to DynamicPatch |
| `json/Json.scala` | None | Delegate to existing `print` |

### Key Structures Identified

1. **TypeName[A]**: `case class TypeName[A](namespace: Namespace, name: String, params: Seq[TypeName[?]])`
2. **Namespace**: `case class Namespace(packages: Seq[String], values: Seq[String] = Nil)` with `elements = packages ++ values`
3. **DynamicOptic**: Has existing toString at lines 35-56 with different syntax than path interpolator
4. **Optic**: `LensImpl`, `PrismImpl`, `OptionalImpl`, `TraversalImpl` - no toString
5. **Reflect**: Complex ADT with 8 cases - Record, Variant, Sequence, Map, Primitive, Wrapper, Deferred, Dynamic
6. **DynamicValue**: 5 cases - Primitive, Record, Variant, Sequence, Map
7. **PrimitiveValue**: 29 cases for all primitive types

---

## Implementation Order (by dependency)

### Phase 1: Foundation Types (No dependencies)

#### Task 1.1: TypeName.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/TypeName.scala`

**Implementation**:
```scala
override def toString: String = {
  val prefix = if (namespace.elements.isEmpty) "" else namespace.elements.mkString(".") + "."
  val base = prefix + name
  if (params.isEmpty) base
  else base + params.map(_.toString).mkString("[", ", ", "]")
}
```

**Examples**:
- `scala.Int`
- `scala.Option[scala.String]`
- `scala.collection.immutable.Map[scala.String, scala.Int]`

**Note**: Also add `toString` to `Namespace.scala`:
```scala
override def toString: String = elements.mkString(".")
```

---

#### Task 1.2: DynamicOptic.toString (Update existing)
**File**: `schema/shared/src/main/scala/zio/blocks/schema/DynamicOptic.scala`
**Current location**: Lines 35-56

**Required changes** (current → target):
| Node | Current | Target |
|------|---------|--------|
| `Case(name)` | `.when[Name]` | `<Name>` |
| `AtIndex(n)` | `.at(n)` | `[n]` |
| `AtIndices(ns)` | `.atIndices(<indices>)` | `[n1,n2,n3]` |
| `AtMapKey(k)` | `.atKey(<key>)` | `{"key"}` or `{42}` or `{true}` |
| `AtMapKeys(ks)` | `.atKeys(<keys>)` | `{"k1","k2"}` |
| `Elements` | `.each` | `[*]` |
| `MapKeys` | `.eachKey` | `{*:}` |
| `MapValues` | `.eachValue` | `{*}` |
| `Wrapped` | `.wrapped` | `.~` |

**Implementation**:
```scala
override lazy val toString: String = {
  val sb = new StringBuilder
  val len = nodes.length
  var idx = 0
  while (idx < len) {
    nodes(idx) match {
      case Node.Field(name)       => sb.append('.').append(name)
      case Node.Case(name)        => sb.append('<').append(name).append('>')
      case Node.AtIndex(index)    => sb.append('[').append(index).append(']')
      case Node.AtIndices(indices) => sb.append('[').append(indices.mkString(",")).append(']')
      case Node.AtMapKey(key)     => sb.append('{').append(formatDynamicKey(key)).append('}')
      case Node.AtMapKeys(keys)   => sb.append('{').append(keys.map(formatDynamicKey).mkString(",")).append('}')
      case Node.Elements          => sb.append("[*]")
      case Node.MapKeys           => sb.append("{*:}")
      case Node.MapValues         => sb.append("{*}")
      case Node.Wrapped           => sb.append(".~")
    }
    idx += 1
  }
  sb.toString
}

private def formatDynamicKey(key: DynamicValue): String = key match {
  case DynamicValue.Primitive(pv) => pv match {
    case PrimitiveValue.String(s) => "\"" + escapeString(s) + "\""
    case PrimitiveValue.Int(i)    => i.toString
    case PrimitiveValue.Long(l)   => l.toString
    case PrimitiveValue.Boolean(b) => b.toString
    case PrimitiveValue.Char(c)   => "'" + c + "'"
    case other                     => other.toString
  }
  case _ => key.toString
}
```

**Note**: Empty path returns empty string `""` (not `"."` as before).

---

### Phase 2: Optic Types (Depends on DynamicOptic)

#### Task 2.1: Optic.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Optic.scala`

Add to sealed trait `Optic[S, A]`:
```scala
override def toString: String = this match {
  case _: Lens[_, _]      => s"Lens(${formatPath(toDynamic)})"
  case _: Prism[_, _]     => s"Prism(${formatPath(toDynamic)})"
  case _: Optional[_, _]  => s"Optional(${formatPath(toDynamic)})"
  case _: Traversal[_, _] => s"Traversal(${formatPath(toDynamic)})"
}

private def formatPath(optic: DynamicOptic): String = {
  val nodes = optic.nodes
  if (nodes.isEmpty) "_"
  else "_" + formatAsScalaPath(nodes)
}

private def formatAsScalaPath(nodes: IndexedSeq[DynamicOptic.Node]): String = {
  nodes.map {
    case DynamicOptic.Node.Field(name)    => s".$name"
    case DynamicOptic.Node.Case(name)     => s".when[$name]"
    case DynamicOptic.Node.AtIndex(i)     => s".at($i)"
    case DynamicOptic.Node.AtIndices(is)  => s".atIndices(${is.mkString(", ")})"
    case DynamicOptic.Node.Elements       => ".each"
    case DynamicOptic.Node.MapKeys        => ".eachKey"
    case DynamicOptic.Node.MapValues      => ".eachValue"
    case DynamicOptic.Node.AtMapKey(k)    => s".atKey(...)"
    case DynamicOptic.Node.AtMapKeys(ks)  => s".atKeys(...)"
    case DynamicOptic.Node.Wrapped        => ".unwrap"
  }.mkString
}
```

**Examples**:
- `Lens(_.name)`
- `Lens(_.address.street)`
- `Prism(_.when[Some].value)`
- `Traversal(_.users.each.email)`

---

### Phase 3: Reflect Types (Depends on TypeName)

#### Task 3.1: Reflect.toString with prettyPrint
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

Add to sealed trait `Reflect[F, A]`:
```scala
override def toString: String = prettyPrint(0, Set.empty)

private[schema] def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String
```

Implement for each case:

**Primitive**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = typeName.name
```

**Record**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  val ind = "  " * indent
  val fieldLines = fields.map { field =>
    val fieldType = field.value.prettyPrint(indent + 1, seen)
    s"$ind  ${field.name}: $fieldType"
  }
  if (fieldLines.isEmpty) s"record ${typeName.name}"
  else s"record ${typeName.name} {\n${fieldLines.mkString("\n")}\n$ind}"
}
```

**Variant**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  val ind = "  " * indent
  val caseLines = cases.map { case_ =>
    val caseType = case_.value.prettyPrint(indent + 1, seen + typeName)
    case_.value.asRecord match {
      case Some(rec) if rec.fields.isEmpty => s"$ind  | ${case_.name}"
      case Some(rec) if rec.fields.length == 1 => 
        s"$ind  | ${case_.name}(${rec.fields.head.name}: ${rec.fields.head.value.prettyPrint(indent + 1, seen + typeName)})"
      case _ => s"$ind  | ${case_.name}(\n$caseType\n$ind    )"
    }
  }
  s"variant ${typeName.name} {\n${caseLines.mkString("\n")}\n$ind}"
}
```

**Sequence**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  val elemStr = element.prettyPrint(indent + 1, seen)
  if (element.isPrimitive) s"sequence ${typeName.name}[$elemStr]"
  else s"sequence ${typeName.name}[\n${"  " * (indent + 1)}$elemStr\n${"  " * indent}]"
}
```

**Map**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  val keyStr = key.prettyPrint(indent + 1, seen)
  val valStr = value.prettyPrint(indent + 1, seen)
  if (key.isPrimitive && value.isPrimitive) s"map ${typeName.name}[$keyStr, $valStr]"
  else s"map ${typeName.name}[\n${"  " * (indent + 1)}$keyStr,\n${"  " * (indent + 1)}$valStr\n${"  " * indent}]"
}
```

**Wrapper**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  val wrappedStr = wrapped.prettyPrint(indent + 1, seen)
  if (wrapped.isPrimitive) s"wrapper ${typeName.name}($wrappedStr)"
  else s"wrapper ${typeName.name}(\n${"  " * (indent + 1)}$wrappedStr\n${"  " * indent})"
}
```

**Deferred** (breaks recursion):
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = {
  if (seen.contains(value.typeName)) s"deferred => ${value.typeName}"
  else value.prettyPrint(indent, seen + value.typeName)
}
```

**Dynamic**:
```scala
def prettyPrint(indent: Int, seen: Set[TypeName[?]]): String = "DynamicValue"
```

---

#### Task 3.2: Term.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Term.scala`

```scala
override def toString: String = s"$name: ${value.toString}"
```

---

#### Task 3.3: Schema.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala`

```scala
override def toString: String = s"Schema {\n  ${reflect.prettyPrint(1, Set.empty)}\n}"
```

---

### Phase 4: Dynamic Values (Independent)

#### Task 4.1: DynamicValue.toString (EJSON format)
**File**: `schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala`

Add to sealed trait:
```scala
override def toString: String = print(0)

private[schema] def print(indent: Int): String
```

**Implementation for each case**:

**Primitive**:
```scala
def print(indent: Int): String = value match {
  case PrimitiveValue.String(s) => "\"" + escapeString(s) + "\""
  case PrimitiveValue.Boolean(b) => b.toString
  case PrimitiveValue.Int(i) => i.toString
  case PrimitiveValue.Long(l) => l.toString
  case PrimitiveValue.Double(d) => d.toString
  case PrimitiveValue.Float(f) => f.toString
  case PrimitiveValue.Char(c) => "\"" + c + "\""
  case PrimitiveValue.Unit => "null"
  // Typed primitives with metadata
  case PrimitiveValue.Instant(v) => s"${v.getEpochSecond} @ {type: \"instant\"}"
  case PrimitiveValue.Duration(v) => s"\"${v}\" @ {type: \"duration\"}"
  case PrimitiveValue.LocalDate(v) => s"\"$v\" @ {type: \"localDate\"}"
  case PrimitiveValue.Period(v) => s"\"$v\" @ {type: \"period\"}"
  // ... other temporal types with @ {type: "..."}
  case other => other.value.toString
}
```

**Record** (unquoted keys):
```scala
def print(indent: Int): String = {
  if (fields.isEmpty) "{}"
  else {
    val ind = "  " * indent
    val fieldStrs = fields.map { case (k, v) =>
      s"$ind  $k: ${v.print(indent + 1)}"
    }
    s"{\n${fieldStrs.mkString(",\n")}\n$ind}"
  }
}
```

**Variant** (postfix @):
```scala
def print(indent: Int): String = {
  val valueStr = value.print(indent)
  s"$valueStr @ {tag: \"$caseName\"}"
}
```

**Sequence**:
```scala
def print(indent: Int): String = {
  if (elements.isEmpty) "[]"
  else if (elements.forall(_.isInstanceOf[DynamicValue.Primitive])) {
    "[" + elements.map(_.print(0)).mkString(", ") + "]"
  } else {
    val ind = "  " * indent
    val elemStrs = elements.map(e => s"$ind  ${e.print(indent + 1)}")
    s"[\n${elemStrs.mkString(",\n")}\n$ind]"
  }
}
```

**Map** (quoted string keys, unquoted non-string keys):
```scala
def print(indent: Int): String = {
  if (entries.isEmpty) "{}"
  else {
    val ind = "  " * indent
    val entryStrs = entries.map { case (k, v) =>
      val keyStr = k match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) => "\"" + escapeString(s) + "\""
        case other => other.print(0)
      }
      s"$ind  $keyStr: ${v.print(indent + 1)}"
    }
    s"{\n${entryStrs.mkString(",\n")}\n$ind}"
  }
}
```

---

### Phase 5: Patch Types (Depends on DynamicOptic, DynamicValue)

#### Task 5.1: DynamicPatch.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala`

```scala
override def toString: String = {
  if (ops.isEmpty) "DynamicPatch {}"
  else {
    val opStrs = ops.map(formatOp)
    s"DynamicPatch {\n${opStrs.mkString("\n")}\n}"
  }
}

private def formatOp(op: DynamicPatchOp): String = {
  val path = op.path.toString
  op.operation match {
    case Operation.Set(value) => s"  $path = ${value.print(1)}"
    case Operation.PrimitiveDelta(delta) => delta match {
      case PrimitiveOp.IntDelta(d) if d >= 0 => s"  $path += $d"
      case PrimitiveOp.IntDelta(d) => s"  $path -= ${-d}"
      // ... similar for other numeric deltas
      case PrimitiveOp.StringEdit(edits) => s"  $path: string edit"
      case _ => s"  $path: delta"
    }
    case Operation.SequenceEdit(seqOps) => formatSeqOps(path, seqOps)
    case Operation.MapEdit(mapOps) => formatMapOps(path, mapOps)
    case Operation.Patch(nested) => s"  $path:\n${nested.toString}"
  }
}

private def formatSeqOps(path: String, ops: Vector[SeqOp]): String = {
  val lines = ops.map {
    case SeqOp.Append(values) => values.map(v => s"    + ${v.print(2)}").mkString("\n")
    case SeqOp.Insert(idx, values) => values.zipWithIndex.map { case (v, i) => 
      s"    + [$idx: ${v.print(2)}]"
    }.mkString("\n")
    case SeqOp.Delete(idx, count) => 
      if (count == 1) s"    - [$idx]"
      else s"    - [$idx..${idx + count - 1}]"
    case SeqOp.Modify(idx, op) => s"    ~ [$idx: ...]"
  }
  s"  $path:\n${lines.mkString("\n")}"
}

private def formatMapOps(path: String, ops: Vector[MapOp]): String = {
  val lines = ops.map {
    case MapOp.Add(k, v) => s"    + {${k.print(0)}: ${v.print(2)}}"
    case MapOp.Remove(k) => s"    - {${k.print(0)}}"
    case MapOp.Modify(k, patch) => s"    ~ {${k.print(0)}: ...}"
  }
  s"  $path:\n${lines.mkString("\n")}"
}
```

---

#### Task 5.2: Patch.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/patch/Patch.scala`

```scala
override def toString: String = dynamicPatch.toString
```

---

### Phase 6: Json Types (Simple delegation)

#### Task 6.1: Json.toString
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`

Add to sealed trait `Json`:
```scala
override def toString: String = print
```

---

## Testing Plan

Create test file: `schema/shared/src/test/scala/zio/blocks/schema/ToStringSpec.scala`

### Test Structure

```scala
package zio.blocks.schema

import zio.test._

object ToStringSpec extends ZIOSpecDefault {
  def spec = suite("toString implementations")(
    suite("TypeName")(
      test("simple types") { ... },
      test("parameterized types") { ... },
      test("nested parameterized types") { ... }
    ),
    suite("DynamicOptic")(
      test("field access") { ... },
      test("case access") { ... },
      test("index access") { ... },
      test("map key access") { ... },
      test("traversals") { ... }
    ),
    suite("Optic")(
      test("lens") { ... },
      test("prism") { ... },
      test("optional") { ... },
      test("traversal") { ... }
    ),
    suite("Reflect")(
      test("primitive") { ... },
      test("record") { ... },
      test("variant") { ... },
      test("sequence") { ... },
      test("map") { ... },
      test("wrapper") { ... },
      test("deferred (recursive)") { ... }
    ),
    suite("DynamicValue")(
      test("primitives") { ... },
      test("records with unquoted keys") { ... },
      test("maps with quoted string keys") { ... },
      test("variants with @ metadata") { ... },
      test("typed primitives with @ metadata") { ... }
    ),
    suite("DynamicPatch")(
      test("set operation") { ... },
      test("numeric delta") { ... },
      test("sequence edits") { ... },
      test("map edits") { ... }
    ),
    suite("Json")(
      test("delegates to print") { ... }
    )
  )
}
```

---

## Task Checklist

- [ ] **Phase 1: Foundation Types**
  - [ ] 1.1: TypeName.toString
  - [ ] 1.1b: Namespace.toString
  - [ ] 1.2: DynamicOptic.toString (update existing)

- [ ] **Phase 2: Optic Types**
  - [ ] 2.1: Optic.toString (Lens, Prism, Optional, Traversal)

- [ ] **Phase 3: Reflect Types**
  - [ ] 3.1: Reflect.prettyPrint and toString
  - [ ] 3.2: Term.toString
  - [ ] 3.3: Schema.toString

- [ ] **Phase 4: Dynamic Values**
  - [ ] 4.1: DynamicValue.toString (EJSON format)

- [ ] **Phase 5: Patch Types**
  - [ ] 5.1: DynamicPatch.toString
  - [ ] 5.2: Patch.toString

- [ ] **Phase 6: Json Types**
  - [ ] 6.1: Json.toString

- [ ] **Testing**
  - [ ] Create ToStringSpec.scala
  - [ ] Test all implementations

- [ ] **Final**
  - [ ] Run `sbt fmt`
  - [ ] Run full test suite
  - [ ] Verify all tests pass

---

## Notes

1. **String Escaping**: Need a helper function `escapeString(s: String): String` to handle special characters in strings (newlines, tabs, quotes, backslashes).

2. **Recursion Protection**: `Reflect.Deferred` needs special handling to avoid infinite loops on recursive types. Use a `Set[TypeName[?]]` to track seen types.

3. **Performance**: Use `lazy val toString` for types where computation is expensive or where the type is immutable.

4. **Consistency**: All path-related formats should match the path interpolator documentation exactly.

5. **EJSON Format Key Points**:
   - Records: unquoted keys `{ name: "John" }`
   - Maps: quoted string keys `{ "name": "John" }` or unquoted non-string keys `{ 42: "answer" }`
   - Variants: postfix metadata `{ value: 42 } @ {tag: "Some"}`
   - Typed primitives: postfix metadata `1705312800 @ {type: "instant"}`

6. **Formatting Policy**: After all code changes, run `sbt fmt` before committing.
