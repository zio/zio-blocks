# Blockers for Schema.derived Delegation to Binding.of

This document describes issues discovered while attempting to refactor `Schema.derived` to delegate binding creation to `Binding.of[A]`.

## Background

Issue #871 specifies that `Schema.derived` should delegate binding creation to `Binding.of`. The attempt to implement this revealed that `Binding.of` doesn't handle all the edge cases that `Schema.derived` currently handles.

## Blocking Issue: Higher-Kinded Type Constructors

### Problem

The `Binding.of` macro fails for types with higher-kinded type parameters:

```scala
case class Record8[F[_]](f: F[Int], fs: F[Record8[F]])

// This fails:
Binding.of[Record8[Option]]
// Error: java.lang.AssertionError: Expected `fun.tpe` to widen into a `MethodType`
```

### Root Cause

In `deriveRecordBinding`, the constructor application is:

```scala
val argss = fieldLists.map(_.map(fieldToArg))
val newExpr = New(TypeTree.of[A])
val select  = Select(newExpr, constructor)
val base    =
  if (tpeTypeArgs.nonEmpty) select.appliedToTypes(tpeTypeArgs)
  else select
val applied = argss.foldLeft(base) { (acc, args) => Apply(acc, args) }
```

When `A = Record8[Option]`:
- `TypeTree.of[A]` creates a type tree for `Record8[Option]` (already applied)
- `select.appliedToTypes(tpeTypeArgs)` tries to apply `Option` again, which is incorrect
- The resulting expression is not a method type, so `Apply` fails

### Why Schema.derived Works

The original `Schema.derived` in `ClassInfo.constructor` uses:

```scala
val constructor = Select(New(Inferred(tpe)), primaryConstructor).appliedToTypes(tpeTypeArgs)
val argss       = fieldInfos.map(_.map(fieldConstructor(in, offset, _)))
argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _)).asExpr.asInstanceOf[Expr[T]]
```

This works because:
1. `Inferred(tpe)` infers the type from the fully applied `TypeRepr`
2. The compiler understands that the type args are already applied and `.appliedToTypes()` is a no-op

### Fix Required

The `Binding.of` macro needs to:
1. Use `Inferred(tpe)` instead of `TypeTree.of[A]` for the new expression
2. Only apply type args when they haven't already been applied
3. Handle the case where the constructor's type parameters are satisfied by the type representation

## Additional Considerations

### Tuple Handling

Standard tuples (`Tuple2`, `Tuple3`, etc.) go through `deriveRecordBinding` but may have issues with constructor application. The current implementation works because `isGenericTuple` returns `false` for them (since `defn.isTupleClass` returns `true`), so they're treated as regular case classes.

### Testing Required

After fixing the constructor application issue, the following test cases need to pass:
- `Schema.derived[Record8[Option]]` - higher-kinded type wrapper
- `Binding.of[(String, Int, Boolean)]` - tuple3 through deriveRecordBinding
- All existing `SchemaSpec` and `BindingOfSpec` tests

## Recommendation

Before attempting to refactor `Schema.derived` to delegate to `Binding.of`:

1. Fix the higher-kinded type constructor handling in `deriveRecordBinding`
2. Add explicit tests for `Binding.of[Record8[Option]]` in `BindingOfVersionSpecificSpec`
3. Verify all `SchemaSpec` tests pass when using `Binding.of` delegation
4. Only then proceed with the refactoring

## Files to Modify

- `schema/shared/src/main/scala-3/zio/blocks/schema/binding/BindingCompanionVersionSpecific.scala`
  - Fix `deriveRecordBinding` constructor application
  - Use `Inferred(tpe)` pattern from Schema macro
- `schema/shared/src/test/scala-3/zio/blocks/schema/binding/BindingOfVersionSpecificSpec.scala`
  - Add test for `Record8[Option]` style types
