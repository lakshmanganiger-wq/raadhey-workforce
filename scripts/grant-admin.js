const admin=require("firebase-admin");
const email=process.argv[2];
if(!email){console.error("Usage: node scripts/grant-admin.js founder@example.com");process.exit(1);}
const keyPath=process.env.GOOGLE_APPLICATION_CREDENTIALS||"./serviceAccountKey.json";
const serviceAccount=require(require("path").resolve(keyPath));
admin.initializeApp({credential:admin.credential.cert(serviceAccount),databaseURL:"https://raadhey-workforce-66e2a-default-rtdb.asia-southeast1.firebasedatabase.app"});
(async()=>{const user=await admin.auth().getUserByEmail(email);await admin.auth().setCustomUserClaims(user.uid,{admin:true});await admin.database().ref("profiles/"+user.uid).set({fullName:user.displayName||"RAADHEY Administrator",email:user.email,role:"admin",active:true,createdAt:admin.database.ServerValue.TIMESTAMP});console.log("Admin access granted to "+email+". Sign out and sign in again to refresh the token.");process.exit(0);})().catch(e=>{console.error(e);process.exit(1);});
