import { describe, expect, it } from 'vitest';
import { announcementSchema } from './announcements';

const valid = {
  title: 'Sports day',
  body: 'Bring your kit',
  audience: 'ALL',
  sectionId: '',
  publishAt: '',
  expiresAt: '',
};

const messages = (values: object) => {
  const result = announcementSchema.safeParse(values);
  return result.success ? [] : result.error.issues.map((issue) => issue.message);
};

describe('announcementSchema', () => {
  it('accepts an announcement for everyone without a class or dates', () => {
    expect(announcementSchema.safeParse(valid).success).toBe(true);
  });

  it('needs a class only for the SECTION audience', () => {
    expect(messages({ ...valid, audience: 'SECTION' })).toEqual(['Choose a class']);
    expect(
      announcementSchema.safeParse({ ...valid, audience: 'SECTION', sectionId: 's1' }).success,
    ).toBe(true);
  });

  it.each([
    [{ title: ' ' }, 'Title is required'],
    [{ title: 'x'.repeat(201) }, 'Title must be at most 200 characters'],
    [{ body: ' ' }, 'Message is required'],
    [{ body: 'x'.repeat(5001) }, 'Message must be at most 5000 characters'],
  ])('rejects %j', (override, message) => {
    expect(messages({ ...valid, ...override })).toContain(message);
  });

  it('rejects an expiry that is not after the start', () => {
    const start = '2026-12-01T10:00';
    expect(messages({ ...valid, publishAt: start, expiresAt: '2026-12-01T09:00' })).toEqual([
      'The expiry must be after the start',
    ]);
    expect(messages({ ...valid, publishAt: start, expiresAt: start })).toEqual([
      'The expiry must be after the start',
    ]);
    expect(
      announcementSchema.safeParse({ ...valid, publishAt: start, expiresAt: '2026-12-01T10:01' })
        .success,
    ).toBe(true);
  });
});
