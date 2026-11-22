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
import type { PageResponse, Room } from "@/types/portal";

interface RoomsTabProps {
  onChanged?: () => void;
}

export function RoomsTab({ onChanged }: RoomsTabProps) {
  const [data, setData] = useState<PageResponse<Room> | null>(null);
  const [search, setSearch] = useState("");
  const [querySearch, setQuerySearch] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [name, setName] = useState("");
  const [location, setLocation] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = buildQuery({ search: querySearch, page, size: 10 });
      const res = await portalRequest<PageResponse<Room>>(
        `/manager/rooms${qs}`
      );
      setData(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load rooms");
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
    setLocation("");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  function openEdit(room: Room) {
    setEditing(room);
    setName(room.name);
    setLocation(room.location ?? "");
    setFieldErrors({});
    setFormError(null);
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!name.trim()) {
      nextErrors.name = "Room name is required";
    } else if (name.trim().length > 40) {
      nextErrors.name = "Room name must be at most 40 characters";
    }
    if (location.trim().length > 120) {
      nextErrors.location = "Location must be at most 120 characters";
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
        location: location.trim() || null
      };
      if (editing) {
        await portalRequest<Room>(`/manager/rooms/${editing.id}`, {
          method: "PUT",
          body: JSON.stringify(payload)
        });
        setFeedback(`Updated room "${payload.name}".`);
      } else {
        await portalRequest<Room>("/manager/rooms", {
          method: "POST",
          body: JSON.stringify(payload)
        });
        setFeedback(`Created room "${payload.name}".`);
      }
      setModalOpen(false);
      await loadData();
      onChanged?.();
    } catch (err) {
      if (err instanceof PortalApiError) {
        setFieldErrors(err.fields);
        setFormError(err.message);
      } else {
        setFormError("Unable to save room");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleStatus(room: Room) {
    setTogglingId(room.id);
    setError(null);
    try {
      await portalRequest<Room>(`/manager/rooms/${room.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ active: !room.active })
      });
      setFeedback(
        `${room.name} is now ${!room.active ? "Active" : "Inactive"}.`
      );
      await loadData();
      onChanged?.();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to update room status"
      );
    } finally {
      setTogglingId(null);
    }
  }

  return (
    <section className="mc-panel">
      <div className="mc-panel-header">
        <div>
          <h2 className="mc-panel-title">Consultation Rooms</h2>
          <p className="mc-panel-subtitle">
            Physical rooms assigned to in-hospital channelling sessions with automatic overlap protection (BR-02).
          </p>
        </div>
        <button
          type="button"
          className="mc-btn mc-btn-primary"
          onClick={openCreate}
        >
          + Add Room
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
          placeholder="Search room name (e.g., Room 101, Wing B)..."
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
          title="No consultation rooms found"
          description="Create consultation rooms so managers can schedule physical channelling sessions."
          action={
            <button
              type="button"
              className="mc-btn mc-btn-primary"
              onClick={openCreate}
            >
              + Add First Room
            </button>
          }
        />
      ) : (
        <>
          <div className="mc-table-wrap">
            <table className="mc-table">
              <thead>
                <tr>
                  <th>Room Name</th>
                  <th>Location / Floor</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((room) => (
                  <tr key={room.id}>
                    <td>
                      <div className="mc-cell-main">{room.name}</div>
                    </td>
                    <td>
                      <div className="mc-cell-sub">
                        {room.location || "Main Building"}
                      </div>
                    </td>
                    <td>
                      <StatusBadge value={room.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                    <td>
                      <div className="mc-actions">
                        <button
                          type="button"
                          className="mc-btn mc-btn-secondary mc-btn-sm"
                          onClick={() => openEdit(room)}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className={`mc-btn mc-btn-sm ${
                            room.active ? "mc-btn-danger" : "mc-btn-success"
                          }`}
                          disabled={togglingId === room.id}
                          onClick={() => void toggleStatus(room)}
                        >
                          {togglingId === room.id
                            ? "Saving..."
                            : room.active
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
        title={editing ? "Edit Consultation Room" : "New Consultation Room"}
        subtitle="Only active rooms can be assigned to new physical sessions."
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
              form="mc-room-form"
              className="mc-btn mc-btn-primary"
              disabled={submitting}
            >
              {submitting ? "Saving..." : editing ? "Save Changes" : "Create Room"}
            </button>
          </>
        }
      >
        <form id="mc-room-form" className="mc-form" onSubmit={handleSubmit}>
          {formError && <AlertBanner tone="error" message={formError} />}
          <div className="mc-field">
            <label className="mc-label" htmlFor="room-name">
              Room Name<span className="mc-required">*</span>
            </label>
            <input
              id="room-name"
              className={`mc-input${fieldErrors.name ? " mc-input-error" : ""}`}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., Room 204"
              maxLength={40}
            />
            {fieldErrors.name && (
              <span className="mc-field-error">{fieldErrors.name}</span>
            )}
          </div>

          <div className="mc-field">
            <label className="mc-label" htmlFor="room-location">
              Location / Wing
            </label>
            <input
              id="room-location"
              className={`mc-input${
                fieldErrors.location ? " mc-input-error" : ""
              }`}
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g., 2nd Floor, East Wing"
              maxLength={120}
            />
            {fieldErrors.location && (
              <span className="mc-field-error">{fieldErrors.location}</span>
            )}
          </div>
        </form>
      </Modal>
    </section>
  );
}