import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useSectionOptions, useYearOptions } from '../../academics/hooks';

interface ClassFilterProps {
  sectionId: string;
  onChange: (sectionId: string) => void;
  /** Adds an "All classes" choice. */
  allowAll?: boolean;
}

const ALL = '';

/** Academic year (defaults to the active one) and class selects; reports the chosen section id. */
export function ClassFilter({ sectionId, onChange, allowAll }: ClassFilterProps) {
  const years = useYearOptions();
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const yearId =
    yearChoice ?? years.data?.content.find((year) => year.status === 'ACTIVE')?.id ?? '';
  const sections = useSectionOptions(yearId || undefined);

  return (
    <>
      <TextField
        select
        size="small"
        label="Academic year"
        value={yearId}
        onChange={(event) => {
          setYearChoice(event.target.value);
          onChange(ALL);
        }}
        sx={{ minWidth: 170 }}
      >
        {(years.data?.content ?? []).map((year) => (
          <MenuItem key={year.id} value={year.id}>
            {year.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label="Class"
        value={sectionId}
        onChange={(event) => onChange(event.target.value)}
        disabled={!yearId}
        sx={{ minWidth: 170 }}
      >
        {allowAll ? <MenuItem value={ALL}>All classes</MenuItem> : null}
        {(sections.data?.content ?? []).map((section) => (
          <MenuItem key={section.id} value={section.id}>
            {section.gradeLevel.name} - {section.name}
          </MenuItem>
        ))}
      </TextField>
    </>
  );
}
