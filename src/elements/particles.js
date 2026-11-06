import { getBeatTriggered, getMeasurement } from '../core/analyser.js';
import { TAU } from '../core/constants.js';

export function renderParticles(ctx, W, H, t, e) {
  const u = H / 720;
  const P = e._p = e._p || [];
  const v = getMeasurement(e.band === 'all' ? 'volume' : e.band);
  const cap = e.countLimit || 400;

  if (getBeatTriggered(e.bsel)) {
    const spawnN = Math.max(1, Math.floor((e.n || 10) * (0.5 + v)));
    for (let i = 0; i < Math.min(spawnN, cap - P.length); i++) {
      const a = (e.velocityAngle || Math.random() * 360) * Math.PI / 180 + (e.useRandomVector ? Math.random() * TAU : 0);
      const sp = (e.speed || 2) * (0.5 + Math.random() * (1 + (e.speedRandom || 0))) * u;
      P.push({
        x: 0, y: 0, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp,
        l: 1, life: Math.max(0.3, e.lifetime || 2),
        size: (e.startSize || 2) * (e.particleScale || 100) / 100,
        phase: Math.random() * TAU,
        sineFreq: (e.sideSineFreq || 0) + (Math.random() - 0.5) * (e.sideSineFreqRandom || 0),
        sineMag: (e.sideSineMag || 0) + (Math.random() - 0.5) * (e.sideSineMagRandom || 0)
      });
    }
  }

  const a0 = ctx.globalAlpha;
  const g = e.gravity || 0;
  const z = ((e.thick || 40) / 10) * u;

  for (let i = P.length - 1; i >= 0; i--) {
    const p = P[i];
    p.x += p.vx; p.y += p.vy; p.vy += g * u * 0.03;
    p.l -= 0.012 / p.life;
    if (p.l <= 0) { P.splice(i, 1); continue; }

    if (p.sineMag) p.x += Math.sin(t * p.sineFreq * TAU + p.phase) * p.sineMag * u * 0.3;

    let scale = 1;
    if (e.perspectiveDepth) {
      const d = Math.hypot(p.x, p.y) / (Math.min(W, H) * 0.4);
      scale = 1 + d * (e.perspectiveDepth / 100);
    }

    ctx.globalAlpha = a0 * p.l;
    const zz = Math.max(1, z * p.size * scale);

    if (e._img && e._img.width) {
      ctx.drawImage(e._img, p.x - zz / 2, p.y - zz / 2, zz, zz);
    } else {
      ctx.fillStyle = e.colorFrom || e.color;
      ctx.fillRect(p.x - zz / 2, p.y - zz / 2, zz, zz);
    }
  }
  ctx.globalAlpha = a0;
}