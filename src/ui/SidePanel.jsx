import { useStore } from '../core/projectStore.js';
import MediaPanel from './MediaPanel.jsx';
import LayerList from './LayerList.jsx';
import PresetLibrary from './PresetLibrary.jsx';
import AudioPanel from './AudioPanel.jsx';
import ScenePanel from './ScenePanel.jsx';
import ExportPanel from './ExportPanel.jsx';

const TITLES = { lib: 'Media', lay: 'Layers', pre: 'Presets', aud: 'Audio', scn: 'Scene', exp: 'Export' };

export default function SidePanel() {
  const open = useStore((s) => s.panelOpen);
  const tab = useStore((s) => s.activeTab);
  const setOpen = useStore((s) => s.setPanelOpen);

  if (!open) return null;

  return (
    <aside className="open">
      <div className="sh">
        <span>{TITLES[tab]}</span>
        <span className="hb">
          <button className="ib" onClick={() => setOpen(false)} aria-label="Close">
            <svg className="i"><use href="#i-close" /></svg>
          </button>
        </span>
      </div>
      <div id="sb">
        {tab === 'lib' && <MediaPanel />}
        {tab === 'lay' && <LayerList />}
        {tab === 'pre' && <PresetLibrary />}
        {tab === 'aud' && <AudioPanel />}
        {tab === 'scn' && <ScenePanel />}
        {tab === 'exp' && <ExportPanel />}
      </div>
    </aside>
  );
}