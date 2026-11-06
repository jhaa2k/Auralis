import { useEffect } from 'react';
import Header from './ui/Header.jsx';
import Stage from './ui/Stage.jsx';
import Transport from './ui/Transport.jsx';
import Nav from './ui/Nav.jsx';
import SidePanel from './ui/SidePanel.jsx';
import SettingsModal from './ui/SettingsModal.jsx';
import { useStore } from './core/projectStore.js';
import { initAudio, setOnEnded, applyAudioSettings } from './core/audioEngine.js';
import { THEMES } from './core/constants.js';

export default function App() {
  const settings = useStore((s) => s.settings);
  const pushToast = useStore((s) => s.pushToast);
  const toasts = useStore((s) => s.toasts);
  const [showSet, setShowSet] = useStore((s) => [s.showSettings, s.setShowSettings]);

  useEffect(() => {
    const t = settings.theme || 'Claude Dark';
    const theme = THEMES[t] || THEMES['Claude Dark'];
    const r = document.documentElement.style;
    ['--bg', '--pn', '--ln', '--tx', '--dm', '--ac'].forEach((k, i) => r.setProperty(k, theme[i]));

    initAudio();
    applyAudioSettings(settings, 1);
    setOnEnded(() => pushToast('Track ended', ''));

    const checkWide = () => {
      if (window.innerWidth >= 820) useStore.getState().setPanelOpen(true);
    };
    checkWide();
    window.addEventListener('resize', checkWide);
    return () => window.removeEventListener('resize', checkWide);
  }, []);

  useEffect(() => {
    applyAudioSettings(settings, 1);
  }, [settings]);

  return (
    <div id="app">
      <Header />
      <main>
        <section className="stage">
          <Stage />
          <Transport />
        </section>
        <SidePanel />
      </main>
      <Nav />
      <SettingsModal open={!!showSet} onClose={() => setShowSet(false)} />
      <div id="toast">
        {toasts.map((t) => (
          <div key={t.id} className={`show ${t.kind}`}>{t.msg}</div>
        ))}
      </div>
    </div>
  );
}