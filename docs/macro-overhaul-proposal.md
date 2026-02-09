# Scope Macro Overhaul: A Simpler, Cleaner Design

## Executive Summary

The current scope macro infrastructure suffers from duplication, inconsistency, and unnecessary complexity. This proposal describes a complete redesign that:

1. **Reduces the API surface** from 7+ entry points to 3
2. **Eliminates macro duplication** by leveraging Resource composition
3. **Improves DX** with one obvious way to do things
4. **Correctly handles uniqueness** by composing Resources, not accumulating values
5. **Provides actionable errors** that tell users exactly how to fix problems

---

## Part 1: Rationale

### 1.1 Current Pain Points

**Massive Code Duplication**
- `WireCodeGen.scala` contains 3 near-identical implementations
- `ResourceMacros.scala` duplicates WireCodeGen logic instead of using it
- Complete duplication between Scala 2 and Scala 3 implementations
- Every bug fix must be applied to 7+ locations

**Inconsistent Behavior**
- `shared[T]`/`unique[T]` look for implicit `Wireable[T]`, but `wires*` macros don't
- Different entry points produce subtly different behavior
- Error messages differ between Scala versions

**Too Many Ways to Do Things**
- 7 different entry points with overlapping functionality
- Users must understand Wire vs Wireable vs Resource distinctions
- Implicit Wireable resolution adds hidden magic

**Broken Uniqueness Semantics**
- The current context-accumulation approach cannot correctly handle `Wire.unique`
- A type-indexed Context can only hold one value per type
- True "unique per injection site" requires Resource-level composition

---

## Part 2: The New Design

### 2.1 API Surface

The new API has exactly **3 macro entry points**:

| Macro | Purpose |
|-------|---------|
| `Wire.shared[T]` | Create a shared wire from T's constructor |
| `Wire.unique[T]` | Create a unique wire from T's constructor |
| `Resource.from[T](wires*)` | Wire up T and all dependencies into a Resource |

**Delete:**
- `shared[T]` / `unique[T]` (top-level)
- `Wireable` typeclass and all related code
- `Wireable.from[T]` / `Wireable.from[T](wires*)`
- `Wire#toResource(wires*)`
- All Scala 2 macro duplicates that become unnecessary

**Keep:**
- `Wire#toResource(ctx: Context[In])` — used by generated code
- `Wire(value)` — wrap a pre-existing value
- `Wire.Shared.apply` / `Wire.Unique.apply` — manual wire construction

### 2.2 User Mental Model

```
Wire.shared[T] / Wire.unique[T]
    ↓
    Creates a "recipe" for building T from its constructor parameters.
    The wire knows what T needs but doesn't resolve those dependencies.
    
Resource.from[T](wires*)
    ↓
    The ONE place where dependency resolution happens.
    Takes explicit wires, auto-creates missing ones with Wire.shared.
    Produces a Resource[T] with proper lifecycle management.
    
    Sharing/uniqueness is determined by each wire:
    - Wire.shared → Resource.Shared (one instance, ref-counted)
    - Wire.unique → Resource.Unique (fresh instance per use)
```

### 2.3 Usage Examples

**Leaf values must be provided:**
```scala
case class Config(host: String, port: Int)
class Database(config: Config)
class Cache(config: Config) 
class App(db: Database, cache: Cache)

// Must provide Wire(value) for types with primitive constructor params
val appResource: Resource[App] = Resource.from[App](
  Wire(Config("localhost", 8080))
)
// Database, Cache, App are auto-created with Wire.shared
```

**With explicit wires:**
```scala
// Override Cache to be unique (fresh instance per dependent)
val appResource = Resource.from[App](
  Wire(Config("localhost", 8080)),
  Wire.unique[Cache]
)
```

**Mixing shared and unique:**
```scala
class Handler1(session: Session)
class Handler2(session: Session)
class App(h1: Handler1, h2: Handler2)

// Each Handler gets its own Session
val appResource = Resource.from[App](
  Wire.unique[Session]
)
// Handler1 and Handler2 each receive a fresh Session instance
```

