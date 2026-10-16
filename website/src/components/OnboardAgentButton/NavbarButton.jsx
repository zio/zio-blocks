import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FaCheck, FaTerminal } from 'react-icons/fa6';

import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

import { promptFor, copyPrompt } from './index';
import styles from './NavbarButton.module.css';

// Compact navbar variant of the hero's "Onboard your agent to ZIO Blocks" button:
// copies the same setup prompt to the clipboard, shown as a small icon pill.
export default function OnboardAgentNavbarButton() {
  const { siteConfig } = useDocusaurusContext();
  const prompt = promptFor(siteConfig.url);
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  const onClick = useCallback(async () => {
    const ok = await copyPrompt(prompt);
    if (!ok) {
      window.prompt('Copy this prompt for your coding agent:', prompt);
      return;
    }
    setCopied(true);
    timer.current && clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 2000);
  }, [prompt]);

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO Blocks"
      aria-label="Copy the ZIO Blocks agent onboarding prompt to the clipboard"
      className={styles.button}
    >
      {copied ? (
        <FaCheck aria-hidden="true" />
      ) : (
        <FaTerminal aria-hidden="true" />
      )}
    </button>
  );
}
