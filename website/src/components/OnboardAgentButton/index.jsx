import React, { useCallback, useEffect, useRef, useState } from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { ClaudeLogo, CodexLogo, CursorLogo, OpenCodeLogo } from './logos';
import { promptFor } from '../../lib/prompt.mjs';

export { promptFor };

export async function copyPrompt(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) {
    // fall through to legacy path
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (_) {
    return false;
  }
}

export default function OnboardAgentButton({ tone = 'default' }) {
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

  const toneClasses =
    tone === 'onDark'
      ? 'border-white bg-black/40 text-white backdrop-blur-sm hover:border-primary hover:text-primary'
      : 'border-zinc-400 text-zinc-800 hover:border-primary hover:text-primary dark:border-zinc-600 dark:text-zinc-100';

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the prompt to onboard your coding agent to ZIO Blocks"
      aria-label="Copy the ZIO Blocks agent onboarding prompt to the clipboard"
      className={`flex items-center gap-2 rounded-none border-2 px-7 py-3.5 text-base font-semibold leading-normal transition-colors ${toneClasses}`}
    >
      <span>{copied ? 'Copied!' : 'Onboard your agent to ZIO Blocks'}</span>
      <span className="flex items-center gap-1" aria-hidden="true">
        <ClaudeLogo />
        <CodexLogo />
        <CursorLogo />
        <OpenCodeLogo />
      </span>
      <span className="sr-only">
        Works with Claude, Codex, Cursor, and OpenCode
      </span>
    </button>
  );
}
