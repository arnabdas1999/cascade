import type { RunStatus } from '../api';

const colors: Record<RunStatus, string> = {
  PENDING:      'bg-slate-600 text-slate-200',
  RUNNING:      'bg-blue-700 text-blue-100 animate-pulse',
  WAITING:      'bg-yellow-700 text-yellow-100',
  COMPLETED:    'bg-green-700 text-green-100',
  FAILED:       'bg-red-700 text-red-100',
  COMPENSATING: 'bg-orange-700 text-orange-100 animate-pulse',
  COMPENSATED:  'bg-purple-700 text-purple-100',
};

export function RunStatusBadge({ status }: { status: RunStatus }) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[status]}`}
    >
      {status}
    </span>
  );
}
