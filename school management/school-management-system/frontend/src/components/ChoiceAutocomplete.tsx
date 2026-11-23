import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form';
import { useDebouncedValue } from '../hooks/useDebouncedValue';

export interface Choice {
  id: string;
  label: string;
}

interface ChoiceAutocompleteProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
  disabled?: boolean;
  helperText?: string;
  noOptionsText: string;
  /** Loads matching options for the (debounced) text the user typed. */
  useOptions: (search: string) => { choices: Choice[]; isFetching: boolean; isError: boolean };
}

/** Searches on the server as the user types; the form value is `{ id, label }` or null. */
export function ChoiceAutocomplete<T extends FieldValues>({
  control,
  name,
  label,
  required,
  disabled,
  helperText,
  noOptionsText,
  useOptions,
}: ChoiceAutocompleteProps<T>) {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input.trim(), 300);
  const query = useOptions(search);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => {
        const selected = (field.value as Choice | null | undefined) ?? null;
        const options =
          selected && !query.choices.some((choice) => choice.id === selected.id)
            ? [selected, ...query.choices]
            : query.choices;

        return (
          <Autocomplete<Choice>
            options={options}
            value={selected}
            disabled={disabled}
            loading={query.isFetching}
            getOptionLabel={(option) => option.label}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            filterOptions={(all) => all}
            onChange={(_event, value) => field.onChange(value)}
            onInputChange={(_event, value, reason) => {
              if (reason === 'input') setInput(value);
            }}
            noOptionsText={query.isError ? 'Could not load options' : noOptionsText}
            renderInput={(params) => (
              <TextField
                {...params}
                label={label}
                required={required}
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
