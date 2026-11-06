import { useState } from 'react';
import { useStore } from '../core/projectStore.js';
import Inspector from './Inspector.jsx';

const TYPES = ['bars', 'circle', 'multiring', 'concentric', 'ring', 'pulse', 'wave',
  'wavec', 'scope', 'dots', 'lines', 'blocks', 'grid', 'particles',
  'shape', 'text', 'image', 'rgbSplit', 'blurEffect', 'motionBlur', 'bg'];

export default function LayerList() {
  const layers = useStore((s) => s.layers);
  const selectedId = useStore((s) => s.selectedId);
  const selectLayer = useStore((s) => s.selectLayer);
  const addLayer = useStore((s) => s.addLayer);
  const deleteLayer = useStore((s) => s.deleteLayer);
  const duplicateLayer = useStore((s) => s.duplicateLayer);
  const toggleVisibility = useStore((s) => s.toggleVisibility);
  const toggleLock = useStore((s) => s.toggleLock);
  const reorderLayer = useStore((s) => s.reorderLayer);
  const [type, setType] = useState('bars');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
      <div className="row" style={{ marginBottom: 0 }}>
        <select value={type} onChange={(e) => setType(e.target.value)} style={{ minWidth: 0 }}>
          {TYPES.map((t) => <option key={t}>{t}</option>)}
        </select>
        <button className="pri" onClick={() => addLayer(type)} style={{ flex: '0 0 auto' }}>
          Add layer
        </button>
      </div>
      <div id="llist">
        {layers.length === 0 && <div className="dm">No layers yet.</div>}
        {[...layers].reverse().map((l) => (
          <div key={l._id} className={`it ${l._id === selectedId ? 'sl' : ''} ${l.lock ? 'lkd' : ''}`}>
            <button onClick={() => selectLayer(l._id)} title={l.name || l.type}>
              {l.name || l.type} <span className="dm">{l.type}</span>
            </button>
            <button onClick={() => toggleVisibility(l._id)} aria-label="Visibility">
              <svg className="i"><use href={l.vis ? '#i-eye' : '#i-eye-off'} /></svg>
            </button>
            <button onClick={() => toggleLock(l._id)} aria-label="Lock">
              <svg className="i"><use href={l.lock ? '#i-lock' : '#i-unlock'} /></svg>
            </button>
          </div>
        ))}
      </div>
      <div className="lay-tools">
        <button onClick={() => reorderLayer(selectedId, 1)} aria-label="Move up">
          <svg className="i"><use href="#i-up" /></svg>
        </button>
        <button onClick={() => reorderLayer(selectedId, -1)} aria-label="Move down">
          <svg className="i"><use href="#i-down" /></svg>
        </button>
        <button onClick={() => duplicateLayer(selectedId)} aria-label="Duplicate">
          <svg className="i"><use href="#i-dup" /></svg>
        </button>
        <button className="dg" onClick={() => deleteLayer(selectedId)} aria-label="Delete">
          <svg className="i"><use href="#i-trash" /></svg>
        </button>
      </div>
      <Inspector />
    </div>
  );
}