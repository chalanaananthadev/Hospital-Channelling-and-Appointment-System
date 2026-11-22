"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  AlertBanner,
  EmptyState,
  TableSkeleton
} from "@/components/portal/FeedbackStates";
import { Modal } from "@/components/portal/Modal";
import { StatusBadge } from "@/components/portal/StatusBadge";
import {
  formatTime12h,
  PortalApiError,
  portalRequest
} from "@/lib/portalClient";
import type { ConsultationRecord, QueueAppointment } from "@/types/portal";

interface ConsultationPadProps {
  appointment: QueueAppointment | null;
  onConsultationEnded: () => Promise<void>;
}

export function ConsultationPad({
  appointment,
  onConsultationEnded
}: ConsultationPadProps) {
  const [record, setRecord] = useState<ConsultationRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [diagnosis, setDiagnosis] = useState("");
  const [advice, setAdvice] = useState("");
  const [prescription, setPrescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const [lockModalOpen, setLockModalOpen] = useState(false);
  const [ending, setEnding] = useState(false);

  const appointmentId = appointment?.appointmentId ?? null;
  const canLoadConsultation = Boolean(
    appointment &&
      (appointment.status === "IN_CONSULTATION" ||
        appointment.status === "COMPLETED" ||
        appointment.hasConsultation)
  );

  const loadConsultation = useCallback(async () => {
    if (!appointmentId || !canLoadConsultation) {
      setRecord(null);
      setError(null);
      setFeedback(null);
      return;
    }

    setLoading(true);
    setError(null);
    setFeedback(null);
    setFieldErrors({});
    try {
      const res = await portalRequest<ConsultationRecord>(
        `/consultations/appointments/${appointmentId}`
      );
      setRecord(res);
      setDiagnosis(res.diagnosis ?? "");
      setAdvice(res.advice ?? "");
      setPrescription(res.prescription ?? "");
    } catch (err) {
      setRecord(null);
      setError(
        err instanceof Error
          ? err.message
          : "Unable to open clinical consultation record"
      );
    } finally {
      setLoading(false);
    }
  }, [appointmentId, canLoadConsultation]);

  useEffect(() => {
    void loadConsultation();
  }, [loadConsultation]);

  async function handleSaveDraft(e: FormEvent) {
    e.preventDefault();
    if (!appointmentId || !record || record.readOnly) {
      return;
    }

    setSaving(true);
    setError(null);
    setFeedback(null);
    setFieldErrors({});
    try {
      const updated = await portalRequest<ConsultationRecord>(
        `/consultations/appointments/${appointmentId}`,
        {
          method: "PUT",
          body: JSON.stringify({
            diagnosis: diagnosis.trim() || null,
            advice: advice.trim() || null,
            prescription: prescription.trim() || null
          })
        }
      );
      setRecord(updated);
      setDiagnosis(updated.diagnosis ?? "");
      setAdvice(updated.advice ?? "");
      setPrescription(updated.prescription ?? "");
      setFeedback("Clinical notes saved and encrypted (AES-256-GCM).");
    } catch (err) {
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setError(err.message);
      } else {
        setError("Failed to save clinical notes");
      }
    } finally {
      setSaving(false);
    }
  }

  function requestEndAndLock() {
    setFieldErrors({});
    setError(null);
    if (!diagnosis.trim()) {
      setFieldErrors({
        diagnosis: "Diagnosis summary is required before ending the consultation"
      });
      return;
    }
    setLockModalOpen(true);
  }

  async function handleConfirmEnd() {
    if (!appointmentId) {
      return;
    }

    setEnding(true);
    setError(null);
    try {
      const ended = await portalRequest<ConsultationRecord>(
        `/consultations/appointments/${appointmentId}/end`,
        {
          method: "POST",
          body: JSON.stringify({
            diagnosis: diagnosis.trim(),
            advice: advice.trim() || null,
            prescription: prescription.trim() || null
          })
        }
      );
      setRecord(ended);
      setDiagnosis(ended.diagnosis ?? "");
      setAdvice(ended.advice ?? "");
      setPrescription(ended.prescription ?? "");
      setLockModalOpen(false);
      setFeedback(
        "Consultation ended, locked read-only (BR-09), and appointment marked COMPLETED."
      );
      await onConsultationEnded();
    } catch (err) {
      setLockModalOpen(false);
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setError(err.message);
      } else {
        setError("Unable to end consultation");
      }
    } finally {
      setEnding(false);
    }
  }

  if (!appointment) {
    return (
      <section className="mc-panel">
        <div className="mc-panel-header">
          <div>
            <h2 className="mc-panel-title">Clinical Consultation Pad</h2>
            <p className="mc-panel-subtitle">
              Encrypted diagnosis, medical advice, and prescription authoring.
            </p>
          </div>
          <span className="mc-security-pill">AES-256-GCM Encrypted</span>
        </div>
        <EmptyState
          title="No patient selected"
          description="Call the next patient from the queue or click 'Open Notes' on an active/completed appointment to author or print clinical records."
        />
      </section>
    );
  }

  if (!canLoadConsultation) {
    return (
      <section className="mc-panel">
        <div className="mc-panel-header">
          <div>
            <h2 className="mc-panel-title">
              Appointment #{appointment.appointmentNumber} ·{" "}
              {appointment.patientName}
            </h2>
            <p className="mc-panel-subtitle">
              Ref {appointment.bookingReference} ·{" "}
              {formatTime12h(appointment.estimatedTime)}
            </p>
          </div>
          <StatusBadge value={appointment.status} />
        </div>
        <AlertBanner
          tone="info"
          message="BR-09: The clinical consultation pad opens only after you call and start this patient's appointment."
        />
        <div className="mc-field" style={{ marginTop: 12 }}>
          <span className="mc-label">Reason for Visit (Patient Submitted)</span>
          <div
            style={{
              background: "#f8fbff",
              border: "1px solid #dbeafe",
              borderRadius: 12,
              padding: "12px 14px",
              fontSize: "0.92rem"
            }}
          >
            {appointment.reasonForVisit || "Not specified"}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="mc-panel">
      <div className="mc-panel-header">
        <div>
          <h2 className="mc-panel-title">
            Clinical Consultation · #{appointment.appointmentNumber}
          </h2>
          <p className="mc-panel-subtitle">
            {appointment.patientName} · Ref {appointment.bookingReference}
          </p>
        </div>
        <div className="mc-actions">
          <span className="mc-security-pill">
            AES-256 Encrypted · Doctor &amp; Patient Only
          </span>
          {record?.readOnly && (
            <StatusBadge value="COMPLETED" label="Locked Read-Only (BR-09)" />
          )}
        </div>
      </div>

      {feedback && (
        <AlertBanner
          tone="success"
          message={feedback}
          onDismiss={() => setFeedback(null)}
        />
      )}
      {error && (
        <AlertBanner
          tone="error"
          message={error}
          onDismiss={() => setError(null)}
        />
      )}

      {loading ? (
        <TableSkeleton rows={4} />
      ) : !record ? (
        <EmptyState
          title="Consultation unavailable"
          description="Try reloading once the appointment has been started."
          action={
            <button
              type="button"
              className="mc-btn mc-btn-secondary"
              onClick={() => void loadConsultation()}
            >
              Retry
            </button>
          }
        />
      ) : (
        <>
          {/* Clinical Header Summary */}
          <div
            style={{
              background: "linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%)",
              border: "1px solid #bae6fd",
              borderRadius: 14,
              padding: "14px 16px",
              marginBottom: 16
            }}
          >
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                justifyContent: "space-between",
                gap: 8,
                marginBottom: 8
              }}
            >
              <div>
                <div className="mc-cell-main">
                  {record.doctorName} ({record.doctorSlmcNumber})
                </div>
                <div className="mc-cell-sub">
                  {record.specialisationName} · {record.doctorQualifications}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <div className="mc-cell-main">
                  {record.sessionDate} · {formatTime12h(record.sessionStartTime)}
                </div>
                <div className="mc-cell-sub">
                  {record.sessionType === "PHYSICAL"
                    ? record.roomName ?? "Hospital Room"
                    : "Online Consultation"}
                </div>
              </div>
            </div>
            <div
              style={{
                borderTop: "1px solid #dbeafe",
                paddingTop: 8,
                fontSize: "0.88rem"
              }}
            >
              <strong>Patient Reason for Visit:</strong>{" "}
              {record.reasonForVisit || "Not specified"}
            </div>
          </div>

          {record.readOnly ? (
            <div className="mc-form">
              <div className="mc-field">
                <span className="mc-label">Diagnosis Summary</span>
                <div
                  style={{
                    background: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    borderRadius: 11,
                    padding: "12px 14px",
                    whiteSpace: "pre-wrap"
                  }}
                >
                  {record.diagnosis || "—"}
                </div>
              </div>

              <div className="mc-field">
                <span className="mc-label">Medical Advice</span>
                <div
                  style={{
                    background: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    borderRadius: 11,
                    padding: "12px 14px",
                    whiteSpace: "pre-wrap"
                  }}
                >
                  {record.advice || "No additional advice recorded"}
                </div>
              </div>

              <div className="mc-field">
                <span className="mc-label">Prescription (Rx)</span>
                <div
                  style={{
                    background: "#f0f9ff",
                    border: "1px solid #bae6fd",
                    borderRadius: 11,
                    padding: "12px 14px",
                    whiteSpace: "pre-wrap",
                    fontWeight: 500
                  }}
                >
                  {record.prescription || "No medication prescribed"}
                </div>
              </div>

              <div className="mc-actions mc-no-print" style={{ marginTop: 6 }}>
                <button
                  type="button"
                  className="mc-btn mc-btn-primary"
                  onClick={() => window.print()}
                >
                  Print Summary / Prescription
                </button>
              </div>
            </div>
          ) : (
            <form className="mc-form" onSubmit={handleSaveDraft}>
              <div className="mc-field">
                <label className="mc-label" htmlFor="clin-diagnosis">
                  Diagnosis Summary<span className="mc-required">*</span>
                </label>
                <textarea
                  id="clin-diagnosis"
                  className={`mc-textarea${
                    fieldErrors.diagnosis ? " mc-input-error" : ""
                  }`}
                  value={diagnosis}
                  onChange={(e) => setDiagnosis(e.target.value)}
                  placeholder="Enter clinical findings and primary diagnosis..."
                  maxLength={4000}
                />
                {fieldErrors.diagnosis && (
                  <span className="mc-field-error">{fieldErrors.diagnosis}</span>
                )}
              </div>

              <div className="mc-field">
                <label className="mc-label" htmlFor="clin-advice">
                  Medical Advice &amp; Follow-up
                </label>
                <textarea
                  id="clin-advice"
                  className={`mc-textarea${
                    fieldErrors.advice ? " mc-input-error" : ""
                  }`}
                  value={advice}
                  onChange={(e) => setAdvice(e.target.value)}
                  placeholder="Lifestyle guidance, lab tests recommended, warning signs, or review date..."
                  maxLength={4000}
                />
                {fieldErrors.advice && (
                  <span className="mc-field-error">{fieldErrors.advice}</span>
                )}
              </div>

              <div className="mc-field">
                <label className="mc-label" htmlFor="clin-rx">
                  Prescription (Rx)
                </label>
                <textarea
                  id="clin-rx"
                  className={`mc-textarea${
                    fieldErrors.prescription ? " mc-input-error" : ""
                  }`}
                  value={prescription}
                  onChange={(e) => setPrescription(e.target.value)}
                  placeholder="e.g., Amoxicillin 500mg — 1 capsule TDS after meals for 5 days"
                  maxLength={4000}
                />
                <span className="mc-field-hint">
                  Encrypted at rest with AES-256-GCM. Admin and Manager accounts can never view this record.
                </span>
                {fieldErrors.prescription && (
                  <span className="mc-field-error">
                    {fieldErrors.prescription}
                  </span>
                )}
              </div>

              <div className="mc-actions" style={{ marginTop: 4 }}>
                <button
                  type="submit"
                  className="mc-btn mc-btn-secondary"
                  disabled={saving || ending}
                >
                  {saving ? "Saving..." : "Save Draft Notes"}
                </button>
                <button
                  type="button"
                  className="mc-btn mc-btn-success"
                  disabled={saving || ending}
                  onClick={requestEndAndLock}
                >
                  End &amp; Lock Consultation
                </button>
              </div>
            </form>
          )}
        </>
      )}

      <Modal
        open={lockModalOpen}
        title="End & Lock Clinical Consultation?"
        subtitle="BR-09: Once ended, diagnosis, advice, and prescription become permanently read-only."
        onClose={() => setLockModalOpen(false)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setLockModalOpen(false)}
            >
              Keep Editing
            </button>
            <button
              type="button"
              className="mc-btn mc-btn-success"
              disabled={ending}
              onClick={() => void handleConfirmEnd()}
            >
              {ending
                ? "Locking..."
                : "Confirm End & Mark Patient Completed"}
            </button>
          </>
        }
      >
        <AlertBanner
          tone="info"
          message={`Ending this consultation will encrypt and lock the record for ${appointment.patientName}, mark Appointment #${appointment.appointmentNumber} as COMPLETED, and make the summary available for the patient to view and print.`}
        />
      </Modal>
    </section>
  );
}