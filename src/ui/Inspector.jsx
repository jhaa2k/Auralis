import { useStore } from '../core/projectStore.js';
import PropertyField from './PropertyField.jsx';
import MeasurementEditor from './MeasurementEditor.jsx';

const BASIC_SECTIONS = [
  ['Transform', [
    ['x', 'X %', 'r', 0, 100, 0.1], ['y', 'Y %', 'r', 0, 100, 0.1],
    ['sx', 'Scale X', 'r', 5, 400, 1], ['sy', 'Scale Y', 'r', 5, 400, 1],
    ['skx', 'Skew X °', 'r', -60, 60, 0.5], ['sky', 'Skew Y °', 'r', -60, 60, 0.5],
    ['uni', 'Uniform', 'k', 0, 0, 0], ['op', 'Opacity', 'r', 0, 100, 1], ['blend', 'Blend', 's', 0, 0, 0]
  ]],
  ['Rotate', [
    ['rmode', 'Mode', 's', 0, 0, 0], ['rot', 'Angle °', 'r', -360, 360, 1], ['spin', 'Spin °/s', 'r', -720, 720, 1],
    ['rbeat', 'Beat kick °', 'r', -180, 180, 1], ['raud', 'Audio twist', 'r', -360, 360, 1],
    ['rpx', 'Pivot X %', 'r', -50, 50, 0.5], ['rpy', 'Pivot Y %', 'r', -50, 50, 0.5], ['flx', 'Flip H', 'k', 0, 0, 0], ['fly', 'Flip V', 'k', 0, 0, 0]
  ]],
  ['Audio', [
    ['band', 'Range', 's', 0, 0, 0], ['f0', 'Min Hz', 'r', 20, 20000, 1], ['f1', 'Max Hz', 'r', 20, 20000, 1],
    ['fsc', 'Scale', 's', 0, 0, 0], ['sens', 'Sensitivity', 'r', 0, 400, 1], ['att', 'Attack', 'r', 1, 100, 1], ['rel', 'Release', 'r', 1, 100, 1]
  ]],
  ['Colour', [
    ['color', 'Colour', 'c', 0, 0, 0], ['color2', 'Colour 2', 'c', 0, 0, 0],
    ['three', 'Three stops', 'k', 0, 0, 0], ['color3', 'Colour 3', 'c', 0, 0, 0],
    ['grad', 'Gradient', 's', 0, 0, 0], ['gAng', 'Angle', 'r', 0, 360, 1], ['gAnim', 'Rotate °/s', 'r', -180, 180, 1],
    ['beatColor', 'Beat Color', 's', 0, 0, 0]
  ]],
  ['Effects', [
    ['glow', 'Glow', 'r', 0, 80, 1], ['shOff', 'Drop shadow', 'r', 0, 40, 1], ['blur', 'Blur', 'r', 0, 30, 0.5],
    ['hue', 'Hue', 'r', -180, 180, 1], ['bright', 'Brightness', 'r', 0, 200, 1], ['contr', 'Contrast', 'r', 0, 200, 1], ['sat', 'Saturation', 'r', 0, 200, 1]
  ]],
  ['Repeat', [
    ['rep', 'Copies', 'r', 1, 16, 1], ['repang', 'Angle step °', 'r', -180, 180, 1],
    ['repox', 'Offset X %', 'r', -30, 30, 0.5], ['repoy', 'Offset Y %', 'r', -30, 30, 0.5],
    ['repsc', 'Scale step %', 'r', -50, 50, 1], ['repfade', 'Fade per copy %', 'r', 0, 50, 1]
  ]],
  ['Motion', [
    ['mm', 'Path', 's', 0, 0, 0], ['mspd', 'Speed', 'r', 0, 5, 0.05], ['mrx', 'Range X %', 'r', 0, 50, 0.5], ['mry', 'Range Y %', 'r', 0, 50, 0.5],
    ['mbp', 'Beat push %', 'r', 0, 30, 0.5], ['bsel', 'Beat trigger', 's', 0, 0, 0],
    ['bk', 'Beat scale %', 'r', 0, 100, 1], ['bop', 'Beat opacity %', 'r', 0, 100, 1], ['bgl', 'Beat glow', 'r', 0, 80, 1], ['bkd', 'Kick decay', 'r', 1, 30, 1]
  ]],
  ['Animation', [['anim', 'Animation', 's', 0, 0, 0], ['as', 'Speed', 'r', 0.1, 8, 0.1], ['aa', 'Amount', 'r', 0, 100, 1]]]
];

