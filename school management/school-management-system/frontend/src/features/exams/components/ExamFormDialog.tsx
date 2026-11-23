import { zodResolver } from '@hookform/resolvers/zod';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useFieldArray, useForm, type Path } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect, type SelectOption } from '../../../components/FormSelect';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import {
  MAX_EXAM_SUBJECTS,
  defaultPassMarks,
  examSchema,
  type ExamFormValues,
  type ExamSubjectFormValues,
} from '../../../schemas/exams';
import type { Exam } from '../../../types/exams';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useGradeOptions, useSubjectOptions, useYearOptions } from '../../academics/hooks';
import { useSaveExam } from '../hooks';

const FORM_ID = 'exam-form';

const blankSubject = (): ExamSubjectFormValues => ({
  subjectId: '',
  maxMarks: '100',
  passMarks: defaultPassMarks('100'),
  examDate: '',
  passEdited: false,
});

export function ExamFormDialog({ exam, onClose }: { exam: Exam | null; onClose: () => void }) {
  const save = useSaveExam();
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const subjects = useSubjectOptions(true);
  const [error, setError] = useState<ApiError | null>(null);
  const editing = exam !== null;

  const form = useForm<ExamFormValues>({
    resolver: zodResolver(examSchema(!editing)),
    defaultValues: {
      academicYearId: exam?.academicYearId ?? '',
      gradeLevelId: exam?.gradeLevelId ?? '',
      name: exam?.name ?? '',
      startDate: exam?.startDate ?? '',
      endDate: exam?.endDate ?? '',
      subjects: exam
        ? exam.subjects.map((row) => ({
            subjectId: row.subjectId,
            maxMarks: String(row.maxMarks),
            passMarks: String(row.passMarks),
            examDate: row.examDate ?? '',
            passEdited: true,
          }))
        : [blankSubject()],
    },
  });
  const rows = useFieldArray({ control: form.control, name: 'subjects' });
  const { errors } = form.formState;

  const yearOptions: SelectOption[] = (years.data?.content ?? []).map((year) => ({
    value: year.id,
    label: year.name,
  }));
  const gradeOptions: SelectOption[] = (grades.data?.content ?? []).map((grade) => ({
    value: grade.id,
    label: grade.name,
  }));
  const subjectOptions = useMemo(() => {
    const options: SelectOption[] = (subjects.data?.content ?? []).map((subject) => ({
      value: subject.id,
      label: `${subject.name} (${subject.code})`,
    }));
    // A subject that was deactivated after the exam was created must stay visible while editing.
    for (const row of exam?.subjects ?? []) {
      if (!options.some((option) => option.value === row.subjectId)) {
        options.push({ value: row.subjectId, label: `${row.subjectName} (${row.subjectCode})` });
      }
    }
    return options;
  }, [subjects.data, exam]);
  // The selected year or grade must exist in the list for the select to show it.
  if (exam) {
    if (!yearOptions.some((option) => option.value === exam.academicYearId)) {
      yearOptions.push({ value: exam.academicYearId, label: exam.academicYearName });
    }
    if (!gradeOptions.some((option) => option.value === exam.gradeLevelId)) {
      gradeOptions.push({ value: exam.gradeLevelId, label: exam.gradeLevelName });
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: exam?.id,
        body: {
          ...(editing
            ? {}
            : { academicYearId: values.academicYearId, gradeLevelId: values.gradeLevelId }),
          name: values.name,
          startDate: values.startDate || null,
          endDate: values.endDate || null,
          subjects: values.subjects.map((row) => ({
            subjectId: row.subjectId,
            maxMarks: Number(row.maxMarks),
            passMarks: Number(row.passMarks),
            examDate: row.examDate || null,
          })),
          version: exam?.version,
        },
      });
      notify.success(editing ? 'Exam updated' : 'Exam created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['name', 'startDate', 'endDate']);
      setError(onField ? null : apiError);
    }
  });

  const subjectsError = errors.subjects?.message ?? errors.subjects?.root?.message;

  /** Text input wiring that keeps the field's `ref` on the input element rather than the wrapper. */
  const registered = (name: Path<ExamFormValues>) => {
    const { ref, ...registration } = form.register(name);
    return { ...registration, inputRef: ref };
  };

  return (
    <ResponsiveDialog
      title={editing ? 'Edit exam' : 'New exam'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create exam'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Name"
          placeholder="Term 1 examination"
          fullWidth
          required
          {...fieldProps(form, 'name')}
        />
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
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Start date"
            type="date"
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'startDate')}
          />
          <TextField
            label="End date"
            type="date"
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'endDate')}
          />
        </Stack>

        <Box component="fieldset" sx={{ border: 0, m: 0, p: 0, minWidth: 0 }}>
          <Typography component="legend" variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
            Subjects
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
            Pass marks start at 35% of the maximum marks until you type your own.
          </Typography>
          <Stack spacing={1.5}>
            {rows.fields.map((field, index) => {
              const rowErrors = errors.subjects?.[index];
              const maxField = registered(`subjects.${index}.maxMarks`);
              const passField = registered(`subjects.${index}.passMarks`);
              const dateField = registered(`subjects.${index}.examDate`);
              return (
                <Paper
                  key={field.id}
                  component="div"
                  role="group"
                  aria-label={`Subject ${index + 1}`}
                  variant="outlined"
                  sx={{ p: 1.5 }}
                >
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <FormSelect
                        control={form.control}
                        name={`subjects.${index}.subjectId`}
                        label="Subject"
                        options={subjectOptions}
                        required
                      />
                    </Box>
                    <IconButton
                      aria-label={`Remove subject ${index + 1}`}
                      onClick={() => rows.remove(index)}
                      disabled={rows.fields.length === 1}
                    >
                      <DeleteOutlineIcon />
                    </IconButton>
                  </Stack>
                  <Box
                    sx={{
                      display: 'grid',
                      gap: 2,
                      mt: 2,
                      gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
                    }}
                  >
                    <TextField
                      label="Maximum marks"
                      required
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                      {...maxField}
                      onChange={(event) => {
                        void maxField.onChange(event);
                        if (!form.getValues(`subjects.${index}.passEdited`)) {
                          form.setValue(
                            `subjects.${index}.passMarks`,
                            defaultPassMarks(event.target.value),
                          );
                        }
                      }}
                      error={Boolean(rowErrors?.maxMarks)}
                      helperText={rowErrors?.maxMarks?.message}
                    />
                    <TextField
                      label="Pass marks"
                      required
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                      {...passField}
                      onChange={(event) => {
                        void passField.onChange(event);
                        form.setValue(
                          `subjects.${index}.passEdited`,
                          event.target.value.trim() !== '',
                        );
                      }}
                      error={Boolean(rowErrors?.passMarks)}
                      helperText={rowErrors?.passMarks?.message}
                    />
                    <TextField
                      label="Exam date"
                      type="date"
                      slotProps={{ inputLabel: { shrink: true } }}
                      {...dateField}
                      error={Boolean(rowErrors?.examDate)}
                      helperText={rowErrors?.examDate?.message}
                      sx={{ gridColumn: { sm: '1 / -1' } }}
                    />
                  </Box>
                </Paper>
              );
            })}
          </Stack>
          {subjectsError ? (
            <Typography role="alert" variant="body2" color="error" sx={{ mt: 1 }}>
              {subjectsError}
            </Typography>
          ) : null}
          <Button
            startIcon={<AddIcon />}
            sx={{ mt: 1.5 }}
            disabled={rows.fields.length >= MAX_EXAM_SUBJECTS}
            onClick={() => rows.append(blankSubject())}
          >
            Add subject
          </Button>
        </Box>
      </Stack>
    </ResponsiveDialog>
  );
}
