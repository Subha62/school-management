import { z } from 'zod';

export const MAX_TITLE = 200;
export const MAX_BODY = 5000;

const AUDIENCES = ['ALL', 'ADMINS', 'TEACHERS', 'STUDENTS', 'SECTION'] as const;

/** `publishAt` and `expiresAt` are `datetime-local` values (or empty); a class is only needed for SECTION. */
export const announcementSchema = z
  .object({
    title: z
      .string()
      .trim()
      .min(1, 'Title is required')
      .max(MAX_TITLE, `Title must be at most ${MAX_TITLE} characters`),
    body: z
      .string()
      .trim()
      .min(1, 'Message is required')
      .max(MAX_BODY, `Message must be at most ${MAX_BODY} characters`),
    audience: z.enum(AUDIENCES, { error: 'Audience is required' }),
    sectionId: z.string(),
    publishAt: z.string(),
    expiresAt: z.string(),
  })
  .superRefine((values, context) => {
    if (values.audience === 'SECTION' && values.sectionId === '') {
      context.addIssue({ code: 'custom', path: ['sectionId'], message: 'Choose a class' });
    }
    if (
      values.publishAt &&
      values.expiresAt &&
      new Date(values.expiresAt).getTime() <= new Date(values.publishAt).getTime()
    ) {
      context.addIssue({
        code: 'custom',
        path: ['expiresAt'],
        message: 'The expiry must be after the start',
      });
    }
  });
export type AnnouncementFormValues = z.infer<typeof announcementSchema>;
