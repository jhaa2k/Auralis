export function renderImage(ctx, W, H, t, e, layer) {
  const img = layer._img;
  if (!img || !img.width) {
    ctx.strokeStyle = '#ff453a'; ctx.lineWidth = 2;
    ctx.strokeRect(-H * 0.15, -H * 0.1, H * 0.3, H * 0.2);
    ctx.fillStyle = '#ff453a'; ctx.font = '12px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText('Upload Image', 0, 0);
    return;
  }
  const w = (W * 0.5 * (e.maxH || 100)) / 100;
  const h = e.imgKeepAspect !== 0 ? (w * img.height) / img.width : (H * 0.5 * (e.maxH || 100)) / 100;

  ctx.save();
  if (e.imgBlurEnabled) ctx.filter = `${ctx.filter === 'none' ? '' : ctx.filter + ' '}blur(${e.imgBlurRadius}px)`;
  if (e.imgMask === 'circle') { ctx.beginPath(); ctx.arc(0, 0, Math.min(w, h) / 2, 0, Math.PI * 2); ctx.clip(); }
  if (e.imgSaturation !== 100) ctx.filter = `${ctx.filter === 'none' ? '' : ctx.filter + ' '}saturate(${e.imgSaturation}%)`;
  
  ctx.drawImage(img, -w / 2, -h / 2, w, h);
  ctx.restore();
}