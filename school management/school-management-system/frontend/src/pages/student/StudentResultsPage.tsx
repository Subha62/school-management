import Link from '@mui/material/Link';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { ResultChip } from '../../features/exams/components/StatusChips';
import { useMyResults } from '../../features/exams/hooks';
import { formatInstant, formatPercent } from '../../features/exams/labels';
import type { ResultSummary } from '../../types/exams';

export function StudentResultsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const results = useMyResults({ page, size });

  const columns: Column<ResultSummary>[] = [
    {
      id: 'exam',
      header: 'Exam',
      render: (row) => (
        <Link component={RouterLink} to={`/student/results/${row.examId}`} underline="hover">
          {row.examName}
        </Link>
      ),
    },
    {
      id: 'year',
      header: 'Year and grade',
      hideBelow: 'sm',
      render: (row) => `${row.academicYearName} · ${row.gradeLevelName}`,
    },
    { id: 'percentage', header: 'Percentage', render: (row) => formatPercent(row.percentage) },
    { id: 'grade', header: 'Grade', render: (row) => row.grade ?? '-' },
    { id: 'result', header: 'Result', render: (row) => <ResultChip result={row.result} /> },
    {
      id: 'published',
      header: 'Published',
      hideBelow: 'lg',
      render: (r) => formatInstant(r.publishedAt),
    },
  ];
  const actions: RowAction<ResultSummary>[] = [
    { label: 'Open', onSelect: (row) => void navigate(`/student/results/${row.examId}`) },
  ];

  return (
    <>
      <PageHeader
        title="Exams and results"
        subtitle="Your results for every exam whose results the school has published."
      />
      <DataList
        data={results.data}
        columns={columns}
        actions={actions}
        getRowId={(row) => row.examId}
        getRowLabel={(row) => row.examName}
        isLoading={results.isPending}
        error={results.error}
        onRetry={() => void results.refetch()}
        emptyTitle="No results yet"
        emptyHint="Your results appear here once the school publishes them."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
    </>
  );
}
