import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import type { TeacherOption } from '../../../types/academics';
import { useTeacherOptions } from '../../academics/hooks';

interface TeacherPickerProps {
  value: TeacherOption | null;
  onChange: (teacher: TeacherOption | null) => void;
}

const labelOf = (teacher: TeacherOption) =>
  `${teacher.firstName} ${teacher.lastName} (${teacher.email})`;

/** Searches active teachers on the server as the user types (not tied to a form). */
export function TeacherPicker({ value, onChange }: TeacherPickerProps) {
  const [input, setInput] = useState('');
  const search = useDebouncedValue(input.trim(), 300);
  const query = useTeacherOptions(search);
  const fromServer = query.data?.content ?? [];
  const options =
    value && !fromServer.some((teacher) => teacher.id === value.id)
      ? [value, ...fromServer]
      : fromServer;

  return (
    <Autocomplete<TeacherOption>
      size="small"
      sx={{ minWidth: { sm: 320 }, flexGrow: { xs: 1, md: 0 } }}
      options={options}
      value={value}
      loading={query.isFetching}
      getOptionLabel={labelOf}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(all) => all}
      onChange={(_event, teacher) => onChange(teacher)}
      onInputChange={(_event, text, reason) => {
        if (reason === 'input') setInput(text);
      }}
      noOptionsText={query.isError ? 'Could not load teachers' : 'No matching teachers'}
      renderInput={(params) => <TextField {...params} label="Teacher" />}
    />
  );
}
