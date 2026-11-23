import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';
import { useAuth } from '../auth/authContext';
import { fullName, rolesOf } from '../auth/roles';
import { PageHeader } from '../components/PageHeader';

const ROLE_LABELS = { admin: 'Administrator', teacher: 'Teacher', student: 'Student' } as const;

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0, sm: 2 }}>
      <Typography color="text.secondary" sx={{ width: { sm: 160 }, flexShrink: 0 }}>
        {label}
      </Typography>
      <Typography sx={{ overflowWrap: 'anywhere' }}>{value}</Typography>
    </Stack>
  );
}

export function ProfilePage() {
  const { user } = useAuth();
  if (!user) {
    return null;
  }

  return (
    <>
      <PageHeader title="My profile" />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, maxWidth: 640 }}>
        <Stack spacing={2} divider={<Divider flexItem />}>
          <Detail label="Name" value={fullName(user)} />
          <Detail label="Email" value={user.email} />
          {user.username ? <Detail label="Login ID" value={user.username} /> : null}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0.5, sm: 2 }}>
            <Typography color="text.secondary" sx={{ width: { sm: 160 }, flexShrink: 0 }}>
              Role
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
              {rolesOf(user).map((role) => (
                <Chip key={role} label={ROLE_LABELS[role]} size="small" />
              ))}
            </Stack>
          </Stack>
          <Button
            variant="outlined"
            component={RouterLink}
            to="../change-password"
            sx={{ alignSelf: 'flex-start' }}
          >
            Change password
          </Button>
        </Stack>
      </Paper>
    </>
  );
}
