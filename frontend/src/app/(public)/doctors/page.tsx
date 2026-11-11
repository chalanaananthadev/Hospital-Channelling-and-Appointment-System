async function loadDoctors() {
  const base = process.env.API_URL ?? "http://localhost:8080";
  try {
    const response = await fetch(`${base}/api/public/doctors`, { next: { revalidate: 120 } });
    if (!response.ok) {
      return [];
    }
    return response.json() as Promise<Array<{
      id: string;
      fullName: string;
      specialisation: string;
      qualifications: string;
      consultationFee: number;
      bio: string;
    }>>;
  } catch {
    return [];
  }
}

export default async function DoctorsPage() {
  const doctors = await loadDoctors();
  return (
    <main>
      <section className="hero">
        <h1>Doctor catalog</h1>
        <p className="muted">Public list cached with a short TTL. Personal records are never cached.</p>
      </section>
      <div className="grid" style={{ marginTop: 16 }}>
        {doctors.map((doctor) => (
          <article className="tile" key={doctor.id}>
            <h3>{doctor.fullName}</h3>
            <p>{doctor.specialisation}</p>
            <p className="muted">{doctor.qualifications}</p>
            <p>Fee: LKR {doctor.consultationFee}</p>
          </article>
        ))}
        {doctors.length === 0 && <p className="muted">No doctors published yet. Start the API and seed data.</p>}
      </div>
    </main>
  );
}
