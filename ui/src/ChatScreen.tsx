import { FormEvent, useEffect, useRef, useState } from "react";
import { ChatStatus, streamChat, ToolPhase } from "./api";

type ToolActivity = { callId: string; name: string; phase: ToolPhase };

type Message = {
  id: number;
  role: "user" | "assistant";
  content: string;
  status?: ChatStatus | "ERROR" | "STREAMING";
  note?: string;
  tools?: ToolActivity[];
};

const TOOL_LABELS: Record<ToolPhase, (name: string) => string> = {
  STARTED: (name) => `Running tool ${name}…`,
  SUCCEEDED: (name) => `Used tool ${name}`,
  FAILED: (name) => `Tool ${name} failed`,
};

function upsertTool(tools: ToolActivity[] = [], activity: ToolActivity): ToolActivity[] {
  return tools.some((t) => t.callId === activity.callId)
    ? tools.map((t) => (t.callId === activity.callId ? activity : t))
    : [...tools, activity];
}

function newConversationId(): string {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, "0")).join("");
}

export default function ChatScreen({ onOpenSettings }: { onOpenSettings: () => void }) {
  const [conversationId, setConversationId] = useState(newConversationId);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const nextId = useRef(0);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => abortRef.current?.abort(), []);
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages]);

  function updateAssistant(id: number, change: (m: Message) => Message) {
    setMessages((all) => all.map((m) => (m.id === id ? change(m) : m)));
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    const text = input.trim();
    if (!text || busy) return;

    const userId = nextId.current++;
    const assistantId = nextId.current++;
    setMessages((all) => [
      ...all,
      { id: userId, role: "user", content: text },
      { id: assistantId, role: "assistant", content: "", status: "STREAMING" },
    ]);
    setInput("");
    setBusy(true);

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamChat(
        conversationId,
        text,
        {
          onDelta: (content) => updateAssistant(assistantId, (m) => ({ ...m, content: m.content + content })),
          onTool: (callId, name, phase) =>
            updateAssistant(assistantId, (m) => ({ ...m, tools: upsertTool(m.tools, { callId, name, phase }) })),
          onDone: (status, message) => updateAssistant(assistantId, (m) => ({ ...m, status, note: message })),
          onError: (message) => updateAssistant(assistantId, (m) => ({ ...m, status: "ERROR", note: message })),
        },
        controller.signal,
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        updateAssistant(assistantId, (m) => ({ ...m, status: "ERROR", note: (error as Error).message }));
      }
    } finally {
      setBusy(false);
    }
  }

  function newChat() {
    abortRef.current?.abort();
    setMessages([]);
    setConversationId(newConversationId());
  }

  return (
    <section className="chat">
      <div className="chat-header">
        <span className="muted">Conversation {conversationId.slice(0, 8)}</span>
        <button className="secondary" onClick={newChat}>
          New chat
        </button>
      </div>
      <div className="messages">
        {messages.length === 0 && <p className="muted">Send a message to start.</p>}
        {messages.map((m) => (
          <div key={m.id} className={`message ${m.role} ${m.status?.toLowerCase() ?? ""}`}>
            {m.tools && m.tools.length > 0 && (
              <div className="tools">
                {m.tools.map((t) => (
                  <span key={t.callId} className={`tool ${t.phase.toLowerCase()}`}>
                    {TOOL_LABELS[t.phase](t.name)}
                  </span>
                ))}
              </div>
            )}
            <div className="content">
              {m.content}
              {m.status === "STREAMING" && <span className="cursor">▍</span>}
            </div>
            {m.status === "NO_LLM" && (
              <button className="link" onClick={onOpenSettings}>
                Open LLM Settings
              </button>
            )}
            {(m.status === "LLM_ERROR" || m.status === "ERROR") && <div className="note">{m.note}</div>}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <form className="composer" onSubmit={send}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a message…"
          disabled={busy}
          autoFocus
        />
        <button type="submit" disabled={busy || !input.trim()}>
          Send
        </button>
      </form>
    </section>
  );
}
