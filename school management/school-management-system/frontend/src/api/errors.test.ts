import { AxiosError, type AxiosResponse } from 'axios';
import { ApiError, toApiError } from './errors';
import type { ProblemDetail } from '../types/api';

function axiosErrorWith(status: number, data: unknown): AxiosError {
  const response = { status, data, statusText: '', headers: {}, config: {} } as AxiosResponse;
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, response);
}

describe('toApiError', () => {
  it('maps validation problems to field errors and keeps the first message per field', () => {
    const problem: ProblemDetail = {
      type: 'https://school.example/errors/validation',
      title: 'Validation failed',
      status: 400,
      detail: 'One or more request fields are invalid.',
      errors: [
        { field: 'admissionNumber', message: 'Admission number is required' },
        { field: 'admissionNumber', message: 'Second message ignored' },
        { field: 'email', message: 'Email must be valid' },
      ],
      traceId: 'trace-1',
    };

    const error = toApiError(axiosErrorWith(400, problem));

    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual({
      admissionNumber: 'Admission number is required',
      email: 'Email must be valid',
    });
    expect(error.traceId).toBe('trace-1');
  });

  it('shows the business message of 4xx problems', () => {
    const error = toApiError(
      axiosErrorWith(422, {
        type: 'x',
        title: 'Business rule violated',
        status: 422,
        detail: 'Marks cannot exceed the maximum marks.',
      }),
    );

    expect(error.message).toBe('Marks cannot exceed the maximum marks.');
  });

  it('never exposes server error details or raw payloads', () => {
    const error = toApiError(
      axiosErrorWith(500, {
        type: 'x',
        title: 'Internal Server Error',
        status: 500,
        detail: 'java.lang.NullPointerException at com.schoolmanagement.Foo',
        traceId: 'trace-2',
      }),
    );

    expect(error.message).not.toMatch(/NullPointer|schoolmanagement/);
    expect(error.traceId).toBe('trace-2');
  });

  it('does not display unstructured response bodies', () => {
    const error = toApiError(axiosErrorWith(400, '<html>stack trace</html>'));

    expect(error.message).toBe('The request could not be completed.');
  });

  it('reports a network failure without a response', () => {
    const error = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'));

    expect(error.status).toBeNull();
    expect(error.message).toMatch(/Unable to reach the server/);
  });

  it('returns existing ApiError instances unchanged', () => {
    const original = new ApiError('boom');

    expect(toApiError(original)).toBe(original);
  });
});
