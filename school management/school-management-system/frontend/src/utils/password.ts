const LOWER = 'abcdefghijkmnopqrstuvwxyz';
const UPPER = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
const DIGITS = '23456789';
const SPECIAL = '!@#$%^&*?-_';
const ALL = LOWER + UPPER + DIGITS + SPECIAL;

function randomIndex(limit: number): number {
  // Rejection sampling keeps the choice uniform.
  const range = Math.floor(0x100000000 / limit) * limit;
  const buffer = new Uint32Array(1);
  do {
    crypto.getRandomValues(buffer);
  } while ((buffer[0] ?? 0) >= range);
  return (buffer[0] ?? 0) % limit;
}

const pick = (characters: string): string => characters.charAt(randomIndex(characters.length));

/** A random password that satisfies the account password rule; look-alike characters are left out. */
export function generatePassword(length = 14): string {
  const characters = [pick(LOWER), pick(UPPER), pick(DIGITS), pick(SPECIAL)];
  while (characters.length < length) {
    characters.push(pick(ALL));
  }
  for (let i = characters.length - 1; i > 0; i -= 1) {
    const j = randomIndex(i + 1);
    [characters[i], characters[j]] = [characters[j] as string, characters[i] as string];
  }
  return characters.join('');
}
