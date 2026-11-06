import { useStore } from '../core/projectStore.js';

export default function Header() {
  const beat = useStore((s) => s.audio.playing);
  const undo = useStore((s) => s.undo);
  const redo = useStore((s) => s.redo);
  const togglePanel = useStore((s) => s.setPanelOpen);
  const panelOpen = useStore((s) => s.panelOpen);
  const setShowSettings = useStore((s) => s.setShowSettings);

  return (
    <header>
      <span className="logo">AUDIOVIS PRO</span>
      <span id="beatDot" className={beat ? 'on' : ''} title="Live beat indicator" />
      <span className="sp" />
      <button className="ib" onClick={undo} aria-label="Undo">
        <svg className="i"><use href="#i-undo" /></svg>
      </button>
      <button className="ib" onClick={redo} aria-label="Redo">
        <svg className="i"><use href="#i-redo" /></svg>
      </button>
      <button className="ib" onClick={() => togglePanel(!panelOpen)} aria-label="Panel">
        <svg className="i"><use href="#i-panel" /></svg>
      </button>
      <button className="ib" onClick={() => setShowSettings(true)} aria-label="Settings">
        <svg className="i"><use href="#i-set" /></svg>
      </button>
    </header>
  );
}