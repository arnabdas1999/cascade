import { useState } from 'react';
import type { WorkflowDefinition } from './api';
import { WorkflowListPage } from './pages/WorkflowListPage';
import { WorkflowDetailPage } from './pages/WorkflowDetailPage';

export default function App() {
  const [selected, setSelected] = useState<WorkflowDefinition | null>(null);

  if (selected) {
    return (
      <WorkflowDetailPage
        workflow={selected}
        onBack={() => setSelected(null)}
      />
    );
  }

  return <WorkflowListPage onSelect={setSelected} />;
}
