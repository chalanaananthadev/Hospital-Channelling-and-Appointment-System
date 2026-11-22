"use client";

import type { ReactNode } from "react";

export function AlertBanner({
  tone,
  message,
  onDismiss
}: {
  tone: "error" | "success" | "info";
  message: string;
  onDismiss?: () => void;
}) {
  return (
    <div className={`mc-banner mc-banner-${tone}`} role="status">
      <span>{message}</span>
      {onDismiss && (
        <button
          type="button"
          className="mc-btn mc-btn-ghost mc-btn-sm"
          onClick={onDismiss}
        >
          Dismiss
        </button>
      )}
    </div>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="mc-skeleton-list" aria-busy="true" aria-label="Loading data">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="mc-skeleton-row" />
      ))}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="mc-empty">
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}