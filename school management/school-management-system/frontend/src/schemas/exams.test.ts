import { describe, expect, it } from 'vitest';
import {
  commentSchema,
  defaultPassMarks,
  examSchema,
  gradeRuleSchema,
  toMarkEntry,
  validateMarkDraft,
} from './exams';

const subject = {
  subjectId: 's1',
  maxMarks: '100',
  passMarks: '35',
  examDate: '',
  passEdited: false,
};
const exam = {
  academicYearId: 'y1',
  gradeLevelId: 'g1',
  name: 'Term 1',
  startDate: '2027-01-02',
  endDate: '2027-01-09',
  subjects: [subject],
};

const messages = (result: { success: boolean; error?: { issues: { message: string }[] } }) =>
  result.error?.issues.map((issue) => issue.message) ?? [];

describe('gradeRuleSchema', () => {
  const rule = { grade: 'A', description: '', minPercent: '80', maxPercent: '100' };

  it('accepts a band with two decimals', () => {
    expect(gradeRuleSchema.safeParse({ ...rule, minPercent: '79.99' }).success).toBe(true);
  });

  it.each([
    [{ grade: ' ' }, 'Grade is required'],
    [{ grade: 'ABCDEFGHIJK' }, 'Grade must be at most 10 characters'],
    [{ minPercent: '' }, 'Minimum is required'],
    [{ minPercent: '12.345' }, 'Use a number with at most two decimal places'],
    [{ maxPercent: '100.01' }, 'Maximum must be at most 100'],
    [{ maxPercent: '101' }, 'Maximum must be at most 100'],
    [{ minPercent: '90', maxPercent: '80' }, 'Maximum must not be below the minimum'],
  ])('rejects %j', (change, message) => {
    expect(messages(gradeRuleSchema.safeParse({ ...rule, ...change }))).toContain(message);
  });
});

describe('defaultPassMarks', () => {
  it('suggests 35% of the maximum', () => {
    expect(defaultPassMarks('100')).toBe('35');
    expect(defaultPassMarks('50')).toBe('17.5');
    expect(defaultPassMarks('33.33')).toBe('11.67');
  });

  it('suggests nothing until the maximum is a usable number', () => {
    expect(defaultPassMarks('')).toBe('');
    expect(defaultPassMarks('abc')).toBe('');
    expect(defaultPassMarks('0')).toBe('');
  });
});

describe('examSchema', () => {
  it('accepts a complete exam', () => {
    expect(examSchema(true).safeParse(exam).success).toBe(true);
  });

  it('needs the year and grade only when creating', () => {
    const withoutScope = { ...exam, academicYearId: '', gradeLevelId: '' };
    expect(messages(examSchema(true).safeParse(withoutScope))).toEqual(
      expect.arrayContaining(['Academic year is required', 'Grade is required']),
    );
    expect(examSchema(false).safeParse(withoutScope).success).toBe(true);
  });

  it('needs at least one subject', () => {
    expect(messages(examSchema(true).safeParse({ ...exam, subjects: [] }))).toContain(
      'Add at least one subject',
    );
  });

  it('rejects pass marks above the maximum and a missing subject', () => {
    const result = examSchema(true).safeParse({
      ...exam,
      subjects: [{ ...subject, subjectId: '', passMarks: '101' }],
    });
    expect(messages(result)).toEqual(
      expect.arrayContaining([
        'Subject is required',
        'Pass marks cannot be more than the maximum marks',
      ]),
    );
  });

  it('rejects the same subject twice on the second row', () => {
    const result = examSchema(true).safeParse({ ...exam, subjects: [subject, subject] });
    expect(result.error?.issues.map((issue) => issue.path.join('.'))).toContain(
      'subjects.1.subjectId',
    );
  });

  it('rejects an end date before the start date', () => {
    expect(messages(examSchema(true).safeParse({ ...exam, endDate: '2027-01-01' }))).toContain(
      'End date cannot be before the start date',
    );
  });
});

describe('validateMarkDraft', () => {
  const draft = { marks: '', absent: false, remarks: '' };

  it('accepts blank, boundary and decimal marks', () => {
    expect(validateMarkDraft(draft, 50)).toEqual({});
    expect(validateMarkDraft({ ...draft, marks: '0' }, 50)).toEqual({});
    expect(validateMarkDraft({ ...draft, marks: '50' }, 50)).toEqual({});
    expect(validateMarkDraft({ ...draft, marks: '42.25' }, 50)).toEqual({});
  });

  it.each([
    ['50.01', 'Marks cannot be more than 50'],
    ['51', 'Marks cannot be more than 50'],
    ['-1', 'Marks cannot be negative'],
    ['4.123', 'Use a number with at most two decimal places'],
    ['abc', 'Use a number with at most two decimal places'],
  ])('rejects marks %s', (marks, message) => {
    expect(validateMarkDraft({ ...draft, marks }, 50)).toEqual({ marks: message });
  });

  it('rejects marks for an absent student and over-long remarks', () => {
    expect(validateMarkDraft({ ...draft, marks: '5', absent: true }, 50).marks).toBe(
      'An absent student has no marks',
    );
    expect(validateMarkDraft({ ...draft, remarks: 'x'.repeat(201) }, 50).remarks).toBe(
      'Remarks must be at most 200 characters',
    );
  });
});

describe('toMarkEntry', () => {
  it('converts typed marks to numbers and blanks to null', () => {
    expect(toMarkEntry('s1', { marks: ' 42.5 ', absent: false, remarks: ' good ' })).toEqual({
      studentId: 's1',
      marks: 42.5,
      absent: false,
      remarks: 'good',
    });
    expect(toMarkEntry('s1', { marks: '', absent: false, remarks: '' })).toEqual({
      studentId: 's1',
      marks: null,
      absent: false,
      remarks: null,
    });
  });

  it('never sends marks for an absent student', () => {
    expect(toMarkEntry('s1', { marks: '7', absent: true, remarks: '' }).marks).toBeNull();
  });
});

describe('commentSchema', () => {
  it('limits the comment length', () => {
    expect(commentSchema.safeParse({ comment: 'x'.repeat(500) }).success).toBe(true);
    expect(commentSchema.safeParse({ comment: 'x'.repeat(501) }).success).toBe(false);
  });
});
