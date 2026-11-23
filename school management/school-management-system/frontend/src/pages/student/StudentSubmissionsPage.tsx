import Link from '@mui/material/Link';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { DataList, type Column } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SubmissionStatusChip } from '../../features/assignments/components/StatusChips';
import { useMySubmissions } from '../../features/assignments/hooks';
import { formatDateTime, formatMarks } from '../../features/assignments/labels';
import type { SubmissionListItem } from '../../types/assignments';

export function StudentSubmissionsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const submissions = useMySubmissions({ page, size });

  const columns: Column<SubmissionListItem>[] = [
    {
      id: 'assignment',
      header: 'Assignment',
      render: (s) => (
        <Link
          component={RouterLink}
          to={`/student/assignments/${s.assignmentId}`}
          underline="hover"
        >
          {s.assignmentTitle}
        </Link>
      ),
    },
    { id: 'subject', header: 'Subject', hideBelow: 'sm', render: (s) => s.subjectName },
    {
      id: 'when',
      header: 'Handed in',
      hideBelow: 'md',
      render: (s) => formatDateTime(s.submittedAt),
    },
    {
      id: 'marks',
      header: 'Marks',
      render: (s) =>
        s.status === 'REVIEWED' ? formatMarks(s.marks, s.maxMarks) : 'Not marked yet',
    },
    { id: 'status', header: 'Status', render: (s) => <SubmissionStatusChip status={s.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="My submissions"
        subtitle="Everything you have handed in, with your marks once your teacher has reviewed it."
      />
      <DataList
        data={submissions.data}
        columns={columns}
        getRowId={(s) => s.id}
        getRowLabel={(s) => s.assignmentTitle}
        isLoading={submissions.isPending}
        error={submissions.error}
        onRetry={() => void submissions.refetch()}
        emptyTitle="Nothing submitted yet"
        emptyHint="When you hand in an assignment it is listed here."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
    </>
  );
}
