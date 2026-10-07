import React from 'react';
import Layout from '@theme/Layout';
import Hero from '../components/landing/Hero';
import Principles from '../components/landing/Principles';
import DeepDives from '../components/landing/DeepDives';
import Catalog from '../components/landing/Catalog';
import '../components/landing/landing.css';

const DESCRIPTION =
  'Type-safe, modular building blocks for Scala. Standalone libraries with zero or minimal dependencies, designed to work with any Scala stack.';

export default function Home() {
  return (
    <Layout title="Type-Safe, Modular Building Blocks for Scala" description={DESCRIPTION}>
      <main id="landing" className="lp-page">
        <Hero />
        <Principles />
        <DeepDives />
        <Catalog />
      </main>
    </Layout>
  );
}
