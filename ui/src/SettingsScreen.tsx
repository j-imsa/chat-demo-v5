import { FormEvent, useEffect, useState } from "react";
import { deleteLlmConfig, getLlmConfig, LlmConfig, saveLlmConfig } from "./api";

type Notice = { kind: "ok" | "error"; text: string } | null;

export default function SettingsScreen() {
  const [current, setCurrent] = useState<LlmConfig | null>(null);
  const [baseUrl, setBaseUrl] = useState("");
  const [model, setModel] = useState("");
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState<Notice>(null);

  useEffect(() => {
    getLlmConfig()
      .then((config) => {
        setCurrent(config);
        setBaseUrl(config?.baseUrl ?? "");
        setModel(config?.model ?? "");
      })
      .catch((error) => setNotice({ kind: "error", text: error.message }))
      .finally(() => setLoading(false));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    try {
      const saved = await saveLlmConfig(baseUrl, model);
      setCurrent(saved);
      setBaseUrl(saved.baseUrl);
      setModel(saved.model);
      setNotice({ kind: "ok", text: "Settings saved." });
    } catch (error) {
      setNotice({ kind: "error", text: (error as Error).message });
    }
  }

  async function remove() {
    try {
      await deleteLlmConfig();
      setCurrent(null);
      setBaseUrl("");
      setModel("");
      setNotice({ kind: "ok", text: "LLM configuration removed." });
    } catch (error) {
      setNotice({ kind: "error", text: (error as Error).message });
    }
  }

  if (loading) return <p className="muted">Loading settings…</p>;

  return (
    <section className="settings">
      <h2>LLM Settings</h2>
      <p className="muted">
        {current ? `Configured: ${current.model} at ${current.baseUrl}` : "No LLM is configured yet."}
      </p>
      <form onSubmit={save}>
        <label>
          Base URL
          <input
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="http://host.docker.internal:11434"
            required
          />
        </label>
        <label>
          Model name
          <input value={model} onChange={(e) => setModel(e.target.value)} placeholder="qwen2.5:7b" required />
        </label>
        <div className="actions">
          <button type="submit">Save</button>
          <button type="button" className="secondary" onClick={remove} disabled={!current}>
            Remove
          </button>
        </div>
      </form>
      {notice && <p className={`notice ${notice.kind}`}>{notice.text}</p>}
    </section>
  );
}
