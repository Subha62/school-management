import {
  changePasswordSchema,
  forgotPasswordSchema,
  loginSchema,
  newPasswordSchema,
  resetPasswordSchema,
} from './auth';

function messagesOf(result: { success: boolean; error?: { issues: { message: string }[] } }) {
  return result.error?.issues.map((issue) => issue.message) ?? [];
}

describe('newPasswordSchema', () => {
  it.each(['Abcdef1!gh', 'Correct-Horse-9-Battery', 'Pässwörd1!xyz'])('accepts %s', (password) => {
    expect(newPasswordSchema.safeParse(password).success).toBe(true);
  });

  it.each([
    ['Ab1!', 'at least 10 characters'],
    ['abcdefgh1!', 'upper case'],
    ['ABCDEFGH1!', 'lower case'],
    ['Abcdefghi!', 'digit'],
    ['Abcdefghi1', 'special character'],
  ])('rejects %s and explains why', (password, reason) => {
    const result = newPasswordSchema.safeParse(password);

    expect(result.success).toBe(false);
    expect(messagesOf(result).join(' ')).toContain(reason);
  });

  it('rejects passwords above the 72-byte BCrypt limit, counting multi-byte characters', () => {
    expect(newPasswordSchema.safeParse(`Aa1!${'x'.repeat(69)}`).success).toBe(false);
    expect(newPasswordSchema.safeParse(`Aa1!${'é'.repeat(35)}`).success).toBe(false);
    expect(newPasswordSchema.safeParse(`Aa1!${'x'.repeat(68)}`).success).toBe(true);
  });
});

describe('loginSchema', () => {
  it('trims the identifier and requires both fields', () => {
    expect(loginSchema.parse({ identifier: '  S-0001 ', password: 'x' }).identifier).toBe('S-0001');
    const result = loginSchema.safeParse({ identifier: '   ', password: '' });

    expect(messagesOf(result)).toEqual(['Email or ID is required', 'Password is required']);
  });

  it('does not apply the strength policy to existing passwords', () => {
    expect(loginSchema.safeParse({ identifier: 'a@b.co', password: 'old' }).success).toBe(true);
  });
});

describe('forgotPasswordSchema', () => {
  it('requires a well-formed email', () => {
    expect(forgotPasswordSchema.safeParse({ email: 'ada@school.test' }).success).toBe(true);
    expect(messagesOf(forgotPasswordSchema.safeParse({ email: 'not-an-email' }))).toEqual([
      'Enter a valid email address',
    ]);
    expect(messagesOf(forgotPasswordSchema.safeParse({ email: '' }))).toEqual([
      'Email is required',
    ]);
  });
});

describe('resetPasswordSchema', () => {
  it('requires the confirmation to match', () => {
    const result = resetPasswordSchema.safeParse({
      newPassword: 'Valid-Passw0rd!',
      confirmPassword: 'Different-Passw0rd!',
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['confirmPassword']);
  });
});

describe('changePasswordSchema', () => {
  const valid = {
    currentPassword: 'Old-Passw0rd!',
    newPassword: 'New-Passw0rd!x',
    confirmPassword: 'New-Passw0rd!x',
  };

  it('accepts a complete valid form', () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects reusing the current password', () => {
    const result = changePasswordSchema.safeParse({
      ...valid,
      newPassword: valid.currentPassword,
      confirmPassword: valid.currentPassword,
    });

    expect(messagesOf(result)).toContain('The new password must be different from the current one');
  });
});
