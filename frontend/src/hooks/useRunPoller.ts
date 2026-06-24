import { useEffect, useState, useRef } from 'react';
import { api } from '../api';
import type { RunResponse, RunStatus } from '../api';

const TERMINAL: ReadonlySet<RunStatus> = new Set([
  'COMPLETED',
  'FAILED',
  'COMPENSATED',
]);

/**
 * Polls GET /api/runs/{runId} every `intervalMs` until the run reaches a
 * terminal status, then stops automatically.
 */
export function useRunPoller(runId: string | null, intervalMs = 1500) {
  const [run, setRun] = useState<RunResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!runId) return;

    let cancelled = false;

    async function poll() {
      try {
        const r = await api.getRun(runId!);
        if (!cancelled) {
          setRun(r);
          if (TERMINAL.has(r.status)) {
            clearInterval(timerRef.current!);
            timerRef.current = null;
          }
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }

    poll();
    timerRef.current = setInterval(poll, intervalMs);

    return () => {
      cancelled = true;
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
    };
  }, [runId, intervalMs]);

  return { run, error };
}
