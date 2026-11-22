"use client";

import "@/styles/portal.css";
import { useCallback, useEffect, useState } from "react";
import { ConsultationPad } from "@/components/doctor/ConsultationPad";
import {
  AlertBanner,
  EmptyState,
  TableSkeleton
} from "@/components/portal/FeedbackStates";
import { Modal } from "@/components/portal/Modal";
import { StatusBadge } from "@/components/portal/StatusBadge";
import {
  buildQuery,
  formatLkr,
  formatTime12h,
  portalRequest,
  todayColomboIso
} from "@/lib/portalClient";
import type {
  DoctorSessionQueue,
  DoctorSessionSummary,
  QueueAppointment
} from "@/types/portal";

export function DoctorCockpit() {
  const [selectedDate, setSelectedDate] = useState(todayColomboIso());
  const [showAllSessions, setShowAllSessions] = useState(false);
  const [colomboTime, setColomboTime] = useState("");

  const [sessions, setSessions] = useState<DoctorSessionSummary[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(
    null
  );

  const [queueData, setQueueData] = useState<DoctorSessionQueue | null>(null);
  const [queueLoading, setQueueLoading] = useState(false);
  const [selectedAppointmentId, setSelectedAppointmentId] = useState<
    string | null
  >(null);

  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [endModalOpen, setEndModalOpen] = useState(false);

  useEffect(() => {
    const updateClock = () => {
      const formatted = new Intl.DateTimeFormat("en-LK", {
        timeZone: "Asia/Colombo",
        weekday: "short",
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit"
      }).format(new Date());
      setColomboTime(formatted);
    };
    updateClock();
    const timer = window.setInterval(updateClock, 30000);
    return () => window.clearInterval(timer);
  }, []);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    setError(null);
    try {
      const qs = buildQuery({
        date: showAllSessions ? undefined : selectedDate,
        all: showAllSessions
      });
      const list = await portalRequest<DoctorSessionSummary[]>(
        `/doctor/sessions${qs}`
      );
      setSessions(list);
      setSelectedSessionId((prev) => {
        if (prev && list.some((s) => s.id === prev)) {
          return prev;
        }
        return list[0]?.id ?? null;
      });
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load your sessions"
      );
      setSessions([]);
      setSelectedSessionId(null);
    } finally {
      setSessionsLoading(false);
    }
  }, [selectedDate, showAllSessions]);

  const loadQueue = useCallback(async (sessionId: string) => {
    setQueueLoading(true);
    setError(null);
    try {
      const data = await portalRequest<DoctorSessionQueue>(
        `/doctor/sessions/${sessionId}/queue`
      );
      setQueueData(data);
      setSelectedAppointmentId((prev) => {
        if (data.session.currentAppointmentId) {
          return data.session.currentAppointmentId;
        }
        if (prev && data.queue.some((a) => a.appointmentId === prev)) {
          return prev;
        }
        return null;
      });
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load patient queue"
      );
      setQueueData(null);
    } finally {
      setQueueLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    if (!selectedSessionId) {
      setQueueData(null);
      setSelectedAppointmentId(null);
      return;
    }
    void loadQueue(selectedSessionId);
  }, [selectedSessionId, loadQueue]);

  function applyQueueUpdate(
    updated: DoctorSessionQueue,
    successMessage: string
  ) {
    setQueueData(updated);
    setSessions((prev) =>
      prev.map((s) => (s.id === updated.session.id ? updated.session : s))
    );
    if (updated.session.currentAppointmentId) {
      setSelectedAppointmentId(updated.session.currentAppointmentId);
    }
    setFeedback(successMessage);
  }

  async function runQueueAction(path: string, successMessage: string) {
    setActionBusy(true);
    setError(null);
    setFeedback(null);
    try {
      const updated = await portalRequest<DoctorSessionQueue>(path, {
        method: "POST"
      });
      applyQueueUpdate(updated, successMessage);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setActionBusy(false);
    }
  }

  const activeSession = queueData?.session ?? null;
  const queue = queueData?.queue ?? [];
  const inConsultationAppt: QueueAppointment | null =
    queue.find((a) => a.status === "IN_CONSULTATION") ?? null;
  const selectedAppointment: QueueAppointment | null =
    queue.find((a) => a.appointmentId === selectedAppointmentId) ??
    inConsultationAppt;

  const readyStatus =
    activeSession?.sessionType === "PHYSICAL" ? "ARRIVED" : "BOOKED";
  const waitingReadyCount = queue.filter(
    (a) => a.status === readyStatus
  ).length;

  return (
    <main className="mc-shell">
      <header className="mc-hero mc-no-print">
        <div>
          <span className="mc-hero-kicker">
            Lanka Care Hospital · Consultant Clinical Cockpit
          </span>
          <h1>Doctor Workspace &amp; Live Queue</h1>
          <p>
            Run your daily channelling sessions, call waiting patients in order (BR-08), and author AES-256-GCM encrypted clinical notes and prescriptions (BR-09).
          </p>
        </div>
        <div className="mc-hero-meta">
          <div className="mc-clock-pill">
            <span>Asia/Colombo</span>
            <span>·</span>
            <span>{colomboTime || "Loading time..."}</span>
          </div>
        </div>
      </header>

      {feedback && (
        <div className="mc-no-print">
          <AlertBanner
            tone="success"
            message={feedback}
            onDismiss={() => setFeedback(null)}
          />
        </div>
      )}
      {error && (
        <div className="mc-no-print">
          <AlertBanner
            tone="error"
            message={error}
            onDismiss={() => setError(null)}
          />
        </div>
      )}

      {/* Session Picker Bar */}
      <section className="mc-panel mc-no-print" style={{ marginBottom: 20 }}>
        <div className="mc-panel-header">
          <div>
            <h2 className="mc-panel-title">My Channelling Sessions</h2>
            <p className="mc-panel-subtitle">
              Only sessions assigned to your consultant profile are shown (server-enforced ownership).
            </p>
          </div>
          <div className="mc-actions">
            <input
              type="date"
              className="mc-input"
              style={{ width: "auto" }}
              value={selectedDate}
              disabled={showAllSessions}
              onChange={(e) => setSelectedDate(e.target.value)}
            />
            <button
              type="button"
              className={`mc-btn ${
                !showAllSessions && selectedDate === todayColomboIso()
                  ? "mc-btn-primary"
                  : "mc-btn-secondary"
              }`}
              onClick={() => {
                setShowAllSessions(false);
                setSelectedDate(todayColomboIso());
              }}
            >
              Today
            </button>
            <button
              type="button"
              className={`mc-btn ${
                showAllSessions ? "mc-btn-primary" : "mc-btn-ghost"
              }`}
              onClick={() => setShowAllSessions((prev) => !prev)}
            >
              {showAllSessions ? "Showing All Dates" : "View All Dates"}
            </button>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => void loadSessions()}
            >
              Refresh
            </button>
          </div>
        </div>

        {sessionsLoading ? (
          <TableSkeleton rows={2} />
        ) : sessions.length === 0 ? (
          <EmptyState
            title={
              showAllSessions
                ? "No sessions assigned yet"
                : `No sessions scheduled for ${selectedDate}`
            }
            description="Pick another date or click 'View All Dates' to see your upcoming and past clinics."
            action={
              !showAllSessions ? (
                <button
                  type="button"
                  className="mc-btn mc-btn-primary"
                  onClick={() => setShowAllSessions(true)}
                >
                  View All My Sessions
                </button>
              ) : undefined
            }
          />
        ) : (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
              gap: 12
            }}
          >
            {sessions.map((s) => {
              const isSelected = s.id === selectedSessionId;
              return (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => setSelectedSessionId(s.id)}
                  style={{
                    textAlign: "left",
                    padding: "14px 16px",
                    borderRadius: 14,
                    cursor: "pointer",
                    background: isSelected
                      ? "linear-gradient(135deg, #e0f2fe 0%, #ffffff 100%)"
                      : "#fafdff",
                    border: isSelected
                      ? "2px solid #0284c7"
                      : "1px solid #dbeafe",
                    color: "#0f2942",
                    boxShadow: isSelected
                      ? "0 8px 20px rgba(2, 132, 199, 0.14)"
                      : "none"
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 8,
                      marginBottom: 8
                    }}
                  >
                    <StatusBadge value={s.status} />
                    <StatusBadge value={s.sessionType} />
                  </div>
                  <div className="mc-cell-main">
                    {s.sessionDate} · {formatTime12h(s.startTime)} –{" "}
                    {formatTime12h(s.endTime)}
                  </div>
                  <div className="mc-cell-sub">
                    {s.sessionType === "PHYSICAL"
                      ? s.roomName ?? "Assigned Room"
                      : "Online Consultation Room"}{" "}
                    · {s.totalActiveBookings}/{s.maxPatients} booked
                  </div>
                  <div className="mc-cell-sub">
                    {formatLkr(s.fee)}
                    {s.delayMinutes > 0 ? ` · Delayed +${s.delayMinutes}m` : ""}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </section>

      {activeSession && (
        <>
          {/* KPI Summary Strip for Selected Session */}
          <section
            className="mc-stats-grid mc-no-print"
            aria-label="Session queue statistics"
          >
            <div className="mc-stat-card">
              <div className="mc-stat-label">Active Bookings</div>
              <div className="mc-stat-value">
                {activeSession.totalActiveBookings}
              </div>
              <div className="mc-stat-sub">
                Max capacity: {activeSession.maxPatients}
              </div>
            </div>
            <div className="mc-stat-card">
              <div className="mc-stat-label">
                {activeSession.sessionType === "PHYSICAL"
                  ? "Arrived & Waiting"
                  : "Booked & Ready"}
              </div>
              <div className="mc-stat-value">{waitingReadyCount}</div>
              <div className="mc-stat-sub">
                {activeSession.sessionType === "PHYSICAL"
                  ? "Checked in at reception"
                  : "Ready for online call"}
              </div>
            </div>
            <div className="mc-stat-card">
              <div className="mc-stat-label">Completed</div>
              <div className="mc-stat-value">{activeSession.completedCount}</div>
              <div className="mc-stat-sub">Consultations finished</div>
            </div>
            <div className="mc-stat-card">
              <div className="mc-stat-label">No-Show</div>
              <div className="mc-stat-value">{activeSession.noShowCount}</div>
              <div className="mc-stat-sub">Missed appointments</div>
            </div>
          </section>

          {/* Two-column Cockpit: Left Queue + Right Clinical Pad */}
          <div className="mc-cockpit">
            {/* LEFT COLUMN: Now Serving + Patient Queue Call List */}
            <div className="mc-no-print">
              <div className="mc-serving-card">
                <div
                  style={{
                    display: "flex",
                    flexWrap: "wrap",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: 14
                  }}
                >
                  <div>
                    <div
                      style={{
                        fontSize: "0.78rem",
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        opacity: 0.9,
                        fontWeight: 700
                      }}
                    >
                      Now Serving
                    </div>
                    <div className="mc-serving-number">
                      {activeSession.nowServing !== null
                        ? `#${activeSession.nowServing}`
                        : "—"}
                    </div>
                    <div style={{ fontSize: "0.9rem", marginTop: 6, opacity: 0.95 }}>
                      {inConsultationAppt
                        ? `${inConsultationAppt.patientName} (${inConsultationAppt.bookingReference})`
                        : activeSession.status === "IN_PROGRESS"
                          ? "Ready to call the next patient in queue"
                          : `Session status: ${activeSession.status.replaceAll("_", " ")}`}
                    </div>
                  </div>

                  <div className="mc-actions">
                    {(activeSession.status === "SCHEDULED" ||
                      activeSession.status === "OPEN") && (
                      <button
                        type="button"
                        className="mc-btn mc-btn-success"
                        disabled={actionBusy}
                        onClick={() =>
                          void runQueueAction(
                            `/doctor/sessions/${activeSession.id}/start`,
                            "Session started! You can now call waiting patients."
                          )
                        }
                      >
                        Start Session
                      </button>
                    )}

                    {activeSession.status === "IN_PROGRESS" && (
                      <>
                        <button
                          type="button"
                          className="mc-btn"
                          style={{
                            background: "#ffffff",
                            color: "#0369a1",
                            fontWeight: 700
                          }}
                          disabled={
                            actionBusy ||
                            inConsultationAppt !== null ||
                            waitingReadyCount === 0
                          }
                          onClick={() =>
                            void runQueueAction(
                              `/doctor/sessions/${activeSession.id}/call-next`,
                              "Called next patient and opened clinical consultation pad."
                            )
                          }
                        >
                          Call Next Patient
                        </button>
                        <button
                          type="button"
                          className="mc-btn mc-btn-danger"
                          disabled={actionBusy || inConsultationAppt !== null}
                          onClick={() => setEndModalOpen(true)}
                        >
                          End Session
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <section className="mc-panel">
                <div className="mc-panel-header">
                  <div>
                    <h2 className="mc-panel-title">Patient Queue Call List</h2>
                    <p className="mc-panel-subtitle">
                      {activeSession.sessionType === "PHYSICAL"
                        ? "BR-08: Physical patients must check in as ARRIVED at reception before being called."
                        : "BR-08: Online patients move directly from BOOKED to IN CONSULTATION."}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="mc-btn mc-btn-ghost mc-btn-sm"
                    onClick={() => void loadQueue(activeSession.id)}
                  >
                    Reload Queue
                  </button>
                </div>

                {queueLoading ? (
                  <TableSkeleton rows={5} />
                ) : queue.length === 0 ? (
                  <EmptyState
                    title="No appointments in this session yet"
                    description="Booked patients will appear here in appointment number order."
                  />
                ) : (
                  <div className="mc-table-wrap">
                    <table className="mc-table" style={{ minWidth: 560 }}>
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Patient &amp; Payment</th>
                          <th>Est. Time</th>
                          <th>Status</th>
                          <th>Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {queue.map((item) => {
                          const isSelected =
                            selectedAppointment?.appointmentId ===
                            item.appointmentId;
                          const canCallRow =
                            activeSession.status === "IN_PROGRESS" &&
                            inConsultationAppt === null &&
                            item.status === readyStatus;
                          const canMarkNoShow =
                            activeSession.status === "IN_PROGRESS" &&
                            (item.status === "BOOKED" ||
                              item.status === "ARRIVED" ||
                              item.status === "IN_CONSULTATION");

                          return (
                            <tr
                              key={item.appointmentId}
                              style={
                                isSelected
                                  ? { background: "#f0f9ff" }
                                  : undefined
                              }
                            >
                              <td>
                                <div className="mc-cell-main">
                                  #{item.appointmentNumber}
                                </div>
                              </td>
                              <td>
                                <div className="mc-cell-main">
                                  {item.patientName}
                                </div>
                                <div className="mc-cell-sub">
                                  {item.bookingReference} ·{" "}
                                  {item.paymentStatus}
                                </div>
                              </td>
                              <td>
                                <div className="mc-cell-sub">
                                  {formatTime12h(item.estimatedTime)}
                                </div>
                              </td>
                              <td>
                                <StatusBadge value={item.status} />
                              </td>
                              <td>
                                <div className="mc-actions">
                                  {canCallRow && (
                                    <button
                                      type="button"
                                      className="mc-btn mc-btn-primary mc-btn-sm"
                                      disabled={actionBusy}
                                      onClick={() =>
                                        void runQueueAction(
                                          `/doctor/sessions/${activeSession.id}/appointments/${item.appointmentId}/call`,
                                          `Called #${item.appointmentNumber} (${item.patientName}).`
                                        )
                                      }
                                    >
                                      Call
                                    </button>
                                  )}

                                  {item.status === "IN_CONSULTATION" && (
                                    <>
                                      <button
                                        type="button"
                                        className="mc-btn mc-btn-secondary mc-btn-sm"
                                        onClick={() =>
                                          setSelectedAppointmentId(
                                            item.appointmentId
                                          )
                                        }
                                      >
                                        Open Notes
                                      </button>
                                      <button
                                        type="button"
                                        className="mc-btn mc-btn-success mc-btn-sm"
                                        disabled={actionBusy}
                                        onClick={() =>
                                          void runQueueAction(
                                            `/doctor/sessions/${activeSession.id}/appointments/${item.appointmentId}/complete`,
                                            `Marked #${item.appointmentNumber} COMPLETED and locked consultation.`
                                          )
                                        }
                                      >
                                        Complete
                                      </button>
                                    </>
                                  )}

                                  {(item.status === "COMPLETED" ||
                                    item.hasConsultation) &&
                                    item.status !== "IN_CONSULTATION" && (
                                      <button
                                        type="button"
                                        className="mc-btn mc-btn-secondary mc-btn-sm"
                                        onClick={() =>
                                          setSelectedAppointmentId(
                                            item.appointmentId
                                          )
                                        }
                                      >
                                        View Notes
                                      </button>
                                    )}

                                  {canMarkNoShow && (
                                    <button
                                      type="button"
                                      className="mc-btn mc-btn-danger mc-btn-sm"
                                      disabled={actionBusy}
                                      onClick={() =>
                                        void runQueueAction(
                                          `/doctor/sessions/${activeSession.id}/appointments/${item.appointmentId}/no-show`,
                                          `Marked #${item.appointmentNumber} as NO_SHOW.`
                                        )
                                      }
                                    >
                                      No-Show
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>

            {/* RIGHT COLUMN: Encrypted Clinical Consultation Pad */}
            <ConsultationPad
              appointment={selectedAppointment}
              onConsultationEnded={async () => {
                if (activeSession) {
                  await loadQueue(activeSession.id);
                  await loadSessions();
                }
              }}
            />
          </div>
        </>
      )}

      <Modal
        open={endModalOpen}
        title="End Channelling Session?"
        subtitle="Marks this session as COMPLETED and closes the live queue."
        onClose={() => setEndModalOpen(false)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setEndModalOpen(false)}
            >
              Cancel
            </button>
            <button
              type="button"
              className="mc-btn mc-btn-danger-solid"
              disabled={actionBusy}
              onClick={async () => {
                if (!activeSession) {
                  return;
                }
                setEndModalOpen(false);
                await runQueueAction(
                  `/doctor/sessions/${activeSession.id}/end`,
                  "Session marked COMPLETED and public catalog cache refreshed."
                );
              }}
            >
              Yes, End Session
            </button>
          </>
        }
      >
        <AlertBanner
          tone="info"
          message="Make sure all waiting patients have been seen or marked as NO_SHOW before ending the session."
        />
      </Modal>
    </main>
  );
}