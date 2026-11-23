import { assignmentSchema, maxMarksSchema, reviewSchema } from './assignments';
import { fileProblem } from '../features/assignments/labels';

const NOW = new Date('2026-11-18T10:00:00').getTime();
const valid = {
  target: 'section-1|subject-1',
  title: 'Worksheet 1',
  description: '',
  dueAt: '2026-11-25T09:00',
  maxMarks: '10',
  allowLate: false,
};
const messages = (result: { success: boolean; error?: { issues: { message: string }[] } }) =>
  result.error?.issues.map((issue) => issue.message) ?? [];

describe('assignmentSchema', () => {
  it('accepts a complete assignment', () => {
    expect(assignmentSchema(true, null, () => NOW).safeParse(valid).success).toBe(true);
  });

  it('needs a class and subject only when creating', () => {
    const blank = { ...valid, target: '' };
    expect(messages(assignmentSchema(true, null, () => NOW).safeParse(blank))).toContain(
      'Choose a class and subject',
    );
    expect(assignmentSchema(false, valid.dueAt, () => NOW).safeParse(blank).success).toBe(true);
  });

  it('rejects a due date that is not in the future', () => {
    const past = { ...valid, dueAt: '2026-11-18T09:00' };
    expect(messages(assignmentSchema(true, null, () => NOW).safeParse(past))).toContain(
      'The due date must be in the future',
    );
  });

  it('lets an existing assignment keep a due date that has already passed', () => {
    const past = { ...valid, dueAt: '2026-11-01T09:00' };
    expect(assignmentSchema(false, '2026-11-01T09:00', () => NOW).safeParse(past).success).toBe(
      true,
    );
    expect(assignmentSchema(false, '2026-11-02T09:00', () => NOW).safeParse(past).success).toBe(
      false,
    );
  });
});

describe('maxMarksSchema', () => {
  it.each(['10', '0.5', '1000', '99.25'])('accepts %s', (value) => {
    expect(maxMarksSchema.safeParse(value).success).toBe(true);
  });

  it.each(['', '0', '-1', '1000.01', '1001', '1.234', 'ten'])('rejects "%s"', (value) => {
    expect(maxMarksSchema.safeParse(value).success).toBe(false);
  });
});

describe('reviewSchema', () => {
  it('needs marks to complete a review and keeps them within the maximum', () => {
    const schema = reviewSchema('REVIEWED', 10);
    expect(messages(schema.safeParse({ marks: '', feedback: '' }))).toContain('Marks are required');
    expect(messages(schema.safeParse({ marks: '11', feedback: '' }))).toContain(
      'Marks cannot be more than 10',
    );
    expect(messages(schema.safeParse({ marks: '1.234', feedback: '' }))).toContain(
      'Use a number with at most two decimal places',
    );
    expect(schema.safeParse({ marks: '0', feedback: '' }).success).toBe(true);
    expect(schema.safeParse({ marks: '8.5', feedback: '' }).success).toBe(true);
  });

  it('needs feedback, but no marks, to ask for a resubmission', () => {
    const schema = reviewSchema('RESUBMISSION_REQUESTED', 10);
    expect(schema.safeParse({ marks: '', feedback: '' }).success).toBe(false);
    expect(schema.safeParse({ marks: '', feedback: 'Show your working' }).success).toBe(true);
  });
});

describe('fileProblem', () => {
  const file = (name: string, size = 10) => new File([new Uint8Array(size)], name);

  it('accepts the allowed types regardless of case', () => {
    expect(fileProblem(file('Notes.PDF'))).toBeNull();
    expect(fileProblem(file('photo.jpeg'))).toBeNull();
  });

  it('refuses other types, files without an extension and empty files', () => {
    expect(fileProblem(file('run.exe'))).toMatch(/not an allowed type/);
    expect(fileProblem(file('README'))).toMatch(/not an allowed type/);
    expect(fileProblem(new File([], 'empty.pdf'))).toMatch(/is empty/);
  });

  it('refuses files over 10 MB', () => {
    const big = file('big.pdf');
    Object.defineProperty(big, 'size', { value: 10 * 1024 * 1024 + 1 });
    expect(fileProblem(big)).toMatch(/larger than 10 MB/);
  });
});
