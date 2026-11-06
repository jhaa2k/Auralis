import { getMeasurement, getBeatTriggered } from './analyser.js';
import { applyRules } from './measurements.js';
import { layerRenderers } from '../elements/registry.js';
import { TAU, clamp } from './constants.js';

const smoothstep = (x) => x * x * (3 - 2 * x);

export function renderFrame(ctx, W, H, t, state) {
  const { layers, scene } = state;
  const u = H / 720;
  
  ctx.globalAlpha = 1; 
  ctx.globalCompositeOperation = 'source-over'; 
  ctx.filter = 'none';
  ctx.fillStyle = scene.bg; 
  ctx.fillRect(0, 0, W, H);

  ctx.save();
  const bass = getMeasurement('bass');
  const z = 1 + bass * (scene.cam / 100);
  const shakeAmt = scene.shake * getMeasurement('beat') * u * 2;
  ctx.translate(W / 2 + (scene.shake ? (Math.random() - 0.5) * shakeAmt : 0), H / 2 + (scene.shake ? (Math.random() - 0.5) * shakeAmt : 0));
  ctx.scale(z, z); 
  ctx.translate(-W / 2, -H / 2);

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

function drawLayer(ctx, W, H, t, eff, layer) {
  const renderer = layerRenderers[eff.type];
  if (!renderer) return;
  const u = H / 720;

  if (eff.rep > 1 && eff.type !== 'bg') {
    for (let r = 0; r < eff.rep; r++) drawLayerSingle(ctx, W, H, t, eff, layer, r, u);
    return;
  }
  drawLayerSingle(ctx, W, H, t, eff, layer, 0, u);
}

function drawLayerSingle(ctx, W, H, t, eff, layer, rep, u) {
  const renderer = layerRenderers[eff.type];
  if (!renderer) return;

  ctx.save();
  ctx.globalAlpha = clamp(eff.op / 100, 0, 1);
  if (rep > 0) ctx.globalAlpha *= Math.max(0, 1 - (rep * eff.repfade) / 100);
  ctx.globalCompositeOperation = eff.blend || 'source-over';
  
  // Filters for the layer
  if (eff.blur || eff.hue || eff.bright !== 100 || eff.contr !== 100 || eff.sat !== 100) {
    ctx.filter = `hue-rotate(${eff.hue || 0}deg) brightness(${eff.bright || 100}%) contrast(${eff.contr || 100}%) saturate(${eff.sat || 100}%) blur(${(eff.blur || 0) * u}px)`;
  } else {
    ctx.filter = 'none';
  }

  if (getBeatTriggered(eff.bsel)) {
    layer._rb = (layer._rb || 0) + (eff.rbeat || 0);
    layer._k = 1;
  }
  layer._rd = (layer._rd || 0) + ((layer._rb || 0) - (layer._rd || 0)) * 0.2;
  layer._k = (layer._k || 0) * Math.max(0, 1 - (eff.bkd || 10) / 40);

  const ts = t * (eff.mspd || 0) * TAU + ((eff.mph || 0) * Math.PI) / 180;
  let dx = 0, dy = 0;
  if (eff.mm === 'drift') { dx = Math.sin(ts) * eff.mrx; dy = Math.cos(ts * 0.7) * eff.mry; }
  else if (eff.mm === 'orbit') { dx = Math.cos(ts) * eff.mrx; dy = Math.sin(ts) * eff.mry; }
  else if (eff.mm === 'figure8') { dx = Math.sin(ts) * eff.mrx; dy = Math.sin(ts * 2) * eff.mry; }
  else if (eff.mm === 'bounce') dy = -Math.abs(Math.sin(ts / 2)) * eff.mry * 2;
  else if (eff.mm === 'shake') { dx = (Math.random() - 0.5) * eff.mrx * eff.mspd; dy = (Math.random() - 0.5) * eff.mry * eff.mspd; }

  const kk = 1 + (layer._k || 0) * (eff.bk || 0) / 100;
  if (eff.bop) ctx.globalAlpha = clamp(ctx.globalAlpha + (layer._k || 0) * eff.bop / 100, 0, 1);
  if (eff.bgl) eff.glow = (eff.glow || 0) + (layer._k || 0) * eff.bgl;

  const repAngle = rep * (eff.repang || 0);
  const repX = rep * (eff.repox || 0);
  const repY = rep * (eff.repoy || 0);
  const repScale = 1 + rep * (eff.repsc || 0) / 100;

  ctx.translate((eff.x + dx + repX) / 100 * W, (eff.y + dy + repY - (layer._k || 0) * eff.mbp) / 100 * H);

  const pvx = (eff.rpx || 0) / 100 * W;
  const pvy = (eff.rpy || 0) / 100 * H;
  ctx.translate(pvx, pvy);
  const angle = computeRotation(eff, t, layer) + repAngle;
  ctx.rotate((angle * Math.PI) / 180);
  ctx.translate(-pvx, -pvy);
  ctx.scale(
    (eff.sx * kk * repScale) / 100 * (eff.flx ? -1 : 1),
    (eff.sy * kk * repScale) / 100 * (eff.fly ? -1 : 1)
  );
  if (eff.skx || eff.sky) {
    ctx.transform(1, Math.tan((eff.sky * Math.PI) / 180), Math.tan((eff.skx * Math.PI) / 180), 1, 0, 0);
  }

  if (eff.shOff) {
    ctx.shadowColor = '#000';
    ctx.shadowOffsetY = eff.shOff * u;
    ctx.shadowBlur = Math.max(eff.glow, 6) * u;
  } else if (eff.glow) {
    ctx.shadowColor = eff.color;
    ctx.shadowBlur = eff.glow * u;
  }

  renderer(ctx, W, H, t, eff, layer);
  ctx.restore();
}

function computeRotation(eff, t, layer) {
  let a = eff.rot + (eff.rph || 0);
  const m = eff.rmode;
  const hr = (k) => {
    const x = Math.sin(k * 127.1 + 311.7) * 43758.5453;
    return x - Math.floor(x);
  };
  if (m === 'continuous') a += (eff.spin || 0) * t;
  else if (m === 'pendulum') a += eff.rmin + (eff.rmax - eff.rmin) * smoothstep((Math.sin(t * eff.rspd * TAU) + 1) / 2);
  else if (m === 'steps') {
    const k = Math.floor(t * eff.rspd);
    const f = t * eff.rspd - k;
    a += (k + smoothstep(Math.min(1, f * 3))) * eff.rstep;
  } else if (m === 'random') {
    const k = Math.floor(t * eff.rspd);
    const f = t * eff.rspd - k;
    const A = Math.max(Math.abs(eff.rmin), Math.abs(eff.rmax));
    const r1 = (hr(k) * 2 - 1) * A;
    const r0 = (hr(k - 1) * 2 - 1) * A;
    a += r0 + (r1 - r0) * smoothstep(Math.min(1, f * 3));
  }
  if (layer) a += (layer._rd || 0);
  if (eff.raud) a += (getMeasurement(eff.rsrc) || 0) * eff.raud;
  return a;
}

function drawSceneEffects(ctx, W, H, u, scene) {
  if (scene.vig) {
    const g = ctx.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.3, W / 2, H / 2, Math.hypot(W, H) / 2);
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(1, `rgba(0,0,0,${scene.vig / 100})`);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
  }
  if (scene.scan) {
    ctx.fillStyle = `rgba(0,0,0,${(scene.scan / 100) * 0.5})`;
    ctx.beginPath();
    for (let y = 0; y < H; y += 4 * u) ctx.rect(0, y, W, u);
    ctx.fill();
  }
  if (scene.grain) {
    for (let c = 0; c < 2; c++) {
      ctx.fillStyle = c ? 'rgba(255,255,255,.12)' : 'rgba(0,0,0,.2)';
      ctx.beginPath();
      for (let i = 0; i < scene.grain * 4; i++) {
        ctx.rect(Math.random() * W, Math.random() * H, 2 * u, 2 * u);
      }
      ctx.fill();
    }
  }
  const beat = getMeasurement('beat');
  if (scene.flash && beat > 0.02) {
    ctx.fillStyle = `rgba(255,255,255,${(beat * scene.flash) / 100 * 0.5})`;
    ctx.fillRect(0, 0, W, H);
  }
}