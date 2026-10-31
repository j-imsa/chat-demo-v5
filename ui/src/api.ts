export type LlmConfig = {
  provider: string;
  baseUrl: string;
  model: string;
  updatedAt: string;
};

export type ChatStatus = "OK" | "NO_LLM" | "LLM_ERROR";

export type ToolPhase = "STARTED" | "SUCCEEDED" | "FAILED";

export type ChatEvents = {
  onDelta: (content: string) => void;
  onTool: (callId: string, name: string, phase: ToolPhase) => void;
  onDone: (status: ChatStatus, message: string) => void;
  onError: (message: string) => void;
};

export async function getLlmConfig(): Promise<LlmConfig | null> {
  const response = await fetch("/api/llm-config");
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Loading settings failed (HTTP ${response.status})`);
  return response.json();
}

export async function saveLlmConfig(baseUrl: string, model: string): Promise<LlmConfig> {
  const response = await fetch("/api/llm-config", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ baseUrl, model }),
  });
  if (!response.ok) throw new Error(await errorText(response));
  return response.json();
}

export async function deleteLlmConfig(): Promise<void> {
  const response = await fetch("/api/llm-config", { method: "DELETE" });
  if (!response.ok) throw new Error(`Removing settings failed (HTTP ${response.status})`);
}

export async function streamChat(
  conversationId: string,
  message: string,
  events: ChatEvents,
  signal: AbortSignal,
): Promise<void> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
    body: JSON.stringify({ conversationId, message }),
    signal,
  });
  if (!response.ok || !response.body) {
    events.onError(await errorText(response));
    return;
  }

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";
  let finished = false;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value.replace(/\r\n/g, "\n");
    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      finished = dispatch(block, events) || finished;
    }
  }
  if (!finished) events.onError("The connection to the server ended unexpectedly.");
}

function dispatch(block: string, events: ChatEvents): boolean {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    const colon = line.indexOf(":");
    if (colon <= 0) continue;
    const field = line.slice(0, colon);
    const value = line.slice(colon + 1).replace(/^ /, "");
    if (field === "event") event = value;
    else if (field === "data") data.push(value);
  }
  if (data.length === 0) return false;
  const payload = JSON.parse(data.join("\n"));
  switch (event) {
    case "delta":
      events.onDelta(payload.content);
      return false;
    case "tool":
      events.onTool(payload.callId, payload.name, payload.phase);
      return false;
    case "done":
      events.onDone(payload.status, payload.message);
      return true;
    case "error":
      events.onError(payload.message);
      return true;
    default:
      return false;
  }
}

async function errorText(response: Response): Promise<string> {
  try {
    const body = await response.json();
    const fieldErrors = body.errors?.map((e: { field: string; defaultMessage: string }) => `${e.field} ${e.defaultMessage}`);
    return fieldErrors?.length ? fieldErrors.join(", ") : body.detail ?? body.error ?? `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}
