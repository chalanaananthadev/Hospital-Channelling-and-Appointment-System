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
  PortalApiError,
  portalRequest
} from "@/lib/portalClient";
import type { PageResponse, Specialisation } from "@/types/portal";

interface SpecialisationsTabProps {
  onChanged?: () => void;
}

export function SpecialisationsTab({ onChanged }: SpecialisationsTabProps) {
  const [data, setData] = useState<PageResponse<Specialisation> | null>(null);
  const [search, setSearch] = useState("");
  const [querySearch, setQuerySearch] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Specialisation | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = buildQuery({ search: querySearch, page, size: 10 });
      const res = await portalRequest<PageResponse<Specialisation>>(
        `/manager/specialisations${qs}`
      );
      setData(res);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load specialisations"
      );
    } finally {
      setLoading(false);
    }
  }, [querySearch, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  function openCreate() {
    setEditing(null);
    setName("");
    setDescription("");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  function openEdit(item: Specialisation) {
    setEditing(item);
    setName(item.name);
    setDescription(item.description ?? "");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!name.trim()) {
      nextErrors.name = "Specialisation name is required";
    } else if (name.trim().length > 80) {
      nextErrors.name = "Specialisation name must be at most 80 characters";
    }
    if (description.trim().length > 1000) {
      nextErrors.description = "Description must be at most 1000 characters";
    }

    if (Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const payload = {
        name: name.trim(),
        description: description.trim() || null
      };
      if (editing) {
        await portalRequest<Specialisation>(
          `/manager/specialisations/${editing.id}`,
          { method: "PUT", body: JSON.stringify(payload) }
        );
        setFeedback(`Updated "${payload.name}" and refreshed public catalog cache.`);
      } else {
        await portalRequest<Specialisation>("/manager/specialisations", {
          method: "POST",
          body: JSON.stringify(payload)
        });
        setFeedback(`Created "${payload.name}" and refreshed public catalog cache.`);
      }
      setModalOpen(false);
      await loadData();
      onChanged?.();
    } catch (err) {
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setFormError(err.message);
      } else {
        setFormError("Unable to save specialisation");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleStatus(item: Specialisation) {
    setTogglingId(item.id);
    setError(null);
    try {
      await portalRequest<Specialisation>(
        `/manager/specialisations/${item.id}/status`,
        {
          method: "PATCH",
          body: JSON.stringify({ active: !item.active })
        }
      );
      setFeedback(
        `${item.name} is now ${!item.active ? "Active" : "Inactive"} (history preserved).`
      );
      await loadData();
      onChanged?.();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to update specialisation status"
      );
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <section className="mc-panel">
      <div className="mc-panel-header">
        <div>
          <h2 className="mc-panel-title">Medical Specialisations</h2>
          <p className="mc-panel-subtitle">
            Manage clinical departments shown in public search. Changes automatically evict the public cache.
          </p>
        </div>
        <button
          type="button"
          className="mc-btn mc-btn-primary"
          onClick={openCreate}
        >
          + Add Specialisation
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
          placeholder="Search specialisations (e.g., Cardiology, Paediatrics)..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit" className="mc-btn mc-btn-secondary">
          Search
        </button>
        {querySearch && (
          <button
            type="button"
            className="mc-btn mc-btn-ghost"
            onClick={() => {
              setSearch("");
              setQuerySearch("");
              setPage(0);
            }}
          >
            Reset
          </button>
        )}
      </form>

      {loading ? (
        <TableSkeleton rows={5} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          title="No specialisations found"
          description="Add your hospital's clinical departments so doctors and sessions can be organised cleanly."
          action={
            <button
              type="button"
              className="mc-btn mc-btn-primary"
              onClick={openCreate}
            >
              + Add First Specialisation
            </button>
          }
        />
      ) : (
        <>
          <div className="mc-table-wrap">
            <table className="mc-table">
              <thead>
                <tr>
                  <th>Specialisation</th>
                  <th>Description</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <div className="mc-cell-main">{item.name}</div>
                    </td>
                    <td>
                      <div className="mc-cell-sub">
                        {item.description || "No description provided"}
                      </div>
                    </td>
                    <td>
                      <StatusBadge value={item.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                    <td>
                      <div className="mc-actions">
                        <button
                          type="button"
                          className="mc-btn mc-btn-secondary mc-btn-sm"
                          onClick={() => openEdit(item)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className={`mc-btn mc-btn-sm ${
                            item.active ? "mc-btn-danger" : "mc-btn-success"
                          }`}
                          disabled={togglingId === item.id}
                          onClick={() => void toggleStatus(item)}
                        >
                          {togglingId === item.id
                            ? "Saving..."
                            : item.active
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
        title={editing ? "Edit Specialisation" : "New Specialisation"}
        subtitle="Specialisation names must be unique across the hospital."
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
              form="mc-spec-form"
              className="mc-btn mc-btn-primary"
              disabled={submitting}
            >
              {submitting ? "Saving..." : editing ? "Save Changes" : "Create"}
            </button>
          </>
        }
      >
        <form id="mc-spec-form" className="mc-form" onSubmit={handleSubmit}>
          {formError && <AlertBanner tone="error" message={formError} />}
          <div className="mc-field">
            <label className="mc-label" htmlFor="spec-name">
              Specialisation Name<span className="mc-required">*</span>
            </label>
            <input
              id="spec-name"
              className={`mc-input${fieldErrors.name ? " mc-input-error" : ""}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., Cardiology"
              maxLength={80}
            />
            {fieldErrors.name && (
              <span className="mc-field-error">{fieldErrors.name}</span>
            )}
          </div>

          <div className="mc-field">
            <label className="mc-label" htmlFor="spec-desc">
              Description
            </label>
            <textarea
              id="spec-desc"
              className={`mc-textarea${
                fieldErrors.description ? " mc-input-error" : ""
              }`}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief overview of treatments and clinics in this department"
              maxLength={1000}
            />
            {fieldErrors.description && (
              <span className="mc-field-error">{fieldErrors.description}</span>
            )}
          </div>
        </form>
      </Modal>
    </section>
  );
}