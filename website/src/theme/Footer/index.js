import React from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import Footer from '@theme-original/Footer';

// The ZIO Blocks chat assistant (ByteBrain widget), mounted on every page that renders the Docusaurus footer. It
// answers from the ZIO Blocks docs. The key is public by design: the chat server accepts it only from the origin
// https://zioblocks.com, so locally and on previews the widget shows but its connection is refused.
// Styles: `.zb-chat` in src/css/custom.css. The wheel/key handlers of the landing page ignore events inside it.
const CHAT_KEY = '61bd74f5-859e-45c0-a80d-9abd1623fc6e';
const WELCOME = ["Hi, I'm the ZIO Blocks assistant.", 'Ask me anything about the docs.'];

export default function FooterWrapper(props) {
  return (
    <>
      <Footer {...props} />
      <div className="zb-chat">
        {/* The widget reads window and opens a WebSocket, so it renders in the browser only. */}
        <BrowserOnly>
          {() => {
            const ChatApp = require('@bytebrain.ai/bytebrain-ui').ChatApp;
            return (
              <ChatApp
                websocketHost="chat.zio.dev"
                websocketPort="80"
                websocketEndpoint="/chat"
                apikey={CHAT_KEY}
                title="ZIO Blocks Chat"
                buttonLabel="Ask ZIO Blocks"
                placeholder="Ask a question about ZIO Blocks"
                welcomeMessages={WELCOME}
              />
            );
          }}
        </BrowserOnly>
      </div>
    </>
  );
}
