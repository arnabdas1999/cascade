import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  BackgroundVariant,
  useNodesState,
  useEdgesState,
} from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import { useMemo } from 'react';

interface StepNode {
  id: string;
  type: string;
  label?: string;
  children?: StepNode[];
}

interface WorkflowCanvasProps {
  definition: object;
  activeStepId?: string | null;
}

function flattenDefinition(
  node: StepNode,
  nodes: Node[],
  edges: Edge[],
  parentId: string | null,
  depthX: number,
  index: number,
) {
  const x = depthX * 220;
  const y = index * 100;

  nodes.push({
    id: node.id,
    position: { x, y },
    data: { label: node.label ?? `[${node.type}] ${node.id}` },
    style: {
      background: typeColor(node.type),
      border: '1px solid #475569',
      borderRadius: 8,
      padding: '8px 14px',
      color: '#e2e8f0',
      fontSize: 13,
      minWidth: 160,
    },
  });

  if (parentId) {
    edges.push({
      id: `${parentId}->${node.id}`,
      source: parentId,
      target: node.id,
      style: { stroke: '#475569' },
    });
  }

  (node.children ?? []).forEach((child, i) =>
    flattenDefinition(child, nodes, edges, node.id, depthX + 1, index + i),
  );
}

function typeColor(type: string) {
  switch (type) {
    case 'sequential': return '#1e3a5f';
    case 'parallel':   return '#1a3a2a';
    case 'http':       return '#2d1b4e';
    case 'delay':      return '#3b2a00';
    case 'transform':  return '#1a2a3a';
    case 'conditional':return '#3a1a2a';
    case 'subworkflow':return '#2a1a3a';
    default:           return '#1e293b';
  }
}

export function WorkflowCanvas({ definition, activeStepId }: WorkflowCanvasProps) {
  const { nodes: initNodes, edges: initEdges } = useMemo(() => {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    const root = (definition as { root?: StepNode }).root;
    if (root) flattenDefinition(root, nodes, edges, null, 0, 0);
    return { nodes, edges };
  }, [definition]);

  const [nodes, , onNodesChange] = useNodesState(
    initNodes.map((n) => ({
      ...n,
      style: {
        ...n.style,
        boxShadow:
          activeStepId === n.id
            ? '0 0 0 2px #38bdf8, 0 0 12px #38bdf866'
            : undefined,
      },
    })),
  );
  const [edges, , onEdgesChange] = useEdgesState(initEdges);

  if (initNodes.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-slate-500 text-sm">
        No workflow definition to display.
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      fitView
      colorMode="dark"
    >
      <Background variant={BackgroundVariant.Dots} color="#334155" gap={20} />
      <Controls />
      <MiniMap nodeColor={() => '#334155'} maskColor="#0f1117cc" />
    </ReactFlow>
  );
}
