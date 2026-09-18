# Documentation Status

## PR Documentation Audit — Batch 1 (2026-09-06)

Scope: PRs #1626–#1665 (20 merged PRs checked).

| Status | Count | PRs |
|---|---|---|
| ✅ Resolved since audit | 1 | #1649 |
| 🟡 Partially Documented | 3 | #1631, #1634, #1635 |
| ✅ Well Documented | 2 | #1632, #1633 |
| ⚠️ Uncertain (resolved, no action) | 6 | #1628, #1629, #1638, #1640, #1642, #1643 |
| ⬜ No docs required (deps/fixes) | 8 | #1626, #1630, #1636, #1637, #1645, #1646, #1652, #1665 |

### ✅ Resolved since audit

- **#1649** — `fix(config): close config review findings`. Originally flagged as 🔴 Not Documented for adding `Sensitive.scala` — but that type is `private[config]` (an internal marker-matching helper), not public API worth its own page. The real gap was two related, undocumented public behaviors from the same PR: `Secret` gained schema-driven decoding (an implicit `Schema[Secret]`, so a `Secret` field decodes via `Schema.derived` like any primitive), and `ConfigError.InvalidValue#message` / `FlagValueParseException#getMessage` now redact the value to `<secret>` when the key/flag name looks sensitive. Fixed in [zio/zio-blocks#1666](https://github.com/zio/zio-blocks/pull/1666): a new "Schema Decoding" subsection in `config-source.md`, and one-line redaction notes in `errors.md` and `flags.md`, all mdoc-verified.

### 🟡 Partially Documented

- **#1634** — `feat(sql): transaction hardening` (isolation, savepoints, Hikari docs). `sql-transactions.md` examples are plain (unverified, not mdoc-compiled); `db-tx.md` is thin; `transactor.md` is solid but has no cross-links to sibling pages.
- **#1635** — `feat(sql): compile-time checked SQL interpolation`. `sql-checked-interpolation.md` has good mdoc example coverage but zero cross-reference links.
- **#1631** — `feat(projection): declarative projection engine`. `projection.md` has good depth (654 lines, 16 mdoc blocks) but zero cross-reference links.

### ✅ Well Documented

- **#1632** — `feat(sql): inspectable SQL + opt-in compile-time dumps` — covered in `query-dsl-sql.md`.
- **#1633** — `feat(sql): upsert and keyset pagination` — covered in `query-dsl-sql.md`.

### Next steps

- `/docs-enrich-section docs/guides/sql-transactions.md` — add mdoc-verified examples, cross-links.
- `/docs-enrich-section docs/guides/sql-checked-interpolation.md` — add cross-links.
- `/docs-enrich-section docs/reference/projection.md` — add cross-links.

Audit state tracked in `.docs-audit-state.json` (gitignored). ~1600+ older merged PRs remain unchecked.
