import AttachFileIcon from '@mui/icons-material/AttachFile';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useId, useRef, useState } from 'react';
import { ALLOWED_EXTENSIONS, fileProblem, formatBytes } from '../labels';

interface FilePickerProps {
  files: File[];
  onChange: (files: File[]) => void;
  /** How many more files may be chosen (existing ones are already deducted). */
  remaining: number;
  disabled?: boolean;
}

/** A file chooser that lists the chosen files with a remove button and explains refused ones. */
export function FilePicker({ files, onChange, remaining, disabled }: FilePickerProps) {
  const inputId = useId();
  const input = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string | null>(null);

  const choose = (chosen: FileList | null) => {
    if (!chosen) return;
    const accepted: File[] = [];
    let message: string | null = null;
    for (const file of Array.from(chosen)) {
      const issue = fileProblem(file);
      if (issue) {
        message = issue;
      } else if (files.length + accepted.length >= remaining) {
        message = `You can add ${remaining} more file${remaining === 1 ? '' : 's'}`;
      } else {
        accepted.push(file);
      }
    }
    setProblem(message);
    onChange([...files, ...accepted]);
    if (input.current) input.current.value = '';
  };

  return (
    <Box>
      <input
        ref={input}
        id={inputId}
        type="file"
        multiple
        hidden
        disabled={disabled}
        accept={ALLOWED_EXTENSIONS.map((extension) => `.${extension}`).join(',')}
        onChange={(event) => choose(event.target.files)}
      />
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
        <Button
          variant="outlined"
          startIcon={<AttachFileIcon />}
          disabled={disabled || remaining <= files.length}
          onClick={() => input.current?.click()}
        >
          Choose files
        </Button>
        <Typography variant="caption" color="text.secondary">
          {ALLOWED_EXTENSIONS.join(', ')}. Up to 10 MB each.
        </Typography>
      </Stack>
      {problem ? (
        <Alert severity="warning" sx={{ mt: 1 }}>
          {problem}
        </Alert>
      ) : null}
      {files.length > 0 ? (
        <Stack
          component="ul"
          spacing={0.5}
          aria-label="Files to upload"
          sx={{ mt: 1, mb: 0, p: 0, listStyle: 'none' }}
        >
          {files.map((file, index) => (
            <Stack
              component="li"
              key={`${file.name}-${index}`}
              direction="row"
              spacing={1}
              sx={{ alignItems: 'center', justifyContent: 'space-between' }}
            >
              <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                {file.name} ({formatBytes(file.size)})
              </Typography>
              <Button
                size="small"
                color="error"
                aria-label={`Remove ${file.name} from the upload`}
                onClick={() => onChange(files.filter((_, position) => position !== index))}
              >
                Remove
              </Button>
            </Stack>
          ))}
        </Stack>
      ) : null}
    </Box>
  );
}
