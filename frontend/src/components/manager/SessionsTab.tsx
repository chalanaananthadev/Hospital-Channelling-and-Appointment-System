"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  AlertBanner,
  EmptyState,
  TableSkeleton
} from "@/components/portal/FeedbackStates";
import { Modal } from "@/components/portal/Modal";
import { PaginationBar } from "@/components/portal/PaginationBar";
import { StatusBadge } from "@/components/portal/StatusBadge";
import {
  addMinutesToTime,
  buildQuery,
  formatLkr,
  formatTime12h,
  PortalApiError,
  portalRequest,
  todayColomboIso,
  toTimeInput
} from "@/lib/portalClient";
import type {
  ChannelSession,
  DoctorProfile,
  PageResponse,
  Room,
  SessionStatus,
  SessionType,
  Specialisation
} from "@/types/portal";

interface SessionsTabProps {
  onChanged?: () => void;
}

export function SessionsTab({ onChanged }: SessionsTabProps) {
  const [data, setData] = useState<PageResponse<ChannelSession> | null>(null);
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [specialisations, setSpecialisations] = useState<Specialisation[]>([]);

  const [search, setSearch] = useState("");
  const [querySearch, setQuerySearch] = useState("");
  const [filterDate, setFilterDate] = useState("");
  const [filterType, setFilterType] = useState<"" | SessionType>("");
  const [filterStatus, setFilterStatus] = useState<"" | SessionStatus>("");
  const [filterSpecId, setFilterSpecId] = useState("");
  const [page, setPage] = useState(0);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  // Create / Edit modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ChannelSession | null>(null);
  const [doctorId, setDoctorId] = useState("");
  const [sessionType, setSessionType] = useState<SessionType>("PHYSICAL");
  const [roomId, setRoomId] = useState("");
  const [sessionDate, setSessionDate] = useState(todayColomboIso());
  const [startTime, setStartTime] = useState("09:00");
  const [endTime, setEndTime] = useState("11:30");
  const [maxPatients, setMaxPatients] = useState("20");
  const [minutesPerPatient, setMinutesPerPatient] = useState("10");
  const [fee, setFee] = useState("3500.00");
  const [initialStatus, setInitialStatus] = useState<SessionStatus>("OPEN");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Delay modal state
  const [delayTarget, setDelayTarget] = useState<ChannelSession | null>(null);
  const [delayMinutes, setDelayMinutes] = useState("15");
  const [delayError, setDelayError] = useState<string | null>(null);
  const [delaySubmitting, setDelaySubmitting] = useState(false);

  // Cancel modal state
  const [cancelTarget, setCancelTarget] = useState<ChannelSession | null>(null);
  const [cancelSubmitting, setCancelSubmitting] = useState(false);

  const [busyId, setBusyId] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = buildQuery({
        search: querySearch,
        date: filterDate || undefined,
        sessionType: filterType || undefined,
        status: filterStatus || undefined,
        specialisationId: filterSpecId || undefined,
        page,
        size: 10
      });
      const res = await portalRequest<PageResponse<ChannelSession>>(
        `/manager/sessions${qs}`
      );
      setData(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load sessions");
    } finally {
      setLoading(false);
    }
  }, [querySearch, filterDate, filterType, filterStatus, filterSpecId, page]);

  const loadLookups = useCallback(async () => {
    try {
      const [docsPage, activeRooms, activeSpecs] = await Promise.all([
        portalRequest<PageResponse<DoctorProfile>>(
          "/manager/doctors?active=true&size=100"
        ),
        portalRequest<Room[]>("/manager/rooms/active"),
        portalRequest<Specialisation[]>("/manager/specialisations/active")
      ]);
      setDoctors(docsPage.items);
      setRooms(activeRooms);
      setSpecialisations(activeSpecs);
    } catch {
      // Surfacing on action if needed
    }
  }, []);

  useEffect(() => {
    void loadSessions();
  }, [loadSessions]);

  useEffect(() => {
    void loadLookups();
  }, [loadLookups]);

  async function openCreate() {
    await loadLookups();
    setEditing(null);
    const defaultDoctor = doctors[0];
    setDoctorId(defaultDoctor?.id ?? "");
    setSessionType("PHYSICAL");
    setRoomId(rooms[0]?.id ?? "");
    setSessionDate(todayColomboIso());
    setStartTime("09:00");
    setEndTime("11:30");
    setMaxPatients("20");
    setMinutesPerPatient("10");
    setFee(defaultDoctor ? String(defaultDoctor.consultationFee) : "3500.00");
    setInitialStatus("OPEN");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  async function openEdit(session: ChannelSession) {
    await loadLookups();
    setEditing(session);
    setDoctorId(session.doctorId);
    setSessionType(session.sessionType);
    setRoomId(session.roomId ?? "");
    setSessionDate(session.sessionDate);
    setStartTime(toTimeInput(session.startTime));
    setEndTime(toTimeInput(session.endTime));
    setMaxPatients(String(session.maxPatients));
    setMinutesPerPatient(String(session.minutesPerPatient));
    setFee(String(session.fee));
    setInitialStatus(session.status);
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  function handleDoctorChange(nextDoctorId: string) {
    setDoctorId(nextDoctorId);
    if (!editing) {
      const picked = doctors.find((d) => d.id === nextDoctorId);
      if (picked) {
        setFee(String(picked.consultationFee));
      }
    }
  }

  async function handleSaveSession(e: FormEvent) {
    e.preventDefault();
    const nextErrors: Record<string, string> = {};

    if (!doctorId) {
      nextErrors.doctorId = "Select a consultant doctor";
    }
    if (sessionType === "PHYSICAL" && !roomId) {
      nextErrors.roomId = "BR-01: Physical sessions require a consultation room";
    }
    if (!sessionDate) {
      nextErrors.sessionDate = "Session date is required";
    }
    if (!startTime) {
      nextErrors.startTime = "Start time is required";
    }
    if (!endTime) {
      nextErrors.endTime = "End time is required";
    } else if (startTime && startTime >= endTime) {
      nextErrors.endTime = "End time must be later than start time";
    }

    const parsedMax = Number(maxPatients);
    if (Number.isNaN(parsedMax) || parsedMax < 1 || parsedMax > 60) {
      nextErrors.maxPatients = "BR-01: Must be between 1 and 60 patients";
    }
    const parsedMin = Number(minutesPerPatient);
    if (Number.isNaN(parsedMin) || parsedMin < 5 || parsedMin > 30) {
      nextErrors.minutesPerPatient = "BR-01: Must be between 5 and 30 minutes";
    }
    const parsedFee = Number(fee);
    if (Number.isNaN(parsedFee) || parsedFee <= 0) {
      nextErrors.fee = "Enter a valid fee in LKR";
    }

    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const basePayload = {
        doctorId,
        roomId: sessionType === "PHYSICAL" ? roomId : null,
        sessionDate,
        startTime,
        endTime,
        sessionType,
        maxPatients: parsedMax,
        minutesPerPatient: parsedMin,
        fee: parsedFee
      };

      if (editing) {
        await portalRequest<ChannelSession>(`/manager/sessions/${editing.id}`, {
          method: "PUT",
          body: JSON.stringify(basePayload)
        });
        setFeedback("Session updated and estimated times recalculated.");
      } else {
        await portalRequest<ChannelSession>("/manager/sessions", {
          method: "POST",
          body: JSON.stringify({ ...basePayload, status: initialStatus })
        });
        setFeedback("New channelling session scheduled with clash verification.");
      }

      setModalOpen(false);
      await loadSessions();
      onChanged?.();
    } catch (err) {
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setFormError(err.message);
      } else {
        setFormError("Unable to save session");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleOpenStatus(session: ChannelSession) {
    const nextStatus: SessionStatus =
      session.status === "OPEN" ? "SCHEDULED" : "OPEN";
    setBusyId(session.id);
    setError(null);
    try {
      await portalRequest<ChannelSession>(
        `/manager/sessions/${session.id}/status`,
        {
          method: "PATCH",
          body: JSON.stringify({ status: nextStatus })
        }
      );
      setFeedback(`Session status updated to ${nextStatus}.`);
      await loadSessions();
      onChanged?.();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to update session status"
      );
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmDelay(e: FormEvent) {
    e.preventDefault();
    if (!delayTarget) {
      return;
    }
    const mins = Number(delayMinutes);
    if (Number.isNaN(mins) || mins < 0 || mins > 360) {
      setDelayError("Delay must be between 0 and 360 minutes");
      return;
    }

    setDelaySubmitting(true);
    setDelayError(null);
    try {
      await portalRequest<ChannelSession>(
        `/manager/sessions/${delayTarget.id}/delay`,
        {
          method: "PATCH",
          body: JSON.stringify({ delayMinutes: mins })
        }
      );
      setFeedback(
        `Session delayed by ${mins} min. All active appointment estimated times recalculated (BR-06).`
      );
      setDelayTarget(null);
      await loadSessions();
      onChanged?.();
    } catch (err) {
      setDelayError(
        err instanceof Error ? err.message : "Unable to update delay"
      );
    } finally {
      setDelaySubmitting(false);
    }
  }

  async function handleConfirmCancel() {
    if (!cancelTarget) {
      return;
    }
    setCancelSubmitting(true);
    setError(null);
    try {
      await portalRequest<ChannelSession>(
        `/manager/sessions/${cancelTarget.id}/cancel`,
        { method: "POST" }
      );
      setFeedback(
        `Session cancelled. All active bookings were cancelled and logged in audit_logs.`
      );
      setCancelTarget(null);
      await loadSessions();
      onChanged?.();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to cancel session"
      );
    } finally {
      setCancelSubmitting(false);
    }
  }

  const parsedDelayPreview = Math.max(0, Number(delayMinutes) || 0);

  return (
    <section className="mc-panel">
      <div className="mc-panel-header">
        <div>
          <h2 className="mc-panel-title">Channelling Sessions Schedule</h2>
          <p className="mc-panel-subtitle">
            Schedule physical and online clinics with automatic doctor/room clash prevention (BR-01, BR-02) and live delay recalculation (BR-06).
          </p>
        </div>
        <button
          type="button"
          className="mc-btn mc-btn-primary"
          onClick={() => void openCreate()}
        >
          + Schedule Session
        </button>
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

      <form
        className="mc-toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setQuerySearch(search.trim());
        }}
      >
        <input
          type="search"
          className="mc-input mc-search-input"
          placeholder="Search doctor, department, or room..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <input
          type="date"
          className="mc-input"
          value={filterDate}
          onChange={(e) => {
            setFilterDate(e.target.value);
            setPage(0);
          }}
        />
        <select
          className="mc-select"
          value={filterSpecId}
          onChange={(e) => {
            setFilterSpecId(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Departments</option>
          {specialisations.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select
          className="mc-select"
          value={filterType}
          onChange={(e) => {
            setFilterType(e.target.value as "" | SessionType);
            setPage(0);
          }}
        >
          <option value="">All Types</option>
          <option value="PHYSICAL">Physical</option>
          <option value="ONLINE">Online</option>
        </select>
        <select
          className="mc-select"
          value={filterStatus}
          onChange={(e) => {
            setFilterStatus(e.target.value as "" | SessionStatus);
            setPage(0);
          }}
        >
          <option value="">All Statuses</option>
          <option value="SCHEDULED">Scheduled</option>
          <option value="OPEN">Open</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <button type="submit" className="mc-btn mc-btn-secondary">
          Filter
        </button>
        {(querySearch ||
          filterDate ||
          filterType ||
          filterStatus ||
          filterSpecId) && (
          <button
            type="button"
            className="mc-btn mc-btn-ghost"
            onClick={() => {
              setSearch("");
              setQuerySearch("");
              setFilterDate("");
              setFilterType("");
              setFilterStatus("");
              setFilterSpecId("");
              setPage(0);
            }}
          >
            Clear
          </button>
        )}
      </form>

      {loading ? (
        <TableSkeleton rows={5} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          title="No channelling sessions found"
          description="Schedule a physical or online session for an active consultant."
          action={
            <button
              type="button"
              className="mc-btn mc-btn-primary"
              onClick={() => void openCreate()}
            >
              + Schedule Session
            </button>
          }
        />
      ) : (
        <>
          <div className="mc-table-wrap">
            <table className="mc-table">
              <thead>
                <tr>
                  <th>Doctor &amp; Dept</th>
                  <th>Schedule (Colombo)</th>
                  <th>Type &amp; Room</th>
                  <th>Capacity &amp; Fee</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((session) => {
                  const canEdit =
                    session.status === "SCHEDULED" || session.status === "OPEN";
                  const canDelayOrCancel =
                    session.status !== "COMPLETED" &&
                    session.status !== "CANCELLED";

                  return (
                    <tr key={session.id}>
                      <td>
                        <div className="mc-cell-main">{session.doctorName}</div>
                        <div className="mc-cell-sub">
                          {session.specialisationName} · {session.doctorSlmcNumber}
                        </div>
                      </td>
                      <td>
                        <div className="mc-cell-main">{session.sessionDate}</div>
                        <div className="mc-cell-sub">
                          {formatTime12h(session.startTime)} –{" "}
                          {formatTime12h(session.endTime)}
                        </div>
                        {session.delayMinutes > 0 && (
                          <div style={{ marginTop: 4 }}>
                            <StatusBadge
                              value="DELAYED"
                              label={`Delayed +${session.delayMinutes}m`}
                            />
                          </div>
                        )}
                      </td>
                      <td>
                        <StatusBadge value={session.sessionType} />
                        <div className="mc-cell-sub">
                          {session.sessionType === "PHYSICAL"
                            ? `${session.roomName ?? "Room"}${
                                session.roomLocation
                                  ? ` (${session.roomLocation})`
                                  : ""
                              }`
                            : "Secure Online Room"}
                        </div>
                      </td>
                      <td>
                        <div className="mc-cell-main">
                          {session.numbersLeft} left / {session.maxPatients}
                        </div>
                        <div className="mc-cell-sub">
                          {formatLkr(session.fee)} · {session.minutesPerPatient}m/pt
                        </div>
                      </td>
                      <td>
                        <StatusBadge value={session.status} />
                        {session.nowServing !== null && (
                          <div className="mc-cell-sub">
                            Now serving #{session.nowServing}
                          </div>
                        )}
                      </td>
                      <td>
                        <div className="mc-actions">
                          {canEdit && (
                            <>
                              <button
                                type="button"
                                className="mc-btn mc-btn-secondary mc-btn-sm"
                                onClick={() => void openEdit(session)}
                              >
                                Edit
                              </button>
                              <button
                                type="button"
                                className="mc-btn mc-btn-ghost mc-btn-sm"
                                disabled={busyId === session.id}
                                onClick={() => void toggleOpenStatus(session)}
                              >
                                {session.status === "OPEN"
                                  ? "Set Scheduled"
                                  : "Open"}
                              </button>
                            </>
                          )}
                          {canDelayOrCancel && (
                            <>
                              <button
                                type="button"
                                className="mc-btn mc-btn-warning mc-btn-sm"
                                onClick={() => {
                                  setDelayTarget(session);
                                  setDelayMinutes(
                                    String(
                                      session.delayMinutes > 0
                                        ? session.delayMinutes
                                        : 15
                                    )
                                  );
                                  setDelayError(null);
                                }}
                              >
                                Delay
                              </button>
                              <button
                                type="button"
                                className="mc-btn mc-btn-danger mc-btn-sm"
                                onClick={() => setCancelTarget(session)}
                              >
                                Cancel
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <PaginationBar
            page={data.page}
            size={data.size}
            totalElements={data.totalElements}
            totalPages={data.totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Create / Edit Session Modal */}
      <Modal
        open={modalOpen}
        title={editing ? "Edit Channelling Session" : "Schedule New Session"}
        subtitle="Doctor and room time overlaps are blocked automatically on the server (BR-02)."
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setModalOpen(false)}
            >
              Close
            </button>
            <button
              type="submit"
              form="mc-session-form"
              className="mc-btn mc-btn-primary"
              disabled={submitting}
            >
              {submitting
                ? "Checking Clashes..."
                : editing
                  ? "Save Session"
                  : "Schedule Session"}
            </button>
          </>
        }
      >
        <form
          id="mc-session-form"
          className="mc-form"
          onSubmit={handleSaveSession}
        >
          {formError && <AlertBanner tone="error" message={formError} />}

          <div className="mc-field">
            <label className="mc-label" htmlFor="sess-doctor">
              Consultant Doctor<span className="mc-required">*</span>
            </label>
            <select
              id="sess-doctor"
              className={`mc-select${
                fieldErrors.doctorId ? " mc-input-error" : ""
              }`}
              value={doctorId}
              onChange={(e) => handleDoctorChange(e.target.value)}
            >
              <option value="">-- Select Doctor --</option>
              {doctors.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.fullName} — {d.specialisationName} ({d.slmcNumber})
                </option>
              ))}
            </select>
            {fieldErrors.doctorId && (
              <span className="mc-field-error">{fieldErrors.doctorId}</span>
            )}
          </div>

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-type">
                Session Type<span className="mc-required">*</span>
              </label>
              <select
                id="sess-type"
                className="mc-select"
                value={sessionType}
                onChange={(e) => {
                  const next = e.target.value as SessionType;
                  setSessionType(next);
                  if (next === "ONLINE") {
                    setRoomId("");
                  }
                }}
              >
                <option value="PHYSICAL">Physical (Hospital Room)</option>
                <option value="ONLINE">Online (Secure Chat Room)</option>
              </select>
            </div>

            {sessionType === "PHYSICAL" ? (
              <div className="mc-field">
                <label className="mc-label" htmlFor="sess-room">
                  Consultation Room<span className="mc-required">*</span>
                </label>
                <select
                  id="sess-room"
                  className={`mc-select${
                    fieldErrors.roomId ? " mc-input-error" : ""
                  }`}
                  value={roomId}
                  onChange={(e) => setRoomId(e.target.value)}
                >
                  <option value="">-- Select Active Room --</option>
                  {rooms.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                      {r.location ? ` (${r.location})` : ""}
                    </option>
                  ))}
                </select>
                {fieldErrors.roomId && (
                  <span className="mc-field-error">{fieldErrors.roomId}</span>
                )}
              </div>
            ) : (
              <div className="mc-field">
                <label className="mc-label">Room Allocation (BR-01)</label>
                <input
                  className="mc-input"
                  value="No physical room needed for ONLINE"
                  disabled
                />
              </div>
            )}
          </div>

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-date">
                Session Date<span className="mc-required">*</span>
              </label>
              <input
                id="sess-date"
                type="date"
                min={todayColomboIso()}
                className={`mc-input${
                  fieldErrors.sessionDate ? " mc-input-error" : ""
                }`}
                value={sessionDate}
                onChange={(e) => setSessionDate(e.target.value)}
              />
              {fieldErrors.sessionDate && (
                <span className="mc-field-error">{fieldErrors.sessionDate}</span>
              )}
            </div>

            {!editing ? (
              <div className="mc-field">
                <label className="mc-label" htmlFor="sess-status">
                  Initial Status
                </label>
                <select
                  id="sess-status"
                  className="mc-select"
                  value={initialStatus}
                  onChange={(e) =>
                    setInitialStatus(e.target.value as SessionStatus)
                  }
                >
                  <option value="OPEN">OPEN (Bookable Immediately)</option>
                  <option value="SCHEDULED">SCHEDULED (Draft / Upcoming)</option>
                </select>
              </div>
            ) : (
              <div className="mc-field">
                <label className="mc-label">Current Status</label>
                <input className="mc-input" value={editing.status} disabled />
              </div>
            )}
          </div>

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-start">
                Start Time<span className="mc-required">*</span>
              </label>
              <input
                id="sess-start"
                type="time"
                className={`mc-input${
                  fieldErrors.startTime ? " mc-input-error" : ""
                }`}
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
              {fieldErrors.startTime && (
                <span className="mc-field-error">{fieldErrors.startTime}</span>
              )}
            </div>

            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-end">
                End Time<span className="mc-required">*</span>
              </label>
              <input
                id="sess-end"
                type="time"
                className={`mc-input${
                  fieldErrors.endTime ? " mc-input-error" : ""
                }`}
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
              {fieldErrors.endTime && (
                <span className="mc-field-error">{fieldErrors.endTime}</span>
              )}
            </div>
          </div>

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-max">
                Max Patients (1–60)<span className="mc-required">*</span>
              </label>
              <input
                id="sess-max"
                type="number"
                min={1}
                max={60}
                className={`mc-input${
                  fieldErrors.maxPatients ? " mc-input-error" : ""
                }`}
                value={maxPatients}
                onChange={(e) => setMaxPatients(e.target.value)}
              />
              {fieldErrors.maxPatients && (
                <span className="mc-field-error">
                  {fieldErrors.maxPatients}
                </span>
              )}
            </div>

            <div className="mc-field">
              <label className="mc-label" htmlFor="sess-mins">
                Minutes / Patient (5–30)<span className="mc-required">*</span>
              </label>
              <input
                id="sess-mins"
                type="number"
                min={5}
                max={30}
                className={`mc-input${
                  fieldErrors.minutesPerPatient ? " mc-input-error" : ""
                }`}
                value={minutesPerPatient}
                onChange={(e) => setMinutesPerPatient(e.target.value)}
              />
              {fieldErrors.minutesPerPatient && (
                <span className="mc-field-error">
                  {fieldErrors.minutesPerPatient}
                </span>
              )}
            </div>
          </div>

          <div className="mc-field">
            <label className="mc-label" htmlFor="sess-fee">
              Session Fee (LKR)<span className="mc-required">*</span>
            </label>
            <input
              id="sess-fee"
              type="number"
              step="0.01"
              min="0.01"
              className={`mc-input${fieldErrors.fee ? " mc-input-error" : ""}`}
              value={fee}
              onChange={(e) => setFee(e.target.value)}
            />
            {fieldErrors.fee && (
              <span className="mc-field-error">{fieldErrors.fee}</span>
            )}
          </div>
        </form>
      </Modal>

      {/* Delay Session Modal with BR-06 Live Recalculation Preview */}
      <Modal
        open={delayTarget !== null}
        title="Delay Session (BR-06)"
        subtitle={
          delayTarget
            ? `${delayTarget.doctorName} · ${delayTarget.sessionDate} (${formatTime12h(delayTarget.startTime)})`
            : undefined
        }
        onClose={() => setDelayTarget(null)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setDelayTarget(null)}
            >
              Cancel
            </button>
            <button
              type="submit"
              form="mc-delay-form"
              className="mc-btn mc-btn-primary"
              disabled={delaySubmitting}
            >
              {delaySubmitting ? "Updating..." : "Apply Delay & Recalculate"}
            </button>
          </>
        }
      >
        {delayTarget && (
          <form
            id="mc-delay-form"
            className="mc-form"
            onSubmit={handleConfirmDelay}
          >
            {delayError && <AlertBanner tone="error" message={delayError} />}
            <div className="mc-field">
              <label className="mc-label" htmlFor="delay-mins">
                Delay in Minutes (0–360)<span className="mc-required">*</span>
              </label>
              <input
                id="delay-mins"
                type="number"
                min={0}
                max={360}
                className="mc-input"
                value={delayMinutes}
                onChange={(e) => setDelayMinutes(e.target.value)}
              />
              <span className="mc-field-hint">
                Formula: Start Time + (Appointment # − 1) × {delayTarget.minutesPerPatient}m + Delay
              </span>
            </div>

            <div className="mc-banner mc-banner-info" style={{ marginBottom: 0 }}>
              <div>
                <strong>Estimated Time Preview:</strong>
                <div style={{ marginTop: 6, fontSize: "0.85rem" }}>
                  Appt #1 →{" "}
                  <strong>
                    {addMinutesToTime(delayTarget.startTime, parsedDelayPreview)}
                  </strong>{" "}
                  | Appt #5 →{" "}
                  <strong>
                    {addMinutesToTime(
                      delayTarget.startTime,
                      4 * delayTarget.minutesPerPatient + parsedDelayPreview
                    )}
                  </strong>{" "}
                  | Appt #10 →{" "}
                  <strong>
                    {addMinutesToTime(
                      delayTarget.startTime,
                      9 * delayTarget.minutesPerPatient + parsedDelayPreview
                    )}
                  </strong>
                </div>
              </div>
            </div>
          </form>
        )}
      </Modal>

      {/* Cancel Session Confirmation Modal */}
      <Modal
        open={cancelTarget !== null}
        title="Cancel Channelling Session?"
        subtitle="This action cannot be undone and is permanently recorded in the audit log."
        onClose={() => setCancelTarget(null)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setCancelTarget(null)}
            >
              Keep Session
            </button>
            <button
              type="button"
              className="mc-btn mc-btn-danger-solid"
              disabled={cancelSubmitting}
              onClick={() => void handleConfirmCancel()}
            >
              {cancelSubmitting
                ? "Cancelling..."
                : "Yes, Cancel Session & Bookings"}
            </button>
          </>
        }
      >
        {cancelTarget && (
          <div className="mc-form">
            <AlertBanner
              tone="error"
              message={`Cancelling ${cancelTarget.doctorName}'s session on ${cancelTarget.sessionDate} will automatically cancel ${cancelTarget.bookedCount} active patient booking(s) while keeping records in history (BR-07 / BR-11).`}
            />
          </div>
        )}
      </Modal>
    </section>
  );
}