# TypeId Pretty-Print Decisions

## 2026-01-30 PR Created

### PR Details
- **PR**: https://github.com/zio/zio-blocks/pull/909
- **Branch**: `typeid-pretty-print`
- **Base**: `main`

### Commits
1. `328ff212` - feat(typeid): add TypeIdPrinter utility for idiomatic Scala type rendering
2. `2dad82a6` - feat(typeid): update TypeId/TypeRepr/TypeParam toString to idiomatic Scala syntax
3. `41c13423` - test(typeid): update tests for new idiomatic Scala toString format
4. `0c46f5e8` - chore: format typeid module

### Design Decisions

1. **Central TypeIdPrinter utility** - All toString methods delegate to a single utility for consistency
2. **No new public API** - Only toString behavior changed, no new methods added
3. **Hybrid naming** - Short names for scala.*/java.lang.*, full names for java.* (except java.lang)
4. **TypeParam simplification** - Removed index suffix, cleaner output