const TYPE_SECTIONS = {
  bars: [
    ['Bars', [
      ['n', 'Count', 'r', 2, 256, 1], ['thick', 'Thickness', 'r', 5, 100, 1], ['round', 'Corner %', 'r', 0, 50, 1],
      ['maxH', 'Length %', 'r', 5, 200, 1], ['dir', 'Direction', 's', 0, 0, 0], ['mir', 'Mirror', 's', 0, 0, 0],
      ['rvar', 'Random variation', 'r', 0, 100, 1], ['flipInput', 'Flip input', 'k', 0, 0, 0],
      ['heightScale', 'Height scale', 'r', 0, 200, 1], ['minHeightScale', 'Min height', 'r', 0, 200, 1], ['maxHeightScale', 'Max height', 'r', 0, 200, 1]
    ]],
    ['Segment 1', [
      ['seg1Color', 'Color', 'c', 0, 0, 0], ['seg1Fixed', 'Fixed height', 'r', 0, 200, 1],
      ['seg1Mul', 'Bar height multiplier', 'r', 0, 200, 1], ['seg1Mirror', 'Mirror', 'k', 0, 0, 0], ['seg1Flip', 'Flip every other', 'k', 0, 0, 0]
    ]],
    ['Segment 2', [
      ['seg2Color', 'Color', 'c', 0, 0, 0], ['seg2Fixed', 'Fixed height', 'r', 0, 200, 1],
      ['seg2Mul', 'Bar height multiplier', 'r', 0, 200, 1], ['seg2Mirror', 'Mirror', 'k', 0, 0, 0], ['seg2Flip', 'Flip every other', 'k', 0, 0, 0]
    ]],
    ['Behaviour', [['softness', 'Softness', 'r', 0, 100, 1], ['reactDelay', 'Reaction delay', 'r', 0, 100, 1], ['reactAcc', 'Reaction accumulated delay', 'r', 0, 100, 1]]]
  ],
  circle: [['Radial', [
    ['n', 'Count', 'r', 2, 256, 1], ['thick', 'Thickness', 'r', 5, 100, 1], ['r0', 'Inner radius %', 'r', 0, 95, 1],
    ['maxH', 'Length %', 'r', 5, 200, 1], ['a0', 'Start angle', 'r', -180, 360, 1], ['a1', 'End angle', 'r', -180, 540, 1],
    ['odir', 'Growth', 's', 0, 0, 0], ['mir', 'Mirror', 's', 0, 0, 0]
  ]]],
  particles: [
    ['Emission', [
      ['n', 'Spawn per beat', 'r', 1, 100, 1], ['countLimit', 'Count limit', 'r', 1, 1000, 1],
      ['spawnTime', 'Spawn time', 'r', 0, 10, 0.1], ['lifetime', 'Lifetime s', 'r', 0, 20, 0.1]
    ]],
    ['Motion', [
      ['speed', 'Speed', 'r', 0, 20, 0.1], ['speedRandom', 'Speed random', 'r', 0, 10, 0.1],
      ['velocityAngle', 'Velocity angle', 'r', 0, 360, 1], ['gravity', 'Gravity', 'r', -20, 20, 0.1],
      ['forceField', 'Force field', 'r', -100, 100, 1]
    ]],
    ['Appearance', [
      ['thick', 'Size', 'r', 5, 100, 1], ['particleScale', 'Particle scale', 'r', 0, 200, 1],
      ['startSize', 'Start size', 'r', 0, 50, 0.5], ['endSize', 'End size', 'r', 0, 50, 0.5],
      ['fadeInOutTime', 'Fade in/out', 'r', 0, 10, 0.1], ['trailLength', 'Trail length', 'r', 0, 100, 1],
      ['colorFrom', 'Color from', 'c', 0, 0, 0], ['colorTo', 'Color to', 'c', 0, 0, 0],
      ['customImg', 'Custom PNG', 'img', 0, 0, 0]
    ]],
    ['Wave Behaviour', [
      ['sideSineFreq', 'Sine freq', 'r', 0, 20, 0.1], ['sideSineMag', 'Sine magnitude', 'r', 0, 50, 0.5],
      ['sideSineFreqRandom', 'Freq random', 'r', 0, 20, 0.1], ['sideSineMagRandom', 'Mag random', 'r', 0, 50, 0.5]
    ]]
  ],
  text: [
    ['Text', [
      ['text', 'Text (| = newline)', 't', 0, 0, 0], ['font', 'Font', 's', 0, 0, 0],
      ['fz', 'Font size', 'r', 2, 60, 0.5], ['fw', 'Weight', 'r', 100, 900, 100],
      ['fi', 'Italic', 'k', 0, 0, 0], ['up', 'Uppercase', 'k', 0, 0, 0],
      ['ls', 'Letter spacing', 'r', -5, 40, 0.5], ['tlh', 'Line height %', 'r', 60, 250, 1], ['al', 'Align', 's', 0, 0, 0]
    ]],
    ['Text Effects', [
      ['tbeat', 'Beat size pulse %', 'r', 0, 150, 1], ['tty', 'Typewriter chars/s', 'r', 0, 30, 0.5],
      ['sw', 'Outline', 'r', 0, 20, 0.5], ['sc', 'Outline colour', 'c', 0, 0, 0]
    ]],
    ['Text Box', [
      ['tbox', 'Background box', 'k', 0, 0, 0], ['tbc', 'Box colour', 'c', 0, 0, 0],
      ['tbo', 'Box opacity', 'r', 0, 100, 1], ['tpad', 'Box padding', 'r', 0, 60, 1], ['trad', 'Box corners', 'r', 0, 60, 1]
    ]]
  ],
  image: [
    ['Image', [['maxH', 'Size %', 'r', 5, 200, 1], ['imgMask', 'Mask', 's', 0, 0, 0], ['imgKeepAspect', 'Keep aspect', 'k', 0, 0, 0], ['upload', 'Custom Image', 'img', 0, 0, 0]]],
    ['Image Colour', [
      ['imgColorKeyEnabled', 'Colour key', 'k', 0, 0, 0], ['imgColorKey', 'Colour key', 'c', 0, 0, 0],
      ['imgTransparencyStrength', 'Transparency strength', 'r', 0, 100, 1],
      ['imgSaturation', 'Saturation', 'r', 0, 200, 1], ['imgColor', 'Tint colour', 'c', 0, 0, 0], ['imgColorTo', 'Tint to', 'c', 0, 0, 0]
    ]],
    ['Image Blur', [['imgBlurEnabled', 'Blur enabled', 'k', 0, 0, 0], ['imgBlurRadius', 'Blur radius', 'r', 0, 40, 1]]]
  ],
  bg: [
    ['Background', [
      ['color', 'Colour', 'c', 0, 0, 0], ['color2', 'Colour 2', 'c', 0, 0, 0],
      ['three', 'Three stops', 'k', 0, 0, 0], ['color3', 'Colour 3', 'c', 0, 0, 0],
      ['grad', 'Gradient', 's', 0, 0, 0], ['gAng', 'Angle', 'r', 0, 360, 1], ['gAnim', 'Rotate °/s', 'r', -180, 180, 1],
      ['upload', 'Custom BG Image', 'img', 0, 0, 0]
    ]]
  ]
};