### 2.4 Unmakeable Types

Some types cannot be auto-created because they have no constructor we can call:

- **Primitives**: `String`, `Int`, `Long`, `Double`, `Boolean`, etc.
- **Functions**: `Function1`, `Function2`, `() => A`, `A => B`, etc.
- **Collections**: `List[A]`, `Map[K, V]`, `Option[A]`, etc.
- **Abstract types**: traits, abstract classes

When `Resource.from` attempts to auto-create a wire for an unmakeable type, it produces an **actionable** compile-time error:

```
Cannot auto-create String: primitives cannot be auto-created.

  Required by: Config(host: String, port: Int)
  Required by: App(config: Config)

  Fix: provide Wire("...") or Wire(Config(...)) with the desired values:
  
    Resource.from[App](
      Wire(Config("localhost", 8080)),
      ...
    )
```

### 2.5 What Wire.shared[T] / Wire.unique[T] Do

These macros are intentionally simple:

1. Inspect T's primary constructor (all parameter lists, including `using` clauses)
2. Extract parameter types (these become the wire's `In` type)
3. Filter out `Finalizer` parameters (auto-injected at make time)
4. Generate a `Wire[In, T]` that:
   - Takes `(Finalizer, Context[In]) => T`
   - Calls the constructor with values from the context
   - Registers `close()` finalizer if T extends `AutoCloseable`

**No dependency resolution. No implicit lookup. No graph logic.**

The wire simply says: "Given these dependencies, here's how to build T."

### 2.6 What Resource.from[T](wires*) Does

This is the ONE macro that handles dependency wiring:

1. Collects wires (explicit or auto-created with `Wire.shared`)
2. Validates the dependency graph
3. Generates Resource composition code using `flatMap`/`zip`

The key insight: **compose Resources, don't accumulate values**. This correctly preserves sharing and uniqueness semantics.

---

## Part 3: The Algorithm

### 3.1 Overview

```
Resource.from[T](explicitWires*):
    
    PHASE 1: Collect Wires
    ─────────────────────────────────────────────────────────────
    Build a Map[Type, Wire] for T and all its dependencies.
    Use explicit wires when provided, otherwise auto-create with Wire.shared.
    
    PHASE 2: Validate
    ─────────────────────────────────────────────────────────────
    Check for errors: cycles, non-instantiable types, unmakeable types,
    duplicate parameter types, private constructors, etc.
    
    PHASE 3: Topological Sort
    ─────────────────────────────────────────────────────────────
    Order types so leaves come first, T comes last.
    Deterministic ordering for stable codegen.
    
    PHASE 4: Generate Resource Composition
    ─────────────────────────────────────────────────────────────
    Build a Map[Type, Expr[Resource]] where each Resource is composed
    from its dependency Resources using flatMap/zip.
```

### 3.2 Pseudocode

```
Resource.from[T](explicitWires*):

  // ═══════════════════════════════════════════════════════════════
  // PHASE 1: Collect Wires
  // ═══════════════════════════════════════════════════════════════
  wireMap: Map[Type, Wire] = {}
  
  def resolveWire(type: Type): Unit =
    if wireMap.contains(type): return
    
    // Check explicit wires first (including subtype matches)
    wire = explicitWires.find(w => w.Out <:< type)
           .getOrElse(autoCreateWire(type))
    
    wireMap[type] = wire
    
    // Recurse into dependencies (excluding Finalizer params)
    for dep in wire.inputTypes:
      resolveWire(dep)
  
  def autoCreateWire(type: Type): Wire =
    // Cannot auto-create unmakeable types
    if isUnmakeableType(type):
      error(renderUnmakeableError(type, dependencyChain))
    
    // Cannot auto-create abstract types
    if type.isTraitOrAbstract:
      error(s"Cannot instantiate $type — provide a wire for a concrete subtype")
    
    // Cannot auto-create types with inaccessible constructors
    if !type.primaryConstructor.isAccessible:
      error(s"Cannot instantiate $type — constructor is not accessible")
    
    Wire.shared[type]
  
  resolveWire(T)
  
  // ═══════════════════════════════════════════════════════════════
  // PHASE 2: Validate
  // ═══════════════════════════════════════════════════════════════
  
  // Check for cycles
  detectCycles(wireMap) match
    case Some(cycle) => error(renderCycleError(cycle))
    case None => // ok
  
  // Check for duplicate parameter types in any constructor
  for (type, wire) <- wireMap:
    val paramTypes = wire.inputTypes
    val duplicates = paramTypes.groupBy(identity).filter(_._2.size > 1).keys
    if duplicates.nonEmpty:
      error(
        s"""Constructor of $type has multiple parameters of type ${duplicates.head}.
           |Context is type-indexed and cannot supply distinct values.
           |
           |Fix: wrap one parameter in an opaque type to distinguish them.""".stripMargin
      )
  
  // ═══════════════════════════════════════════════════════════════
  // PHASE 3: Topological Sort
  // ═══════════════════════════════════════════════════════════════
  
  // Sort by dependency order (leaves first), with deterministic tie-breaking
  sorted: List[Type] = topologicalSort(wireMap, tieBreaker = _.fullName)
  
  // ═══════════════════════════════════════════════════════════════
  // PHASE 4: Generate Resource Composition
  // ═══════════════════════════════════════════════════════════════
  //
  // Key insight: Build a Map[Type, Expr[Resource[Type]]], then compose
  // Resources using flatMap/zip. This preserves sharing/uniqueness semantics.
  //
  // For diamond: Config → Database, Cache → App
  //
  // Generated code:
  //
  //   lazy val configResource: Resource[Config] = 
  //     Resource.shared(f => configWire.make(f, Context.empty))
  //
  //   lazy val databaseResource: Resource[Database] =
  //     configResource.flatMap { config =>
  //       Resource.shared(f => databaseWire.make(f, Context(config)))
  //     }
  //
  //   lazy val cacheResource: Resource[Cache] =
  //     configResource.flatMap { config =>
  //       Resource.shared(f => cacheWire.make(f, Context(config)))
  //     }
  //
  //   lazy val appResource: Resource[App] =
  //     databaseResource.flatMap { db =>
  //       cacheResource.flatMap { cache =>
  //         Resource.shared(f => appWire.make(f, Context(db, cache)))
  //       }
  //     }
  //
  //   appResource
  
  resourceMap: Map[Type, Expr[Resource]] = {}
  
  for type in sorted:
    wire = wireMap[type]
    deps = wire.inputTypes
    
    resourceExpr = if deps.isEmpty:
      // Leaf node: no dependencies
      if wire.isShared:
        '{ Resource.shared[$type](f => $wire.make(f, Context.empty)) }
      else:
        '{ Resource.unique[$type](f => $wire.make(f, Context.empty)) }
    else:
      // Has dependencies: compose with flatMap
      // Build nested flatMap chain over dependency resources
      buildFlatMapChain(type, wire, deps, resourceMap)
    
    resourceMap[type] = resourceExpr
  
  resourceMap[T]

def buildFlatMapChain(type, wire, deps, resourceMap): Expr[Resource[type]] =
  // Generate:
  //   dep1Resource.flatMap { dep1 =>
  //     dep2Resource.flatMap { dep2 =>
  //       ...
  //       Resource.shared/unique(f => wire.make(f, Context(dep1, dep2, ...)))
  //     }
  //   }
  
  def loop(remainingDeps: List[Type], accumulatedBindings: List[(Type, Term)]): Expr[Resource[type]] =
    remainingDeps match
      case Nil =>
        // Base case: all deps bound, create the resource
        val ctxExpr = buildContextExpr(accumulatedBindings)
        if wire.isShared:
          '{ Resource.shared[$type](f => $wire.make(f, $ctxExpr)) }
        else:
          '{ Resource.unique[$type](f => $wire.make(f, $ctxExpr)) }
      
      case dep :: rest =>
        val depResource = resourceMap[dep]
        '{
          $depResource.flatMap { depValue =>
            ${ loop(rest, accumulatedBindings :+ (dep, 'depValue)) }
          }
        }
  
  loop(deps, Nil)
```

### 3.3 Why This Works

**Sharing (Diamond Pattern):**
```scala
lazy val configResource = Resource.shared(...)  // ONE Resource instance

lazy val databaseResource = configResource.flatMap { config => ... }
lazy val cacheResource = configResource.flatMap { config => ... }
```

Both `databaseResource` and `cacheResource` reference the SAME `configResource` instance. When allocated:
1. First `flatMap` calls `configResource.make(finalizer)` → initializes Config, refCount=1
2. Second `flatMap` calls `configResource.make(finalizer)` → returns same Config, refCount=2
3. On scope close, refCount decrements; finalizer runs when refCount=0

**Uniqueness:**
```scala
lazy val sessionResource = Resource.unique(...)  // Creates fresh each time

lazy val handler1Resource = sessionResource.flatMap { session => ... }
lazy val handler2Resource = sessionResource.flatMap { session => ... }
```

Each `flatMap` calls `sessionResource.make(finalizer)`, and since it's `Resource.unique`, each call creates a fresh Session. Handler1 and Handler2 get different instances.

### 3.4 Subtype Wire Matching

When looking for a wire to satisfy dependency type `Service`:

1. Check explicit wires for exact match: `w.Out =:= Service`
2. Check explicit wires for subtype match: `w.Out <:< Service` (e.g., `Wire.shared[LiveService]`)
3. If found, use that wire — the value produced is a subtype, which is valid

For Context lookup at runtime, `Context` is covariant, so `Context[LiveService]` works where `Context[Service]` is expected.

### 3.5 Default Parameters

If a constructor parameter has a default value:

1. If a wire exists for that type → use the wire (wire takes precedence)
2. If no wire exists and type is unmakeable → use the default value
3. If no wire exists and type is makeable → auto-create `Wire.shared[Type]`

This allows convenient defaults for configuration while still permitting override:

```scala
class Database(config: Config = Config.default)

// Uses default
Resource.from[Database]

// Overrides default
Resource.from[Database](Wire(Config("custom", 9999)))
```

---

## Part 4: Validation & Errors

### 4.1 Error Types

All errors must be **actionable** — tell the user exactly what to do.

| Error | When | Message Pattern |
|-------|------|-----------------|
| Unmakeable type | Auto-creating primitive/function/collection | Show dependency chain + fix with `Wire(value)` |
| Abstract type | Auto-creating trait/abstract class | Suggest providing wire for concrete subtype |
| Private constructor | Constructor not accessible | State the problem clearly |
| Cycle detected | A → B → A | Show cycle path with ASCII visualization |
| Duplicate param types | `class X(a: T, b: T)` | Suggest opaque type wrapper |
| Inner class | Requires outer instance | Not supported, suggest refactoring |
| Java class ambiguity | Multiple constructors | Not supported for auto-creation |

### 4.2 Error Message Examples

**Unmakeable type:**
```
Cannot auto-create String: primitives cannot be auto-created.

  Required by: Config(host: String, port: Int)
  Required by: App(config: Config)

  Fix: provide Wire("...") or Wire(Config(...)):
  
    Resource.from[App](
      Wire(Config("localhost", 8080))
    )
```

**Cycle:**
```
Dependency cycle detected:

  A
  ↓
  B
  ↓
  A  ← cycle

Fix: break the cycle by restructuring dependencies or using lazy initialization.
```

**Duplicate parameter types:**
```
Constructor of App has multiple parameters of type Config.
Context is type-indexed and cannot supply distinct values.

  class App(primary: Config, fallback: Config)
            ^^^^^^^          ^^^^^^^^
            
Fix: wrap one parameter in an opaque type:

  opaque type FallbackConfig = Config
  class App(primary: Config, fallback: FallbackConfig)
```

---

## Part 5: Test Strategy

### 5.1 Test Matrix

```
┌─────────────────────────────────────────────────────────────────┐
│                        TEST MATRIX                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Scenarios              Wire Variants         Validations       │
│  ──────────────────     ─────────────────     ───────────────   │
│  • No dependencies      • All Wire.shared     • Correct value   │
│  • Single dependency    • All Wire.unique     • Sharing works   │
│  • Multiple deps        • Mixed shared/unique • Uniqueness works│
│  • Diamond pattern      • With Wire(value)    • Finalization    │
│  • Deep chain           • Subtype wires       • Order correct   │
│  • AutoCloseable        • Default params      • Error messages  │
│  • With Finalizer param •                     • Cross-Scala     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Structural Sharing Tests

```scala
test("diamond pattern shares single instance") {
  var configCount = 0
  class Config { configCount += 1 }
  class Database(config: Config)
  class Cache(config: Config)
  class App(db: Database, cache: Cache)
  
  Scope.global.scoped { scope =>
    val app = scope.allocate(Resource.from[App](Wire(new Config)))
    assert(configCount == 1)  // Only one Config created
  }
}

test("shared resource finalizes when last ref closes") {
  var closed = false
  class Shared extends AutoCloseable { def close() = closed = true }
  class User1(s: Shared)
  class User2(s: Shared)
  class App(u1: User1, u2: User2)
  
  Scope.global.scoped { scope =>
    scope.allocate(Resource.from[App](Wire(new Shared { ... })))
    assert(!closed)
  }
  assert(closed)  // Finalized after scope closes
}
```

### 5.3 Uniqueness Tests

```scala
test("unique wire creates fresh instances per dependent") {
  var sessionCount = 0
  class Session { sessionCount += 1 }
  class Handler1(session: Session)
  class Handler2(session: Session)
  class App(h1: Handler1, h2: Handler2)
  
  Scope.global.scoped { scope =>
    val app = scope.allocate(Resource.from[App](Wire.unique[Session]))
    assert(sessionCount == 2)  // Two Sessions: one for Handler1, one for Handler2
  }
}

test("unique at leaf, shared at intermediate") {
  var leafCount = 0
  var midCount = 0
  class Leaf { leafCount += 1 }
  class Mid(leaf: Leaf) { midCount += 1 }
  class Top1(mid: Mid)
  class Top2(mid: Mid)
  class App(t1: Top1, t2: Top2)
  
  Scope.global.scoped { scope =>
    scope.allocate(Resource.from[App](Wire.unique[Leaf]))
    // Mid is shared (default), so only one Mid
    // But each Mid creation triggers Leaf (unique), and Mid is created once
    // Wait, Mid is shared so created once, Leaf is unique but only one Mid...
    // Actually: Mid.shared means one Mid, Leaf.unique means fresh per Mid creation
    // Since Mid created once, Leaf created once
    assert(midCount == 1)
    assert(leafCount == 1)
  }
}
```

### 5.4 Override Tests

```scala
test("explicit wire overrides auto-creation") {
  class Config(val env: String)
  class Database(config: Config)
  
  Scope.global.scoped { scope =>
    val db = scope.allocate(Resource.from[Database](
      Wire(new Config("test"))
    ))
    assert(scope.$(db)(_.config.env) == "test")
  }
}

test("subtype wire satisfies supertype dependency") {
  trait Service { def name: String }
  class LiveService extends Service { def name = "live" }
  class App(service: Service)
  
  Scope.global.scoped { scope =>
    val app = scope.allocate(Resource.from[App](
      Wire.shared[LiveService]
    ))
    assert(scope.$(app)(_.service.name) == "live")
  }
}
```

### 5.5 Finalization Tests

```scala
test("finalization runs in LIFO order") {
  val order = mutable.ListBuffer[String]()
  
  class A extends AutoCloseable { def close() = order += "A" }
  class B(a: A) extends AutoCloseable { def close() = order += "B" }
  class C(b: B) extends AutoCloseable { def close() = order += "C" }
  
  Scope.global.scoped { scope =>
    scope.allocate(Resource.from[C](Wire(new A), Wire.shared[B]))
  }
  
  assert(order == List("C", "B", "A"))  // LIFO
}
```

### 5.6 Error Tests

```scala
test("error for unmakeable type is actionable") {
  case class Config(host: String, port: Int)
  class App(config: Config)
  
  // Should fail with dependency chain and fix suggestion
  assertCompileError("""Resource.from[App]""")
}

test("error for abstract type") {
  trait Service
  class App(s: Service)
  
  assertCompileError("""Resource.from[App]""")
}

test("error for duplicate param types") {
  class App(c1: Config, c2: Config)
  
  assertCompileError("""Resource.from[App](Wire(Config(...)))""")
}

test("error for cycle") {
  class A(b: B)
  class B(a: A)
  
  assertCompileError("""Resource.from[A]""")
}
```

### 5.7 Cross-Scala Tests

The same test suite must run on both Scala 2.13 and Scala 3.x with identical results. Any divergence is a bug.

---

## Part 6: Implementation Notes

### 6.1 This Is a Sketch

The pseudocode in this document is a **sketch**, not a specification. Implementers will need to:

- Handle edge cases not explicitly covered
- Make decisions about error message formatting
- Optimize generated code if needed
- Adapt to Scala 2 vs Scala 3 macro API differences

### 6.2 Non-Negotiables

1. **API Surface**: Exactly 3 macro entry points (`Wire.shared`, `Wire.unique`, `Resource.from`)
2. **No Implicit Resolution**: Either supply wires explicitly or accept `Wire.shared` defaults
3. **Correct Uniqueness**: `Wire.unique` must create fresh instances per injection site
4. **Correct Sharing**: `Wire.shared` must share within diamond patterns
5. **Test Coverage**: All scenarios in the test matrix must pass
6. **Cross-Scala Parity**: Identical behavior on Scala 2.13 and Scala 3.x
7. **Actionable Errors**: All compile errors must explain the fix

### 6.3 Negotiables

- Internal code organization
- Exact error message wording (as long as actionable)
- Performance optimizations in generated code
- Helper methods and utilities

### 6.4 What About the Resource Operators?

The operators added to Resource (`contextual`, `++`, `:+`, `allocate`, `build`) remain useful for **manual Resource composition** by users. However, the macro-generated code uses the simpler `flatMap`/`zip` pattern directly, which correctly preserves sharing/uniqueness semantics.

---

## Part 7: Summary

### Before
- 7+ macro entry points
- Duplicated logic across files
- Broken uniqueness semantics (context accumulation)
- Inconsistent implicit handling
- Untestable macro internals

### After
- 3 macro entry points
- Resource composition via flatMap/zip
- Correct uniqueness (per injection site)
- Correct sharing (per Resource instance)
- No implicit magic
- Simple mental model

### The Key Insight

**Compose Resources, don't accumulate values.**

Each type gets its own `Resource` instance (shared or unique). Dependencies are composed using `flatMap`. This naturally preserves:
- **Sharing**: Same `Resource.Shared` instance → same value
- **Uniqueness**: `Resource.Unique` → fresh value per `flatMap` call

The macro's job is simply to:
1. Collect wires (explicit or auto-created)
2. Validate the graph
3. Generate a chain of `flatMap` calls

All the interesting semantics live in `Resource.Shared` and `Resource.Unique`, which are runtime code and fully testable.
