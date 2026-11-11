import Link from "next/link";

async function loadPublic<T>(path: string): Promise<T[]> {
  const base = process.env.API_URL ?? "http://localhost:8085";
  try {
    const response = await fetch(`${base}${path}`, { next: { revalidate: 120 } });
    if (!response.ok) {
      return [];
    }
    return response.json();
  } catch {
    return [];
  }
}

export default async function HomePage() {
  const [specialisations, notices] = await Promise.all([
    loadPublic<{ id: string; name: string; description: string }>("/api/public/specialisations"),
    loadPublic<{ id: string; title: string; body: string }>("/api/public/notices")
  ]);

  return (
    <main>
      <div style={{
        background: "linear-gradient(135deg, #0284c7 0%, #0369a1 100%)",
        color: "#ffffff",
        padding: "24px 32px",
        borderRadius: "12px",
        marginBottom: "24px",
        boxShadow: "0 4px 12px rgba(2, 132, 199, 0.2)"
      }}>
        <h1 style={{ margin: "0 0 8px 0", fontSize: "1.8rem" }}>
          👋 Hello World! MediChannel Frontend is Working Perfectly!
        </h1>
        <p style={{ margin: 0, fontSize: "1.05rem", opacity: 0.95 }}>
          <strong>Next.js 15 (App Router)</strong> connected to <strong>Spring Boot 3 REST API</strong> and <strong>PostgreSQL</strong>.
        </p>
      </div>

      <section className="hero">
        <p className="muted">Colombo · Physical &amp; online consultations</p>
        <h1>Book a doctor without the paper register.</h1>
        <p>
          Search 15 specialisations, follow the live queue, and join a secure online consultation.
          Patient data is encrypted at rest and never shown to admin or manager staff.
        </p>
        <p><Link href="/doctors">Browse the doctor catalog →</Link></p>
      </section>

      {notices.map((notice) => (
        <section key={notice.id} className="card" style={{ marginTop: 16 }}>
          <div className="notice">
            <h2>{notice.title}</h2>
            <p>{notice.body}</p>
          </div>
        </section>
      ))}

      <section className="card" style={{ marginTop: 16 }}>
        <h2>Specialisations</h2>
        <div className="grid">
          {specialisations.map((item) => (
            <article className="tile" key={item.id}>
              <h3>{item.name}</h3>
              <p className="muted">{item.description}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
