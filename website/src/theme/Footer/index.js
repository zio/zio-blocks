import React, {useCallback, useEffect, useRef, useState} from 'react';
import Footer from '@theme-original/Footer';

// The ZIO Blocks chat assistant (ByteBrain widget), mounted on every page that renders the Docusaurus footer. It
// answers from the ZIO Blocks docs. The key is public by design: the chat server accepts it only from the origin
// https://zioblocks.com, so locally and on previews the widget shows but its connection is refused.
// The widget loads on demand (see Chat below). Styles: `.zb-chat` in src/css/custom.css. The wheel/key handlers of the landing page ignore events inside it.
const CHAT_KEY = '61bd74f5-859e-45c0-a80d-9abd1623fc6e';
const BUTTON_LABEL = 'Ask ZIO Blocks';
const WELCOME = ["Hi, I'm the ZIO Blocks assistant.", 'Ask me anything about the docs.'];
// Readers can ask about text they select inside the docs. The widget shows its "Ask" button there (askOnSelect), and
// selecting that text also starts loading the widget, so the button is ready by the time the reader reaches for it.
const DOC_TEXT = '.theme-doc-markdown';

/** True when the current, non-empty text selection starts or ends inside the docs content. */
function selectionInDocs() {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || !sel.anchorNode) return false;
  const el = sel.anchorNode instanceof Element ? sel.anchorNode : sel.anchorNode.parentElement;
  return el !== null && el.closest(DOC_TEXT) !== null;
}

/** True when `node` sits in a scroller inside `root` that can still move in the direction of `deltaY`. */
function innerScrollerCanMove(node, root, deltaY) {
  for (let el = node; el && el !== root; el = el.parentElement) {
    const {overflowY} = getComputedStyle(el);
    if ((overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight) {
      const atTop = el.scrollTop <= 0;
      const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
      if (!(deltaY < 0 && atTop) && !(deltaY > 0 && atBottom)) return true;
    }
  }
  return false;
}

let widgetImport = null;
/** Fetches the widget bundle once, on demand. It is large, so it stays out of the page load (the landing page has a Lighthouse performance budget). */
const loadWidget = () => (widgetImport ??= import('@bytebrain.ai/bytebrain-ui').then((m) => m.ChatApp));

/**
 * Shows a plain launcher button first and loads the widget when the reader hovers, focuses or clicks it. Once the
 * widget has mounted, its own launcher takes the placeholder's place (same id, same styles) and opens if the reader
 * had clicked.
 */
function Chat() {
  const [ChatApp, setChatApp] = useState(null);
  const [openWhenReady, setOpenWhenReady] = useState(false);
  const load = useCallback(() => loadWidget().then((C) => setChatApp(() => C)), []);
  useEffect(() => {
    if (ChatApp && openWhenReady) document.querySelector('.zb-chat #chat-button')?.click();
  }, [ChatApp, openWhenReady]);
  // Selecting docs text starts the load early. This listener goes away once the widget is in.
  useEffect(() => {
    if (ChatApp) return undefined;
    const onSelectionChange = () => {
      if (selectionInDocs()) load();
    };
    document.addEventListener('selectionchange', onSelectionChange);
    return () => document.removeEventListener('selectionchange', onSelectionChange);
  }, [ChatApp, load]);
  // The widget only reacts to selection changes that happen after it mounts, so a selection made while it was loading
  // would be missed. Replaying one change event after mount makes it read the selection that is already there.
  useEffect(() => {
    if (ChatApp && selectionInDocs()) document.dispatchEvent(new Event('selectionchange'));
  }, [ChatApp]);
  if (!ChatApp) {
    return (
      <button
        id="chat-button"
        type="button"
        onPointerEnter={load}
        onFocus={load}
        onClick={() => {
          setOpenWhenReady(true);
          load();
        }}>
        {BUTTON_LABEL}
      </button>
    );
  }
  return (
    <ChatApp
      websocketHost="chat.zio.dev"
      websocketPort="80"
      websocketEndpoint="/chat"
      apikey={CHAT_KEY}
      title="ZIO Blocks Chat"
      buttonLabel={BUTTON_LABEL}
      placeholder="Ask a question about ZIO Blocks"
      welcomeMessages={WELCOME}
      askOnSelect
    />
  );
}

export default function FooterWrapper(props) {
  const root = useRef(null);
  // The wheel stays inside the chat: when its message list cannot scroll further (or there is nothing to scroll, as with
  // a short conversation), Chromium would otherwise hand the gesture to the page behind. `overscroll-behavior` alone
  // does not stop that for a box with no overflow, so the gesture is cancelled here.
  useEffect(() => {
    const el = root.current;
    if (!el) return undefined;
    const onWheel = (e) => {
      if (e.ctrlKey) return; // pinch-zoom
      if (!innerScrollerCanMove(e.target, el, e.deltaY)) e.preventDefault();
    };
    el.addEventListener('wheel', onWheel, {passive: false});
    return () => el.removeEventListener('wheel', onWheel);
  }, []);
  return (
    <>
      <Footer {...props} />
      <div className="zb-chat" ref={root}>
        <Chat />
      </div>
    </>
  );
}
