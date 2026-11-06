import { getDestinationStream } from './audioEngine.js';

export function listFormats() {
  if (!window.MediaRecorder) return [];
  const c = [
    ['video/mp4;codecs=avc1,mp4a.40.2', 'MP4 H.264'],
    ['video/webm;codecs=vp9,opus', 'WebM VP9'],
    ['video/webm;codecs=vp8,opus', 'WebM VP8'],
    ['video/webm;codecs=av1,opus', 'WebM AV1']
  ];
  return c.filter((x) => MediaRecorder.isTypeSupported(x[0]));
}

export function startRecord(canvas, settings, onDone, onError) {
  const formats = listFormats();
  if (!formats.length) { onError?.('No supported recording format'); return null; }
  const fmt = formats[settings.fmt] || formats[0];
  let stream;
  try { stream = canvas.captureStream(settings.xfps); }
  catch (e) { onError?.('Capture not supported'); return null; }

  const audioStream = getDestinationStream();
  if (audioStream) audioStream.getAudioTracks().forEach((t) => stream.addTrack(t));

  let rec;
  try {
    rec = new MediaRecorder(stream, {
      mimeType: fmt[0],
      videoBitsPerSecond: settings.mbps * 1e6,
      audioBitsPerSecond: settings.abr * 1000
    });
  } catch (e) { onError?.('Recorder failed: ' + e.message); return null; }

  const chunks = [];
  rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
  rec.onerror = () => onError?.('Recording error');
  rec.onstop = () => {
    const blob = new Blob(chunks, { type: fmt[0].split(';')[0] });
    const ext = fmt[0].includes('mp4') ? 'mp4' : 'webm';
    onDone?.(blob, `audiovis.${ext}`);
  };
  rec.start(1000);
  return rec;
}