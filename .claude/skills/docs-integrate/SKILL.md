---
name: docs-integrate
description: Shared integration checklist for new ZIO Blocks documentation pages. Include after writing any new reference page or how-to guide to ensure it is wired into the site navigation.
---

# Documentation Integration Checklist

After writing a new documentation page (reference page or how-to guide), complete these steps to
integrate it into the Docusaurus site.

## Step 1: Add to `sidebars.js`

Add the page's `id` to the sidebar in `docs/sidebars.js`. Place it in the appropriate category:

- **Reference pages**: add under the `"Reference"` category, maintaining alphabetical or logical
  order.
- **How-to guides**: add under the `"Guides"` category. If the category does not yet exist, create
  it:

```javascript
{
  type: "category",
  label: "Guides",
  items: [
    "guides/guide-id-here",
  ]
}
```

## Step 2: Update `docs/index.md`

Add a link to the new page under the appropriate section in `docs/index.md`:

- Reference pages go under the "Reference Documentation" heading.
- Guides go under a "Guides" heading (create it if missing, after the reference section).

## Step 3: Cross-Reference Related Pages

Add links from related existing docs to the new page:

- For each data type or topic the new page covers, find existing documentation pages that mention
  it and add a "See also" link.
- If you wrote a guide that uses a specific type (e.g., `Schema`, `DynamicOptic`), add a
  cross-reference from the type's reference page to the guide.

## Step 4: Verify Compilation and Links (Mandatory Gate)

This is a **mandatory compilation gate**. All code examples in documentation are compile-checked via mdoc.

### Check Relative Links

Verify that all relative links in the new page and in any updated pages are correct:

- Internal links use relative paths: `[TypeName](./type-name.md)`.
- Anchor links match actual heading text (Docusaurus converts headings to lowercase kebab-case
  anchors).

### Run mdoc Compilation Check

Run the full mdoc compilation check:

```bash
sbt docs/mdoc
```

**Success criterion:** The output contains **zero `[error]` lines**. Warnings are acceptable.

**What to look for:**
- Type errors in Scala code blocks (mismatched types, undefined names, missing imports)
- Broken cross-references (mdoc reports these as `[error] Unknown link '...'`)
- Unresolved imports or package references

**If mdoc reports errors:** Fix them immediately. Do not proceed to commit or claim the work
is done until all errors are resolved.

The compilation check ensures:
- Code examples are syntactically correct
- Readers can copy-paste examples without errors
- Cross-references are valid and unbroken
