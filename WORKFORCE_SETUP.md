# RAADHEY Workforce — Firebase setup

Android package: `com.raadhey.workforce.v3`  
Firebase project: `raadhey-workforce-66e2a`  
Realtime Database region: Singapore (`asia-southeast1`)  
Cloud Functions region: Mumbai (`asia-south1`)

## Backend

The Android app uses Firebase Authentication and Realtime Database. Attendance check-in/check-out and live GPS writes go through callable Cloud Functions. Client writes to attendance, profiles, GPS and events are denied by Realtime Database rules.

## Required one-time Firebase deployment

1. Install Node.js 20 and Firebase CLI on a computer.
2. Run `npm install -g firebase-tools` then `firebase login`.
3. In Firebase Console, upgrade this Firebase project to the Blaze plan. Cloud Functions deployment requires billing enabled. Set a budget alert.
4. From the repository root run:
   `firebase deploy --only database,functions`
5. In Firebase Console > Authentication, create the founder's administrator user with an email and password.
6. In Firebase Console > Project settings > Service accounts, generate a private key JSON. Save it locally as `serviceAccountKey.json` in the repository root. Never commit or upload this key.
7. Run `npm install firebase-admin`, then:
   `node scripts/grant-admin.js founder@example.com`
   Replace the email with the founder account email. Delete the downloaded service-account key from the computer after use.
8. Open `admin/index.html` using a static web host (for example GitHub Pages). The founder dashboard signs in using the administrator account.

## Employee setup

After the founder is granted the admin claim, use the dashboard's Employees tab to create employee accounts. Employee IDs become login emails in the format `EMPLOYEEID@employees.raadhey.com`. The temporary password must be at least 8 characters.

## Factory geofence

The server-side function uses the factory polygon carried over from the previous workplace configuration:
- [13.0869225, 77.4191348]
- [13.0868676, 77.4192857]
- [13.0867285, 77.4192381]
- [13.0867945, 77.4190815]

Review these coordinates before production deployment. Attendance is restricted to the polygon, GPS accuracy must be 150 metres or better, shift timezone is Asia/Kolkata, late arrival is after 09:15, and Sunday is weekly holiday.

## Security notes

- Never make the Realtime Database public.
- Never commit a service account JSON key.
- Founder dashboard requires the Firebase custom claim `admin: true`.
- Only Cloud Functions Admin SDK writes attendance and live location data.
