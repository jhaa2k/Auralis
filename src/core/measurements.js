import { clamp } from './constants.js';
import { getMeasurement } from './analyser.js';

export const CURVES = {
  linear: (x) => x,
  ease_in: (x) => x * x,
  ease_out: (x) => 1 - (1 - x) * (1 - x),
  smooth: (x) => x * x * (3 - 2 * x),
  sqrt: Math.sqrt,
  step: (x) => (x > 0.5 ? 1 : 0)
};

export const RULE_DEFAULTS = {
  on: 1, src: 'bass', prop: 'sy',
  i0: 0, i1: 1, o0: 100, o1: 130,
  cv: 'linear', inv: 0, att: 60, rel: 15
};

export function applyRules(layer, eff) {
  if (!layer.rules || !layer.rules.length) return;
  for (const r of layer.rules) {
    if (!r.on) continue;
    let x = getMeasurement(r.src) || 0;
    x = clamp((x - r.i0) / Math.max(0.001, r.i1 - r.i0), 0, 1);
    x = CURVES[r.cv](x);
    if (r.inv) x = 1 - x;

    const prev = r._v || 0;
    const resp = x > prev ? r.att / 100 : r.rel / 100;
    const smoothed = prev + (x - prev) * resp;
    r._v = smoothed;

    eff[r.prop] = r.o0 + (r.o1 - r.o0) * smoothed;
  }
}