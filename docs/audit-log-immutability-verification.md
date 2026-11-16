# Audit Log Immutability Verification Guide

This guide provides test SQL commands for pgAdmin 4 Query Tool or `psql` to verify and capture evidence that the `audit_logs` table cannot be tampered with, updated, deleted, or truncated.

---

## 1. Test Verification SQL Script

Open **pgAdmin 4 Query Tool** or terminal `psql` on the `medichannel` database and execute the following statements:

```sql
-- 1. Inspect existing audit logs
SELECT id, user_id, action, entity_type, ip_address, created_at 
FROM audit_logs 
ORDER BY created_at DESC 
LIMIT 5;

-- 2. Attempt to UPDATE an audit log (Should FAIL)
UPDATE audit_logs 
SET action = 'TAMPERED_RECORD' 
WHERE action = 'LOGIN_SUCCESS';

-- 3. Attempt to DELETE from audit_logs (Should FAIL)
DELETE FROM audit_logs;

-- 4. Attempt to TRUNCATE audit_logs (Should FAIL)
TRUNCATE TABLE audit_logs;
```

---

## 2. Expected Database Output (Evidence for Report Screenshot)

### UPDATE Attempt Output:
```text
ERROR:  audit_logs is immutable
CONTEXT:  PL/pgSQL function prevent_audit_log_change() line 3 at RAISE
```

### DELETE Attempt Output:
```text
ERROR:  audit_logs is immutable
CONTEXT:  PL/pgSQL function prevent_audit_log_change() line 3 at RAISE
```

### TRUNCATE Attempt Output:
```text
ERROR:  audit_logs is immutable
CONTEXT:  PL/pgSQL function prevent_audit_log_change() line 3 at RAISE
```

---

## 3. Explanation of Enforcement Mechanism

- **Trigger Function**: `prevent_audit_log_change()` intercepts any incoming write before rows are modified.
- **Triggers**:
  - `trg_audit_logs_no_update`: Row-level trigger for `UPDATE` or `DELETE`.
  - `trg_audit_logs_no_truncate`: Statement-level trigger for `TRUNCATE`.
- **Privilege Revocation**: `REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM PUBLIC;`
