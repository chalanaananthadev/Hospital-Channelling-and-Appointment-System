import Link from "next/link";

async function loadPublic<T>(path: string): Promise<T[]> {
  const base = process.env.API_URL ?? "http://localhost:8080";
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
