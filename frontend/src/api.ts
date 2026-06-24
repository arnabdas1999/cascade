const BASE = '/api';

export interface WorkflowDefinition {
  id: string;
  name: string;
  definition: object;
  createdAt: string;
}

export interface RunResponse {
  runId: string;
  workflowId: string;
  status: RunStatus;
  currentStepId: string | null;
  outputs: Record<string, unknown>;
  errorMessage: string | null;
  attemptCount: number;
  createdAt: string;
  updatedAt: string;
}

export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'WAITING'
  | 'COMPLETED'
  | 'FAILED'
  | 'COMPENSATING'
  | 'COMPENSATED';

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  async listWorkflows(): Promise<WorkflowDefinition[]> {
    return json(await fetch(`${BASE}/workflows`));
  },

  async createWorkflow(name: string, definition: object): Promise<WorkflowDefinition> {
    return json(
      await fetch(`${BASE}/workflows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, definition }),
      }),
    );
  },

  async triggerRun(
    workflowId: string,
    inputs: Record<string, unknown> = {},
  ): Promise<{ runId: string; status: string }> {
    return json(
      await fetch(`${BASE}/workflows/${workflowId}/runs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ inputs }),
      }),
    );
  },

  async listRuns(workflowId: string): Promise<RunResponse[]> {
    return json(await fetch(`${BASE}/workflows/${workflowId}/runs`));
  },

  async getRun(runId: string): Promise<RunResponse> {
    return json(await fetch(`${BASE}/runs/${runId}`));
  },
};
