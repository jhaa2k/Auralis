export const TAU = Math.PI * 2;
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const fmtTime = (s) =>
  isFinite(s) ? `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}` : '0:00';
export const esc = (s) =>
  String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

export const THEMES = {
  'Claude Dark': ['#262624', '#30302E', '#454440', '#FAF9F5', '#B0AEA5', '#D97757'],
  CapCut: ['#000000', '#141416', '#2a2a2e', '#ffffff', '#8e8e93', '#00e5c0'],
  'Claude Light': ['#FAF9F5', '#FFFFFF', '#E8E6DC', '#141413', '#6B6A63', '#C15F3C'],
  'Pure Black': ['#000000', '#0e0e10', '#252529', '#f2f2f5', '#a8a8b2', '#00e6a8'],
  Graphite: ['#1a1a1c', '#232326', '#3a3a3e', '#f0f0f0', '#8e8e93', '#f0f0f0'],
  Slate: ['#1e2530', '#262e3b', '#3b4657', '#e8edf4', '#8794a8', '#cfd8e6'],
  'Midnight Blue': ['#060b1f', '#0c1330', '#223063', '#e6ecff', '#7a89c2', '#6d8cff'],
  'Electric Cyan': ['#031215', '#08202a', '#0f4a57', '#e0fbff', '#5fa3b0', '#00e5ff'],
  'Neon Purple': ['#0d0617', '#170a29', '#3d1d6b', '#f3e8ff', '#9a7bc4', '#b44dff'],
  Crimson: ['#120406', '#20080c', '#5a1620', '#ffe8ea', '#b07078', '#ff2d4a'],
  Emerald: ['#03120b', '#08201a', '#134a36', '#e3fff1', '#6aa88a', '#18e08a'],
  Amber: ['#120c03', '#201607', '#5a3f12', '#fff3dc', '#b09260', '#ffb020']
};

export const BANDS = {
  sub: [20, 60], bass: [60, 250], lowmid: [250, 500], mid: [500, 2000],
  upmid: [2000, 4000], presence: [4000, 6000], brill: [6000, 20000]
};

export const SRC_LIST = [
  'bass', 'sub', 'lowmid', 'mid', 'upmid', 'presence', 'brill',
  'volume', 'peak', 'beat', 'kick', 'snare', 'flux',
  'left', 'right', 'phase', 'lfo1', 'lfo2', 'rnd'
];

export const BEAT_TRIGGERS = ['global', 'kick', 'snare', 'hat', 'onset', 'tap', 'off'];

export const BLEND_MODES = [
  'source-over', 'lighter', 'screen', 'multiply', 'overlay',
  'darken', 'lighten', 'color-dodge', 'difference', 'exclusion',
  'hard-light', 'soft-light'
];

export const FONTS = [
  'Archivo', 'Inter', 'Roboto', 'Montserrat', 'Oswald', 'Bebas Neue',
  'Anton', 'Poppins', 'Playfair Display', 'Orbitron', 'Audiowide',
  'Righteous', 'Bungee', 'Russo One', 'Raleway', 'Cinzel'
];

export const SHAPES = ['circle', 'rect', 'triangle', 'poly', 'star', 'capsule'];

export const COMMON = {
  type: 'bars', name: '', tag: '', vis: 1, lock: 0,
  x: 50, y: 50, sx: 100, sy: 100, uni: 0, rot: 0, spin: 0,
  anchorX: 50, anchorY: 50,
  measurePos: '', measureScale: '', measureRot: '', alignPos: 'center',
  op: 100, blend: 'source-over',
  band: 'all', f0: 30, f1: 16000, fsc: 'log',
  sens: 100, att: 60, rel: 15,
  color: '#ffffff', color2: '#6a6a75', color3: '#00e6a8',
  grad: 'linear', gAng: 90, gAnim: 0,
  glow: 0, shOff: 0, blur: 0,
  hue: 0, bright: 100, contr: 100, sat: 100,
  anim: 'none', as: 1, aa: 30,
  rep: 1, repang: 0, repox: 0, repoy: 0, repsc: 0, repfade: 0,
  rmode: 'continuous', rmin: -30, rmax: 30, rspd: 0.5, rstep: 45,
  rease: 'smooth', rbeat: 0, rpx: 0, rpy: 0, flx: 0, fly: 0,
  raud: 0, rsrc: 'bass', rph: 0,
  mm: 'none', mspd: 0.3, mrx: 10, mry: 8, mph: 0,
  mbp: 0, bk: 0, bkd: 10, bsel: 'global',
  skx: 0, sky: 0, bop: 0, bgl: 0,
  rules: []
};

export const SCENE_DEFAULTS = {
  bg: '#000000', cam: 0, vig: 0, scan: 0, grain: 0,
  flash: 0, shake: 0, hueB: 0
};

export const SETTINGS_DEFAULTS = {
  theme: 'Claude Dark', rot: 'auto', pq: 75, fps: 60, showFps: 0, guides: 0,
  fft: 2048, smooth: 50, floor: 8, beat: 130,
  bmode: 'auto', bsrc: 'bass', bmin: 30, bcool: 180, bdec: 90, boff: 0, bpm: 120,
  lfo1: 0.25, lfo2: 0.5, auto: 1,
  vol: 100, pre: 0, bass: 0, bal: 0, speed: 100, norm: 0,
  rev: 0, dly: 0, dist: 0,
  eq: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  sh: 0, rp: 'off',
  ratio: '16:9', res: 720, cw: 1280, ch: 720,
  xfps: 30, mbps: 8, abr: 192, fmt: 0, t0: 0, t1: 0,
  cc: 1
};

