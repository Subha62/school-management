import AutorenewIcon from '@mui/icons-material/Autorenew';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import TextField, { type TextFieldProps } from '@mui/material/TextField';
import { useState } from 'react';
import { generatePassword } from '../utils/password';

type InitialPasswordFieldProps = Omit<TextFieldProps, 'type' | 'variant' | 'label'> & {
  label?: string;
  onGenerate: (password: string) => void;
};

/**
 * The password an administrator sets for a new account. It can be generated, and is then shown so it can be passed
 * on; the person should change it after first sign-in.
 */
export function InitialPasswordField({
  label = 'Initial password',
  onGenerate,
  helperText,
  ...props
}: InitialPasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={1.5}
      sx={{ alignItems: { sm: 'flex-start' } }}
    >
      <TextField
        {...props}
        label={label}
        type={visible ? 'text' : 'password'}
        autoComplete="new-password"
        fullWidth
        required
        helperText={helperText ?? 'At least 10 characters with upper, lower, digit and symbol.'}
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  edge="end"
                  aria-label={visible ? 'Hide password' : 'Show password'}
                  onClick={() => setVisible((current) => !current)}
                  onMouseDown={(event) => event.preventDefault()}
                >
                  {visible ? <VisibilityOff /> : <Visibility />}
                </IconButton>
              </InputAdornment>
            ),
          },
        }}
      />
      <Button
        variant="outlined"
        startIcon={<AutorenewIcon />}
        onClick={() => {
          setVisible(true);
          onGenerate(generatePassword());
        }}
        sx={{ flexShrink: 0, mt: { sm: 1 } }}
      >
        Generate
      </Button>
    </Stack>
  );
}
