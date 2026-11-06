import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const respond = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return respond({ error: "Method not allowed" }, 405);

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) return respond({ error: "Sign-in required" }, 401);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const caller = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false },
    });
    const { data: { user }, error: authError } = await caller.auth.getUser();
    if (authError || !user) return respond({ error: "Invalid session" }, 401);

    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
    const { data: actor, error: actorError } = await admin
      .from("profiles").select("role,active").eq("id", user.id).single();
    if (actorError || actor?.role !== "admin" || actor?.active !== true) {
      return respond({ error: "Founder/admin access required" }, 403);
    }

    const body = await req.json();
    const full_name = String(body.full_name ?? "").trim();
    const employee_code = String(body.employee_code ?? "").trim().toUpperCase();
    const password = String(body.password ?? "");
    const email = employee_code.toLowerCase() + "@employees.raadhey.com";
    if (!full_name || !employee_code || password.length < 8) {
      return respond({ error: "Name, employee ID and a password of at least 8 characters are required." }, 400);
    }
    if (!/^[A-Z0-9_-]{2,24}$/.test(employee_code)) {
      return respond({ error: "Employee ID may contain 2–24 letters, numbers, hyphens or underscores." }, 400);
    }

    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email,
      password,
      email_confirm: true,
      user_metadata: { full_name, employee_code },
    });
    if (createError || !created.user) return respond({ error: createError?.message ?? "Could not create employee." }, 400);

    const { error: profileError } = await admin.from("profiles").upsert({
      id: created.user.id,
      full_name,
      email,
      employee_code,
      phone: body.phone || null,
      designation: body.designation || null,
      department: body.department || null,
      joining_date: body.joining_date || null,
      salary: body.salary === "" || body.salary == null ? null : Number(body.salary),
      employment_type: body.employment_type || "full_time",
      role: "employee",
      active: true,
    }, { onConflict: "id" });

    if (profileError) {
      await admin.auth.admin.deleteUser(created.user.id);
      return respond({ error: "Employee profile could not be saved: " + profileError.message }, 500);
    }
    return respond({ success: true, employee_id: created.user.id, employee_code, email });
  } catch (error) {
    return respond({ error: error instanceof Error ? error.message : "Unexpected server error" }, 500);
  }
});
