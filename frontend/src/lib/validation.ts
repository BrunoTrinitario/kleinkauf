// Rules for a user's data. They are the same the backend validates
// (kleinkaufusers, util/UserValidator.java); if they change, change both.

export const NAME_MAX_LENGTH = 100
export const EMAIL_MAX_LENGTH = 254
export const PASSWORD_MIN_LENGTH = 8
// bcrypt ignores anything past 72 bytes
export const PASSWORD_MAX_BYTES = 72

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// Bug report (kleinkaufusers, entities/BugReport.java)
export const BUG_REPORT_MIN_LENGTH = 10
export const BUG_REPORT_MAX_LENGTH = 2000

export function isValidName(name: string): boolean {
  const trimmed = name.trim()
  return trimmed.length > 0 && trimmed.length <= NAME_MAX_LENGTH
}

export function isValidEmail(email: string): boolean {
  const trimmed = email.trim()
  return trimmed.length <= EMAIL_MAX_LENGTH && EMAIL_PATTERN.test(trimmed)
}

export interface PasswordChecks {
  minLength: boolean
  hasNumber: boolean
  hasLetter: boolean
  maxLength: boolean
}

export function checkPassword(password: string): PasswordChecks {
  return {
    minLength: password.length >= PASSWORD_MIN_LENGTH,
    hasNumber: /\d/.test(password),
    hasLetter: /\p{L}/u.test(password),
    maxLength: new TextEncoder().encode(password).length <= PASSWORD_MAX_BYTES,
  }
}

export function isValidPassword(password: string): boolean {
  return Object.values(checkPassword(password)).every(Boolean)
}
