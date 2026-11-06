import { useStore } from '../core/projectStore.js';

const TABS = [
  ['lib', 'Media', 'lib'],
  ['lay', 'Layers', 'lay'],
  ['pre', 'Presets', 'pre'],
  ['aud', 'Audio', 'aud'],
  ['scn', 'Scene', 'scn'],
  ['exp', 'Export', 'exp']
];

export default function Nav() {
  const activeTab = useStore((s) => s.activeTab);
  const setActiveTab = useStore((s) => s.setActiveTab);
  const panelOpen = useStore((s) => s.panelOpen);
  const setPanelOpen = useStore((s) => s.setPanelOpen);

  return (
    <nav>
      {TABS.map(([id, label, ico]) => (
        <button
          key={id}
          className={panelOpen && activeTab === id ? 'on' : ''}
          onClick={() => {
            if (panelOpen && activeTab === id) setPanelOpen(false);
            else { setActiveTab(id); setPanelOpen(true); }
          }}
        >
          <svg className="i"><use href={`#i-${ico}`} /></svg>
          {label}
        </button>
      ))}
    </nav>
  );
}