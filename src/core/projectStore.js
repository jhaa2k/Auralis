import { create } from 'zustand';
import { SETTINGS_DEFAULTS, SCENE_DEFAULTS, mkLayer, mkBackground } from './constants.js';

const LS_KEY = 'av.set';
const PROJ_KEY = 'av.proj';
const USR_KEY = 'av.presets';
const FAV_KEY = 'av.favs';

const lsGet = (k, d) => {
  try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); }
  catch { return d; }
};
const lsSet = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };

let autosaveTimer = null;
const scheduleSave = (state) => {
  clearTimeout(autosaveTimer);
  autosaveTimer = setTimeout(() => {
    lsSet(PROJ_KEY, {
      v: 4,
      layers: state.layers.map((l) => stripRuntime(l)),
      scene: state.scene
    });
  }, 400);
};

const stripRuntime = (l) => {
  const out = {};
  for (const k of Object.keys(l)) {
    if (k[0] === '_') continue;
    out[k] = l[k];
  }
  return out;
};

const initialSettings = { ...SETTINGS_DEFAULTS, ...lsGet(LS_KEY, {}) };
const initialProject = lsGet(PROJ_KEY, null);

const baseLayers =
  initialProject && Array.isArray(initialProject.layers) && initialProject.layers.length
    ? initialProject.layers.map((l, i) => ({ ...l, _id: l._id || `init-${i}` }))
    : [
        { ...mkBackground('#000', '#0b1018'), _id: 'bg-init' },
        { ...mkLayer('bars', { n: 64, y: 78, color: '#00e6a8', color2: '#6d8cff' }), _id: 'bars-init' }
      ];

export const useStore = create((set, get) => ({
  settings: initialSettings,
  updateSettings(patch) {
    const settings = { ...get().settings, ...patch };
    set({ settings });
    lsSet(LS_KEY, settings);
  },

  layers: baseLayers,
  scene: { ...SCENE_DEFAULTS, ...(initialProject?.scene || {}) },
  selectedId: baseLayers[1]?._id || null,

  setLayers(layers) { set({ layers }); scheduleSave(get()); },
  setScene(scene) { set({ scene }); scheduleSave(get()); },
  selectLayer(id) { set({ selectedId: id }); },

  addLayer(type, overrides = {}) {
    const layer = { ...mkLayer(type, overrides), _id: crypto.randomUUID() };
    const layers = [...get().layers, layer];
    set({ layers, selectedId: layer._id });
    scheduleSave(get());
    return layer._id;
  },
  duplicateLayer(id) {
    const src = get().layers.find((l) => l._id === id);
    if (!src) return;
    const copy = { ...JSON.parse(JSON.stringify(stripRuntime(src))), _id: crypto.randomUUID(), name: src.name + ' copy' };
    const idx = get().layers.findIndex((l) => l._id === id);
    const layers = [...get().layers];
    layers.splice(idx + 1, 0, copy);
    set({ layers, selectedId: copy._id });
    scheduleSave(get());
  },
  deleteLayer(id) {
    const layers = get().layers.filter((l) => l._id !== id);
    const selectedId = layers.length ? layers[layers.length - 1]._id : null;
    set({ layers, selectedId });
    scheduleSave(get());
  },
  reorderLayer(id, dir) {
    const layers = [...get().layers];
    const i = layers.findIndex((l) => l._id === id);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= layers.length) return;
    [layers[i], layers[j]] = [layers[j], layers[i]];
    set({ layers });
    scheduleSave(get());
  },
  updateLayer(id, patch) {
    const layers = get().layers.map((l) => (l._id === id ? { ...l, ...patch } : l));
    set({ layers });
    scheduleSave(get());
  },
  toggleVisibility(id) {
    const layers = get().layers.map((l) => l._id === id ? { ...l, vis: l.vis ? 0 : 1 } : l);
    set({ layers });
    scheduleSave(get());
  },
  toggleLock(id) {
    const layers = get().layers.map((l) => l._id === id ? { ...l, lock: l.lock ? 0 : 1 } : l);
    set({ layers });
    scheduleSave(get());
  },

  userPresets: lsGet(USR_KEY, []),
  favorites: lsGet(FAV_KEY, []),
  savePreset(name) {
    const p = { name, layers: get().layers.map(stripRuntime), scene: get().scene, created: Date.now() };
    const userPresets = [p, ...get().userPresets];
    set({ userPresets });
    lsSet(USR_KEY, userPresets);
  },
  deletePreset(name) {
    const userPresets = get().userPresets.filter((p) => p.name !== name);
    set({ userPresets });
    lsSet(USR_KEY, userPresets);
  },
  toggleFavorite(name) {
    const has = get().favorites.includes(name);
    const favorites = has ? get().favorites.filter((x) => x !== name) : [...get().favorites, name];
    set({ favorites });
    lsSet(FAV_KEY, favorites);
  },
  applyPreset(layers, scene) {
    const cloned = layers.map((l, i) => ({ ...JSON.parse(JSON.stringify(stripRuntime(l))), _id: crypto.randomUUID() }));
    set({ layers: cloned, scene: { ...SCENE_DEFAULTS, ...scene }, selectedId: cloned[cloned.length - 1]?._id });
    scheduleSave(get());
  },

  history: { past: [], future: [] },
  snapshot() {
    const { layers, scene, history } = get();
    const snap = JSON.stringify({ layers: layers.map(stripRuntime), scene });
    set({ history: { past: [...history.past.slice(-40), snap], future: [] } });
  },
  undo() {
    const { history } = get();
    if (!history.past.length) return;
    const prev = history.past[history.past.length - 1];
    const cur = JSON.stringify({ layers: get().layers.map(stripRuntime), scene: get().scene });
    const parsed = JSON.parse(prev);
    set({
      layers: parsed.layers.map((l) => ({ ...l, _id: crypto.randomUUID() })),
      scene: parsed.scene,
      history: { past: history.past.slice(0, -1), future: [cur, ...history.future].slice(0, 40) }
    });
    scheduleSave(get());
  },
  redo() {
    const { history } = get();
    if (!history.future.length) return;
    const next = history.future[0];
    const cur = JSON.stringify({ layers: get().layers.map(stripRuntime), scene: get().scene });
    const parsed = JSON.parse(next);
    set({
      layers: parsed.layers.map((l) => ({ ...l, _id: crypto.randomUUID() })),
      scene: parsed.scene,
      history: { past: [...history.past, cur].slice(-40), future: history.future.slice(1) }
    });
    scheduleSave(get());
  },

  panelOpen: false,
  activeTab: 'lib',
  setPanelOpen(open) { set({ panelOpen: open }); },
  setActiveTab(tab) { set({ activeTab: tab }); },

  showSettings: false,
  setShowSettings(v) { set({ showSettings: v }); },

  toasts: [],
  pushToast(msg, kind = '') {
    const id = crypto.randomUUID();
    set({ toasts: [...get().toasts, { id, msg, kind }] });
    setTimeout(() => { set({ toasts: get().toasts.filter((t) => t.id !== id) }); }, 2600);
  },

  audio: { files: [], cur: -1, playing: false, duration: 0, currentTime: 0 },
  setAudioState(patch) { set({ audio: { ...get().audio, ...patch } }); }
}));