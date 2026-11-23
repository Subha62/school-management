import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect, type SelectOption } from '../../../components/FormSelect';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import {
  MAX_BODY,
  announcementSchema,
  type AnnouncementFormValues,
} from '../../../schemas/announcements';
import type { Announcement, AnnouncementManager } from '../../../types/announcements';
import { applyServerFieldErrors, fieldProps, fieldPropsWithHint } from '../../../utils/forms';
import { fromLocalInput, toLocalInput } from '../../assignments/labels';
import { useMyClasses } from '../../operations/hooks';
import { useAdminClassOptions, useSaveAnnouncement } from '../hooks';
import { ADMIN_AUDIENCES, AUDIENCE_LABELS, TEACHER_AUDIENCES } from '../labels';

const FORM_ID = 'announcement-form';

interface AnnouncementFormDialogProps {
  manager: AnnouncementManager;
  /** Null when writing a new announcement. */
  announcement: Announcement | null;
  onClose: () => void;
}

export function AnnouncementFormDialog({
  manager,
  announcement,
  onClose,
}: AnnouncementFormDialogProps) {
  const save = useSaveAnnouncement(manager);
  const notify = useNotify();
  const teacherClasses = useMyClasses(manager === 'teacher');
  const adminClasses = useAdminClassOptions(manager === 'admin');
  const [error, setError] = useState<ApiError | null>(null);
  const editing = announcement !== null;
  // A published announcement keeps its audience; only a draft can be retargeted.
  const audienceFixed = editing && announcement.status !== 'DRAFT';

  const form = useForm<AnnouncementFormValues>({
    resolver: zodResolver(announcementSchema),
    defaultValues: {
      title: announcement?.title ?? '',
      body: announcement?.body ?? '',
      audience: announcement?.audience ?? (manager === 'teacher' ? 'SECTION' : 'ALL'),
      sectionId: announcement?.sectionId ?? '',
      publishAt: announcement?.publishAt ? toLocalInput(announcement.publishAt) : '',
      expiresAt: announcement?.expiresAt ? toLocalInput(announcement.expiresAt) : '',
    },
  });
  const audience = form.watch('audience');

  const audienceOptions: SelectOption[] = (
    manager === 'teacher' ? TEACHER_AUDIENCES : ADMIN_AUDIENCES
  ).map((value) => ({ value, label: AUDIENCE_LABELS[value] }));

  const classOptions: SelectOption[] =
    manager === 'teacher'
      ? (teacherClasses.data ?? []).map((option) => ({
          value: option.sectionId,
          label: option.sectionName,
        }))
      : (adminClasses.data?.content ?? []).map((section) => ({
          value: section.id,
          label: `${section.gradeLevel.name} - ${section.name} (${section.academicYear.name})`,
        }));
  // The class of an existing announcement must stay selectable even if it is not in the list.
  if (announcement?.sectionId && !classOptions.some((o) => o.value === announcement.sectionId)) {
    classOptions.push({
      value: announcement.sectionId,
      label: announcement.sectionName ?? 'Class',
    });
  }
  const classesLoaded = manager === 'teacher' ? teacherClasses.data : adminClasses.data;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: announcement?.id,
        body: {
          title: values.title,
          body: values.body,
          audience: values.audience,
          ...(values.audience === 'SECTION' ? { sectionId: values.sectionId } : {}),
          publishAt: values.publishAt ? fromLocalInput(values.publishAt) : null,
          expiresAt: values.expiresAt ? fromLocalInput(values.expiresAt) : null,
          version: announcement?.version,
        },
      });
      notify.success(editing ? 'Announcement updated' : 'Draft saved');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'title',
        'body',
        'audience',
        'sectionId',
        'publishAt',
        'expiresAt',
      ]);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit announcement' : 'New announcement'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Save draft'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField label="Title" fullWidth required {...fieldProps(form, 'title')} />
        <TextField
          label="Message"
          fullWidth
          required
          multiline
          minRows={4}
          {...fieldPropsWithHint(form, 'body', `Plain text, up to ${MAX_BODY} characters.`)}
        />
        <FormSelect
          control={form.control}
          name="audience"
          label="Audience"
          options={audienceOptions}
          required
          disabled={audienceFixed}
          helperText={
            audienceFixed
              ? 'A published announcement keeps its audience.'
              : manager === 'teacher'
                ? 'You can write to the classes you teach.'
                : undefined
          }
        />
        {audience === 'SECTION' ? (
          <FormSelect
            control={form.control}
            name="sectionId"
            label="Class"
            options={classOptions}
            required
            disabled={audienceFixed}
            helperText={
              classesLoaded && classOptions.length === 0
                ? 'There are no classes to choose.'
                : undefined
            }
          />
        ) : null}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Show from"
            type="datetime-local"
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldPropsWithHint(
              form,
              'publishAt',
              'Optional. Empty means as soon as published.',
            )}
          />
          <TextField
            label="Expires at"
            type="datetime-local"
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldPropsWithHint(form, 'expiresAt', 'Optional. Empty means it never expires.')}
          />
        </Stack>
      </Stack>
    </ResponsiveDialog>
  );
}
