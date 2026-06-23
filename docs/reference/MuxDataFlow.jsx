import React, { useState, useRef, useEffect } from "react";

const MAX_VISIBLE = 8;
const QUEUE_CAP = 256;
const STREAM_COUNT = 3;

const stateMap = {
  open:   { label: "open",               bg: "#e8f5e0", color: "#3B6D11", dot: "#639922" },
  hcl:    { label: "half-closed local",  bg: "#fef3cd", color: "#854F0B", dot: "#BA7517" },
  hcr:    { label: "half-closed remote", bg: "#fef3cd", color: "#854F0B", dot: "#BA7517" },
  closed: { label: "closed",             bg: "#fce8e8", color: "#A32D2D", dot: "#E24B4A" },
};

const initStreams = () =>
  Array.from({ length: STREAM_COUNT }, (_, i) => ({
    id: i + 1, state: "open", outQ: [], inQ: [], logs: [],
  }));

const Badge = ({ state }) => (
  <span style={{
    display: "inline-block", fontSize: 11, fontWeight: 500,
    padding: "2px 8px", borderRadius: 6,
    background: stateMap[state].bg, color: stateMap[state].color,
  }}>
    {stateMap[state].label}
  </span>
);

const Pill = ({ label, dir }) => (
  <span style={{
    padding: "2px 8px", borderRadius: 4, fontSize: 11, whiteSpace: "nowrap",
    fontFamily: "monospace",
    background: dir === "out" ? "rgba(126,119,221,0.15)" : "rgba(29,158,117,0.15)",
    color: dir === "out" ? "#534AB7" : "#0F6E56",
  }}>
    {label}
  </span>
);

const Btn = ({ children, variant, disabled, onClick }) => {
  const palette = {
    app:   { border: "rgba(126,119,221,0.4)", color: "#534AB7", hover: "rgba(126,119,221,0.1)" },
    proto: { border: "rgba(29,158,117,0.4)",  color: "#0F6E56", hover: "rgba(29,158,117,0.1)" },
    lc:    { border: "rgba(186,117,23,0.4)",  color: "#854F0B", hover: "rgba(186,117,23,0.1)" },
  }[variant];
  return (
    <button
      disabled={disabled}
      onClick={onClick}
      style={{
        fontSize: 12, padding: "4px 10px", borderRadius: 6, cursor: disabled ? "not-allowed" : "pointer",
        border: `1px solid ${palette.border}`, color: palette.color, background: "transparent",
        opacity: disabled ? 0.35 : 1, transition: "background 0.15s",
      }}
      onMouseEnter={e => { if (!disabled) e.currentTarget.style.background = palette.hover; }}
      onMouseLeave={e => { e.currentTarget.style.background = "transparent"; }}
    >
      {children}
    </button>
  );
};

const Zone = ({ children, label, tint }) => (
  <div style={{
    padding: "12px 16px", borderRadius: 12, marginBottom: 6,
    background: tint.bg, border: `1px solid ${tint.border}`,
  }}>
    <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 8, color: tint.label }}>
      {label}
    </div>
    {children}
  </div>
);

const QueueRow = ({ label, items, dir, count }) => (
  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
    <span style={{ fontSize: 12, color: "#888", minWidth: 80, textAlign: "right" }}>{label}</span>
    <div style={{
      flex: 1, display: "flex", gap: 3, alignItems: "center", minHeight: 28,
      padding: "3px 6px", borderRadius: 6, border: "1px dashed #ddd", overflow: "hidden",
    }}>
      {items.slice(0, MAX_VISIBLE).map((m, i) => <Pill key={`${m}-${i}`} label={m} dir={dir} />)}
      {items.length > MAX_VISIBLE && <span style={{ fontSize: 10, color: "#aaa" }}>+{items.length - MAX_VISIBLE}</span>}
    </div>
    <span style={{ fontSize: 10, color: "#aaa", minWidth: 40, textAlign: "center" }}>{count}/{QUEUE_CAP}</span>
  </div>
);

