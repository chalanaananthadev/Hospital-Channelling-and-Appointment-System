"use client";

interface PaginationBarProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (nextPage: number) => void;
}

export function PaginationBar({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange
}: PaginationBarProps) {
  if (totalElements === 0) {
    return null;
  }

  const start = page * size + 1;
  const end = Math.min((page + 1) * size, totalElements);

  return (
    <div className="mc-pagination">
      <span>
        Showing <strong>{start}</strong>–<strong>{end}</strong> of{" "}
        <strong>{totalElements}</strong> records
      </span>
      <div className="mc-pagination-controls">
        <button
          type="button"
          className="mc-btn mc-btn-ghost mc-btn-sm"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          ← Previous
        </button>
        <span>
          Page <strong>{page + 1}</strong> of <strong>{Math.max(totalPages, 1)}</strong>
        </span>
        <button
          type="button"
          className="mc-btn mc-btn-ghost mc-btn-sm"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next →
        </button>
      </div>
    </div>
  );
}