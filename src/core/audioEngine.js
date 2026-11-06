import { clamp } from './constants.js';

let ac = null;
let pre, vg, dest, pan, analyser;
let shaper, shaperDry, shaperWet, shaperMix;
let delay, delayFeedback, delayDry, delayWet, delayStage;
let convolver, reverbDry, reverbWet, reverbStage;
let bassShelf;
let eqFilters = [];
let splitter, analyserL, analyserR;

let buffer = null;
let sourceNode = null;
let paused = true;
let offset = 0;
let startTime = 0;
let rate = 1;
let onEnded = () => {};

export const EQ_FREQS = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];

export function initAudio() {
  if (ac) return true;
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) return false;
  ac = new Ctx();

  pre = ac.createGain();
  let chain = pre;

  // EQ
  eqFilters = EQ_FREQS.map((f, i) => {
    const b = ac.createBiquadFilter();
    b.type = i === 0 ? 'lowshelf' : i === 9 ? 'highshelf' : 'peaking';
    b.frequency.value = f;
    b.Q.value = 1;
    chain.connect(b);
    chain = b;
    return b;
  });

  bassShelf = ac.createBiquadFilter();
  bassShelf.type = 'lowshelf';
  bassShelf.frequency.value = 120;
  chain.connect(bassShelf);

  // Distortion
  shaper = ac.createWaveShaper();
  shaperDry = ac.createGain();
  shaperWet = ac.createGain();
  shaperWet.gain.value = 0;
  shaperMix = ac.createGain();
  bassShelf.connect(shaperDry).connect(shaperMix);
  bassShelf.connect(shaper).connect(shaperWet).connect(shaperMix);

  // Delay
  delay = ac.createDelay(2);
  delay.delayTime.value = 0.32;
  delayFeedback = ac.createGain();
  delayFeedback.gain.value = 0.4;
  delay.connect(delayFeedback).connect(delay);
  delayDry = ac.createGain();
  delayWet = ac.createGain();
  delayWet.gain.value = 0;
  delayStage = ac.createGain();
  shaperMix.connect(delayDry).connect(delayStage);
  shaperMix.connect(delay).connect(delayWet).connect(delayStage);

  // Reverb
  convolver = ac.createConvolver();
  const len = ac.sampleRate * 2;
  const ir = ac.createBuffer(2, len, ac.sampleRate);
  for (let c = 0; c < 2; c++) {
    const d = ir.getChannelData(c);
    for (let i = 0; i < len; i++) {
      d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2.5);
    }
  }
  convolver.buffer = ir;
  reverbDry = ac.createGain();
  reverbWet = ac.createGain();
  reverbWet.gain.value = 0;
  reverbStage = ac.createGain();
  delayStage.connect(reverbDry).connect(reverbStage);
  delayStage.connect(convolver).connect(reverbWet).connect(reverbStage);

  // Panning
  pan = ac.createStereoPanner ? ac.createStereoPanner() : ac.createGain();
  reverbStage.connect(pan);

  // Analyser for Visuals
  analyser = ac.createAnalyser();
  analyser.fftSize = 2048;
  analyser.smoothingTimeConstant = 0.5;

  // Main Output Gain
  vg = ac.createGain();

  // ** MEDIA STREAM DESTINATION FOR EXPORT **
  dest = ac.createMediaStreamDestination();

  // Connect graph
  pan.connect(analyser);
  analyser.connect(vg);
  vg.connect(ac.destination); // To speakers
  pan.connect(dest);           // To MediaRecorder for export

  // Left/Right Channel Splitting
  splitter = ac.createChannelSplitter(2);
  analyserL = ac.createAnalyser();
  analyserR = ac.createAnalyser();
  analyserL.fftSize = 512;
  analyserR.fftSize = 512;
  pan.connect(splitter);
  splitter.connect(analyserL, 0);
  splitter.connect(analyserR, 1);

  return true;
}

export function getContext() { return ac; }
export function getAnalyser() { return analyser; }
export function getAnalyserLR() { return { L: analyserL, R: analyserR }; }
export function getDestinationStream() { return dest ? dest.stream : null; }

export function loadBuffer(audioBuffer) {
  stop();
  buffer = audioBuffer;
  offset = 0;
  return buffer.duration;
}

export function getDuration() { return buffer ? buffer.duration : 0; }

export function getCurrentTime() {
  if (!buffer || !ac) return 0;
  if (paused) return clamp(offset, 0, buffer.duration);
  return clamp(offset + (ac.currentTime - startTime) * rate, 0, buffer.duration);
}

export function seek(t) {
  if (!buffer) return;
  const wasPlaying = !paused;
  stop();
  offset = clamp(t, 0, buffer.duration - 0.05);
  if (wasPlaying) play();
}

export function setRate(r) {
  if (!paused && ac) {
    offset = getCurrentTime();
    startTime = ac.currentTime;
  }
  rate = r;
  if (sourceNode) sourceNode.playbackRate.value = r;
}

export function play() {
  if (!buffer || !paused || !ac) return;
  ac.resume();
  sourceNode = ac.createBufferSource();
  sourceNode.buffer = buffer;
  sourceNode.playbackRate.value = rate;
  sourceNode.connect(pre);
  sourceNode.onended = () => {
    if (sourceNode) {
      stop();
      offset = 0;
      onEnded();
    }
  };
  sourceNode.start(0, offset);
  startTime = ac.currentTime;
  paused = false;
}

export function pause() {
  if (paused) return;
  offset = getCurrentTime();
  stop();
}

export function stop() {
  const n = sourceNode;
  sourceNode = null;
  paused = true;
  if (n) {
    n.onended = null;
    try { n.stop(); } catch {}
    n.disconnect();
  }
}

export function setOnEnded(fn) { onEnded = fn; }
export function isPaused() { return paused; }

export function applyAudioSettings(s) {
  if (!ac) return;
  const now = ac.currentTime;
  const sp = (param, v) => param.setTargetAtTime(v, now, 0.02);

  sp(pre.gain, Math.pow(10, s.pre / 20));
  sp(bassShelf.gain, s.bass);
  if (pan.pan) pan.pan.value = s.bal / 100;
  sp(vg.gain, s.vol / 100);

  shaperWet.gain.value = s.dist / 100;
  shaperDry.gain.value = 1 - s.dist / 200;
  if (s.dist > 0) {
    const k = 1 + s.dist * 0.8;
    const curve = new Float32Array(2048);
    for (let i = 0; i < 2048; i++) {
      const x = i / 1024 - 1;
      curve[i] = Math.tanh(k * x) / Math.tanh(k);
    }
    shaper.curve = curve;
  }

  delayWet.gain.value = s.dly / 100;
  delayDry.gain.value = 1;
  reverbWet.gain.value = s.rev / 100;

  eqFilters.forEach((f, i) => sp(f.gain, s.eq[i] || 0));
  setRate(s.speed / 100);

  if (analyser.fftSize !== +s.fft) analyser.fftSize = +s.fft;
  analyser.smoothingTimeConstant = s.smooth / 100;
}