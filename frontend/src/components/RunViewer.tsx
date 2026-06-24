import { useRunPoller } from '../hooks/useRunPoller';
import { RunStatusBadge } from './RunStatusBadge';
import { WorkflowCanvas } from './WorkflowCanvas';
import { Loader2 } from 'lucide-react';

interface RunViewerProps {
  runId: string;
  workflowDefinition: object;
}

export function RunViewer({ runId, workflowDefinition }: RunViewerProps) {
  const { run, error } = useRunPoller(runId);

  return (
    <div className="flex flex-col gap-4 h-full">
      <div className="flex items-center gap-3 flex-wrap">
        <span className="text-xs font-mono text-slate-500">{runId}</span>
        {run ? (
          <RunStatusBadge status={run.status} />
        ) : (
          <Loader2 size={16} className="animate-spin text-slate-400" />
        )}
        {run?.currentStepId && (
          <span className="text-xs text-slate-400">
            Current step:{' '}
            <span className="font-mono text-blue-300">{run.currentStepId}</span>
          </span>
        )}
      </div>

      {error && (
        <div className="text-red-400 text-sm bg-red-950 border border-red-800 rounded-lg px-3 py-2">
          {error}
        </div>
      )}

      <div className="flex-1 rounded-xl overflow-hidden border border-slate-700 min-h-[300px]">
        <WorkflowCanvas
          definition={workflowDefinition}
          activeStepId={run?.currentStepId}
        />
      </div>

      {run && Object.keys(run.outputs ?? {}).length > 0 && (
        <div className="bg-slate-900 border border-slate-700 rounded-xl p-4">
          <p className="text-xs font-semibold text-slate-400 uppercase mb-2">
            Step Outputs
          </p>
          <pre className="text-xs text-slate-300 font-mono overflow-auto max-h-40">
            {JSON.stringify(run.outputs, null, 2)}
          </pre>
        </div>
      )}

      {run?.errorMessage && (
        <div className="bg-red-950 border border-red-800 rounded-xl p-4">
          <p className="text-xs font-semibold text-red-400 uppercase mb-1">Error</p>
          <pre className="text-xs text-red-300 font-mono">{run.errorMessage}</pre>
        </div>
      )}
    </div>
  );
}
