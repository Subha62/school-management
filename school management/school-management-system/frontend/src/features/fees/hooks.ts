import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { PageResponse } from '../../types/api';
import type {
  CategoryQuery,
  Collections,
  FeeCategory,
  FeeStructure,
  FeeSummary,
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
import * as api from './api';

/** Every fee query shares this root: a payment must refresh fee lists, payments and report summaries together. */
const ROOT = 'fees';

export const useCategories = (params: CategoryQuery) =>
  useQuery<PageResponse<FeeCategory>, ApiError>({
    queryKey: [ROOT, 'categories', params],
    queryFn: () => api.listCategories(params),
    placeholderData: keepPreviousData,
  });

/** Dropdown options: the first 100 categories. */
export const useCategoryOptions = (active?: boolean) =>
  useQuery<PageResponse<FeeCategory>, ApiError>({
    queryKey: [ROOT, 'category-options', active],
    queryFn: () => api.listCategories({ page: 0, size: 100, active }),
  });

export const useStructures = (params: StructureQuery) =>
  useQuery<PageResponse<FeeStructure>, ApiError>({
    queryKey: [ROOT, 'structures', params],
    queryFn: () => api.listStructures(params),
    placeholderData: keepPreviousData,
  });

export const useStudentFees = (params: StudentFeeQuery, enabled = true) =>
  useQuery<PageResponse<StudentFee>, ApiError>({
    queryKey: [ROOT, 'student-fees', params],
    queryFn: () => api.listStudentFees(params),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useStudentFee = (id: string) =>
  useQuery<StudentFeeDetail, ApiError>({
    queryKey: [ROOT, 'student-fee', id],
    queryFn: () => api.getStudentFee(id),
  });

export const usePayments = (params: PaymentQuery) =>
  useQuery<PageResponse<Payment>, ApiError>({
    queryKey: [ROOT, 'payments', params],
    queryFn: () => api.listPayments(params),
    placeholderData: keepPreviousData,
  });

export const useReceipt = (paymentId: string) =>
  useQuery<Receipt, ApiError>({
    queryKey: [ROOT, 'receipt', paymentId],
    queryFn: () => api.getReceipt(paymentId),
  });

export const useFeeSummary = (academicYearId: string | undefined, gradeLevelId?: string) =>
  useQuery<FeeSummary, ApiError>({
    queryKey: [ROOT, 'summary', academicYearId, gradeLevelId],
    queryFn: () => api.getSummary(academicYearId as string, gradeLevelId),
    enabled: Boolean(academicYearId),
  });

export const useCollections = (from?: string, to?: string) =>
  useQuery<Collections, ApiError>({
    queryKey: [ROOT, 'collections', from, to],
    queryFn: () => api.getCollections(from, to),
  });

// ---- students ----
export const useMyFees = (params: MyFeeQuery) =>
  useQuery<PageResponse<StudentFee>, ApiError>({
    queryKey: [ROOT, 'my-fees', params],
    queryFn: () => api.listMyFees(params),
    placeholderData: keepPreviousData,
  });

export const useMyFee = (id: string) =>
  useQuery<StudentFeeDetail, ApiError>({
    queryKey: [ROOT, 'my-fee', id],
    queryFn: () => api.getMyFee(id),
  });

export const useMyReceipt = (paymentId: string) =>
  useQuery<Receipt, ApiError>({
    queryKey: [ROOT, 'my-receipt', paymentId],
    queryFn: () => api.getMyReceipt(paymentId),
  });

// ---- mutations ----
function useFeeMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export const useSaveCategory = () =>
  useFeeMutation((vars: { id?: string; body: Parameters<typeof api.saveCategory>[1] }) =>
    api.saveCategory(vars.id, vars.body),
  );
export const useDeleteCategory = () => useFeeMutation((id: string) => api.deleteCategory(id));

export const useSaveStructure = () =>
  useFeeMutation((vars: { id?: string; body: Parameters<typeof api.saveStructure>[1] }) =>
    api.saveStructure(vars.id, vars.body),
  );
export const useDeleteStructure = () => useFeeMutation((id: string) => api.deleteStructure(id));
export const useGenerateFees = () => useFeeMutation((id: string) => api.generateFees(id));

export const useRecordPayment = () =>
  useFeeMutation((vars: { feeId: string; body: PaymentInput }) =>
    api.recordPayment(vars.feeId, vars.body),
  );
export const useWaiveFee = () =>
  useFeeMutation((vars: { id: string; reason: string }) => api.waiveFee(vars.id, vars.reason));
export const useUnwaiveFee = () =>
  useFeeMutation((vars: { id: string; reason: string }) => api.unwaiveFee(vars.id, vars.reason));
export const useReversePayment = () =>
  useFeeMutation((vars: { id: string; reason: string }) =>
    api.reversePayment(vars.id, vars.reason),
  );
