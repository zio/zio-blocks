import React from 'react';
import ColorModeToggle from '@theme-original/ColorModeToggle';
import { useColorMode } from '@docusaurus/theme-common';

// Every click flips what the page shows. Docusaurus cycles system -> light -> dark -> system, so one click in three
// changed nothing on screen (system -> light on a light OS, dark -> system on a dark one). The page follows the OS
// until the first click, then switches between light and dark. The Scaladoc pages under /api do the same
// (src/api-theme/theme.js).
export default function ColorModeToggleWrapper(props) {
  const { colorMode } = useColorMode();
  return <ColorModeToggle {...props} onChange={() => props.onChange(colorMode === 'dark' ? 'light' : 'dark')} />;
}