export const TYPE_DEFAULTS = {
  bars: { n: 48, thick: 70, round: 0, maxH: 100, dir: 'up', mir: 'none', rvar: 0,
    flipInput: 0, heightScale: 100, minHeightScale: 0, maxHeightScale: 100,
    colorBlendOffset: 0, barsShapePath: 'line',
    seg1Fixed: 0, seg1Mul: 100, seg1Mirror: 0, seg1Flip: 0,
    seg2Fixed: 0, seg2Mul: 100, seg2Mirror: 0, seg2Flip: 0,
    softness: 0, reactDelay: 0, reactAcc: 0 },
  circle: { n: 96, thick: 50, r0: 60, a0: -90, a1: 270, maxH: 100, odir: 'out', mir: 'none' },
  multiring: { n: 48, thick: 40, r0: 50, rings: 3, a0: -90, a1: 270, maxH: 100, odir: 'out' },
  concentric: { rings: 6, thick: 20, maxH: 100 },
  ring: { thick: 30, r0: 60, sens: 120 },
  pulse: { thick: 20, r0: 40 },
  wave: { n: 100, thick: 30, maxH: 100, ws: 'line', wfill: 0, mir: 'none' },
  wavec: { thick: 25, r0: 70, maxH: 100 },
  scope: { n: 100, thick: 20, ws: 'line' },
  dots: { n: 36, thick: 70, maxH: 100 },
  lines: { n: 48, thick: 70, maxH: 100 },
  blocks: { n: 28, seg: 12, thick: 80, maxH: 100 },
  grid: { n: 48, seg: 24, maxH: 100 },
  particles: {
    n: 120, countLimit: 200, thick: 40,
    mirrorX: 0, mirrorY: 0,
    perspectiveDepth: 0, perspectiveFov: 60,
    spawnArea: 100, vectorRelCenter: 0, useRandomVector: 0,
    vectorAngle: 0, vectorAngleZ: 0, forceField: 0,
    particleScale: 100, trailLength: 0,
    colorFrom: '#ffffff', colorTo: '#000000',
    fadeInOutTime: 0, velocityAngle: 0, sizeAspect: 1,
    startSize: 2, fadeInSize: 0, endSize: 4, endSizeRandom: 0, endSizeZFactor: 0,
    nearCamFadeOut: 0, spawnTime: 1, measureSpeed: 'none',
    speedAX: 0, speedBY: 0, speed: 2, speedRandom: 0,
    lifetime: 2, gravity: 0,
    sideSineFreq: 0, sideSineFreqRandom: 0, sideSineMag: 0, sideSineMagRandom: 0
  },
  shape: { shape: 'circle', sides: 5, maxH: 100, sw: 0, sc: '#ffffff' },
  text: {
    text: 'AUDIOVIS', font: 'Archivo', fz: 12, fw: 800, fi: 0,
    ls: 0, up: 0, al: 'center',
    tlh: 120, tbeat: 35, tty: 0, sw: 0, sc: '#ffffff',
    tbox: 0, tbc: '#000000', tbo: 60, tpad: 14, trad: 10
  },
  image: {
    maxH: 100, imgMask: 'none', imgKeepAspect: 1,
    imgColorKeyEnabled: 0, imgColorKey: '#00ff00', imgTransparencyStrength: 100,
    imgBlurEnabled: 0, imgBlurRadius: 4,
    imgSaturation: 100, imgColor: '#ffffff', imgColorTo: '#000000'
  },
  rgbSplit: {
    targetImage: '', splitMultiplier: 1, splitAmount: 8,
    splitColor0: '#ff0000', splitColor1: '#00ff00', splitColor2: '#0000ff',
    showUnblurred: 0, showUnblurredUnder: 0
  },
  blurEffect: {
    sourceComposition: 0, blurRadius: 8, blurMultiplier: 1,
    layer1Scale: 1, layer2Scale: 1.4, layer3Scale: 1.8,
    layer1Color: '#ffffff', layer2Color: '#ffffff', layer3Color: '#ffffff',
    maskImage: '', showUnblurred: 0, showUnblurredUnder: 0
  },
  motionBlur: {
    targetImage: '', blurAmount: 1, motionSource: 'transform',
    relativeMotion: 'absolute', showUnblurred: 0, showUnblurredUnder: 0
  },
  bg: {
    color: '#000000', color2: '#000000', color3: '#000000',
    three: 0, grad: 'linear', gAng: 90, gAnim: 0
  }
};

export function mkLayer(type, overrides = {}) {
  return {
    ...COMMON,
    ...(TYPE_DEFAULTS[type] || {}),
    type,
    name: type,
    rules: [],
    ...overrides
  };
}

export function mkBackground(color = '#000', color2 = '#0a0a0a', overrides = {}) {
  return mkLayer('bg', { color, color2, ...overrides });
}