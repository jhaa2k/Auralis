import { useStore } from '../core/projectStore.js';
import { THEMES } from '../core/constants.js';

export default function SettingsModal({ open, onClose }) {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);

  if (!open) return null;

  const applyTheme = (name) => {
    const t = THEMES[name] || THEMES['Claude Dark'];
    const r = document.documentElement.style;
    ['--bg', '--pn', '--ln', '--tx', '--dm', '--ac'].forEach((k, i) => r.setProperty(k, t[i]));
    updateSettings({ theme: name });
  };

  return (
    <div id="set">
      <div className="sh">
        <span>Settings</span>
        <button className="pri" onClick={onClose}>Close</button>
      </div>
      <div className="bd">
        <label className="f">
          <span>Theme</span>
          <select value={settings.theme} onChange={(e) => applyTheme(e.target.value)}>
            {Object.keys(THEMES).map((k) => <option key={k}>{k}</option>)}
          </select>
        </label>
        <label className="f">
          <span>Rotation</span>
          <select value={settings.rot} onChange={(e) => updateSettings({ rot: e.target.value })}>
            <option value="auto">Auto (device)</option>
            <option value="portrait">Manual portrait</option>
            <option value="landscape">Manual landscape</option>
          </select>
        </label>
        <label className="f">
          <span>Preview quality</span>
          <select value={settings.pq} onChange={(e) => updateSettings({ pq: +e.target.value })}>
            <option value={40}>Low 40%</option>
            <option value={60}>Medium 60%</option>
            <option value={75}>High 75%</option>
            <option value={100}>Full 100%</option>
          </select>
        </label>
        <label className="f">
          <span>Frame limit</span>
          <select value={settings.fps} onChange={(e) => updateSettings({ fps: +e.target.value })}>
            <option value={24}>24</option>
            <option value={30}>30 (low power)</option>
            <option value={60}>60</option>
          </select>
        </label>
        <label className="f">
          <span>Show FPS</span>
          <input type="checkbox" checked={!!settings.showFps}
            onChange={(e) => updateSettings({ showFps: e.target.checked ? 1 : 0 })} />
        </label>
        <label className="f">
          <span>Guides</span>
          <input type="checkbox" checked={!!settings.guides}
            onChange={(e) => updateSettings({ guides: e.target.checked ? 1 : 0 })} />
        </label>
      </div>
    </div>
  );
}