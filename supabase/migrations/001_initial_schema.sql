-- RAADHEY Workforce initial schema
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  employee_code text unique,
  full_name text not null,
  email text,
  phone text,
  role text not null default 'employee' check (role in ('employee','admin')),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.workplace_settings (
  id uuid primary key default gen_random_uuid(),
  workplace_name text not null default 'RAADHEY Factory',
  latitude double precision not null default 13.0868273605,
  longitude double precision not null default 77.4191855295,
  geofence_radius_m integer not null default 100,
  boundary_coordinates jsonb,
  shift_start time not null default '09:00',
  shift_end time not null default '18:00',
  late_after time not null default '09:15',
  weekly_holiday integer not null default 0,
  timezone text not null default 'Asia/Kolkata',
  attendance_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);

create table if not exists public.attendance (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.profiles(id) on delete cascade,
  work_date date not null,
  check_in timestamptz,
  check_out timestamptz,
  check_in_lat double precision,
  check_in_lng double precision,
  check_in_accuracy_m double precision,
  check_in_distance_m double precision,
  check_out_lat double precision,
  check_out_lng double precision,
  check_out_accuracy_m double precision,
  check_out_distance_m double precision,
  worked_minutes integer,
  status text not null default 'present' check (status in ('present','late','absent','half_day')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(employee_id, work_date)
);

create table if not exists public.attendance_events (
  id uuid primary key default gen_random_uuid(),
  employee_id uuid not null references public.profiles(id) on delete cascade,
  attendance_id uuid references public.attendance(id) on delete set null,
  event_type text not null,
  latitude double precision,
  longitude double precision,
  accuracy_m double precision,
  distance_from_workplace_m double precision,
  outcome text not null default 'accepted',
  details text,
  created_at timestamptz not null default now()
);

create table if not exists public.employee_locations (
  id bigint generated always as identity primary key,
  employee_id uuid not null references public.profiles(id) on delete cascade,
  latitude double precision not null,
  longitude double precision not null,
  accuracy_m double precision,
  recorded_at timestamptz not null default now()
);
create index if not exists employee_locations_employee_time_idx on public.employee_locations(employee_id, recorded_at desc);
create index if not exists attendance_work_date_idx on public.attendance(work_date desc);

create or replace function public.distance_m(lat1 double precision, lon1 double precision, lat2 double precision, lon2 double precision)
returns double precision language sql immutable strict as $$
  select 6371000 * 2 * asin(sqrt(
    power(sin(radians(lat2-lat1)/2),2) +
    cos(radians(lat1))*cos(radians(lat2))*power(sin(radians(lon2-lon1)/2),2)
  ));
$$;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path=public as $$
  select exists(select 1 from public.profiles where id=auth.uid() and role='admin' and active=true);
$$;

alter table public.profiles enable row level security;
alter table public.workplace_settings enable row level security;
alter table public.attendance enable row level security;
alter table public.attendance_events enable row level security;
alter table public.employee_locations enable row level security;

drop policy if exists "profiles read self or admin" on public.profiles;
create policy "profiles read self or admin" on public.profiles for select to authenticated using (id=auth.uid() or public.is_admin());
drop policy if exists "admins manage profiles" on public.profiles;
create policy "admins manage profiles" on public.profiles for all to authenticated using (public.is_admin()) with check (public.is_admin());
drop policy if exists "authenticated read workplace settings" on public.workplace_settings;
create policy "authenticated read workplace settings" on public.workplace_settings for select to authenticated using (true);
drop policy if exists "admins manage workplace settings" on public.workplace_settings;
create policy "admins manage workplace settings" on public.workplace_settings for all to authenticated using (public.is_admin()) with check (public.is_admin());
drop policy if exists "attendance employee or admin read" on public.attendance;
create policy "attendance employee or admin read" on public.attendance for select to authenticated using (employee_id=auth.uid() or public.is_admin());
drop policy if exists "events employee or admin read" on public.attendance_events;
create policy "events employee or admin read" on public.attendance_events for select to authenticated using (employee_id=auth.uid() or public.is_admin());
drop policy if exists "locations admin read employee insert" on public.employee_locations;
create policy "locations admin read employee insert" on public.employee_locations for select to authenticated using (employee_id=auth.uid() or public.is_admin());
create policy "employees insert own locations" on public.employee_locations for insert to authenticated with check (employee_id=auth.uid());

insert into public.workplace_settings (workplace_name, latitude, longitude, geofence_radius_m, shift_start, shift_end, late_after, weekly_holiday, timezone)
select 'RAADHEY Factory',13.0868273605,77.4191855295,100,'09:00','18:00','09:15',0,'Asia/Kolkata'
where not exists(select 1 from public.workplace_settings);

-- Create a profile automatically when an Auth user is added. Admins can later set role and employee_code.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  insert into public.profiles(id, full_name, email, employee_code, role)
  values(new.id, coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email,'@',1)), new.email,
         new.raw_user_meta_data->>'employee_code', 'employee')
  on conflict(id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.handle_new_user();

grant select on public.profiles, public.workplace_settings, public.attendance, public.attendance_events, public.employee_locations to authenticated;
grant insert on public.employee_locations to authenticated;
