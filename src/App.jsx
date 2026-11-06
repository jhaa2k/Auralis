import { useEffect } from 'react';
import Header from './ui/Header.jsx';
import Stage from './ui/Stage.jsx';
import Transport from './ui/Transport.jsx';
import Nav from './ui/Nav.jsx';
import SidePanel from './ui/SidePanel.jsx';
import SettingsModal from './ui/SettingsModal.jsx';
import { useStore } from './core/projectStore.js';
import { 
  initAudio, setOnEnded, applyAudioSettings, 
  play, pause, getCurrentTime, getDuration, seek, setupMediaSession 
} from './core/audioEngine.js';
import { THEMES } from './core/constants.js';

export default function App() {
  const settings = useStore((s) => s.settings);
  const pushToast = useStore((s) => s.pushToast);
  const toasts = useStore((s) => s.toasts);
  const showSet = useStore((s) => s.showSettings);
  const setShowSettings = useStore((s) => s.setShowSettings);
  const setAudioState = useStore((s) => s.setAudioState);

  useEffect(() => {
    const t = settings.theme || 'Claude Dark';
    const theme = THEMES[t] || THEMES['Claude Dark'];
    const r = document.documentElement.style;
    ['--bg', '--pn', '--ln', '--tx', '--dm', '--ac'].forEach((k, i) => r.setProperty(k, theme[i]));

    initAudio();
    applyAudioSettings(settings);
    setOnEnded(() => pushToast('Track ended', ''));

    setupMediaSession({
      play: () => { play(); setAudioState({ playing: true }); },
      pause: () => { pause(); setAudioState({ playing: false }); },
      next: () => pushToast('Next track', 'ok'),
      prev: () => pushToast('Prev track', 'ok'),
      seekBack: () => seek(Math.max(0, getCurrentTime() - 10)),
      seekFwd: () => seek(Math.min(getDuration(), getCurrentTime() + 10))
    });

    const syncWide = () => {
      const wide = window.innerWidth >= 820;
      document.body.classList.toggle('wide', wide);
      if (wide) useStore.getState().setPanelOpen(true);
    };
    syncWide();
    window.addEventListener('resize', syncWide);
    window.addEventListener('orientationchange', syncWide);
    return () => {
      window.removeEventListener('resize', syncWide);
      window.removeEventListener('orientationchange', syncWide);
    };
  }, []);

  useEffect(() => { applyAudioSettings(settings); }, [settings]);

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
      <SettingsModal open={!!showSet} onClose={() => setShowSettings(false)} />
      <div id="toast">
        {toasts.map((t) => (
          <div key={t.id} className={`show ${t.kind}`}>{t.msg}</div>
        ))}
      </div>
    </div>
  );
}