import { useState } from 'react';
import { useStore } from '../core/projectStore.js';
import { mkLayer, mkBackground } from '../core/constants.js';

const BUILTIN = [
  ['Minimal', 'Classic Vertical', [mkBackground('#000', '#0b1018'), mkLayer('bars', { n: 64, y: 78, color: '#00e6a8', color2: '#6d8cff' })]],
  ['Circular', 'Circular Pulse', [mkBackground('#000', '#0a0a14'), mkLayer('circle', { n: 90, thick: 50, glow: 8 })]],
  ['Waveform', 'Line Wave', [mkBackground('#000', '#0a0a14'), mkLayer('wave', { n: 100, thick: 30, color: '#00e6a8', color2: '#00e6a8' })]],
  ['Neon', 'Neon Bars', [mkBackground('#05010f', '#12002a'), mkLayer('bars', { n: 56, y: 78, glow: 20, color: '#00f0ff', color2: '#ff2bd6' })]],
  ['Particles', 'Particle Burst', [mkBackground('#000', '#1a001a'), mkLayer('particles', { n: 120, color: '#ff00ff', color2: '#ff00ff' })]],
  ['Typography', 'Beat Title', [mkBackground('#111', '#000'), mkLayer('text', { band: 'bass', f0: 60, f1: 250, sens: 150, y: 45, fz: 16 })]]
];

export default function PresetLibrary() {
  const userPresets = useStore((s) => s.userPresets);
  const favorites = useStore((s) => s.favorites);
  const applyPreset = useStore((s) => s.applyPreset);
  const savePreset = useStore((s) => s.savePreset);
  const deletePreset = useStore((s) => s.deletePreset);
  const toggleFavorite = useStore((s) => s.toggleFavorite);
  const pushToast = useStore((s) => s.pushToast);
  const [name, setName] = useState('');

  const all = [
    ...BUILTIN.map(([cat, n, layers]) => ({ cat, name: n, layers, scene: {} })),
    ...userPresets.map((p) => ({ cat: 'Mine', name: p.name, layers: p.layers, scene: p.scene, user: true }))
  ];

  return (
    <div>
      <input placeholder="Search presets" style={{ marginBottom: 8, width: '100%' }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 8 }}>
        {all.map((p, i) => (
          <div key={i} className="it">
            <button onClick={() => { applyPreset(p.layers, p.scene); pushToast(`Applied ${p.name}`, 'ok'); }}>
              {p.name} <span className="dm" style={{ marginLeft: 4 }}>{p.cat}</span>
            </button>
            <button onClick={() => toggleFavorite(p.name)}>
              <svg className="i" style={favorites.includes(p.name) ? { fill: 'var(--ac)', stroke: 'var(--ac)' } : {}}>
                <use href="#i-pre" />
              </svg>
            </button>
            {p.user && (
              <button className="dg" onClick={() => deletePreset(p.name)}>
                <svg className="i sm"><use href="#i-trash" /></svg>
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="sec">Save current as</div>
      <div className="row">
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Preset name" />
        <button className="pri" onClick={() => {
          if (!name.trim()) return;
          savePreset(name.trim());
          setName('');
          pushToast('Preset saved', 'ok');
        }}>Save</button>
      </div>
    </div>
  );
}