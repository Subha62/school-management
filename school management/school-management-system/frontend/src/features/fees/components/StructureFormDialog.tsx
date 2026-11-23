import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect, type SelectOption } from '../../../components/FormSelect';
import { FormSwitch } from '../../../components/FormSwitch';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { blankToNull } from '../../../schemas/people';
import { structureSchema, type StructureFormValues } from '../../../schemas/fees';
import type { FeeStructure } from '../../../types/fees';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useGradeOptions, useYearOptions } from '../../academics/hooks';
import { useCategoryOptions, useSaveStructure } from '../hooks';

const FORM_ID = 'fee-structure-form';

interface StructureFormDialogProps {
  /** Null when creating. */
  structure: FeeStructure | null;
  onClose: () => void;
}

export function StructureFormDialog({ structure, onClose }: StructureFormDialogProps) {
  const save = useSaveStructure();
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const categories = useCategoryOptions(true);
  const [error, setError] = useState<ApiError | null>(null);
  const editing = structure !== null;
  // Once fees exist, the amount and due date are frozen on the server.
  const frozen = editing && structure.billedStudents > 0;
  const schema = useMemo(() => structureSchema(!editing), [editing]);

  const form = useForm<StructureFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      academicYearId: structure?.academicYearId ?? '',
      gradeLevelId: structure?.gradeLevelId ?? '',
      feeCategoryId: structure?.feeCategoryId ?? '',
      description: structure?.description ?? '',
      amount: structure ? structure.amount.toFixed(2) : '',
      dueDate: structure?.dueDate ?? '',
      active: structure?.active ?? true,
    },
  });

  const yearOptions: SelectOption[] = (years.data?.content ?? []).map((year) => ({
    value: year.id,
    label: year.name,
  }));
  const gradeOptions: SelectOption[] = (grades.data?.content ?? []).map((grade) => ({
    value: grade.id,
    label: grade.name,
  }));
  const categoryOptions: SelectOption[] = (categories.data?.content ?? []).map((category) => ({
    value: category.id,
    label: category.name,
  }));
  // The selected value must exist in the options for the select to show it.
  if (structure) {
    if (!yearOptions.some((o) => o.value === structure.academicYearId)) {
      yearOptions.push({ value: structure.academicYearId, label: structure.academicYearName });
    }
    if (!gradeOptions.some((o) => o.value === structure.gradeLevelId)) {
      gradeOptions.push({ value: structure.gradeLevelId, label: structure.gradeLevelName });
    }
    if (!categoryOptions.some((o) => o.value === structure.feeCategoryId)) {
      categoryOptions.push({ value: structure.feeCategoryId, label: structure.categoryName });
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: structure?.id,
        body: {
          ...(editing
            ? {}
            : {
                academicYearId: values.academicYearId,
                gradeLevelId: values.gradeLevelId,
                feeCategoryId: values.feeCategoryId,
              }),
          description: blankToNull(values.description),
          amount: frozen ? structure.amount : Number(values.amount),
          dueDate: frozen ? structure.dueDate : values.dueDate,
          active: values.active,
          version: structure?.version,
        },
      });
      notify.success(editing ? 'Fee structure updated' : 'Fee structure created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'academicYearId',
        'gradeLevelId',
        'feeCategoryId',
        'description',
        'amount',
        'dueDate',
      ]);
      setError(onField ? null : apiError);
    }
  });

  const amountProps = fieldProps(form, 'amount');
  const dueDateProps = fieldProps(form, 'dueDate');

  return (
    <ResponsiveDialog
      title={editing ? 'Edit fee structure' : 'New fee structure'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create structure'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        {frozen ? (
          <Alert severity="info">
            Fees were already generated for {structure.billedStudents}{' '}
            {structure.billedStudents === 1 ? 'student' : 'students'}, so the amount and due date
            can no longer change. You can still edit the description and deactivate the structure.
          </Alert>
        ) : null}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <FormSelect
            control={form.control}
            name="academicYearId"
            label="Academic year"
            options={yearOptions}
            required
            disabled={editing}
          />
          <FormSelect
            control={form.control}
            name="gradeLevelId"
            label="Grade"
            options={gradeOptions}
            required
            disabled={editing}
          />
        </Stack>
        <FormSelect
          control={form.control}
          name="feeCategoryId"
          label="Fee category"
          options={categoryOptions}
          required
          disabled={editing}
        />
        <TextField
          label="Description"
          fullWidth
          placeholder="Term 1 tuition"
          {...fieldProps(form, 'description')}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Amount"
            fullWidth
            required
            disabled={frozen}
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            {...amountProps}
            helperText={amountProps.helperText ?? (frozen ? 'Frozen after fees exist' : undefined)}
          />
          <TextField
            label="Due date"
            type="date"
            fullWidth
            required
            disabled={frozen}
            slotProps={{ inputLabel: { shrink: true } }}
            {...dueDateProps}
            helperText={dueDateProps.helperText ?? (frozen ? 'Frozen after fees exist' : undefined)}
          />
        </Stack>
        <FormSwitch control={form.control} name="active" label="Active" />
      </Stack>
    </ResponsiveDialog>
  );
}
