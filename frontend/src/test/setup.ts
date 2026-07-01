// Global Vitest setup: jest-dom matchers + MSW lifecycle.

import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './msw/server';

// jsdom lacks the layout APIs @tanstack/react-virtual needs (HTS-043): it reports zero element size
// and has no ResizeObserver. Stub ResizeObserver so that, on observe(), it feeds the virtualizer a
// fixed viewport size (300×800) — enough for it to compute a small, non-empty window from index 0.
// With ~800px / ~96px-per-card + overscan, a handful of cards render (whole small columns; only a
// slice of a 1000-card column), which is exactly what the virtualization tests assert. scrollTo is
// stubbed because the virtualizer calls it when scrolling programmatically.
const STUB_RECT = { width: 300, height: 800 };
class ResizeObserverStub {
  private readonly cb: ResizeObserverCallback;
  constructor(cb: ResizeObserverCallback) {
    this.cb = cb;
  }
  observe(target: Element) {
    const entry = {
      target,
      contentRect: STUB_RECT as DOMRectReadOnly,
      borderBoxSize: [{ inlineSize: STUB_RECT.width, blockSize: STUB_RECT.height }],
      contentBoxSize: [{ inlineSize: STUB_RECT.width, blockSize: STUB_RECT.height }],
      devicePixelContentBoxSize: [{ inlineSize: STUB_RECT.width, blockSize: STUB_RECT.height }],
    } as unknown as ResizeObserverEntry;
    queueMicrotask(() => this.cb([entry], this as unknown as ResizeObserver));
  }
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
if (!Element.prototype.scrollTo) {
  Element.prototype.scrollTo = () => {};
}

// Fail fast on requests that no handler covers, so missing mocks surface as errors.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
