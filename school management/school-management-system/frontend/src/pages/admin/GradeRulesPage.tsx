import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import { useMemo, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { GradeRuleFormDialog } from '../../features/exams/components/GradeRuleFormDialog';
import { ResponsiveRows, type RowColumn } from '../../features/exams/components/ResponsiveRows';
import { checkCoverage, sortByMinDescending } from '../../features/exams/gradeScale';
import { useDeleteGradeRule, useGradeRules } from '../../features/exams/hooks';
import { formatNumber } from '../../features/exams/labels';
import type { GradeRule } from '../../types/exams';

const PAGE = { page: 0, size: 100 } as const;

export function GradeRulesPage() {
  const notify = useNotify();
  const rules = useGradeRules(PAGE);
  const remove = useDeleteGradeRule();
  const [editing, setEditing] = useState<GradeRule | 'new' | null>(null);
  const [deleting, setDeleting] = useState<GradeRule | null>(null);

  const sorted = useMemo(() => sortByMinDescending(rules.data?.content ?? []), [rules.data]);
  const coverage = useMemo(() => checkCoverage(sorted), [sorted]);

  const columns: RowColumn<GradeRule>[] = [
    { id: 'grade', header: 'Grade', render: (rule) => rule.grade },
    {
      id: 'range',
      header: 'Percentage',
      render: (rule) => `${formatNumber(rule.minPercent)}% to ${formatNumber(rule.maxPercent)}%`,
    },
    { id: 'description', header: 'Description', render: (rule) => rule.description ?? '-' },
  ];

  const cancelDelete = () => {
    remove.reset();
    setDeleting(null);
  };

  return (
    <>
      <PageHeader
        title="Grade rules"
        subtitle="The grade scale that turns a percentage into a grade. Bands must not overlap."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New grade band
          </Button>
        }
      />

      {rules.error && !rules.data ? (
        <ErrorState error={rules.error} onRetry={() => void rules.refetch()} />
      ) : rules.isPending ? (
        <LoadingState label="Loading grade rules" />
      ) : (
        <>
          {coverage.complete ? (
            <Alert severity="success" sx={{ mb: 2 }}>
              The scale covers every percentage from 0% to 100%.
            </Alert>
          ) : (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <AlertTitle>The scale does not cover 0% to 100%</AlertTitle>
              Marks cannot be locked until it does. {coverage.problems.join(' ')}
            </Alert>
          )}
          {sorted.length === 0 ? (
            <Paper variant="outlined">
              <EmptyState
                title="No grade bands yet"
                hint="Add a band such as A: 80% to 100% to start building the scale."
              />
            </Paper>
          ) : (
            <ResponsiveRows
              label="Grade scale"
              rows={sorted}
              columns={columns}
              getRowId={(rule) => rule.id}
              actions={(rule) => (
                <>
                  <IconButton aria-label={`Edit ${rule.grade}`} onClick={() => setEditing(rule)}>
                    <EditIcon />
                  </IconButton>
                  <IconButton
                    aria-label={`Delete ${rule.grade}`}
                    color="error"
                    onClick={() => setDeleting(rule)}
                  >
                    <DeleteOutlineIcon />
                  </IconButton>
                </>
              )}
            />
          )}
        </>
      )}

      {editing ? (
        <GradeRuleFormDialog
          rule={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete grade band?"
        message={`The band ${deleting?.grade ?? ''} is deleted permanently. Results are graded with the remaining bands.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Grade band deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
