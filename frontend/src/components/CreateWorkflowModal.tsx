import { useState } from 'react';
import { X } from 'lucide-react';
import { api } from '../api';

const STARTER_JSON = JSON.stringify(
  {
    root: {
      type: 'sequential',
      id: 'main',
      children: [
        { type: 'delay', id: 'wait', ms: 500 },
        { type: 'transform', id: 'result', expr: "'hello world'" },
      ],
    },
  },
  null,
  2,
);

interface Props {
  onClose: () => void;
  onCreated: () => void;
}

export function CreateWorkflowModal({ onClose, onCreated }: Props) {
  const [name, setName] = useState('');
  const [definitionText, setDefinitionText] = useState(STARTER_JSON);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    setError(null);
    let parsed: object;
    try {
      parsed = JSON.parse(definitionText);
    } catch {
      setError('Invalid JSON in workflow definition.');
      return;
    }
    if (!name.trim()) {
      setError('Workflow name is required.');
      return;
    }
    setSaving(true);
    try {
      await api.createWorkflow(name.trim(), parsed);
      onCreated();
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
      <div className="bg-slate-900 border border-slate-700 rounded-xl w-[640px] max-h-[90vh] flex flex-col shadow-2xl">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-700">
          <h2 className="text-lg font-semibold text-white">New Workflow</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X size={20} />
          </button>
        </div>

        <div className="p-6 flex flex-col gap-4 overflow-y-auto flex-1">
          <div>
            <label className="text-sm text-slate-400 mb-1 block">Name</label>
            <input
              className="w-full bg-slate-800 border border-slate-600 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-blue-500"
              placeholder="e.g. Order Processing"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="flex-1">
            <label className="text-sm text-slate-400 mb-1 block">
              Definition (JSON)
            </label>
            <textarea
              className="w-full h-72 bg-slate-800 border border-slate-600 rounded-lg px-3 py-2 text-white text-xs font-mono focus:outline-none focus:border-blue-500 resize-none"
              value={definitionText}
              onChange={(e) => setDefinitionText(e.target.value)}
              spellCheck={false}
            />
          </div>

          {error && (
            <p className="text-red-400 text-sm bg-red-950 border border-red-800 rounded-lg px-3 py-2">
              {error}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-slate-700">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm text-slate-300 hover:text-white hover:bg-slate-700"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="px-4 py-2 rounded-lg text-sm bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Create Workflow'}
          </button>
        </div>
      </div>
    </div>
  );
}