const MuxDataFlow = () => {
  const [streams, setStreams] = useState(initStreams);
  const [activeIdx, setActiveIdx] = useState(0);
  const msgRef = useRef(0);
  const logRef = useRef(null);

  const current = streams[activeIdx];

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  });

  const update = (fn) => {
    setStreams(prev => {
      const next = prev.map(s => ({ ...s, outQ: [...s.outQ], inQ: [...s.inQ], logs: [...s.logs] }));
      fn(next[activeIdx]);
      return next;
    });
  };

  const addLog = (st, msg, ok) => {
    st.logs.push({ msg, ok });
    if (st.logs.length > 40) st.logs.splice(0, st.logs.length - 40);
  };

  const nextTag = (p) => { msgRef.current += 1; return p + msgRef.current; };

  const appSend = () => update(st => {
    if (st.state === "closed" || st.state === "hcl") return addLog(st, "send() → StreamClosed", false);
    if (st.outQ.length >= QUEUE_CAP) return addLog(st, `send() → QueueFull(${QUEUE_CAP})`, false);
    const t = nextTag("M"); st.outQ.push(t); addLog(st, `send("${t}") → ok`, true);
  });

  const appReceive = () => update(st => {
    if (st.inQ.length > 0) { const m = st.inQ.shift(); addLog(st, `receive() → Some("${m}")`, true); }
    else if (st.state === "closed") addLog(st, "receive() → StreamClosed", false);
    else addLog(st, "receive() → None", null);
  });

  const protoTake = () => update(st => {
    if (st.outQ.length > 0) { const m = st.outQ.shift(); addLog(st, `takeOutbound() → Some("${m}")`, true); }
    else if (st.state === "closed") addLog(st, "takeOutbound() → StreamClosed", false);
    else addLog(st, "takeOutbound() → None", null);
  });

  const protoOffer = () => update(st => {
    if (st.state === "closed" || st.state === "hcr") return addLog(st, "offerInbound() → StreamClosed", false);
    if (st.inQ.length >= QUEUE_CAP) return addLog(st, `offerInbound() → QueueFull(${QUEUE_CAP})`, false);
    const t = nextTag("R"); st.inQ.push(t); addLog(st, `offerInbound("${t}") → ok`, true);
  });

  const doHalfClose = () => update(st => {
    if (st.state === "open") { st.state = "hcl"; addLog(st, "halfClose() → HALF_CLOSED_LOCAL", true); }
    else if (st.state === "hcr") { st.state = "closed"; addLog(st, "halfClose() → CLOSED (both sides done)", true); }
    else addLog(st, `halfClose() → no-op (${stateMap[st.state].label})`, null);
  });

  const doSignalRemote = () => update(st => {
    if (st.state === "open") { st.state = "hcr"; addLog(st, "signalRemoteClose() → HALF_CLOSED_REMOTE", true); }
    else if (st.state === "hcl") { st.state = "closed"; addLog(st, "signalRemoteClose() → CLOSED (both sides done)", true); }
    else addLog(st, `signalRemoteClose() → no-op (${stateMap[st.state].label})`, null);
  });

  const doClose = () => update(st => { st.state = "closed"; addLog(st, "close() → CLOSED (immediate)", true); });

  const doReset = () => update(st => {
    st.state = "open"; st.outQ = []; st.inQ = []; st.logs = [];
    addLog(st, `Stream ${st.id} reset to OPEN`, true);
  });

  const isClosed = current.state === "closed";
  const isHCL = current.state === "hcl";
  const isHCR = current.state === "hcr";
  const activeCount = streams.filter(s => s.state !== "closed").length;
  const recentLogs = current.logs.slice(-14);

  const flowArrow = { textAlign: "center", fontSize: 11, color: "#aaa", padding: "2px 0" };

  return (
    <div style={{ maxWidth: 640, margin: "0 auto", fontFamily: "system-ui, sans-serif", fontSize: 14 }}>

      {/* Stream tabs */}
      <div style={{ display: "flex", gap: 6, marginBottom: 8 }}>
        {streams.map((ss, i) => (
          <button key={ss.id} onClick={() => setActiveIdx(i)} style={{
            padding: "4px 14px", borderRadius: 8, fontSize: 13, cursor: "pointer",
            border: i === activeIdx ? "none" : "1px solid #ddd",
            background: i === activeIdx ? "#333" : "#fff",
            color: i === activeIdx ? "#fff" : "#888",
          }}>
            Stream {ss.id}
            <span style={{ display: "inline-block", width: 7, height: 7, borderRadius: "50%", marginLeft: 6, verticalAlign: "middle", background: stateMap[ss.state].dot }} />
          </button>
        ))}
      </div>

      {/* Stats */}
      <div style={{ fontSize: 12, color: "#888", marginBottom: 10 }}>
        Active streams: <strong>{activeCount}/{STREAM_COUNT}</strong>
        <span style={{ marginLeft: 16 }}>Mux capacity: <strong>{STREAM_COUNT}</strong></span>
      </div>

      {/* Application zone */}
      <Zone label="Application code" tint={{ bg: "rgba(126,119,221,0.06)", border: "rgba(126,119,221,0.2)", label: "#534AB7" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <Btn variant="app" disabled={isClosed || isHCL} onClick={appSend}>↑ send()</Btn>
          <Btn variant="app" disabled={false} onClick={appReceive}>↓ receive()</Btn>
        </div>
      </Zone>

      <div style={flowArrow}>↓ send() &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; receive() ↑</div>

      {/* Mux zone */}
      <Zone label="" tint={{ bg: "#f9f9f7", border: "#e5e5e0", label: "#888" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
          <span style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5, color: "#888" }}>MuxStream</span>
          <Badge state={current.state} />
        </div>
        <QueueRow label="Outbound" items={current.outQ} dir="out" count={current.outQ.length} />
        <QueueRow label="Inbound" items={current.inQ} dir="in" count={current.inQ.length} />
      </Zone>

      <div style={flowArrow}>↓ takeOutbound() &nbsp;&nbsp;&nbsp; offerInbound() ↑</div>

      {/* Protocol zone */}
      <Zone label="Protocol layer" tint={{ bg: "rgba(29,158,117,0.06)", border: "rgba(29,158,117,0.2)", label: "#0F6E56" }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <Btn variant="proto" disabled={false} onClick={protoTake}>↑ takeOutbound()</Btn>
          <Btn variant="proto" disabled={isClosed || isHCR} onClick={protoOffer}>↓ offerInbound()</Btn>
        </div>
      </Zone>

      {/* Lifecycle controls */}
      <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap", marginTop: 8, marginBottom: 4 }}>
        <span style={{ fontSize: 11, color: "#aaa", marginRight: 2 }}>Lifecycle:</span>
        <Btn variant="lc" disabled={isClosed} onClick={doHalfClose}>halfClose()</Btn>
        <Btn variant="lc" disabled={isClosed} onClick={doSignalRemote}>signalRemoteClose()</Btn>
        <Btn variant="lc" disabled={isClosed} onClick={doClose}>close()</Btn>
        <Btn variant="lc" disabled={false} onClick={doReset}>↻ reset</Btn>
      </div>

      {/* Event log */}
      <div ref={logRef} style={{
        marginTop: 8, maxHeight: 100, overflowY: "auto", borderRadius: 8,
        background: "#f7f7f5", border: "1px solid #e5e5e0", padding: "8px 12px",
        fontFamily: "monospace", fontSize: 11, lineHeight: 1.7, color: "#888",
      }}>
        {recentLogs.map((entry, i) => (
          <div key={i} style={{ color: entry.ok === true ? "#3B6D11" : entry.ok === false ? "#c03030" : undefined }}>
            {entry.msg}
          </div>
        ))}
        {recentLogs.length === 0 && (
          <div style={{ color: "#bbb", fontStyle: "italic" }}>Click an action to begin</div>
        )}
      </div>
    </div>
  );
};

export default MuxDataFlow;
