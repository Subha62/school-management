import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useState, type ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { EnrollDialog } from '../../features/people/components/EnrollDialog';
import { GuardianLinkDialog } from '../../features/people/components/GuardianLinkDialog';
import { StudentFormDialog } from '../../features/people/components/StudentFormDialog';
import { TransferDialog } from '../../features/people/components/TransferDialog';
import {
  useStudent,
  useStudentEnrollments,
  useStudentGuardians,
  useUnlinkGuardian,
} from '../../features/people/hooks';
import {
  ENROLLMENT_LABELS,
  GENDER_LABELS,
  RELATIONSHIP_LABELS,
  STUDENT_STATUS_LABELS,
  enrollmentTone,
  fullName,
  studentTone,
} from '../../features/people/labels';
import type { StudentGuardianLink } from '../../types/people';
import { formatDate } from '../../utils/format';

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography sx={{ overflowWrap: 'anywhere' }}>{children || '-'}</Typography>
    </Box>
  );
}

export function StudentDetailPage() {
  const { id = '' } = useParams();
  const notify = useNotify();
  const student = useStudent(id);
  const guardians = useStudentGuardians(id);
  const history = useStudentEnrollments(id);
  const unlink = useUnlinkGuardian();
  const [editing, setEditing] = useState(false);
  const [enrolling, setEnrolling] = useState(false);
  const [transferring, setTransferring] = useState(false);
  const [linking, setLinking] = useState<StudentGuardianLink | 'new' | null>(null);
  const [unlinking, setUnlinking] = useState<StudentGuardianLink | null>(null);

  if (student.isPending) {
    return <LoadingState />;
  }
  if (student.isError) {
    return (
      <ErrorState
        title="Unable to load the student"
        error={student.error}
        onRetry={() => void student.refetch()}
      />
    );
  }

  const s = student.data;
  const name = fullName(s);
  const activeEnrollment = history.data?.find((enrollment) => enrollment.status === 'ACTIVE');
  const address = [s.addressLine1, s.addressLine2, s.city, s.state, s.postalCode]
    .filter(Boolean)
    .join(', ');

  return (
    <>
      <Button
        component={RouterLink}
        to="/admin/students"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 1 }}
      >
        All students
      </Button>
      <PageHeader
        title={name}
        subtitle={`Admission number ${s.admissionNumber}`}
        actions={
          <Button variant="outlined" startIcon={<EditIcon />} onClick={() => setEditing(true)}>
            Edit student
          </Button>
        }
      />

      <Stack spacing={3}>
        <Card>
          <CardHeader
            title="Details"
            action={
              <StatusChip label={STUDENT_STATUS_LABELS[s.status]} tone={studentTone(s.status)} />
            }
          />
          <CardContent>
            <Box
              sx={{
                display: 'grid',
                gap: 2,
                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
              }}
            >
              <Detail label="Email">{s.email}</Detail>
              <Detail label="Phone">{s.phoneNumber}</Detail>
              <Detail label="Date of birth">{formatDate(s.dateOfBirth)}</Detail>
              <Detail label="Gender">{GENDER_LABELS[s.gender]}</Detail>
              <Detail label="Blood group">{s.bloodGroup}</Detail>
              <Detail label="Admission date">{formatDate(s.admissionDate)}</Detail>
              <Detail label="Address">{address}</Detail>
              <Detail label="Emergency contact">
                {`${s.emergencyContactName} (${s.emergencyContactPhone})`}
              </Detail>
            </Box>
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            title="Current class"
            action={
              s.status === 'ACTIVE' && history.data ? (
                activeEnrollment ? (
                  <Button onClick={() => setTransferring(true)}>Transfer</Button>
                ) : (
                  <Button variant="contained" onClick={() => setEnrolling(true)}>
                    Enroll
                  </Button>
                )
              ) : null
            }
          />
          <CardContent>
            {s.currentEnrollment ? (
              <Typography>
                {s.currentEnrollment.grade} - {s.currentEnrollment.section},{' '}
                {s.currentEnrollment.academicYear}
                {s.currentEnrollment.rollNumber ? ` (roll ${s.currentEnrollment.rollNumber})` : ''}
              </Typography>
            ) : (
              <Typography color="text.secondary">Not enrolled in any section.</Typography>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            title="Guardians"
            action={<Button onClick={() => setLinking('new')}>Link guardian</Button>}
          />
          <CardContent>
            {guardians.isError ? (
              <Alert severity="error">{guardians.error.message}</Alert>
            ) : guardians.data && guardians.data.length === 0 ? (
              <Typography color="text.secondary">No guardians linked yet.</Typography>
            ) : (
              <Stack spacing={2} divider={<Box sx={{ borderTop: 1, borderColor: 'divider' }} />}>
                {(guardians.data ?? []).map((link) => (
                  <Stack
                    key={link.id}
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
                  >
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontWeight: 600 }}>
                        {fullName(link)} ({RELATIONSHIP_LABELS[link.relationship]})
                      </Typography>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ overflowWrap: 'anywhere' }}
                      >
                        {[link.phoneNumber, link.email].filter(Boolean).join(' · ')}
                      </Typography>
                      <Stack
                        direction="row"
                        spacing={0.5}
                        useFlexGap
                        sx={{ mt: 0.5, flexWrap: 'wrap' }}
                      >
                        {link.primaryGuardian ? (
                          <Chip size="small" color="primary" label="Primary" />
                        ) : null}
                        {link.receivesNotifications ? (
                          <Chip size="small" variant="outlined" label="Notifications" />
                        ) : null}
                        {link.authorizedPickup ? (
                          <Chip size="small" variant="outlined" label="Pickup" />
                        ) : null}
                      </Stack>
                    </Box>
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        aria-label={`Edit link for ${fullName(link)}`}
                        onClick={() => setLinking(link)}
                      >
                        Edit
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        aria-label={`Unlink ${fullName(link)}`}
                        onClick={() => setUnlinking(link)}
                      >
                        Unlink
                      </Button>
                    </Stack>
                  </Stack>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Enrollment history" />
          <CardContent sx={{ pt: 0 }}>
            {history.isError ? (
              <Alert severity="error">{history.error.message}</Alert>
            ) : history.data && history.data.length === 0 ? (
              <Typography color="text.secondary">No enrollments yet.</Typography>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Enrollment history">
                  <TableHead>
                    <TableRow>
                      <TableCell>Academic year</TableCell>
                      <TableCell>Class</TableCell>
                      <TableCell>Roll</TableCell>
                      <TableCell>Enrolled</TableCell>
                      <TableCell>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(history.data ?? []).map((enrollment) => (
                      <TableRow key={enrollment.id}>
                        <TableCell>{enrollment.academicYear.name}</TableCell>
                        <TableCell>
                          {enrollment.gradeLevel.name} - {enrollment.section.name}
                        </TableCell>
                        <TableCell>{enrollment.rollNumber ?? '-'}</TableCell>
                        <TableCell>{formatDate(enrollment.enrollmentDate)}</TableCell>
                        <TableCell>
                          <StatusChip
                            label={ENROLLMENT_LABELS[enrollment.status]}
                            tone={enrollmentTone(enrollment.status)}
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>
      </Stack>

      {editing ? <StudentFormDialog student={s} onClose={() => setEditing(false)} /> : null}
      {enrolling ? (
        <EnrollDialog
          student={{ id: s.id, label: `${name} (${s.admissionNumber})` }}
          onClose={() => setEnrolling(false)}
        />
      ) : null}
      {transferring && activeEnrollment ? (
        <TransferDialog enrollment={activeEnrollment} onClose={() => setTransferring(false)} />
      ) : null}
      {linking ? (
        <GuardianLinkDialog
          studentId={s.id}
          link={linking === 'new' ? null : linking}
          onClose={() => setLinking(null)}
        />
      ) : null}
      <ConfirmDialog
        open={unlinking !== null}
        title="Unlink guardian?"
        message={`${unlinking ? fullName(unlinking) : 'This guardian'} will no longer be linked to ${name}. The guardian record is kept.`}
        confirmLabel="Unlink"
        destructive
        loading={unlink.isPending}
        error={unlink.error}
        onCancel={() => {
          unlink.reset();
          setUnlinking(null);
        }}
        onConfirm={() => {
          if (!unlinking) return;
          unlink.mutate(
            { studentId: s.id, linkId: unlinking.id },
            {
              onSuccess: () => {
                notify.success('Guardian unlinked');
                setUnlinking(null);
              },
            },
          );
        }}
      />
    </>
  );
}
