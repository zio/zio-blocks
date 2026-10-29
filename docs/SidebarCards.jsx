import React from 'react';
import DocCardList from '@theme/DocCardList';
import { useDoc, useDocsSidebar } from '@docusaurus/plugin-content-docs/client';

// One card per entry of the sidebar shown beside the page (its `displayed_sidebar`), as a category's generated index
// page shows its items, so an entry added to docs/sidebars.js appears here without editing the page. With
// `categoriesOnly`, only the sidebar's categories get a card.
// A category whose link is this page is the page's own group, so its items are the cards rather than a card for the page
// itself. (The guides index is not in its sidebar, so there the cards are the sidebar's four top-level sections.)
export default function SidebarCards({ categoriesOnly = false }) {
  const sidebar = useDocsSidebar();
  const { permalink } = useDoc().metadata;
  const entries = (sidebar?.items ?? []).flatMap((item) =>
    item.type === 'category' && item.href === permalink ? item.items : [item],
  );
  const items = entries.filter((item) => !categoriesOnly || item.type === 'category');
  return <DocCardList items={items} />;
}
