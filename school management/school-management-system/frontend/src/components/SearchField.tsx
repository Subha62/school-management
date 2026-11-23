import SearchIcon from '@mui/icons-material/Search';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import { useEffect, useState } from 'react';
import { useDebouncedValue } from '../hooks/useDebouncedValue';

interface SearchFieldProps {
  label: string;
  onSearch: (value: string) => void;
  delayMs?: number;
}

/** A search box that reports its value only after the user pauses typing. */
export function SearchField({ label, onSearch, delayMs = 300 }: SearchFieldProps) {
  const [text, setText] = useState('');
  const debounced = useDebouncedValue(text.trim(), delayMs);

  useEffect(() => {
    onSearch(debounced);
  }, [debounced, onSearch]);

  return (
    <TextField
      label={label}
      type="search"
      value={text}
      onChange={(event) => setText(event.target.value)}
      size="small"
      sx={{ minWidth: { sm: 240 }, flexGrow: { xs: 1, md: 0 } }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" aria-hidden />
            </InputAdornment>
          ),
        },
      }}
    />
  );
}
