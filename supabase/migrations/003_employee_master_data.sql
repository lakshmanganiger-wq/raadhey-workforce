-- RAADHEY Workforce: employee master data for founder dashboard
alter table public.profiles
  add column if not exists designation text,
  add column if not exists department text,
  add column if not exists joining_date date,
  add column if not exists salary numeric(12,2),
  add column if not exists employment_type text default 'full_time',
  add column if not exists notes text;

create index if not exists profiles_employee_code_idx on public.profiles(employee_code);
create index if not exists attendance_employee_work_date_idx on public.attendance(employee_id, work_date desc);
create index if not exists attendance_events_created_at_idx on public.attendance_events(created_at desc);

-- Admin-only employee profile management is already protected by the
-- "admins manage profiles" RLS policy created in migration 001.
