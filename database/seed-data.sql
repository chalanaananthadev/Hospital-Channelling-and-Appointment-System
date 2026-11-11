-- Non-PII reference data. User/patient/clinical seed rows are created by
-- Spring DataSeeder so AES-256-GCM and HMAC-SHA256 are applied correctly.

INSERT INTO specialisations (id, name, description) VALUES
    ('11111111-1111-4111-8111-111111111001', 'Cardiology', 'Heart and vascular care'),
    ('11111111-1111-4111-8111-111111111002', 'Dermatology', 'Skin, hair and nail conditions'),
    ('11111111-1111-4111-8111-111111111003', 'ENT', 'Ear, nose and throat'),
    ('11111111-1111-4111-8111-111111111004', 'General Medicine', 'Adult general medical consultations'),
    ('11111111-1111-4111-8111-111111111005', 'Gynaecology', 'Women''s health'),
    ('11111111-1111-4111-8111-111111111006', 'Neurology', 'Brain and nerve disorders'),
    ('11111111-1111-4111-8111-111111111007', 'Orthopaedics', 'Bones, joints and sports injuries'),
    ('11111111-1111-4111-8111-111111111008', 'Paediatrics', 'Child health'),
    ('11111111-1111-4111-8111-111111111009', 'Psychiatry', 'Mental health consultations'),
    ('11111111-1111-4111-8111-111111111010', 'Radiology', 'Imaging review clinics'),
    ('11111111-1111-4111-8111-111111111011', 'Surgery', 'General surgical opinions'),
    ('11111111-1111-4111-8111-111111111012', 'Urology', 'Urinary and men''s health'),
    ('11111111-1111-4111-8111-111111111013', 'Ophthalmology', 'Eye care'),
    ('11111111-1111-4111-8111-111111111014', 'Pulmonology', 'Chest and lung clinics'),
    ('11111111-1111-4111-8111-111111111015', 'Endocrinology', 'Hormone and diabetes care')
ON CONFLICT (name) DO NOTHING;

INSERT INTO rooms (id, name, location) VALUES
    ('22222222-2222-4222-8222-222222222001', 'Room 101', 'Block A, Ground Floor'),
    ('22222222-2222-4222-8222-222222222002', 'Room 102', 'Block A, Ground Floor'),
    ('22222222-2222-4222-8222-222222222003', 'Room 201', 'Block B, First Floor'),
    ('22222222-2222-4222-8222-222222222004', 'Room 202', 'Block B, First Floor'),
    ('22222222-2222-4222-8222-222222222005', 'Paediatric Clinic', 'Block C, Ground Floor')
ON CONFLICT (name) DO NOTHING;
