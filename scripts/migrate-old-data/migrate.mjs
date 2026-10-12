// One-time import from the old Supabase app (e3dady2026) into the new schema.
//
//   npm run dry-run   -> reads both sides and prints what would be imported (writes nothing)
//   npm run apply     -> imports everything in ONE transaction (all or nothing)
//
// What is imported:
//   users        -> app_users   (unverified; owners become SUPER_ADMIN, approved users ADMIN)
//   groups       -> student_groups (same ids, balance = last group event)
//   students     -> students       (same ids, balance = last point event)
//   point_events / group_point_events -> point_transactions (full history, as manual transactions)
//   attendance   -> attendance     (NO automatic points are created for it)
// and app_settings.points_start_date = POINTS_START_DATE, so points are only distributed from then on.
//
// The old database is only read, never modified.

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import pg from "pg";

const APPLY = process.argv.includes("--apply");
const env = (name, fallback) => {
  const value = process.env[name] ?? fallback;
  if (value === undefined || value === "") throw new Error(`Missing ${name} (see .env.migrate.example)`);
  return value;
};

// Source: JSON exports in OLD_DATA_DIR (one <table>.txt/.json per table), or the live Supabase REST API.
const OLD_DATA_DIR = process.env.OLD_DATA_DIR || (existsSync(new URL("./old data", import.meta.url)) ? fileURLToPath(new URL("./old data", import.meta.url)) : "");
const OLD_URL = OLD_DATA_DIR ? "" : env("OLD_SUPABASE_URL").replace(/\/+$/, "");
const OLD_KEY = OLD_DATA_DIR ? "" : env("OLD_SUPABASE_SERVICE_ROLE_KEY");
const NEW_URL = env("NEW_DATABASE_URL");
const POINTS_START_DATE = env("POINTS_START_DATE");
const OWNER_EMAILS = new Set(env("OWNER_EMAILS", "").split(",").map((e) => e.trim().toLowerCase()).filter(Boolean));

if (!/^\d{4}-\d{2}-\d{2}$/.test(POINTS_START_DATE)) throw new Error("POINTS_START_DATE must be YYYY-MM-DD");

const GRADES = { "الأول": "FIRST", "الثاني": "SECOND", "الثالث": "THIRD" };
const KINDS = { add: "MANUAL_ADD", deduct: "MANUAL_DEDUCT", reset: "RESET" };

// ---- Old side: Supabase REST with the service role key (bypasses RLS, read-only use) --------

function readExport(table) {
  const file = [".txt", ".json"].map((ext) => join(OLD_DATA_DIR, table + ext)).find(existsSync);
  if (!file) throw new Error(`Missing export for ${table} in ${OLD_DATA_DIR}`);
  // Exports copied from the SQL editor may end with "...},\n]"; drop trailing commas before parsing.
  const text = readFileSync(file, "utf8").replace(/^﻿/, "").trim().replace(/,\s*([\]}])/g, "$1");
  return text ? JSON.parse(text) : [];
}

async function readAll(table, select, order = "id") {
  if (OLD_DATA_DIR) {
    const key = order;
    return readExport(table).sort((a, b) => (a[key] < b[key] ? -1 : a[key] > b[key] ? 1 : 0));
  }
  const rows = [];
  const pageSize = 1000;
  for (let from = 0; ; from += pageSize) {
    const url = `${OLD_URL}/rest/v1/${table}?select=${encodeURIComponent(select)}&order=${order}.asc`;
    const headers = { apikey: OLD_KEY, Range: `${from}-${from + pageSize - 1}`, "Range-Unit": "items" };
    // Legacy service_role keys are JWTs and go in Authorization too; new sb_secret_ keys must not.
    if (!OLD_KEY.startsWith("sb_")) headers.Authorization = `Bearer ${OLD_KEY}`;
    const response = await fetch(url, { headers });
    if (!response.ok) throw new Error(`Reading ${table} failed: HTTP ${response.status} ${await response.text()}`);
    const page = await response.json();
    rows.push(...page);
    if (page.length < pageSize) return rows;
  }
}

// ---- Main ------------------------------------------------------------------------------------

