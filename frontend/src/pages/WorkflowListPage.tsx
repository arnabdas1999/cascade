import { useState, useEffect } from 'react';
import { Plus, Loader2, GitBranch } from 'lucide-react';
import { api } from '../api';
import type { WorkflowDefinition } from '../api';
import { CreateWorkflowModal } from '../components/CreateWorkflowModal';

interface Props {
  onSelect: (wf: WorkflowDefinition) => void;
}

export function WorkflowListPage({ onSelect }: Props) {
  const [workflows, setWorkflows] = useState<WorkflowDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try {
      setWorkflows(await api.listWorkflows());
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <div className="min-h-screen flex flex-col">
      {/* Nav */}
      <header className="border-b border-slate-700 px-6 py-4 flex items-center gap-3 bg-slate-900/50">
        <GitBranch size={20} className="text-blue-400" />
        <span className="text-white font-semibold text-lg">Cascade</span>
        <span className="text-slate-600 text-sm ml-1">Workflow Engine</span>
        <button
          onClick={() => setShowCreate(true)}
          className="ml-auto flex items-center gap-2 bg-blue-600 hover:bg-blue-500 text-white px-3 py-1.5 rounded-lg text-sm"
        >
          <Plus size={14} /> New Workflow
        </button>
      </header>

      <main className="flex-1 max-w-4xl w-full mx-auto px-6 py-10">
        <h1 className="text-2xl font-semibold text-white mb-6">Workflows</h1>

        {loading && (
          <div className="flex justify-center py-20">
            <Loader2 size={28} className="animate-spin text-slate-500" />
          </div>
        )}

        {error && (
          <div className="text-red-400 text-sm bg-red-950 border border-red-800 rounded-lg px-4 py-3 mb-4">
            {error}
          </div>
        )}

        {!loading && workflows.length === 0 && !error && (
          <div className="text-center py-20 text-slate-500">
            <GitBranch size={48} className="mx-auto mb-4 opacity-30" />
            <p className="text-lg mb-2">No workflows yet</p>
            <p className="text-sm">Create your first workflow to get started.</p>
          </div>
        )}

        <ul className="grid gap-3">
          {workflows.map((wf) => (
            <li key={wf.id}>
              <button
                onClick={() => onSelect(wf)}
                className="w-full text-left bg-slate-900 border border-slate-700 hover:border-blue-500 rounded-xl px-5 py-4 transition-colors group"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-white font-medium group-hover:text-blue-300 transition-colors">
                      {wf.name}
                    </p>
                    <p className="text-xs font-mono text-slate-500 mt-1">{wf.id}</p>
                  </div>
                  <span className="text-xs text-slate-600">
                    {new Date(wf.createdAt).toLocaleDateString()}
                  </span>
                </div>
              </button>
            </li>
          ))}
        </ul>
      </main>

      {showCreate && (
        <CreateWorkflowModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            load();
          }}
        />
      )}
    </div>
  );
}
