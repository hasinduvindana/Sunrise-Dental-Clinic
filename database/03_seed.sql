-- =====================================================================
-- Sunrise Dental Clinic - Reference data
-- User accounts are NOT seeded here: the application creates the first
-- super admin on start-up so the password hash always matches the
-- hashing routine in util.PasswordUtil.
-- =====================================================================
USE sunrise_dental;

INSERT INTO treatments (code, name, base_price, duration_minutes) VALUES
 ('CONS',  'Consultation only',        0.00,    15),
 ('SCAL',  'Scaling and polishing',    6500.00, 45),
 ('FILL',  'Composite filling',        4500.00, 40),
 ('EXTR',  'Tooth extraction',         5500.00, 30),
 ('RCT',   'Root canal treatment',     25000.00, 90),
 ('CROWN', 'Crown fitting',            35000.00, 60),
 ('WHIT',  'Teeth whitening',          18000.00, 60),
 ('DENT',  'Denture fitting',          45000.00, 90),
 ('ORTHO', 'Orthodontic review',       3500.00, 30),
 ('XRAY',  'Dental X-ray',             2500.00, 15);

INSERT INTO settings (setting_key, setting_value) VALUES
 ('clinic.name',         'Sunrise Dental Clinic'),
 ('clinic.address',      'No. 152, Galle Road, Colombo 03'),
 ('clinic.phone',        '+94 11 234 5678'),
 ('clinic.email',        'info@sunrisedental.lk'),
 ('clinic.logo',         'logo.png'),
 ('billing.tax.percent', '0'),
 ('billing.vip.discount.percent', '10'),
 ('billing.currency',    'LKR'),
 ('billing.bill.prefix', 'SDC'),
 ('session.default.max.patients', '20');
