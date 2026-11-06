import { renderBars, renderCircle, renderMultiRing, renderConcentric, renderRing, renderPulse, renderDots, renderLines, renderBlocks, renderGrid } from './bars.js';
import { renderWave, renderWaveCircular, renderScope } from './wave.js';
import { renderParticles } from './particles.js';
import { renderShape } from './shape.js';
import { renderText } from './text.js';
import { renderImage } from './image.js';
import { renderBackground } from './background.js';
import { renderRgbSplit } from './rgbSplit.js';
import { renderBlurEffect } from './blurEffect.js';
import { renderMotionBlur } from './motionBlur.js';

export const layerRenderers = {
  bars: renderBars,
  barsh: (ctx, W, H, t, eff, layer) => renderBars(ctx, W, H, t, eff, layer, true),
  circle: renderCircle,
  multiring: renderMultiRing,
  concentric: renderConcentric,
  ring: renderRing,
  pulse: renderPulse,
  wave: renderWave,
  wavec: renderWaveCircular,
  scope: renderScope,
  dots: renderDots,
  lines: renderLines,
  blocks: renderBlocks,
  grid: renderGrid,
  particles: renderParticles,
  shape: renderShape,
  text: renderText,
  image: renderImage,
  rgbSplit: renderRgbSplit,
  blurEffect: renderBlurEffect,
  motionBlur: renderMotionBlur,
  bg: renderBackground
};