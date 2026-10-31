import { useState } from "react";
import ChatScreen from "./ChatScreen";
import SettingsScreen from "./SettingsScreen";

type Screen = "chat" | "settings";

export default function App() {
  const [screen, setScreen] = useState<Screen>("chat");

  return (
    <div className="app">
      <header className="topbar">
        <h1>AI Chat Demo</h1>
        <nav>
          <button className={screen === "chat" ? "active" : ""} onClick={() => setScreen("chat")}>
            Chat
          </button>
          <button className={screen === "settings" ? "active" : ""} onClick={() => setScreen("settings")}>
            LLM Settings
          </button>
        </nav>
      </header>
      <main>
        <div hidden={screen !== "chat"}>
          <ChatScreen onOpenSettings={() => setScreen("settings")} />
        </div>
        {screen === "settings" && <SettingsScreen />}
      </main>
    </div>
  );
}
