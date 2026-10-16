import React from 'react';
import DocCardList from '@theme/DocCardList';
import { useDocsSidebar } from '@docusaurus/plugin-content-docs/client';

// One card per entry of the sidebar shown beside the page (its `displayed_sidebar`), as a category's generated index
// page shows its items, so an entry added to docs/sidebars.js appears here without editing the page. With
// `categoriesOnly`, only the sidebar's categories get a card.
export default function SidebarCards({ categoriesOnly = false }) {
  const sidebar = useDocsSidebar();
  const items = (sidebar?.items ?? []).filter((item) => !categoriesOnly || item.type === 'category');
  return <DocCardList items={items} />;
}
