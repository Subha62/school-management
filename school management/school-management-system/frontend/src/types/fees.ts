export type FeeStatus = 'NOT_DUE' | 'DUE' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'WAIVED';
export type PaymentMethod = 'CASH' | 'CHEQUE' | 'BANK_TRANSFER' | 'UPI' | 'CARD' | 'OTHER';

// ---- configuration ----
export interface FeeCategory {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  version: number;
}

export interface FeeCategoryInput {
  name: string;
  description: string | null;
  active: boolean;
  version?: number;
}

export interface FeeStructure {
  id: string;
  academicYearId: string;
  academicYearName: string;
  gradeLevelId: string;
  gradeLevelName: string;
  feeCategoryId: string;
  categoryName: string;
  description: string | null;
  amount: number;
  dueDate: string;
  active: boolean;
  billedStudents: number;
  version: number;
}

/** The year, grade and category are only sent when creating. The server always needs the other fields. */
export interface FeeStructureInput {
  academicYearId?: string;
  gradeLevelId?: string;
  feeCategoryId?: string;
  description: string | null;
  amount: number;
  dueDate: string;
  active: boolean;
  version?: number;
}

export interface GenerateResult {
  created: number;
  alreadyBilled: number;
}

export interface StructureQuery {
  page: number;
  size: number;
  academicYearId?: string;
  gradeLevelId?: string;
  feeCategoryId?: string;
  active?: boolean;
}

// ---- student fees and payments ----
export interface StudentFee {
  id: string;
  studentId: string;
  admissionNumber: string;
  studentName: string;
  className: string | null;
  academicYearId: string;
  academicYearName: string;
  structureId: string;
  categoryName: string;
  description: string | null;
  amount: number;
  paidAmount: number;
  balance: number;
  dueDate: string;
  status: FeeStatus;
  waiveReason: string | null;
  waivedAt: string | null;
  version: number;
}

export interface Payment {
  id: string;
  receiptNumber: string;
  studentFeeId: string;
  studentId: string;
  admissionNumber: string;
  studentName: string;
  categoryName: string;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  paidOn: string;
  receivedByName: string | null;
  createdAt: string;
  reversed: boolean;
  reversalReason: string | null;
  reversedAt: string | null;
  reversedByName: string | null;
}

export interface StudentFeeDetail {
  fee: StudentFee;
  payments: Payment[];
}

export interface PaymentInput {
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  paidOn: string;
}

export interface Receipt {
  receiptNumber: string;
  schoolName: string;
  studentName: string;
  admissionNumber: string;
  className: string | null;
  academicYearName: string;
  categoryName: string;
  description: string | null;
  feeAmount: number;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  paidOn: string;
  receivedByName: string | null;
  /** The fee's balance now, not at the time of payment. */
  balance: number;
  reversed: boolean;
  reversalReason: string | null;
  reversedAt: string | null;
}

export interface StudentFeeQuery {
  page: number;
  size: number;
  sort?: 'dueDate' | 'amount' | 'paidAmount' | 'createdAt';
  direction?: 'asc' | 'desc';
  academicYearId?: string;
  gradeLevelId?: string;
  feeCategoryId?: string;
  studentId?: string;
  status?: FeeStatus;
  search?: string;
}

export interface CategoryQuery {
  page: number;
  size: number;
  search?: string;
  active?: boolean;
}

export interface MyFeeQuery {
  page: number;
  size: number;
  status?: FeeStatus;
}

export interface PaymentQuery {
  page: number;
  size: number;
  from?: string;
  to?: string;
  method?: PaymentMethod;
  studentId?: string;
  reversed?: boolean;
  search?: string;
}

// ---- reports ----
export interface CategoryTotals {
  categoryName: string;
  billed: number;
  collected: number;
  outstanding: number;
}

export interface FeeSummary {
  academicYearId: string;
  gradeLevelId: string | null;
  billed: number;
  collected: number;
  outstanding: number;
  overdue: number;
  waived: number;
  countByStatus: Partial<Record<FeeStatus, number>>;
  byCategory: CategoryTotals[];
}

export interface MethodTotal {
  method: PaymentMethod;
  count: number;
  total: number;
}

export interface Collections {
  from: string | null;
  to: string | null;
  payments: number;
  received: number;
  reversed: number;
  net: number;
  byMethod: MethodTotal[];
}
