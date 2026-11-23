import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type {
  CategoryQuery,
  Collections,
  FeeCategory,
  FeeCategoryInput,
  FeeStructure,
  FeeStructureInput,
  FeeSummary,
  GenerateResult,
  MyFeeQuery,
  Payment,
  PaymentInput,
  PaymentQuery,
  Receipt,
  StructureQuery,
  StudentFee,
  StudentFeeDetail,
  StudentFeeQuery,
} from '../../types/fees';

const ADMIN = '/admin';
const STUDENT = '/student';

async function get<T>(path: string, params?: object): Promise<T> {
  const { data } = await apiClient.get<T>(path, { params });
  return data;
}

async function post<T>(path: string, body?: object): Promise<T> {
  const { data } = await apiClient.post<T>(path, body);
  return data;
}

// ---- categories ----
export const listCategories = (params: CategoryQuery) =>
  get<PageResponse<FeeCategory>>(`${ADMIN}/fee-categories`, params);

export async function saveCategory(
  id: string | undefined,
  body: FeeCategoryInput,
): Promise<FeeCategory> {
  const { data } = id
    ? await apiClient.put<FeeCategory>(`${ADMIN}/fee-categories/${id}`, body)
    : await apiClient.post<FeeCategory>(`${ADMIN}/fee-categories`, body);
  return data;
}

export const deleteCategory = async (id: string): Promise<void> => {
  await apiClient.delete(`${ADMIN}/fee-categories/${id}`);
};

// ---- structures ----
export const listStructures = (params: StructureQuery) =>
  get<PageResponse<FeeStructure>>(`${ADMIN}/fee-structures`, params);

export async function saveStructure(
  id: string | undefined,
  body: FeeStructureInput,
): Promise<FeeStructure> {
  const { data } = id
    ? await apiClient.put<FeeStructure>(`${ADMIN}/fee-structures/${id}`, body)
    : await apiClient.post<FeeStructure>(`${ADMIN}/fee-structures`, body);
  return data;
}

export const deleteStructure = async (id: string): Promise<void> => {
  await apiClient.delete(`${ADMIN}/fee-structures/${id}`);
};

export const generateFees = (id: string) =>
  post<GenerateResult>(`${ADMIN}/fee-structures/${id}/generate`);

// ---- student fees and payments (administrators) ----
export const listStudentFees = (params: StudentFeeQuery) =>
  get<PageResponse<StudentFee>>(`${ADMIN}/student-fees`, params);
export const getStudentFee = (id: string) => get<StudentFeeDetail>(`${ADMIN}/student-fees/${id}`);

export const recordPayment = (feeId: string, body: PaymentInput) =>
  post<Payment>(`${ADMIN}/student-fees/${feeId}/payments`, body);
export const waiveFee = (feeId: string, reason: string) =>
  post<StudentFee>(`${ADMIN}/student-fees/${feeId}/waive`, { reason });
export const unwaiveFee = (feeId: string, reason: string) =>
  post<StudentFee>(`${ADMIN}/student-fees/${feeId}/unwaive`, { reason });

export const listPayments = (params: PaymentQuery) =>
  get<PageResponse<Payment>>(`${ADMIN}/payments`, params);
export const getReceipt = (paymentId: string) =>
  get<Receipt>(`${ADMIN}/payments/${paymentId}/receipt`);
export const reversePayment = (paymentId: string, reason: string) =>
  post<Payment>(`${ADMIN}/payments/${paymentId}/reverse`, { reason });

// ---- reports ----
export const getSummary = (academicYearId: string, gradeLevelId?: string) =>
  get<FeeSummary>(`${ADMIN}/fee-reports/summary`, { academicYearId, gradeLevelId });
export const getCollections = (from?: string, to?: string) =>
  get<Collections>(`${ADMIN}/fee-reports/collections`, { from, to });

// ---- students ----
export const listMyFees = (params: MyFeeQuery) =>
  get<PageResponse<StudentFee>>(`${STUDENT}/fees`, params);
export const getMyFee = (id: string) => get<StudentFeeDetail>(`${STUDENT}/fees/${id}`);
export const getMyReceipt = (paymentId: string) =>
  get<Receipt>(`${STUDENT}/payments/${paymentId}/receipt`);
