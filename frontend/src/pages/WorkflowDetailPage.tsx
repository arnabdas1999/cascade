import { useState, useEffect, useCallback } from 'react';
import { Play, ChevronLeft, Loader2 } from 'lucide-react';
import { api } from '../api';
import type { RunResponse, WorkflowDefinition } from '../api';
import { WorkflowCanvas } from '../components/WorkflowCanvas';
import { RunStatusBadge } from '../components/RunStatusBadge';
import { RunViewer } from '../components/RunViewer';

interface Props {
  workflow: WorkflowDefinition;
  onBack: () => void;
}

export function WorkflowDetailPage({ workflow, onBack }: Props) {
  const [runs, setRuns] = useState<RunResponse[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [triggering, setTriggering] = useState(false);
  const [loadingRuns, setLoadingRuns] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRuns = useCallback(async () => {
    try {
      const r = await api.listRuns(workflow.id);
      setRuns(r);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoadingRuns(false);
    }
  }, [workflow.id]);

  useEffect(() => {
    fetchRuns();
  }, [fetchRuns]);

  async function triggerRun() {
    setTriggering(true);
    setError(null);
    try {
      const { runId } = await api.triggerRun(workflow.id);
      setSelectedRunId(runId);
      await fetchRuns();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setTriggering(false);
    }
  }

  return (
    <div className="flex flex-col h-screen">
      {/* Header */}
      <div className="border-b border-slate-700 px-6 py-3 flex items-center gap-3 bg-slate-900/50">
        <button
          onClick={onBack}
          className="text-slate-400 hover:text-white flex items-center gap-1 text-sm"
        >
          <ChevronLeft size={16} /> Workflows
        </button>
        <span className="text-slate-600">/</span>
        <span className="text-white font-medium">{workflow.name}</span>
        <span className="ml-auto text-xs font-mono text-slate-600">{workflow.id}</span>
        <button
          onClick={triggerRun}
          disabled={triggering}
          className="flex items-center gap-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white px-3 py-1.5 rounded-lg text-sm"
        >
          {triggering ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />}
          Trigger Run
        </button>
      </div>

      {error && (
        <div className="mx-6 mt-3 text-red-400 text-sm bg-red-950 border border-red-800 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar: run list */}
        <div className="w-72 border-r border-slate-700 flex flex-col bg-slate-900/30">
          <div className="px-4 py-3 border-b border-slate-700">
            <p className="text-xs font-semibold text-slate-400 uppercase">Runs</p>
          </div>
          {loadingRuns ? (
            <div className="flex justify-center py-8">
              <Loader2 size={20} className="animate-spin text-slate-500" />
            </div>
          ) : runs.length === 0 ? (
            <p className="text-slate-500 text-sm px-4 py-6 text-center">
              No runs yet. Trigger one above.
            </p>
          ) : (
            <ul className="overflow-y-auto flex-1">
              {[...runs].reverse().map((run) => (
                <li key={run.runId}>
                  <button
                    onClick={() => setSelectedRunId(run.runId)}
                    className={`w-full text-left px-4 py-3 border-b border-slate-800 hover:bg-slate-800 transition-colors ${
                      selectedRunId === run.runId ? 'bg-slate-800' : ''
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <RunStatusBadge status={run.status} />
                      <span className="text-xs text-slate-500">
                        #{run.attemptCount}
                      </span>
                    </div>
                    <p className="text-xs font-mono text-slate-500 truncate">
                      {run.runId}
                    </p>
                    <p className="text-xs text-slate-600 mt-0.5">
                      {new Date(run.createdAt).toLocaleString()}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Main content area */}
        <div className="flex-1 overflow-hidden p-4">
          {selectedRunId ? (
            <RunViewer
              key={selectedRunId}
              runId={selectedRunId}
              workflowDefinition={workflow.definition}
            />
          ) : (
            <div className="h-full rounded-xl overflow-hidden border border-slate-700">
              <WorkflowCanvas definition={workflow.definition} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
