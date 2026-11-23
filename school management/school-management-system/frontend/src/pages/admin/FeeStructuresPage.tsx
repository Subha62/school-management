import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { ActiveChip } from '../../components/StatusChip';
import { useGradeOptions, useYearOptions } from '../../features/academics/hooks';
import { StructureFormDialog } from '../../features/fees/components/StructureFormDialog';
import {
  useCategoryOptions,
  useDeleteStructure,
  useGenerateFees,
  useStructures,
} from '../../features/fees/hooks';
import { formatMoney } from '../../features/fees/labels';
import type { FeeStructure } from '../../types/fees';
import { formatDate } from '../../utils/format';

const ALL = 'all';

export function FeeStructuresPage() {
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const categories = useCategoryOptions();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [yearId, setYearId] = useState(ALL);
  const [gradeId, setGradeId] = useState(ALL);
  const [categoryId, setCategoryId] = useState(ALL);
  const [editing, setEditing] = useState<FeeStructure | 'new' | null>(null);
  const [deleting, setDeleting] = useState<FeeStructure | null>(null);
  const [generating, setGenerating] = useState<FeeStructure | null>(null);

  const structures = useStructures({
    page,
    size,
    academicYearId: yearId === ALL ? undefined : yearId,
    gradeLevelId: gradeId === ALL ? undefined : gradeId,
    feeCategoryId: categoryId === ALL ? undefined : categoryId,
  });
  const remove = useDeleteStructure();
  const generate = useGenerateFees();
  const filtered = yearId !== ALL || gradeId !== ALL || categoryId !== ALL;

  const columns: Column<FeeStructure>[] = [
    {
      id: 'fee',
      header: 'Fee',
      render: (s) => (s.description ? `${s.categoryName} · ${s.description}` : s.categoryName),
    },
    {
      id: 'class',
      header: 'Year and grade',
      render: (s) => `${s.academicYearName} · ${s.gradeLevelName}`,
    },
    { id: 'amount', header: 'Amount', render: (s) => formatMoney(s.amount) },
    { id: 'due', header: 'Due date', hideBelow: 'md', render: (s) => formatDate(s.dueDate) },
    {
      id: 'billed',
      header: 'Billed students',
      hideBelow: 'sm',
      render: (s) => s.billedStudents,
    },
    {
      id: 'active',
      header: 'Status',
      hideBelow: 'lg',
      render: (s) => <ActiveChip active={s.active} />,
    },
  ];
  const actions: RowAction<FeeStructure>[] = [
    { label: 'Edit', onSelect: setEditing },
    { label: 'Generate fees', onSelect: setGenerating, hidden: (s) => !s.active },
    {
      label: 'Delete',
      onSelect: setDeleting,
      destructive: true,
      hidden: (s) => s.billedStudents > 0,
    },
  ];

  const cancelDelete = () => {
    remove.reset();
    setDeleting(null);
  };
  const cancelGenerate = () => {
    generate.reset();
    setGenerating(null);
  };

  const filter = (
    label: string,
    value: string,
    onChange: (value: string) => void,
    options: { value: string; label: string }[],
    allLabel: string,
  ) => (
    <TextField
      select
      size="small"
      label={label}
      value={value}
      onChange={(event) => {
        onChange(event.target.value);
        setPage(0);
      }}
      sx={{ minWidth: 170 }}
    >
      <MenuItem value={ALL}>{allLabel}</MenuItem>
      {options.map((option) => (
        <MenuItem key={option.value} value={option.value}>
          {option.label}
        </MenuItem>
      ))}
    </TextField>
  );

  return (
    <>
      <PageHeader
        title="Fee structures"
        subtitle="What each grade pays for a fee category in an academic year, and when it is due."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New fee structure
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap' }}
      >
        {filter(
          'Academic year',
          yearId,
          setYearId,
          (years.data?.content ?? []).map((year) => ({ value: year.id, label: year.name })),
          'All years',
        )}
        {filter(
          'Grade',
          gradeId,
          setGradeId,
          (grades.data?.content ?? []).map((grade) => ({ value: grade.id, label: grade.name })),
          'All grades',
        )}
        {filter(
          'Fee category',
          categoryId,
          setCategoryId,
          (categories.data?.content ?? []).map((category) => ({
            value: category.id,
            label: category.name,
          })),
          'All categories',
        )}
      </Stack>

      <DataList
        data={structures.data}
        columns={columns}
        actions={actions}
        getRowId={(s) => s.id}
        getRowLabel={(s) => `${s.categoryName} ${s.gradeLevelName}`}
        isLoading={structures.isPending}
        error={structures.error}
        onRetry={() => void structures.refetch()}
        emptyTitle="No fee structures found"
        emptyHint={
          filtered ? 'Try a different filter.' : 'Create the first fee structure for a grade.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <StructureFormDialog
          structure={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={generating !== null}
        title="Generate fees?"
        message={`Every student enrolled in ${generating?.gradeLevelName ?? 'the grade'} for ${generating?.academicYearName ?? 'the year'} is billed ${generating ? formatMoney(generating.amount) : ''} for ${generating?.categoryName ?? 'this fee'}. Students who were already billed are skipped, so this is safe to repeat when new students enrol.`}
        confirmLabel="Generate fees"
        loading={generate.isPending}
        error={generate.error}
        onCancel={cancelGenerate}
        onConfirm={() => {
          if (!generating) return;
          generate.mutate(generating.id, {
            onSuccess: (result) => {
              notify.success(
                `Fees generated: ${result.created} created, ${result.alreadyBilled} already billed`,
              );
              setGenerating(null);
            },
          });
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title="Delete fee structure?"
        message={`${deleting?.categoryName ?? 'This structure'} for ${deleting?.gradeLevelName ?? 'the grade'} is deleted permanently. A structure that has billed students cannot be deleted.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Fee structure deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
