# ai-service — Agent Rules

These rules are architectural invariants. They hold for every change in this repo, no matter how small. If a task seems to require breaking one, stop and ask the developer instead of working around it.

## System context

| Component | Stack | Role |
|---|---|---|
| `ui` | React | Chat UI. Talks only to `core`. |
| `core` | Spring Boot | The only component with a public IP and outbound (egress) access. Owns authN/authZ, application data, LLM configuration, tool execution and all LLM calls. |
| `auth` | Keycloak | Identity. Used by `core`, never by `ai-service`. |
| `ai-service` | Python (this repo) | AI logic only: prompts, tool loop, chat history. |

```
ui ──HTTPS + SSE──▶ core ◀──bidi gRPC (internal)──▶ ai-service
                     │
                     └──HTTP──▶ LLM (e.g. Ollama)

ai-service ──✕──▶ LLM / internet / ui / auth   (blocked)
```

## Invariants

### 1. No outbound access
- `ai-service` cannot reach the internet or any LLM. The deployment network blocks it.
- Never add direct LLM calls, LLM provider SDKs used for network calls, HTTP clients to external services, or any code that assumes egress.
- Every LLM call goes through `core` over gRPC. `ai-service` builds the request, `core` executes it and streams the result back.

### 2. `core` is the only peer
- The only communication channel is bidirectional gRPC with `core`.
- Never expose a public HTTP/REST/WebSocket API, and never talk to `ui` or `auth` directly.
- Chat flow: `ui` → `core` (HTTPS) → `ai-service` (gRPC) → `core` → `ui` (SSE or equivalent streaming).

### 3. Streaming end to end
- Chat responses must stream (token/chunk level) from `ai-service` to `core` over the gRPC stream, so `core` can forward them to `ui`.
- Never collect a full response before sending it when the flow is a chat interaction.

### 4. Data ownership
- `ai-service` owns its own database and everything AI-specific (chat history, prompts, AI state). It handles that data itself.
- Anything that belongs to `core` is requested from `core` over gRPC. Never duplicate, cache as a source of truth, or store `core`-owned data.
- LLM configuration is `core`-owned: the user enters it in `ui`, `core` stores it. `ai-service` asks `core` whether an LLM is available and receives its details or a clear "not available" answer. Handle both cases.
- Tools are executed by `core`. `ai-service` requests a tool call; `core` authorizes and runs it and returns the result.

### 5. `ai-service` owns the contract
- The `.proto` contract between `core` and `ai-service` may be changed by `ai-service` whenever its development needs it.
- `ai-service` development must never be blocked or constrained by other components.

### 6. No authN/authZ
- Never implement authentication, authorization, user management, token validation, or any other `core` responsibility.
- Every request arriving over the gRPC channel comes from `core` and is already validated. Treat it as trusted, including any user identity or context it carries.

### 7. Separation of responsibilities
- General application responsibilities belong to `core`. `ai-service` handles only the AI part.
- If a feature needs something outside the AI scope, the right change is a new gRPC message or call that `core` serves, not a reimplementation here.

## Tool calling

Tool calling applies invariants 1, 4, 6 and 7 to tools. These rules hold for every tool, including ones that look harmless.

- `core` owns the tool catalog. At the start of each turn `ai-service` asks `core` which tools the current user may use (`ToolCatalogQuery`) and offers only those to the LLM. Never hard-code, cache or invent tool definitions in `ai-service`.
- `ai-service` owns the tool loop: it decides when to call the LLM again, feeds tool results back, and stops after a bounded number of iterations.
- Every tool call is a `ToolCallRequest` to `core`. `core` authorizes it for the user again and executes it. `ai-service` never executes a tool itself.
- Tool results are untrusted data. Pass them to the LLM only as `tool` messages; never merge them into the system prompt or treat them as instructions.
- Tool results can contain `core`-owned data. Keep them in memory for the current turn only; persist only the user message and the final assistant reply.
- The final answer streams exactly like a plain chat reply. Report tool progress to `core` with `ChatToolActivity` so the user can see it.
- A failed or unauthorized tool call is a normal result (`ok=false`) that goes back to the LLM, not an error that ends the turn.
