# RAADHEY Workforce setup

## Included
- Android employee app (application ID: `com.raadhey.workforce.v2`; the older app remains separate).
- Supabase email/password sign-in.
- Server-side GPS attendance check-in/check-out and the RAADHEY factory polygon.
- Visible foreground location sharing while checked in (approximately every five minutes, subject to Android power and location settings).
- Founder dashboard in `/admin/index.html`: employee directory, employee account creation, employee profile editing, activation/deactivation, attendance filters, CSV attendance export, GPS view for employees currently checked in, and rejected attendance-event review.

## Supabase database setup
1. Create or open the RAADHEY Workforce Supabase project.
2. In Supabase SQL Editor, run these migrations in order:
   - `supabase/migrations/001_initial_schema.sql`
   - `supabase/migrations/002_factory_polygon_geofence.sql`
   - `supabase/migrations/003_employee_master_data.sql`
3. Create an administrator in Authentication > Users.
4. Promote that account in SQL Editor (replace the email):
   ```sql
   update public.profiles
   set role='admin', full_name='RAADHEY Founder'
   where email='founder@example.com';
   ```
5. Copy the Project URL and public anon/publishable key from Project Settings > API. Never put the `service_role` key in the Android app or browser dashboard.

## Secure employee account creation
The dashboard creates employee Auth accounts through the Supabase Edge Function at `supabase/functions/create-employee/index.ts`. The function verifies the caller's signed-in session and checks that the caller is an active admin before using the service-role key server-side.

Deploy it once using the Supabase CLI from the repository root:
```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase functions deploy create-employee
```
The Supabase Edge Function runtime provides `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`. If your project/runtime does not expose `SUPABASE_ANON_KEY` automatically, set it as a function secret using the public anon key:
```bash
supabase secrets set SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```
Do not commit secrets to GitHub. The dashboard sends employee details and a temporary password to this function over the authenticated Supabase session. New employee login email is generated as `EMPLOYEEID@employees.raadhey.com`; employees enter only their employee ID in the Android app. Give each employee their temporary password securely.

## Founder dashboard
Host the repository's `admin` folder as a static website (for example, GitHub Pages or another HTTPS static host) and open `admin/index.html`. Sign in with the Supabase Project URL, public anon/publishable key, and active administrator account. The dashboard does not require or accept a service-role key.

Dashboard features:
- Add employees with name, employee ID, phone, designation, department, joining date, employment type and monthly salary.
- Edit employee master data; deactivate or reactivate employee accounts.
- Search/filter employee records.
- Filter attendance by date range, employee and status.
- Export the filtered attendance register to CSV for payroll preparation.
- View latest GPS updates only for employees with an open check-in.
- Review accepted and rejected attendance events, including GPS accuracy and distance.

## Attendance rules
Factory boundary is the four-corner polygon stored in migration 002. Shift is 09:00–18:00 IST; late after 09:15; Sunday weekly holiday. Check-in/out are rejected outside the boundary or when reported GPS accuracy exceeds 150m.

## Privacy and operational notes
Android location sharing runs as a visible foreground service and shows a persistent notification. Employees must be informed that location is shared while checked in. The founder map filters to employees with an open attendance session; it does not present checked-out employees as currently tracked. Android battery optimisation and manufacturer restrictions can affect background update frequency. Test on employee phones before operational rollout.

The attendance CSV is a record export, not a payroll calculation or statutory wage statement. Review attendance, leave, overtime, salary terms and applicable deductions before paying employees.
