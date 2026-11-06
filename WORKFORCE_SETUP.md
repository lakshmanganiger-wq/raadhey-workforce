# RAADHEY Workforce — Firebase Spark setup

Android package: `com.raadhey.workforce.v3`  
Firebase project: `raadhey-workforce-66e2a`  
Realtime Database: Singapore (`asia-southeast1`)

## Current architecture

- Firebase Authentication Email/Password for employee and founder sign-in.
- Realtime Database for employee profiles, daily attendance and live location.
- No Cloud Functions are required for this Spark-plan version.
- Founder dashboard is hosted from the `admin` folder.
- Employee IDs are converted to login emails as `employeeid@employees.raadhey.com`.

## One-time founder setup

1. Create the founder login in Firebase Authentication (Email/Password).
2. In Realtime Database > Data, create/update `profiles/FOUNDER_AUTH_UID` with:
   ```json
   {
     "active": true,
     "employeeCode": "ADMIN",
     "fullName": "RAADHEY Founder",
     "role": "admin"
   }
   ```
   Replace `FOUNDER_AUTH_UID` with the exact User UID shown in Authentication. Do not use the email as the key.
3. Publish the `admin` folder using Firebase Hosting. The founder signs into that dashboard using the same email and password.

## Create employees

Open Founder Dashboard > Employees > Create employee account. Enter name, Employee ID and a temporary password of at least 8 characters. The dashboard creates the Firebase Authentication login and profile. Share the Employee ID and temporary password with the employee privately.

The dashboard uses a secondary Firebase Auth session to create an employee without signing the founder out. Realtime Database rules permit profile creation only by an active profile whose role is `admin`. Ordinary employees cannot create or edit profiles.

**Important Spark limitation:** Firebase's client-side Email/Password sign-up endpoint is not an admin-only endpoint. The dashboard gates account creation behind the founder interface and database rules prevent unapproved accounts from obtaining an employee profile, but this is not equivalent to trusted Admin SDK provisioning. Do not treat it as tamper-proof HR security. Strong server-enforced provisioning requires a trusted backend such as Cloud Functions (which requires Blaze billing).

## Attendance and location

- Employee app: one check-in and one check-out per day.
- GPS validation uses the confirmed four-corner factory polygon in the Android app.
- Location accuracy must be 150 metres or better.
- Live location is intended to run only during an active shift and stop at check-out.
- All dates use Asia/Kolkata.

## Security

- Keep Realtime Database locked; publish the repository's `database.rules.json` rules.
- Do not enable public read/write access.
- The admin profile is the authority for founder dashboard access. Protect the founder password and account.
- Spark plan only; no paid upgrade is needed for this prototype.
