import { useRef, useCallback } from 'react';

export function usePointerDrag(onDrag) {
  const stateRef = useRef(null);
  const onPointerDown = useCallback((e) => {
    stateRef.current = { sx: e.clientX, sy: e.clientY };
    e.currentTarget.setPointerCapture?.(e.pointerId);
  }, []);
  const onPointerMove = useCallback((e) => {
    if (!stateRef.current) return;
    const { sx, sy } = stateRef.current;
    onDrag?.(e.clientX - sx, e.clientY - sy, e);
  }, [onDrag]);
  const onPointerUp = useCallback(() => { stateRef.current = null; }, []);
  return { onPointerDown, onPointerMove, onPointerUp, onPointerCancel: onPointerUp };
}