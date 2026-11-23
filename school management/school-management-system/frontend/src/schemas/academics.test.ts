import {
  academicYearSchema,
  assignmentSchema,
  gradeSchema,
  sectionSchema,
  subjectSchema,
} from './academics';

const messages = (result: { error?: { issues: { message: string }[] } }) =>
  result.error?.issues.map((issue) => issue.message) ?? [];

describe('academicYearSchema', () => {
  const valid = { name: '2026-2027', startDate: '2026-06-01', endDate: '2027-03-31' };

  it('accepts a valid year', () => {
    expect(academicYearSchema.safeParse(valid).success).toBe(true);
  });

  it('requires the end date to be after the start date', () => {
    const result = academicYearSchema.safeParse({ ...valid, endDate: '2026-06-01' });

    expect(messages(result)).toEqual(['End date must be after the start date']);
    expect(result.error?.issues[0]?.path).toEqual(['endDate']);
  });

  it('asks for every missing field', () => {
    const result = academicYearSchema.safeParse({ name: ' ', startDate: '', endDate: '' });

    expect(messages(result)).toEqual(
      expect.arrayContaining([
        'Name is required',
        'Start date is required',
        'End date is required',
      ]),
    );
  });
});

describe('gradeSchema', () => {
  it.each(['0', '8', '1000'])('accepts display order %s', (displayOrder) => {
    expect(gradeSchema.safeParse({ name: 'Grade 8', displayOrder, active: true }).success).toBe(
      true,
    );
  });

  it.each([
    ['-1', 'whole number'],
    ['1.5', 'whole number'],
    ['abc', 'whole number'],
    ['1001', 'at most 1000'],
    ['', 'required'],
  ])('rejects display order %j', (displayOrder, reason) => {
    const result = gradeSchema.safeParse({ name: 'Grade 8', displayOrder, active: true });

    expect(messages(result).join(' ')).toContain(reason);
  });
});

describe('subjectSchema', () => {
  const base = { name: 'Maths', description: '', active: true };

  it.each(['MATH', 'math101', 'CS-1', 'A.B_C'])('accepts code %s', (code) => {
    expect(subjectSchema.safeParse({ ...base, code }).success).toBe(true);
  });

  it.each(['bad code', '-start', 'x!'])('rejects code %j', (code) => {
    expect(subjectSchema.safeParse({ ...base, code }).success).toBe(false);
  });

  it('limits the description length', () => {
    expect(
      subjectSchema.safeParse({ ...base, code: 'M', description: 'x'.repeat(501) }).success,
    ).toBe(false);
  });
});

describe('sectionSchema', () => {
  const valid = {
    academicYearId: 'y1',
    gradeLevelId: 'g1',
    name: 'A',
    capacity: '40',
    classTeacher: null,
    active: true,
  };

  it('accepts an optional class teacher', () => {
    expect(sectionSchema.safeParse(valid).success).toBe(true);
    expect(
      sectionSchema.safeParse({ ...valid, classTeacher: { id: 't1', label: 'Tara' } }).success,
    ).toBe(true);
  });

  it.each(['0', '501', 'ten', ''])('rejects capacity %j', (capacity) => {
    expect(sectionSchema.safeParse({ ...valid, capacity }).success).toBe(false);
  });

  it('requires a year and a grade', () => {
    const result = sectionSchema.safeParse({ ...valid, academicYearId: '', gradeLevelId: '' });

    expect(messages(result)).toEqual(['Academic year is required', 'Grade is required']);
  });
});

describe('assignmentSchema', () => {
  it.each(['1', '40'])('accepts %s weekly periods', (weeklyPeriods) => {
    expect(
      assignmentSchema.safeParse({ subjectId: 's1', teacher: null, weeklyPeriods, active: true })
        .success,
    ).toBe(true);
  });

  it.each(['0', '41'])('rejects %s weekly periods', (weeklyPeriods) => {
    expect(
      assignmentSchema.safeParse({ subjectId: 's1', teacher: null, weeklyPeriods, active: true })
        .success,
    ).toBe(false);
  });
});
