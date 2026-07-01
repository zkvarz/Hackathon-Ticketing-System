// Drag-drop tests (HTS-028). dnd-kit's pointer sensor needs real layout measurements that jsdom
// can't provide, so we mock @dnd-kit/core to capture the DndContext's onDragEnd and invoke it
// directly — exercising the real optimistic-move + revert-on-failure logic without a real drag.

import { describe, expect, it, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { act, screen, waitFor, within } from '@testing-library/react';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { BoardPage } from './BoardPage';

// Capture the board's onDragEnd so tests can fire drops; stub the drag hooks as no-ops.
const dnd = vi.hoisted(() => ({ onDragEnd: undefined as undefined | ((e: unknown) => void) }));
vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children, onDragEnd }: { children: React.ReactNode; onDragEnd: (e: unknown) => void }) => {
    dnd.onDragEnd = onDragEnd;
    return children;
  },
  useDraggable: () => ({ attributes: {}, listeners: {}, setNodeRef: () => {}, transform: null, isDragging: false }),
  useDroppable: () => ({ setNodeRef: () => {}, isOver: false }),
  useSensor: () => ({}),
  useSensors: () => [],
  PointerSensor: function PointerSensor() {},
  KeyboardSensor: function KeyboardSensor() {},
  closestCorners: () => [],
}));

const teams = [{ id: 't1', name: 'Payments', epicCount: 0, ticketCount: 2, createdAt: 'x', modifiedAt: 'x' }];

function tk(over: Partial<Record<string, unknown>>) {
  return {
    id: 'x',
    teamId: 't1',
    epicId: null,
    epicTitle: null,
    type: 'bug',
    state: 'new',
    title: 'A ticket',
    body: 'B',
    createdBy: 'u1',
    createdByEmail: 'u@example.com',
    createdAt: '2026-06-30T10:00:00Z',
    modifiedAt: '2026-06-30T10:00:00Z',
    ...over,
  };
}

let board: ReturnType<typeof tk>[];
let patchCalls: { id: string; state: string }[];

function successHandlers() {
  server.use(
    http.get('/api/teams', () => HttpResponse.json(teams)),
    http.get('/api/tickets', () => HttpResponse.json(board)),
    http.patch('/api/tickets/:id/state', async ({ params, request }) => {
      const { state } = (await request.json()) as { state: string };
      patchCalls.push({ id: String(params.id), state });
      board = board.map((t) => (t.id === params.id ? { ...t, state } : t));
      return HttpResponse.json(board.find((t) => t.id === params.id));
    }),
  );
}

function renderBoard() {
  return renderWithProviders(
    <Routes>
      <Route path="/board" element={<BoardPage />} />
      <Route path="/tickets/:id" element={<div>TICKET VIEW</div>} />
    </Routes>,
    { route: '/board' },
  );
}

function column(label: string) {
  return screen.getByRole('heading', { name: label }).closest('.board__column') as HTMLElement;
}

async function drop(id: string, toState: string) {
  await act(async () => {
    dnd.onDragEnd?.({ active: { id }, over: { id: toState } });
  });
}

describe('Board drag-drop (HTS-028)', () => {
  beforeEach(() => {
    patchCalls = [];
    board = [
      tk({ id: 'a', title: 'Payment bug', state: 'new' }),
      tk({ id: 'b', title: 'Report feature', type: 'feature', state: 'in_progress' }),
    ];
  });

  // AC-1 (positive): a drop moves the card and calls the state API with the target state.
  it('moves a card to another column and PATCHes its state (positive)', async () => {
    successHandlers();
    renderBoard();
    await screen.findByText('Payment bug');
    expect(within(column('New')).getByText('Payment bug')).toBeInTheDocument();

    await drop('a', 'in_progress');

    await waitFor(() =>
      expect(within(column('In progress')).getByText('Payment bug')).toBeInTheDocument(),
    );
    expect(patchCalls).toEqual([{ id: 'a', state: 'in_progress' }]);
    expect(within(column('New')).queryByText('Payment bug')).not.toBeInTheDocument();
  });

  // AC-2 (negative): the API fails → the card returns to its origin column + an error toast.
  it('reverts to the origin column and shows an error when the API fails (negative)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      http.get('/api/tickets', () => HttpResponse.json(board)),
      http.patch('/api/tickets/:id/state', () => new HttpResponse(null, { status: 500 })),
    );
    renderBoard();
    await screen.findByText('Payment bug');

    await drop('a', 'done');

    // Error surfaced and the card is back in New (rolled back).
    expect(await screen.findByRole('alert')).toHaveTextContent(/move failed/i);
    await waitFor(() =>
      expect(within(column('New')).getByText('Payment bug')).toBeInTheDocument(),
    );
    expect(within(column('Done')).queryByText('Payment bug')).not.toBeInTheDocument();
  });

  // AC-3 (boundary): a direct new->done move works, and two sequential moves don't desync state.
  it('supports any-to-any and rapid sequential moves (boundary)', async () => {
    successHandlers();
    renderBoard();
    await screen.findByText('Payment bug');

    await drop('a', 'done');
    await waitFor(() =>
      expect(within(column('Done')).getByText('Payment bug')).toBeInTheDocument(),
    );

    await drop('a', 'ready_for_acceptance');
    await waitFor(() =>
      expect(within(column('Ready for acceptance')).getByText('Payment bug')).toBeInTheDocument(),
    );

    expect(patchCalls).toEqual([
      { id: 'a', state: 'done' },
      { id: 'a', state: 'ready_for_acceptance' },
    ]);
    // No duplicate cards left behind in prior columns.
    expect(screen.getAllByText('Payment bug')).toHaveLength(1);
  });

  // A no-op drop (same column) neither calls the API nor changes anything.
  it('ignores a drop onto the same column (boundary)', async () => {
    successHandlers();
    renderBoard();
    await screen.findByText('Payment bug');

    await drop('a', 'new');
    expect(patchCalls).toHaveLength(0);
    expect(within(column('New')).getByText('Payment bug')).toBeInTheDocument();
  });
});
