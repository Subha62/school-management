import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { useMyResults } from '../../features/exams/hooks';
import { formatInstant } from '../../features/exams/labels';
import type { ResultSummary } from '../../types/exams';

export function StudentReportCardsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const results = useMyResults({ page, size });

  const columns: Column<ResultSummary>[] = [
    {
      id: 'exam',
      header: 'Exam',
      render: (row) => (
        <Link component={RouterLink} to={`/student/report-cards/${row.examId}`} underline="hover">
          {row.examName}
        </Link>
      ),
    },
    {
      id: 'year',
      header: 'Year and grade',
      render: (row) => `${row.academicYearName} · ${row.gradeLevelName}`,
    },
    {
      id: 'published',
      header: 'Published',
      hideBelow: 'sm',
      render: (r) => formatInstant(r.publishedAt),
    },
    {
      id: 'open',
      header: 'Report card',
      hideBelow: 'md',
      render: (row) => (
        <Button
          component={RouterLink}
          to={`/student/report-cards/${row.examId}`}
          size="small"
          variant="outlined"
          aria-label={`View report card: ${row.examName}`}
        >
          View
        </Button>
      ),
    },
  ];
  const actions: RowAction<ResultSummary>[] = [
    {
      label: 'View report card',
      onSelect: (row) => void navigate(`/student/report-cards/${row.examId}`),
    },
  ];

  return (
    <>
      <PageHeader title="Report cards" subtitle="Open a report card to read it or print it." />
      <DataList
        data={results.data}
        columns={columns}
        actions={actions}
        getRowId={(row) => row.examId}
        getRowLabel={(row) => row.examName}
        isLoading={results.isPending}
        error={results.error}
        onRetry={() => void results.refetch()}
        emptyTitle="No report cards yet"
        emptyHint="A report card appears here once the school publishes the results of an exam."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
    </>
  );
}