const lower = (s) => (s ?? "").trim().toLowerCase();
const clip = (s, max) => (s ?? "").slice(0, max);

async function main() {
  console.log(`Mode: ${APPLY ? "APPLY (writes to the new database)" : "DRY RUN (nothing is written)"}`);
  console.log(`Source: ${OLD_DATA_DIR ? `exported files in ${OLD_DATA_DIR}` : OLD_URL}\n`);

  const [users, groups, students, attendance, pointEvents, groupEvents] = await Promise.all([
    readAll("users", "id,email,display_name,avatar_url,role,status,created_at,last_login_at", "created_at"),
    readAll("groups", "id,name,created_at"),
    readAll("students", "id,name,grade,group_id,active,phone,created_at"),
    readAll("attendance", "id,student_id,date,service,mass,updated_by,updated_at"),
    readAll("point_events", "id,student_id,kind,delta,balance_after,reason,actor_email,created_at"),
    readAll("group_point_events", "id,group_id,kind,delta,balance_after,reason,actor_email,created_at"),
  ]);

  // Validate before touching anything.
  const badGrades = students.filter((s) => !GRADES[s.grade]);
  if (badGrades.length) throw new Error(`Unknown grades: ${[...new Set(badGrades.map((s) => s.grade))].join(", ")}`);
  const studentIds = new Set(students.map((s) => s.id));
  const groupIds = new Set(groups.map((g) => g.id));
  const orphanAttendance = attendance.filter((a) => !studentIds.has(a.student_id));
  const orphanEvents = pointEvents.filter((e) => !studentIds.has(e.student_id));

  // Balances = balance_after of each target's latest event (events are ordered by id).
  const studentBalance = new Map();
  for (const e of pointEvents) studentBalance.set(e.student_id, e.balance_after);
  const groupBalance = new Map();
  for (const e of groupEvents) groupBalance.set(e.group_id, e.balance_after);

  console.log("Old app contains:");
  console.table({
    users: users.length,
    groups: groups.length,
    students: `${students.length} (${students.filter((s) => s.active).length} active)`,
    attendance: `${attendance.length} rows, ${new Set(attendance.map((a) => a.date)).size} meetings`,
    "student point events": pointEvents.length,
    "group point events": groupEvents.length,
    "total student points": [...studentBalance.values()].reduce((a, b) => a + b, 0),
  });
  if (orphanAttendance.length || orphanEvents.length) {
    console.log(`Skipping ${orphanAttendance.length} attendance rows and ${orphanEvents.length} events of unknown students.`);
  }

  const db = new pg.Client({ connectionString: NEW_URL });
  await db.connect();
  try {
    const { rows: [{ version }] } = await db.query(
      "select max(version::int) as version from flyway_schema_history where success");
    if (version < 4) throw new Error(`New database is at schema v${version}; deploy the backend (needs v4) first.`);

    const { rows: [counts] } = await db.query(`select
        (select count(*) from students)::int as students,
        (select count(*) from student_groups)::int as groups,
        (select count(*) from attendance)::int as attendance,
        (select count(*) from point_transactions)::int as transactions`);
    if (Object.values(counts).some((n) => n > 0)) {
      throw new Error(`New database already has data ${JSON.stringify(counts)}; refusing to import twice.`);
    }

    if (!APPLY) {
      console.log(`\nNew database is empty and ready. Points will start from ${POINTS_START_DATE}.`);
      console.log("Run `npm run apply` to import.");
      return;
    }

    await db.query("begin");

    // Users: keep accounts that already exist in the new app (matched by email).
    const userIdByEmail = new Map();
    const { rows: existing } = await db.query("select id, email from app_users");
    for (const u of existing) userIdByEmail.set(lower(u.email), u.id);
    let usersCreated = 0;
    for (const u of users) {
      const email = lower(u.email);
      if (!email || userIdByEmail.has(email)) continue;
      const owner = OWNER_EMAILS.has(email) || u.role === "superAdmin";
      // In the old app every approved account could record attendance and points -> ADMIN here.
      const status = owner ? "APPROVED" : u.status === "approved" ? "APPROVED" : u.status === "rejected" ? "REJECTED" : "PENDING";
      const role = owner ? "SUPER_ADMIN" : status === "APPROVED" ? "ADMIN" : "USER";
      const avatar = u.avatar_url && u.avatar_url.length <= 500 ? u.avatar_url : null;
      const { rows: [created] } = await db.query(
        `insert into app_users (email, display_name, avatar_url, role, status, email_verified, created_at, last_login_at)
         values ($1, $2, $3, $4, $5, false, $6, $7) returning id`,
        [email, clip(u.display_name || email, 150), avatar, role, status, u.created_at, u.last_login_at]);
      userIdByEmail.set(email, created.id);
      usersCreated++;
    }
    const actor = (email) => userIdByEmail.get(lower(email)) ?? null;

    for (const g of groups) {
      await db.query(
        "insert into student_groups (id, name, points_balance, created_at) values ($1, $2, $3, $4)",
        [g.id, clip(g.name, 60), groupBalance.get(g.id) ?? 0, g.created_at]);
    }
    for (const s of students) {
      await db.query(
        `insert into students (id, name, grade, phone, group_id, active, points_balance, created_at)
         values ($1, $2, $3, $4, $5, $6, $7, $8)`,
        [s.id, clip(s.name, 100), GRADES[s.grade], clip((s.phone ?? "").replace(/[\s-]/g, ""), 30),
         groupIds.has(s.group_id) ? s.group_id : null, s.active, studentBalance.get(s.id) ?? 0, s.created_at]);
    }

    for (const e of pointEvents.filter((e) => studentIds.has(e.student_id))) {
      await db.query(
        `insert into point_transactions (student_id, type, amount, balance_after, notes, created_by_id, created_at)
         values ($1, $2, $3, $4, $5, $6, $7)`,
        [e.student_id, KINDS[e.kind], e.delta, e.balance_after, clip(e.reason, 255), actor(e.actor_email), e.created_at]);
    }
    for (const e of groupEvents.filter((e) => groupIds.has(e.group_id))) {
      await db.query(
        `insert into point_transactions (group_id, type, amount, balance_after, notes, created_by_id, created_at)
         values ($1, $2, $3, $4, $5, $6, $7)`,
        [e.group_id, KINDS[e.kind], e.delta, e.balance_after, clip(e.reason, 255), actor(e.actor_email), e.created_at]);
    }

    // Attendance is copied as-is: no ATTENDANCE_AWARD rows, so it carries no automatic points.
    for (const a of attendance.filter((a) => studentIds.has(a.student_id))) {
      await db.query(
        `insert into attendance (student_id, meeting_date, service, mass, updated_by_id, updated_at)
         values ($1, $2, $3, $4, $5, $6)`,
        [a.student_id, a.date, a.service, a.mass, actor(a.updated_by), a.updated_at]);
    }

    // Explicit ids were inserted; move the sequences past them.
    await db.query("select setval(pg_get_serial_sequence('students', 'id'), greatest((select max(id) from students), 1))");
    await db.query("select setval(pg_get_serial_sequence('student_groups', 'id'), greatest((select max(id) from student_groups), 1))");

    await db.query("update app_settings set points_start_date = $1 where id = 1", [POINTS_START_DATE]);

    // Sanity check: every cached balance must equal the sum of its ledger.
    const { rows: mismatches } = await db.query(`
      select s.id, s.points_balance, coalesce(sum(t.amount), 0) as ledger
      from students s left join point_transactions t on t.student_id = s.id
      group by s.id having s.points_balance <> coalesce(sum(t.amount), 0)`);
    if (mismatches.length) throw new Error(`Balance check failed for ${mismatches.length} students: ${JSON.stringify(mismatches.slice(0, 5))}`);

    await db.query("commit");
    console.log("\nImport committed:");
    console.table({
      "users created": usersCreated,
      groups: groups.length,
      students: students.length,
      "point transactions": pointEvents.length + groupEvents.length,
      attendance: attendance.length - orphanAttendance.length,
      "points start date": POINTS_START_DATE,
    });
  } catch (error) {
    await db.query("rollback").catch(() => {});
    throw error;
  } finally {
    await db.end();
  }
}

main().catch((error) => {
  console.error(`\nFAILED: ${error.message}`);
  process.exit(1);
});
