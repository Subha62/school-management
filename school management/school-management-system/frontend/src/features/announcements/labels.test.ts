import { describe, expect, it } from 'vitest';
import { displayState } from './labels';

const NOW = Date.parse('2026-11-20T10:00:00Z');

describe('displayState', () => {
  it('keeps drafts and archived announcements as they are', () => {
    expect(displayState({ status: 'DRAFT', live: false, publishAt: null }, NOW)).toBe('Draft');
    expect(displayState({ status: 'ARCHIVED', live: false, publishAt: null }, NOW)).toBe(
      'Archived',
    );
  });

  it('is live while the server says it is visible', () => {
    expect(displayState({ status: 'PUBLISHED', live: true, publishAt: null }, NOW)).toBe('Live');
  });

  it('is scheduled before its start and expired after its end', () => {
    expect(
      displayState({ status: 'PUBLISHED', live: false, publishAt: '2026-11-21T00:00:00Z' }, NOW),
    ).toBe('Scheduled');
    expect(
      displayState({ status: 'PUBLISHED', live: false, publishAt: '2026-11-01T00:00:00Z' }, NOW),
    ).toBe('Expired');
  });
});
