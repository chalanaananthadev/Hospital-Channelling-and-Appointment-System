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
  buildQuery,
  formatLkr,
  PortalApiError,
  portalRequest
} from "@/lib/portalClient";
import type {
  DoctorProfile,
  DoctorUserOption,
  PageResponse,
  Specialisation
} from "@/types/portal";

const SLMC_REGEX = /^(SLMC[- ]?)?\d{4,6}$/i;

interface DoctorsTabProps {
  onChanged?: () => void;
}

export function DoctorsTab({ onChanged }: DoctorsTabProps) {
  const [data, setData] = useState<PageResponse<DoctorProfile> | null>(null);
  const [specialisations, setSpecialisations] = useState<Specialisation[]>([]);
  const [availableUsers, setAvailableUsers] = useState<DoctorUserOption[]>([]);

  const [search, setSearch] = useState("");
  const [querySearch, setQuerySearch] = useState("");
  const [filterSpecId, setFilterSpecId] = useState("");
  const [filterActive, setFilterActive] = useState("");
  const [page, setPage] = useState(0);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DoctorProfile | null>(null);
  const [userId, setUserId] = useState("");
  const [fullName, setFullName] = useState("");
  const [specialisationId, setSpecialisationId] = useState("");
  const [slmcNumber, setSlmcNumber] = useState("");
  const [qualifications, setQualifications] = useState("");
  const [consultationFee, setConsultationFee] = useState("");
  const [bio, setBio] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const loadDoctors = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = buildQuery({
        search: querySearch,
        specialisationId: filterSpecId || undefined,
        active: filterActive === "" ? undefined : filterActive,
        page,
        size: 10
      });
      const res = await portalRequest<PageResponse<DoctorProfile>>(
        `/manager/doctors${qs}`
      );
      setData(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load doctors");
    } finally {
      setLoading(false);
    }
  }, [querySearch, filterSpecId, filterActive, page]);

  const loadLookups = useCallback(async () => {
    try {
      const [specs, users] = await Promise.all([
        portalRequest<Specialisation[]>("/manager/specialisations/active"),
        portalRequest<DoctorUserOption[]>("/manager/doctors/available-users")
      ]);
      setSpecialisations(specs);
      setAvailableUsers(users);
    } catch {
      // Handled when opening modal if empty
    }
  }, []);

  useEffect(() => {
    void loadDoctors();
  }, [loadDoctors]);

  useEffect(() => {
    void loadLookups();
  }, [loadLookups]);

  async function openCreate() {
    await loadLookups();
    setEditing(null);
    setUserId("");
    setFullName("");
    setSpecialisationId(specialisations[0]?.id ?? "");
    setSlmcNumber("");
    setQualifications("");
    setConsultationFee("3500.00");
    setBio("");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  function openEdit(doc: DoctorProfile) {
    setEditing(doc);
    setUserId(doc.userId);
    setFullName(doc.fullName);
    setSpecialisationId(doc.specialisationId);
    setSlmcNumber(doc.slmcNumber);
    setQualifications(doc.qualifications);
    setConsultationFee(String(doc.consultationFee));
    setBio(doc.bio ?? "");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  function handleUserSelection(selectedId: string) {
    setUserId(selectedId);
    const picked = availableUsers.find((u) => u.userId === selectedId);
    if (picked) {
      setFullName(picked.fullName);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const nextErrors: Record<string, string> = {};

    if (!editing && !userId) {
      nextErrors.userId = "Select a doctor account created by Admin";
    }
    if (!fullName.trim()) {
      nextErrors.fullName = "Doctor full name is required";
    }
    if (!specialisationId) {
      nextErrors.specialisationId = "Specialisation is required";
    }
    if (!slmcNumber.trim()) {
      nextErrors.slmcNumber = "SLMC registration number is required";
    } else if (!SLMC_REGEX.test(slmcNumber.trim())) {
      nextErrors.slmcNumber =
        "Use 4 to 6 digits, optionally prefixed with SLMC- (e.g., SLMC-12345)";
    }
    if (!qualifications.trim()) {
      nextErrors.qualifications = "Qualifications are required";
    }
    const parsedFee = Number(consultationFee);
    if (!consultationFee.trim() || Number.isNaN(parsedFee) || parsedFee <= 0) {
      nextErrors.consultationFee = "Enter a valid fee greater than 0 LKR";
    }

    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const commonPayload = {
        fullName: fullName.trim(),
        specialisationId,
        slmcNumber: slmcNumber.trim(),
        qualifications: qualifications.trim(),
        consultationFee: parsedFee,
        bio: bio.trim() || null
      };

      if (editing) {
        await portalRequest<DoctorProfile>(`/manager/doctors/${editing.id}`, {
          method: "PUT",
          body: JSON.stringify(commonPayload)
        });
        setFeedback(
          `Updated profile for ${commonPayload.fullName} and refreshed public doctor cache.`
        );
      } else {
        await portalRequest<DoctorProfile>("/manager/doctors", {
          method: "POST",
          body: JSON.stringify({ ...commonPayload, userId })
        });
        setFeedback(
          `Created doctor profile for ${commonPayload.fullName} and refreshed public doctor cache.`
        );
      }

      setModalOpen(false);
      await Promise.all([loadDoctors(), loadLookups()]);
      onChanged?.();
    } catch (err) {
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setFormError(err.message);
      } else {
        setFormError("Unable to save doctor profile");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleStatus(doc: DoctorProfile) {
    setTogglingId(doc.id);
    setError(null);
    try {
      await portalRequest<DoctorProfile>(`/manager/doctors/${doc.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ active: !doc.active })
      });
      setFeedback(
        `${doc.fullName} is now ${!doc.active ? "Active" : "Inactive"}. Public catalog cache updated.`
      );
      await loadDoctors();
      onChanged?.();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to update doctor status"
      );
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <section className="mc-panel">
      <div className="mc-panel-header">
        <div>
          <h2 className="mc-panel-title">Consultant Doctor Profiles</h2>
          <p className="mc-panel-subtitle">
            Manage visiting consultants, SLMC registrations, qualifications, and consultation fees in LKR.
          </p>
        </div>
        <button
          type="button"
          className="mc-btn mc-btn-primary"
          onClick={() => void openCreate()}
        >
          + Add Doctor Profile
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
          placeholder="Search doctor name, SLMC number, or specialisation..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          className="mc-select"
          value={filterSpecId}
          onChange={(e) => {
            setFilterSpecId(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Specialisations</option>
          {specialisations.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <select
          className="mc-select"
          value={filterActive}
          onChange={(e) => {
            setFilterActive(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Statuses</option>
          <option value="true">Active Only</option>
          <option value="false">Inactive Only</option>
        </select>
        <button type="submit" className="mc-btn mc-btn-secondary">
          Filter
        </button>
      </form>

      {loading ? (
        <TableSkeleton rows={5} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          title="No doctor profiles match your filter"
          description="Link an Admin-created DOCTOR account to create a consultant profile."
          action={
            <button
              type="button"
              className="mc-btn mc-btn-primary"
              onClick={() => void openCreate()}
            >
              + Add Doctor Profile
            </button>
          }
        />
      ) : (
        <>
          <div className="mc-table-wrap">
            <table className="mc-table">
              <thead>
                <tr>
                  <th>Consultant</th>
                  <th>SLMC &amp; Department</th>
                  <th>Qualifications</th>
                  <th>Fee</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((doc) => (
                  <tr key={doc.id}>
                    <td>
                      <div className="mc-cell-main">{doc.fullName}</div>
                      <div className="mc-cell-sub">{doc.email}</div>
                    </td>
                    <td>
                      <div className="mc-cell-main">{doc.slmcNumber}</div>
                      <div className="mc-cell-sub">{doc.specialisationName}</div>
                    </td>
                    <td>
                      <div className="mc-cell-sub">{doc.qualifications}</div>
                    </td>
                    <td>
                      <div className="mc-cell-main">
                        {formatLkr(doc.consultationFee)}
                      </div>
                    </td>
                    <td>
                      <StatusBadge value={doc.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                    <td>
                      <div className="mc-actions">
                        <button
                          type="button"
                          className="mc-btn mc-btn-secondary mc-btn-sm"
                          onClick={() => openEdit(doc)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className={`mc-btn mc-btn-sm ${
                            doc.active ? "mc-btn-danger" : "mc-btn-success"
                          }`}
                          disabled={togglingId === doc.id}
                          onClick={() => void toggleStatus(doc)}
                        >
                          {togglingId === doc.id
                            ? "Saving..."
                            : doc.active
                              ? "Deactivate"
                              : "Activate"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
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

      <Modal
        open={modalOpen}
        title={editing ? "Edit Doctor Profile" : "Create Doctor Profile"}
        subtitle="SLMC numbers are validated and normalised automatically (e.g., SLMC-12345)."
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button
              type="button"
              className="mc-btn mc-btn-ghost"
              onClick={() => setModalOpen(false)}
            >
              Cancel
            </button>
            <button
              type="submit"
              form="mc-doctor-form"
              className="mc-btn mc-btn-primary"
              disabled={submitting || (!editing && availableUsers.length === 0)}
            >
              {submitting
                ? "Saving..."
                : editing
                  ? "Save Profile"
                  : "Create Profile"}
            </button>
          </>
        }
      >
        <form id="mc-doctor-form" className="mc-form" onSubmit={handleSubmit}>
          {formError && <AlertBanner tone="error" message={formError} />}

          {!editing && (
            <div className="mc-field">
              <label className="mc-label" htmlFor="doc-user">
                Doctor User Account<span className="mc-required">*</span>
              </label>
              {availableUsers.length === 0 ? (
                <AlertBanner
                  tone="info"
                  message="No unassigned DOCTOR accounts found. Ask an IT Admin to create a new DOCTOR login account first."
                />
              ) : (
                <select
                  id="doc-user"
                  className={`mc-select${
                    fieldErrors.userId ? " mc-input-error" : ""
                  }`}
                  value={userId}
                  onChange={(e) => handleUserSelection(e.target.value)}
                >
                  <option value="">-- Select a DOCTOR account --</option>
                  {availableUsers.map((u) => (
                    <option key={u.userId} value={u.userId}>
                      {u.fullName} ({u.email})
                    </option>
                  ))}
                </select>
              )}
              {fieldErrors.userId && (
                <span className="mc-field-error">{fieldErrors.userId}</span>
              )}
            </div>
          )}

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="doc-name">
                Full Name<span className="mc-required">*</span>
              </label>
              <input
                id="doc-name"
                className={`mc-input${
                  fieldErrors.fullName ? " mc-input-error" : ""
                }`}
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Dr. Nimal Silva"
              />
              {fieldErrors.fullName && (
                <span className="mc-field-error">{fieldErrors.fullName}</span>
              )}
            </div>

            <div className="mc-field">
              <label className="mc-label" htmlFor="doc-slmc">
                SLMC Reg. Number<span className="mc-required">*</span>
              </label>
              <input
                id="doc-slmc"
                className={`mc-input${
                  fieldErrors.slmcNumber ? " mc-input-error" : ""
                }`}
                value={slmcNumber}
                onChange={(e) => setSlmcNumber(e.target.value)}
                placeholder="SLMC-12345"
              />
              <span className="mc-field-hint">
                4–6 digits, optional SLMC- prefix
              </span>
              {fieldErrors.slmcNumber && (
                <span className="mc-field-error">{fieldErrors.slmcNumber}</span>
              )}
            </div>
          </div>

          <div className="mc-form-row">
            <div className="mc-field">
              <label className="mc-label" htmlFor="doc-spec">
                Specialisation<span className="mc-required">*</span>
              </label>
              <select
                id="doc-spec"
                className={`mc-select${
                  fieldErrors.specialisationId ? " mc-input-error" : ""
                }`}
                value={specialisationId}
                onChange={(e) => setSpecialisationId(e.target.value)}
              >
                <option value="">-- Select Specialisation --</option>
                {specialisations.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
              {fieldErrors.specialisationId && (
                <span className="mc-field-error">
                  {fieldErrors.specialisationId}
                </span>
              )}
            </div>

            <div className="mc-field">
              <label className="mc-label" htmlFor="doc-fee">
                Consultation Fee (LKR)<span className="mc-required">*</span>
              </label>
              <input
                id="doc-fee"
                type="number"
                step="0.01"
                min="0.01"
                className={`mc-input${
                  fieldErrors.consultationFee ? " mc-input-error" : ""
                }`}
                value={consultationFee}
                onChange={(e) => setConsultationFee(e.target.value)}
                placeholder="3500.00"
              />
              {fieldErrors.consultationFee && (
                <span className="mc-field-error">
                  {fieldErrors.consultationFee}
                </span>
              )}
            </div>
          </div>

          <div className="mc-field">
            <label className="mc-label" htmlFor="doc-qual">
              Qualifications<span className="mc-required">*</span>
            </label>
            <input
              id="doc-qual"
              className={`mc-input${
                fieldErrors.qualifications ? " mc-input-error" : ""
              }`}
              value={qualifications}
              onChange={(e) => setQualifications(e.target.value)}
              placeholder="MBBS (Colombo), MD, MRCP (UK)"
            />
            {fieldErrors.qualifications && (
              <span className="mc-field-error">
                {fieldErrors.qualifications}
              </span>
            )}
          </div>

          <div className="mc-field">
            <label className="mc-label" htmlFor="doc-bio">
              Consultant Bio
            </label>
            <textarea
              id="doc-bio"
              className={`mc-textarea${fieldErrors.bio ? " mc-input-error" : ""}`}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Clinical experience, areas of interest, and hospital affiliations"
            />
            {fieldErrors.bio && (
              <span className="mc-field-error">{fieldErrors.bio}</span>
            )}
          </div>
        </form>
      </Modal>
    </section>
  );
}