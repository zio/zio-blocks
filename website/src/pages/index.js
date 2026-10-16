import React from 'react';
import Layout from '@theme/Layout';
import useBrokenLinks from '@docusaurus/useBrokenLinks';
import Hero from '../components/landing/Hero';
import Principles from '../components/landing/Principles';
import DeepDives from '../components/landing/DeepDives';
import Catalog from '../components/landing/Catalog';
import SectionRail from '../components/landing/SectionRail';
import useSectionScroll from '../components/landing/useSectionScroll';
import {RAIL} from '../lib/rail.mjs';
import '../components/landing/landing.css';

const DESCRIPTION =
  'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.';

// Section ids are plain JSX attributes, which Docusaurus' anchor check cannot see; register them
// so navbar links such as `/#catalog` validate on every page.
const ANCHORS = RAIL.map((s) => s.id);

export default function Home() {
  useSectionScroll(ANCHORS);
  const brokenLinks = useBrokenLinks();
  ANCHORS.forEach((id) => brokenLinks.collectAnchor(id));
  return (
    <Layout title="Type-Safe, Modular Building Blocks for Scala" description={DESCRIPTION}>
      <main id="landing" className="lp-page">
        <Hero />
        <Principles />
        <DeepDives />
        <Catalog />
      </main>
      <SectionRail />
    </Layout>
  );
}
