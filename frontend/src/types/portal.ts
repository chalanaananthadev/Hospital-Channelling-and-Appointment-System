export type SessionType = "PHYSICAL" | "ONLINE";

export type SessionStatus =
  | "SCHEDULED"
  | "OPEN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type AppointmentStatus =
  | "BOOKED"
  | "ARRIVED"
  | "IN_CONSULTATION"
  | "COMPLETED"
  | "NO_SHOW"
  | "CANCELLED";

export type PaymentStatus = "PENDING" | "PAID";

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fields?: Record<string, string>;
}

export interface Specialisation {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Room {
  id: string;
  name: string;
  location: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DoctorProfile {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  specialisationId: string;
  specialisationName: string;
  slmcNumber: string;
  qualifications: string;
  consultationFee: number;
  bio: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DoctorUserOption {
  userId: string;
  fullName: string;
  email: string;
}

export interface ChannelSession {
  id: string;
  doctorId: string;
  doctorName: string;
  doctorSlmcNumber: string;
  specialisationId: string;
  specialisationName: string;
  roomId: string | null;
  roomName: string | null;
  roomLocation: string | null;
  sessionDate: string;
  startTime: string;
  endTime: string;
  sessionType: SessionType;
  maxPatients: number;
  minutesPerPatient: number;
  fee: number;
  delayMinutes: number;
  status: SessionStatus;
  nowServing: number | null;
  bookedCount: number;
  numbersLeft: number;
  createdAt: string;
  updatedAt: string;
}

export interface PublicSession {
  id: string;
  doctorId: string;
  doctorName: string;
  doctorQualifications: string;
  specialisationId: string;
  specialisationName: string;
  roomName: string | null;
  roomLocation: string | null;
  sessionDate: string;
  startTime: string;
  endTime: string;
  sessionType: SessionType;
  maxPatients: number;
  minutesPerPatient: number;
  fee: number;
  delayMinutes: number;
  status: SessionStatus;
  nowServing: number | null;
  numbersLeft: number;
}

export interface DoctorSessionSummary {
  id: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  sessionType: SessionType;
  roomName: string | null;
  roomLocation: string | null;
  maxPatients: number;
  minutesPerPatient: number;
  fee: number;
  delayMinutes: number;
  status: SessionStatus;
  nowServing: number | null;
  totalActiveBookings: number;
  arrivedCount: number;
  completedCount: number;
  noShowCount: number;
  currentAppointmentId: string | null;
}

export interface QueueAppointment {
  appointmentId: string;
  appointmentNumber: number;
  bookingReference: string;
  patientId: string;
  patientName: string;
  appointmentType: SessionType;
  status: AppointmentStatus;
  paymentStatus: PaymentStatus;
  estimatedTime: string;
  reasonForVisit: string;
  hasConsultation: boolean;
  consultationReadOnly: boolean;
}

export interface DoctorSessionQueue {
  session: DoctorSessionSummary;
  queue: QueueAppointment[];
}

export interface ConsultationRecord {
  id: string;
  appointmentId: string;
  appointmentNumber: number;
  bookingReference: string;
  appointmentStatus: AppointmentStatus;
  sessionType: SessionType;
  sessionDate: string;
  sessionStartTime: string;
  roomName: string | null;
  doctorId: string;
  doctorName: string;
  doctorSlmcNumber: string;
  doctorQualifications: string;
  specialisationName: string;
  patientId: string;
  patientName: string;
  reasonForVisit: string;
  diagnosis: string | null;
  advice: string | null;
  prescription: string | null;
  readOnly: boolean;
  startedAt: string;
  endedAt: string | null;
  updatedAt: string;
}