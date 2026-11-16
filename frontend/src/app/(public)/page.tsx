export default function HomePage() {
  return (
    <main style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      minHeight: "100vh",
      margin: 0,
      padding: "20px"
    }}>
      <div style={{
        background: "linear-gradient(135deg, #0284c7 0%, #0369a1 100%)",
        color: "#ffffff",
        padding: "48px 56px",
        borderRadius: "16px",
        boxShadow: "0 10px 25px rgba(2, 132, 199, 0.25)",
        textAlign: "center",
        maxWidth: "520px",
        width: "100%"
      }}>
        <h1 style={{ margin: "0 0 16px 0", fontSize: "2.5rem" }}>
          👋 Hello World!
        </h1>
        <p style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600 }}>
          MediChannel Frontend is Working Perfectly!
        </p>
      </div>
    </main>
  );
}
