import type { FieldValues, Path, UseFormReturn } from 'react-hook-form';
import type { ApiError } from '../api/errors';

/** Wires a text field to React Hook Form, including the accessible error message. */
export function fieldProps<T extends FieldValues>(form: UseFormReturn<T>, name: Path<T>) {
  const { ref, ...registration } = form.register(name);
  const message = (form.formState.errors[name] as { message?: string } | undefined)?.message;
  return {
    ...registration,
    inputRef: ref,
    error: Boolean(message),
    helperText: message,
  };
}

/** `fieldProps` with a hint that is shown until the field has a validation message. */
export function fieldPropsWithHint<T extends FieldValues>(
  form: UseFormReturn<T>,
  name: Path<T>,
  hint: string,
) {
  const props = fieldProps(form, name);
  return { ...props, helperText: props.helperText ?? hint };
}

/**
 * Shows server-side field errors next to their inputs. Returns true when at least one was applied, so the
 * caller can skip a redundant general error message.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  form: UseFormReturn<T>,
  error: ApiError,
  fields: readonly Path<T>[],
): boolean {
  let applied = false;
  for (const field of fields) {
    const message = error.fieldErrors[field];
    if (message) {
      form.setError(field, { type: 'server', message });
      applied = true;
    }
  }
  return applied;
}