export default function Inspector() {
  const layers = useStore((s) => s.layers);
  const selectedId = useStore((s) => s.selectedId);
  const updateLayer = useStore((s) => s.updateLayer);

  const layer = layers.find((l) => l._id === selectedId);
  if (!layer) return <div className="dm">No layer selected.</div>;

  const isBg = layer.type === 'bg';
  const needsAudio = !(isBg || layer.type === 'image' || layer.type === 'text');

  const handle = (key, val) => {
    if (layer.lock) return;
    updateLayer(layer._id, { [key]: val });
  };

  return (
    <div className={layer.lock ? 'lk' : ''}>
      <div className="it" style={{ marginBottom: 8 }}>
        <input type="text" value={layer.name} onChange={(e) => handle('name', e.target.value)} style={{ flex: '1 1 auto', minWidth: 0, minHeight: 40, background: 'transparent', padding: '0 14px', fontWeight: 600, fontSize: 13, border: 0, color: 'var(--tx)' }} />
      </div>
      {BASIC_SECTIONS.map(([sec, fields]) => {
        if (sec === 'Audio' && !needsAudio) return null;
        if (sec === 'Colour' && layer.type === 'image') return null;
        return (
          <details key={sec}>
            <summary>{sec}</summary>
            <div>
              {fields.map((f) => <PropertyField key={f[0]} def={f} value={layer[f[0]]} onChange={handle} />)}
            </div>
          </details>
        );
      })}
      {(TYPE_SECTIONS[layer.type] || []).map(([sec, fields]) => (
        <details key={sec}>
          <summary>{sec}</summary>
          <div>
            {fields.map((f) => <PropertyField key={f[0]} def={f} value={layer[f[0]]} onChange={handle} />)}
          </div>
        </details>
      ))}
      <details>
        <summary>Audio reactive mapping</summary>
        <div><MeasurementEditor layer={layer} /></div>
      </details>
    </div>
  );
}