import { getMeasurement, getBeatTriggered } from './analyser.js';
import { applyRules } from './measurements.js';
import { layerRenderers } from '../elements/registry.js';
import { TAU, clamp } from './constants.js';

const smoothstep = (x) => x * x * (3 - 2 * x);

export function renderFrame(ctx, W, H, t, state) {
  const { layers, scene } = state;
  const u = H / 720;
  ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over'; ctx.filter = 'none';
  ctx.fillStyle = scene.bg; ctx.fillRect(0, 0, W, H);

  ctx.save();
  const bass = getMeasurement('bass');
  const z = 1 + bass * (scene.cam / 100);
  const shakeAmt = scene.shake * getMeasurement('beat') * u * 2;
  ctx.translate(W / 2 + (scene.shake ? (Math.random() - 0.5) * shakeAmt : 0), H / 2 + (scene.shake ? (Math.random() - 0.5) * shakeAmt : 0));
  ctx.scale(z, z); ctx.translate(-W / 2, -H / 2);

  for (const layer of layers) {
    if (!layer.vis) continue;
    const eff = prepareLayer(layer, t);
    drawLayer(ctx, W, H, t, eff, layer);
  }
  ctx.restore();
  drawSceneEffects(ctx, W, H, u, scene);
}

function prepareLayer(layer, t) {
  const eff = { ...layer };
  applyRules(layer, eff);

  // Beat Color Processing
  if (eff.beatColor && eff.beatColor !== 'none') {
    const beat = getMeasurement('beat');
    if (beat > 0.1) {
      if (eff.beatColor === 'B&W') {
        const lum = 0.299 * parseInt(eff.color.slice(1,3),16) + 0.587 * parseInt(eff.color.slice(3,5),16) + 0.114 * parseInt(eff.color.slice(5,7),16);
        eff.color = `rgb(${lum},${lum},${lum})`;
        eff.color2 = '#000000';
      } else if (eff.beatColor === 'Invert') {
        eff.hue = (eff.hue || 0) + 180;
      } else if (eff.beatColor === 'Shift Hue') {
        eff.hue = (eff.hue || 0) + 45;
      } else if (eff.beatColor === 'Pulse Alpha') {
        eff.op = Math.min(100, eff.op + beat * 30);
      } else if (eff.beatColor === 'Complement') {
        eff.hue = (eff.hue || 0) + 180;
      }
    }
  }

  const a = (eff.aa || 0) / 100;
  const s = t * (eff.as || 1);
  if (eff.anim === 'pulse') { const k = 1 + Math.sin(s * TAU) * a * 0.3; eff.sx *= k; eff.sy *= k; }
  else if (eff.anim === 'bounce') eff.y -= Math.abs(Math.sin(s * Math.PI)) * a * 12;
  else if (eff.anim === 'sway') eff.x += Math.sin(s * TAU) * a * 10;
  else if (eff.anim === 'fade') eff.op *= 1 - a * (1 + Math.sin(s * TAU)) / 2;
  return eff;
}

// ... [rest of drawLayer, drawLayerSingle, computeRotation, drawSceneEffects is unchanged] ...