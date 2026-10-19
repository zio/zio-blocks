import React from 'react';
import clsx from 'clsx';
import {ThemeClassNames} from '@docusaurus/theme-common';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

// The footer on every page: the logo and tagline on the left, the link columns (themeConfig.footer.links) on the
// right, and the credit line under a rule. It sits on the landing page's content measure, so its left edge lines up
// with the sections above. Styles: `.zb-footer` in src/css/custom.css.
export default function FooterLayout({links, logo, copyright}) {
  const {siteConfig} = useDocusaurusContext();
  return (
    <footer className={clsx(ThemeClassNames.layout.footer.container, 'footer', 'zb-footer')}>
      <div className="zb-footer-main">
        <div className="zb-footer-brand">
          {logo}
          <p>{siteConfig.tagline}.</p>
        </div>
        {links}
      </div>
      {copyright && <div className="zb-footer-base">{copyright}</div>}
    </footer>
  );
}
