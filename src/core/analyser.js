import { TAU, clamp, BANDS } from './constants.js';
import { getAnalyser, getAnalyserLR, isPaused, getCurrentTime } from './audioEngine.js';

let fd = new Uint8Array(1024);
let td = new Uint8Array(2048).fill(128);
let tL = new Uint8Array(512).fill(128);
let tR = new Uint8Array(512).fill(128);

const PV = {};
const RS = {};
const pkS = {};
const EN = {};
const hist = [];

let beatNow = false;
let lastBeat = 0;
let bGap = 500;
let rndV = 0;
let gridK = -1;
let tapPend = false;
let aT = 0;

export const SV = { beat: 0, phase: 0, lfo1: 0, lfo2: 0, rnd: 0 };
export const BT = { global: false, kick: false, snare: false, hat: false, onset: false, tap: false, off: false };

let mapBeats = [];
let mapT = -9;
let mapI = 0;

const edge = (k, v, th) => {
  const r = v >= th && !pkS[k];
  pkS[k] = v >= th;
  return r;
};

export function getMeasurement(name) {
  if (name in SV) return SV[name];
  if (name in BT) return BT[name] ? 1 : 0;
  return 0;
}

export function getBeatTriggered(sel) {
  if (sel === 'off') return false;
  if (!sel || sel === 'global') return beatNow;
  return !!BT[sel];
}

export function setBeatMap(beats) { mapBeats = beats; mapT = -9; }

function mapBeat(t) {
  if (Math.abs(t - mapT) > 0.4) {
    let lo = 0, hi = mapBeats.length;
    while (lo < hi) {
      const m = (lo + hi) >> 1;
      if (mapBeats[m] <= t) lo = m + 1;
      else hi = m;
    }
    mapI = lo;
    mapT = t;
    return false;
  }
  let hit = false;
  while (mapI < mapBeats.length && mapBeats[mapI] <= t) {
    hit = true;
    mapI++;
  }
  mapT = t;
  return hit;
}

function bandAvg(a, b) {
  const an = getAnalyser();
  if (!an) return 0;
  const sr = an.context.sampleRate;
  const hz = sr / 2 / fd.length;
  let i = Math.max(0, Math.floor(a / hz));
  let j = Math.max(i + 1, Math.min(fd.length, Math.ceil(b / hz)));
  let s = 0;
  for (let k = i; k < j; k++) s += fd[k];
  return s / (j - i) / 255;
}

const rms = (a) => {
  let r = 0;
  for (let i = 0; i < a.length; i++) {
    const x = (a[i] - 128) / 128;
    r += x * x;
  }
  return clamp(Math.sqrt(r / a.length) * 2.2, 0, 1);
};

export function fpos(l, t) {
  const a = Math.max(20, l.f0);
  const b = Math.max(a + 1, l.f1);
  if (l.fsc === 'linear') return a + (b - a) * t;
  if (l.fsc === 'mel') {
    const m = (x) => 2595 * Math.log10(1 + x / 700);
    return 700 * (Math.pow(10, (m(a) + (m(b) - m(a)) * t) / 2595) - 1);
  }
  return a * Math.pow(b / a, t);
}

