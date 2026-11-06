import { useEffect, useRef } from 'react';

export function useAnimationLoop(cb) {
  const rafRef = useRef(null);
  const cbRef = useRef(cb);
  cbRef.current = cb;

  useEffect(() => {
    const loop = (t) => {
      cbRef.current(t);
      rafRef.current = requestAnimationFrame(loop);
    };
    rafRef.current = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(rafRef.current);
  }, []);
}