export interface FieldError {
  field: string;
  message: string;
}

/** RFC 7807 problem document as produced by the backend's global exception handler. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: FieldError[];
  timestamp?: string;
  traceId?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SystemInfo {
  name: string;
  status: string;
  profile: string | null;
}
