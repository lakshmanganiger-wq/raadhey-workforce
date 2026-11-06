# RAADHEY Workforce setup

## Included
- Android employee app (application ID: com.raadhey.workforce.v2; the older app remains separate).
- Supabase email/password sign-in.
- Server-side GPS attendance check-in/check-out and the RAADHEY factory polygon.
- Foreground location sharing while checked in (approximately every five minutes, subject to Android power and location settings).
- Browser admin dashboard in /admin/index.html.

## Supabase setup
1. Create or open the RAADHEY Workforce Supabase project.
2. In SQL Editor, run supabase/migrations/001_initial_schema.sql, then supabase/migrations/002_factory_polygon_geofence.sql.
3. In Authentication > Users, create an admin user and each employee user. For employees, use the sign-in email format `EMPLOYEEID@employees.raadhey.com` (for example, `RHD001@employees.raadhey.com`); employees type only their ID in the app. Set passwords and confirm the accounts in Supabase.
4. In SQL Editor, promote the administrator and create employee codes. Replace the email address below:
   `update public.profiles set role='admin', full_name='RAADHEY Admin' where email='admin@example.com';`
   Employee codes can be assigned with:
   `update public.profiles set employee_code='RHD001', full_name='Employee Name' where email='employee@example.com';`
5. Copy the Project URL and public anon/publishable key from Project Settings > API. Never use the service_role key in the app or dashboard.

## Build configuration
The GitHub Actions build reads two repository Actions secrets:
- SUPABASE_URL
- SUPABASE_ANON_KEY

Add them under Repository > Settings > Secrets and variables > Actions, then run the "Build RAADHEY Android APK" workflow. If these are missing, the APK can compile but cannot sign in or sync.

## Admin dashboard
Open /admin/index.html in a static host (for example, deploy the repository's admin folder to a static site). Sign in with the Supabase URL, public anon key, and an active profile whose role is admin.

## Attendance rules
Factory boundary is the four-corner polygon stored in migration 002. Shift is 09:00–18:00 IST; late after 09:15; Sunday weekly holiday. Check-in/out are rejected outside the boundary or when reported GPS accuracy exceeds 150m.

## Important
Android location sharing runs as a visible foreground service and shows a persistent notification. Employees should be informed that location is shared while checked in. Android battery optimisation and manufacturer restrictions can affect background update frequency. Test on employee phones before operational rollout.
