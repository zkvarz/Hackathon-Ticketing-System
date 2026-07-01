// Virtualized board tests (HTS-043). With a very large column, only a small window of cards is
// mounted (AC-1), and drag-and-drop still resolves a move against the full ticket data even for a
// card the virtualizer is rendering — dnd-kit is mocked (as in drag-drop.test.tsx) so the drop
// handler runs without a real pointer drag, while @tanstack/react-virtual runs for real. The
// ResizeObserver/scrollTo stubs live in src/test/setup.ts.

import { describe, expect, it, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { act, screen, waitFor, within } from '@testing-library/react';
import { server } from '../../test/msw/server';
import { renderWithProviders } from '../../test/renderWithProviders';
import { BoardPage } from './BoardPage';

// Capture the board's onDragEnd; stub the drag hooks as no-ops (react-virtual is NOT mocked).
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

const teams = [{ id: 't1', name: 'Payments', epicCount: 0, ticketCount: 1000, createdAt: 'x', modifiedAt: 'x' }];

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

// A big "New" column (1000 cards) so virtualization has something to window; each has a stable,
// server-ordered title Card-0000 … Card-0999.
function largeBoard() {
  return Array.from({ length: 1000 }, (_, i) =>
    tk({ id: `c${i}`, state: 'new', title: `Card-${String(i).padStart(4, '0')}` }),
  );
}

let patchCalls: { id: string; state: string }[];

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

describe('Virtualized board (HTS-043)', () => {
  beforeEach(() => {
    patchCalls = [];
  });

  // AC-1: with 1000 cards in a column, only a small window is actually in the DOM.
  it('mounts only a window of cards for a large column (AC-1)', async () => {
    server.use(
      http.get('/api/teams', () => HttpResponse.json(teams)),
      http.get('/api/tickets', () => HttpResponse.json(largeBoard())),
    );

    renderBoard();
    await screen.findByText('Card-0000');

    // The count reflects the full column even though most cards are not rendered.
    expect(within(column('New')).getByLabelText('New count')).toHaveTextContent('1000');
    // Only a fraction of the 1000 cards is in the DOM.
    const rendered = within(column('New')).getAllByRole('button');
    expect(rendered.length).toBeGreaterThan(0);
    expect(rendered.length).toBeLessThan(100);
    // A card far down the list is not mounted.
    expect(screen.queryByText('Card-0999')).not.toBeInTheDocument();
  });

  // AC-2 (boundary): a drop still PATCHes the correct state for a card in a large virtualized column.
  it('drag-and-drop still moves a card in a large column (AC-2)', async () => {
    let board = largeBoard();
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

    renderBoard();
    await screen.findByText('Card-0000');

    // Move the first (rendered) card to Done.
    await act(async () => {
      dnd.onDragEnd?.({ active: { id: 'c0' }, over: { id: 'done' } });
    });

    await waitFor(() =>
      expect(within(column('Done')).getByText('Card-0000')).toBeInTheDocument(),
    );
    expect(patchCalls).toEqual([{ id: 'c0', state: 'done' }]);
  });
});
