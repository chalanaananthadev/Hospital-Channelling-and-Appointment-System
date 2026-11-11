"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

export default function RegisterPage() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setFieldErrors({});
    const form = new FormData(event.currentTarget);
    const response = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName: form.get("fullName"),
        email: form.get("email"),
        nic: form.get("nic"),
        dateOfBirth: form.get("dateOfBirth"),
        gender: form.get("gender"),
        phone: form.get("phone"),
        address: form.get("address"),
        password: form.get("password")
      })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const mapped: Record<string, string> = {};
      for (const item of body.fieldErrors ?? []) {
        mapped[item.field] = item.message;
      }
      setFieldErrors(mapped);
      setError(body.message ?? "Unable to register");
      return;
    }
    router.push("/patient");
  }

  return (
    <main>
      <section className="card">
        <h1>Patient registration</h1>
        <form onSubmit={onSubmit}>
          <label>Full name<input name="fullName" required />{fieldErrors.fullName && <span className="error">{fieldErrors.fullName}</span>}</label>
          <label>Email<input name="email" type="email" required />{fieldErrors.email && <span className="error">{fieldErrors.email}</span>}</label>
          <label>NIC<input name="nic" required placeholder="199012345678 or 901234567V" />{fieldErrors.nic && <span className="error">{fieldErrors.nic}</span>}</label>
          <label>Date of birth<input name="dateOfBirth" type="date" required />{fieldErrors.dateOfBirth && <span className="error">{fieldErrors.dateOfBirth}</span>}</label>
          <label>Gender
            <select name="gender" required defaultValue="FEMALE">
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
            </select>
          </label>
          <label>Phone<input name="phone" required placeholder="07XXXXXXXX" />{fieldErrors.phone && <span className="error">{fieldErrors.phone}</span>}</label>
          <label>Address<input name="address" required />{fieldErrors.address && <span className="error">{fieldErrors.address}</span>}</label>
          <label>Password<input name="password" type="password" required />{fieldErrors.password && <span className="error">{fieldErrors.password}</span>}</label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Create account</button>
        </form>
      </section>
    </main>
  );
}
