import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import { useAssignments } from '../../academics/hooks';
import { TYPE_LABELS } from '../labels';
import type { SessionType } from '../../../types/operations';

interface AttendanceKindProps {
  type: SessionType;
  subjectId: string;
  /** The class whose subjects can be chosen; subject-wise needs one. */
  sectionId: string;
  onType: (type: SessionType) => void;
  onSubject: (subjectId: string) => void;
}

/** Daily or subject-wise, and for subject-wise which subject of the chosen class. */
export function AttendanceKind({
  type,
  subjectId,
  sectionId,
  onType,
  onSubject,
}: AttendanceKindProps) {
  const assignments = useAssignments(
    { page: 0, size: 100, sectionId },
    type === 'SUBJECT' && Boolean(sectionId),
  );
  const subjects = (assignments.data?.content ?? []).map((assignment) => assignment.subject);

  return (
    <>
      <TextField
        select
        size="small"
        label="Attendance type"
        value={type}
        onChange={(event) => {
          onType(event.target.value as SessionType);
          onSubject('');
        }}
        sx={{ minWidth: 160 }}
      >
        {(Object.keys(TYPE_LABELS) as SessionType[]).map((value) => (
          <MenuItem key={value} value={value}>
            {TYPE_LABELS[value]}
          </MenuItem>
        ))}
      </TextField>
      {type === 'SUBJECT' ? (
        <TextField
          select
          size="small"
          label="Subject"
          value={subjectId}
          onChange={(event) => onSubject(event.target.value)}
          disabled={!sectionId}
          helperText={sectionId ? undefined : 'Choose a class first'}
          sx={{ minWidth: 180 }}
        >
          {subjects.map((subject) => (
            <MenuItem key={subject.id} value={subject.id}>
              {subject.name}
            </MenuItem>
          ))}
        </TextField>
      ) : null}
    </>
  );
}
