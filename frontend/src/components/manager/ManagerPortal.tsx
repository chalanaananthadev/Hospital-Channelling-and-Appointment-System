"use client";

import "@/styles/portal.css";
import { useCallback, useEffect, useState } from "react";
import { DoctorsTab } from "@/components/manager/DoctorsTab";
import { RoomsTab } from "@/components/manager/RoomsTab";
import { SessionsTab } from "@/components/manager/SessionsTab";
import { SpecialisationsTab } from "@/components/manager/SpecialisationsTab";
import { portalRequest } from "@/lib/portalClient";
import type {
  ChannelSession,
  DoctorProfile,
  PageResponse,
  Room,
  Specialisation
} from "@/types/portal";

type ManagerTabKey = "sessions" | "doctors" | "rooms" | "specialisations";

interface PortalSummary {
  sessions: number;
  doctors: number;
  rooms: number;
  specialisations: number;
}

export function ManagerPortal() {
  const [activeTab, setActiveTab] = useState<ManagerTabKey>("sessions");
  const [colomboTime, setColomboTime] = useState("");
  const [summary, setSummary] = useState<PortalSummary>({
    sessions: 0,
    doctors: 0,
    rooms: 0,
    specialisations: 0
  });

  const refreshSummary = useCallback(async () => {
    try {
      const [sessRes, docRes, roomRes, specRes] = await Promise.all([
        portalRequest<PageResponse<ChannelSession>>(
          "/manager/sessions?page=0&size=1"
        ),
        portalRequest<PageResponse<DoctorProfile>>(
          "/manager/doctors?page=0&size=1"
        ),
        portalRequest<PageResponse<Room>>("/manager/rooms?page=0&size=1"),
        portalRequest<PageResponse<Specialisation>>(
          "/manager/specialisations?page=0&size=1"
        )
      ]);
      setSummary({
        sessions: sessRes.totalElements,
        doctors: docRes.totalElements,
        rooms: roomRes.totalElements,
        specialisations: specRes.totalElements
      });
    } catch {
      // Tab-level alerts display API errors
    }
  }, []);

  useEffect(() => {
    void refreshSummary();
  }, [refreshSummary]);

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

  return (
    <main className="mc-shell">
      <header className="mc-hero">
        <div>
          <span className="mc-hero-kicker">
            Lanka Care Hospital · Channelling Centre Desk
          </span>
          <h1>Manager Operations Portal</h1>
          <p>
            Configure clinical departments, physical rooms, consultant profiles with SLMC validation, and clash-free channelling sessions. Clinical consultation notes remain strictly restricted to doctors and patients.
          </p>
        </div>
        <div className="mc-hero-meta">
          <div className="mc-clock-pill" title="Hospital timezone (BR-11)">
            <span>Asia/Colombo</span>
            <span>·</span>
            <span>{colomboTime || "Loading time..."}</span>
          </div>
        </div>
      </header>

      <section className="mc-stats-grid" aria-label="Hospital catalog summary">
        <div className="mc-stat-card">
          <div className="mc-stat-label">Scheduled Sessions</div>
          <div className="mc-stat-value">{summary.sessions}</div>
          <div className="mc-stat-sub">Physical &amp; online clinics</div>
        </div>
        <div className="mc-stat-card">
          <div className="mc-stat-label">Consultant Doctors</div>
          <div className="mc-stat-value">{summary.doctors}</div>
          <div className="mc-stat-sub">SLMC-verified profiles</div>
        </div>
        <div className="mc-stat-card">
          <div className="mc-stat-label">Consultation Rooms</div>
          <div className="mc-stat-value">{summary.rooms}</div>
          <div className="mc-stat-sub">Overlap-checked rooms</div>
        </div>
        <div className="mc-stat-card">
          <div className="mc-stat-label">Specialisations</div>
          <div className="mc-stat-value">{summary.specialisations}</div>
          <div className="mc-stat-sub">Cached public departments</div>
        </div>
      </section>

      <nav className="mc-tabs" aria-label="Manager sections">
        <button
          type="button"
          className={`mc-tab${activeTab === "sessions" ? " mc-tab-active" : ""}`}
          onClick={() => setActiveTab("sessions")}
        >
          Channelling Sessions
        </button>
        <button
          type="button"
          className={`mc-tab${activeTab === "doctors" ? " mc-tab-active" : ""}`}
          onClick={() => setActiveTab("doctors")}
        >
          Doctor Profiles
        </button>
        <button
          type="button"
          className={`mc-tab${activeTab === "rooms" ? " mc-tab-active" : ""}`}
          onClick={() => setActiveTab("rooms")}
        >
          Rooms
        </button>
        <button
          type="button"
          className={`mc-tab${
            activeTab === "specialisations" ? " mc-tab-active" : ""
          }`}
          onClick={() => setActiveTab("specialisations")}
        >
          Specialisations
        </button>
      </nav>

      {activeTab === "sessions" && <SessionsTab onChanged={refreshSummary} />}
      {activeTab === "doctors" && <DoctorsTab onChanged={refreshSummary} />}
      {activeTab === "rooms" && <RoomsTab onChanged={refreshSummary} />}
      {activeTab === "specialisations" && (
        <SpecialisationsTab onChanged={refreshSummary} />
      )}
    </main>
  );
}