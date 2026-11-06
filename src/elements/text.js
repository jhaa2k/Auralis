import { bv, fillFor } from './bars.js';

export function renderText(ctx, W, H, t, e) {
  const v = bv(e, 0, 1);
  const px = (H * e.fz / 100) * (1 + (v * (e.tbeat || 0)) / 100);
  let tx = String(e.text || '');
  if (e.up) tx = tx.toUpperCase();
  if (e.tty > 0) {
    const nn = tx.length + 14;
    tx = tx.slice(0, Math.min(Math.floor(t * e.tty) % nn, tx.length));
  }
  const lines = tx.split('|');
  const lh = (px * (e.tlh || 100)) / 100;

  ctx.font = `${e.fi ? 'italic ' : ''}${e.fw} ${px}px "${e.font}",sans-serif`;
  if ('letterSpacing' in ctx) ctx.letterSpacing = `${e.ls * (H / 720)}px`;
  ctx.textAlign = e.al;
  ctx.textBaseline = 'middle';

  const y0 = -((lines.length - 1) * lh) / 2;

  if (e.tbox && tx) {
    const bw = Math.max(...lines.map((q) => ctx.measureText(q).width));
    const pd = (e.tpad || 0) * (H / 720);
    const bh = lines.length * lh;
    const bx = e.al === 'left' ? 0 : e.al === 'right' ? -bw : -bw / 2;
    ctx.save();
    ctx.globalAlpha *= e.tbo / 100;
    ctx.fillStyle = e.tbc;
    ctx.shadowBlur = 0;
    ctx.shadowOffsetY = 0;
    ctx.beginPath();
    if (ctx.roundRect) ctx.roundRect(bx - pd, -bh / 2 - pd / 2, bw + pd * 2, bh + pd, e.trad * (H / 720));
    else ctx.rect(bx - pd, -bh / 2 - pd / 2, bw + pd * 2, bh + pd);
    ctx.fill();
    ctx.restore();
  }

  ctx.fillStyle = fillFor(ctx, e, -W * 0.3, -H * 0.1, W * 0.6, H * 0.2, t);
  lines.forEach((q, k) => {
    ctx.fillText(q, 0, y0 + k * lh);
    if (e.sw) {
      ctx.strokeStyle = e.sc;
      ctx.lineWidth = e.sw * (H / 720);
      ctx.strokeText(q, 0, y0 + k * lh);
    }
  });
}