export function analyse(now, S) {
  const an = getAnalyser();
  const dt = Math.min(0.1, Math.max(0.001, (now - (aT || now - 16)) / 1000));
  aT = now;

  if (an) {
    if (fd.length !== an.frequencyBinCount) fd = new Uint8Array(an.frequencyBinCount);
    if (td.length !== an.fftSize) td = new Uint8Array(an.fftSize);
    an.getByteFrequencyData(fd);
    an.getByteTimeDomainData(td);
    const lr = getAnalyserLR();
    lr.L.getByteTimeDomainData(tL);
    lr.R.getByteTimeDomainData(tR);
  }

  const fl = S.floor / 100;
  const g = (v) => clamp((v - fl) / (1 - fl), 0, 1);
  let fx = 0;
  for (const k in BANDS) {
    const v = g(bandAvg(...BANDS[k]));
    const d = Math.max(0, v - (PV[k] || 0));
    fx += d;
    RS[k] = d;
    PV[k] = v;
    SV[k] = v;
  }
  SV.volume = rms(td);
  SV.left = rms(tL);
  SV.right = rms(tR);

  let pk = 0;
  for (let i = 0; i < fd.length; i++) if (fd[i] > pk) pk = fd[i];
  SV.peak = pk / 255;

  for (const k of ['bass', 'upmid']) {
    const f = Math.max(0, SV[k] - (EN['p' + k] || 0));
    EN['p' + k] = SV[k];
    EN[k] = Math.max(f * 5, (EN[k] || 0) * 0.88);
  }
  SV.kick = Math.min(1, EN.bass || 0);
  SV.snare = Math.min(1, EN.upmid || 0);
  SV.flux = Math.min(1, (EN.f || 0) * 0.9 + fx * 2);
  EN.f = SV.flux;

  const playing = !!an && !isPaused();
  const ct = getCurrentTime();
  const off = S.boff / 1000;
  const mode = S.bmode;
  beatNow = false;

  if (playing && mode === 'auto') {
    const x = SV[S.bsrc] || 0;
    const m = hist.length ? hist.reduce((a, b) => a + b, 0) / hist.length : 0;
    beatNow = x > S.bmin / 100 && x > m * (S.beat / 100) && now - lastBeat > S.bcool;
    hist.push(x);
    const cap = Math.max(12, Math.round(S.fps * 0.7));
    while (hist.length > cap) hist.shift();
  } else if (playing && mode === 'map' && mapBeats.length) {
    beatNow = mapBeat(ct + off) && now - lastBeat > S.bcool * 0.5;
  } else if (playing && mode === 'bpm') {
    const k = Math.floor(((ct + off) * S.bpm) / 60);
    if (gridK < 0 || Math.abs(k - gridK) > 1) gridK = k;
    else if (k !== gridK) { gridK = k; beatNow = true; }
  }

  BT.tap = false;
  if (tapPend) { tapPend = false; beatNow = true; BT.tap = true; }

  if (beatNow) {
    const gp = now - lastBeat;
    if (gp > 200 && gp < 2000) bGap += (gp - bGap) * 0.3;
    lastBeat = now;
    rndV = Math.random();
  }

  BT.global = beatNow;
  BT.kick = edge('kick', SV.kick, 0.55);
  BT.snare = edge('snare', SV.snare, 0.5);
  BT.hat = edge('hat', clamp(((RS.brill || 0) + (RS.presence || 0)) * 5, 0, 1), 0.35);
  BT.onset = edge('onset', SV.flux, 0.5);
  BT.off = false;

  SV.beat = beatNow ? 1 : SV.beat * Math.pow(S.bdec / 100, dt * 60);
  SV.phase = mode === 'bpm'
    ? (((((ct + off) * S.bpm) / 60) % 1) + 1) % 1
    : clamp((now - lastBeat) / bGap, 0, 1);

  const sec = now / 1000;
  const q = (sec * S.lfo2) % 1;
  SV.lfo1 = (Math.sin(sec * S.lfo1 * TAU) + 1) / 2;
  SV.lfo2 = q < 0.5 ? q * 2 : 2 - q * 2;
  SV.rnd += (rndV - SV.rnd) * Math.min(1, dt * 9);
}

export function triggerTap() { tapPend = true; }

export function scanBeats(audioBuffer, S) {
  const d = audioBuffer.getChannelData(0);
  const sr = audioBuffer.sampleRate;
  const w = Math.max(1, Math.floor(sr * 0.01));
  const n = Math.floor(d.length / w);
  if (n < 20) return [];
  const a = Math.exp((-TAU * 180) / sr);
  const e = new Float32Array(n);
  let y = 0, mx = 0;
  for (let i = 0; i < n; i++) {
    let s = 0;
    for (let j = 0, o = i * w; j < w; j++) {
      y = a * y + (1 - a) * d[o + j];
      s += y * y;
    }
    e[i] = Math.sqrt(s / w);
    if (e[i] > mx) mx = e[i];
  }
  const f = new Float32Array(n);
  const ps = new Float64Array(n + 1);
  for (let i = 1; i < n; i++) f[i] = Math.max(0, e[i] - e[i - 1]);
  for (let i = 0; i < n; i++) ps[i + 1] = ps[i] + f[i];

  const R = 50;
  const gm = Math.max(10, Math.round(S.bcool / 10));
  const k = (S.beat / 100) * 1.6;
  let last = -99;
  const out = [];
  for (let i = 2; i < n - 2; i++) {
    const a0 = Math.max(0, i - R);
    const b0 = Math.min(n, i + R);
    const m = (ps[b0] - ps[a0]) / (b0 - a0);
    if (f[i] > m * k && e[i] > mx * 0.06 && f[i] >= f[i - 1] && f[i] >= f[i + 1] && i - last >= gm) {
      out.push(i * 0.01);
      last = i;
    }
  }
  return out;
}

export function estimateBPM(beats) {
  if (beats.length < 8) return 0;
  const h = new Float32Array(400);
  for (let i = 1; i < beats.length; i++) {
    for (let k = 1; k <= 3 && i - k >= 0; k++) {
      let b = (60 * k) / (beats[i] - beats[i - k]);
      while (b < 70) b *= 2;
      while (b >= 180) b /= 2;
      h[Math.round(b * 2)] += 1 / k;
    }
  }
  let best = 0, bi = 0;
  for (let i = 1; i < 399; i++) {
    const s = h[i - 1] + h[i] * 2 + h[i + 1];
    if (s > best) { best = s; bi = i; }
  }
  return bi / 2;
}