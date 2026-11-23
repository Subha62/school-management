import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import type { TeacherChoice } from '../../../schemas/academics';
import { useTeacherOptions } from '../hooks';

interface TeacherAutocompleteProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  helperText?: string;
}

type Choice = NonNullable<TeacherChoice>;

/** Searches active teachers on the server as the user types; the form value is `{ id, label }` or null. */
export function TeacherAutocomplete<T extends FieldValues>({
  control,
  name,
  label,
  helperText,
}: TeacherAutocompleteProps<T>) {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input.trim(), 300);
  const query = useTeacherOptions(search);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const selected = (field.value as TeacherChoice | undefined) ?? null;
        const fromServer: Choice[] = (query.data?.content ?? []).map((teacher) => ({
          id: teacher.id,
          label: `${teacher.firstName} ${teacher.lastName} (${teacher.email})`,
        }));
        const options =
          selected && !fromServer.some((choice) => choice.id === selected.id)
            ? [selected, ...fromServer]
            : fromServer;

        return (
          <Autocomplete<Choice>
            options={options}
            value={selected}
            loading={query.isFetching}
            getOptionLabel={(option) => option.label}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            filterOptions={(all) => all}
            onChange={(_event, value) => field.onChange(value)}
            onInputChange={(_event, value, reason) => {
              if (reason === 'input') setInput(value);
            }}
            noOptionsText={query.isError ? 'Could not load teachers' : 'No matching teachers'}
            renderInput={(params) => (
              <TextField
                {...params}
                label={label}
                inputRef={field.ref}
                onBlur={field.onBlur}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message ?? helperText}
              />
            )}
          />
        );
      }}
    />
  );
}
