// Local stand-in for https://fewchurs.com/api/v1 so the SDK can be tested with no account.
// Mirrors the real routes, validation rules, error envelope and rate limits. Data is in memory.
//
//   node tools/mock-server/server.mjs            # port 4010
//   PORT=5000 LATENCY=800 node tools/mock-server/server.mjs
//
// Any API key starting with "fr_" works, except "fr_bad" (-> 401 invalid_key).
// Special titles when submitting: "fail" -> 500, "limit" -> 402 limit_reached.
import { createServer } from "node:http";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT ?? 4010);
const LATENCY = Number(process.env.LATENCY ?? 300);
const HIDE_VOTES = process.env.HIDE_VOTES === "1";
const PRO = process.env.PRO === "1";

const now = Date.now();
const iso = (msAgo) => new Date(now - msAgo).toISOString();
const day = 86_400_000;

const features = new Map();
const seed = [
  ["Dark mode", "A dark theme for night owls. Easier on the eyes.", "in_progress", 42, 5],
  ["Export to CSV", "Let me export my data to a spreadsheet.", "planned", 31, 12],
  ["Apple Watch app", "Quick glance at my stats from the wrist.", "open", 18, 2],
  ["Offline mode", "Keep working on the subway.", "open", 15, 9],
  ["Widgets", "Home screen widgets with today's summary.", "done", 27, 30],
  ["Share with family", "Invite family members to a shared space.", "open", 9, 1],
  ["Custom app icons", "", "declined", 3, 40],
  ["Keyboard shortcuts", "For tablet users with a keyboard.", "open", 6, 4],
  ["Two-factor authentication", "Extra security for my account.", "planned", 22, 20],
  ["Localized in German", "Bitte!", "open", 4, 3],
  ["Recurring reminders", "Every Monday at 9, for example.", "open", 11, 6],
  ["Better search", "Search inside notes, not just titles.", "open", 13, 8],
  ["iPad layout", "Use the whole screen.", "open", 7, 15],
  ["Undo delete", "I deleted something by mistake once...", "done", 19, 25],
  ["Siri shortcuts", "", "open", 2, 0.5],
  ["Calendar sync", "Two-way sync with Google Calendar.", "open", 16, 10],
  ["Tags and filters", "Organize with tags.", "open", 10, 11],
  ["Markdown support", "Bold, lists, links.", "open", 8, 13],
  ["Faster startup", "It takes 3 seconds to open on my old phone.", "in_progress", 25, 7],
  ["Themes", "More accent colors please.", "open", 5, 2],
  ["Import from Evernote", "", "open", 3, 1.5],
  ["Password-protected notes", "", "open", 12, 16],
  ["Reading time estimate", "", "open", 1, 0.2],
];
seed.forEach(([title, description, status, upvotes, daysAgo], i) => {
  const id = `feat_${String(i + 1).padStart(2, "0")}`;
  features.set(id, {
    id, title, description, status,
    upvoteCount: upvotes, commentCount: 0, followerCount: 0,
    authorDeviceId: "seed-device", authorIsSubscriber: i % 4 === 0, translation: null,
    createdAt: iso(daysAgo * day), updatedAt: iso(daysAgo * day),
    votes: new Set(), followers: new Map(), comments: [],
  });
});
const addComment = (fid, text, authorName, isDeveloper, deviceId = "seed-device", isDeleted = false) => {
  const f = features.get(fid);
  f.comments.push({ id: `c_${randomUUID().slice(0, 8)}`, text, authorName, deviceId, isDeveloper, isDeleted, createdAt: new Date().toISOString() });
  if (!isDeleted) f.commentCount++;
};
addComment("feat_01", "Yes please, my eyes hurt at night.", "Maya", false);
addComment("feat_01", "Deleted comment", "Spam", false, "x", true);
addComment("feat_01", "We're on it! Shipping in the next release.", "Team", true);
addComment("feat_02", "Would love Excel format too.", "Jonas", false);

const quotas = new Map(); // `${kind}:${device}:${day}` -> count

const send = (res, status, body) => {
  setTimeout(() => {
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(JSON.stringify(body));
  }, LATENCY);
};
const fail = (res, code, message) => {
  const status = { invalid_key: 401, missing_device_id: 400, validation_failed: 400, rate_limited: 429, not_found: 404, internal: 500, limit_reached: 402 }[code];
  send(res, status, { error: { code, message } });
};
const isEmail = (s) => typeof s === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s.trim());
const underQuota = (kind, device, max) => {
  const key = `${kind}:${device}:${new Date().toISOString().slice(0, 10)}`;
  const n = quotas.get(key) ?? 0;
  if (n >= max) return false;
  quotas.set(key, n + 1);
  return true;
};
const publicFeature = (f, device) => {
  const { votes, followers, comments, ...rest } = f;
  return { ...rest, upvoteCount: HIDE_VOTES ? 0 : f.upvoteCount, hasVoted: votes.has(device), isFollowing: followers.has(device) };
};
const readBody = (req) => new Promise((resolve) => {
  let data = "";
  req.on("data", (c) => (data += c));
  req.on("end", () => { try { resolve(JSON.parse(data || "null")); } catch { resolve(null); } });
});
const page = (items, limit, cursor) => {
  let start = 0;
  if (cursor) { const i = items.findIndex((x) => x.id === cursor); if (i >= 0) start = i + 1; }
  const slice = items.slice(start, start + limit);
  const hasMore = items.length > start + limit;
  return { slice, nextCursor: hasMore ? slice[slice.length - 1].id : null };
};

createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname.replace(/^\/api\/v1/, "");
  const device = req.headers["x-device-id"];
  console.log(`${new Date().toISOString().slice(11, 19)} ${req.method} ${url.pathname}${url.search} device=${device ?? "-"}`);

  const auth = req.headers.authorization ?? "";
  const key = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!key.startsWith("fr_") || key === "fr_bad") return fail(res, "invalid_key", "Invalid or inactive API key.");

  const limit = Math.min(Math.max(Number(url.searchParams.get("limit")) || 20, 1), 100);
  const cursor = url.searchParams.get("cursor");
  let m;

  if (req.method === "GET" && path === "/config") {
    return send(res, 200, { showBranding: !PRO, hideVoteCounts: HIDE_VOTES, appName: "Mock App" });
  }

  if (path === "/features" && req.method === "GET") {
    if (!device) return fail(res, "missing_device_id", "X-Device-Id header is required.");
    const sort = url.searchParams.get("sort") === "top" ? "top" : "new";
    const all = [...features.values()].sort((a, b) =>
      sort === "top" ? b.upvoteCount - a.upvoteCount || b.createdAt.localeCompare(a.createdAt) : b.createdAt.localeCompare(a.createdAt));
    const { slice, nextCursor } = page(all, limit, cursor);
    return send(res, 200, { features: slice.map((f) => publicFeature(f, device)), nextCursor });
  }

  if (path === "/features" && req.method === "POST") {
    if (!device) return fail(res, "missing_device_id", "X-Device-Id header is required.");
    const body = await readBody(req);
    const title = typeof body?.title === "string" ? body.title.trim() : "";
    const description = typeof body?.description === "string" ? body.description.trim() : "";
    if (title.length < 3) return fail(res, "validation_failed", "String must contain at least 3 character(s)");
    if (title.length > 100) return fail(res, "validation_failed", "String must contain at most 100 character(s)");
    if (description.length > 1000) return fail(res, "validation_failed", "String must contain at most 1000 character(s)");
    if (body.email !== undefined && !isEmail(body.email)) return fail(res, "validation_failed", "Invalid email");
    if (title.toLowerCase() === "fail") return fail(res, "internal", "Failed to create feature.");
    if (title.toLowerCase() === "limit") return fail(res, "limit_reached", "Plan limit reached.");
    if (!underQuota("submit", device, 15)) return fail(res, "rate_limited", "Daily submission limit reached.");
    const id = `feat_${randomUUID().slice(0, 8)}`;
    const ts = new Date().toISOString();
    const f = { id, title, description, status: "open", upvoteCount: 0, commentCount: 0, followerCount: 0,
      authorDeviceId: device, authorIsSubscriber: body.isSubscriber === true, translation: null,
      createdAt: ts, updatedAt: ts, votes: new Set(), followers: new Map(), comments: [] };
    features.set(id, f);
    if (body.email) { f.followers.set(device, body.email.trim()); f.followerCount = 1; }
    const { votes, followers, comments, ...created } = f;
    return send(res, 201, { ...created, followerCount: 0 }); // like the real server: response predates the follow
  }

  if ((m = path.match(/^\/features\/([^/]+)$/)) && req.method === "GET") {
    const f = features.get(m[1]);
    if (!f) return fail(res, "not_found", "Feature not found.");
    return send(res, 200, publicFeature(f, device));
  }

  if ((m = path.match(/^\/features\/([^/]+)\/vote$/))) {
    if (!device) return fail(res, "missing_device_id", "X-Device-Id header is required.");
    const f = features.get(m[1]);
    if (!f) return fail(res, "not_found", "Feature not found.");
    if (req.method === "POST" && !f.votes.has(device)) { f.votes.add(device); f.upvoteCount++; }
    if (req.method === "DELETE" && f.votes.has(device)) { f.votes.delete(device); f.upvoteCount--; }
    return send(res, 200, { upvoteCount: HIDE_VOTES ? 0 : f.upvoteCount, hasVoted: f.votes.has(device) });
  }

  if ((m = path.match(/^\/features\/([^/]+)\/follow$/))) {
    if (!device) return fail(res, "missing_device_id", "X-Device-Id header is required.");
    const f = features.get(m[1]);
    if (req.method === "POST") {
      const body = await readBody(req);
      if (!isEmail(body?.email)) return fail(res, "validation_failed", "Invalid email");
      if (!f) return fail(res, "not_found", "Feature not found.");
      if (!f.followers.has(device)) f.followerCount++;
      f.followers.set(device, body.email.trim());
      return send(res, 200, { following: true });
    }
    if (!f) return fail(res, "not_found", "Feature not found.");
    if (f.followers.delete(device)) f.followerCount--;
    return send(res, 200, { following: false });
  }

  if ((m = path.match(/^\/features\/([^/]+)\/comments$/)) && req.method === "GET") {
    const f = features.get(m[1]);
    if (!f) return send(res, 200, { comments: [], nextCursor: null });
    // Like the real server: page first, then drop deleted ones (pages can be short).
    const { slice, nextCursor } = page(f.comments, limit, cursor);
    return send(res, 200, { comments: slice.filter((c) => !c.isDeleted).map(({ isDeleted, ...c }) => c), nextCursor });
  }

  if (path === "/comments" && req.method === "POST") {
    if (!device) return fail(res, "missing_device_id", "X-Device-Id header is required.");
    const body = await readBody(req);
    const text = typeof body?.text === "string" ? body.text.trim() : "";
    if (!body?.featureId) return fail(res, "validation_failed", "Required");
    if (text.length < 1 || text.length > 1000) return fail(res, "validation_failed", "Comment must be 1-1000 characters");
    if (body.authorName && body.authorName.trim().length > 60) return fail(res, "validation_failed", "Name too long");
    const f = features.get(body.featureId);
    if (!f) return fail(res, "not_found", "Feature not found.");
    if (!underQuota("comment", device, 10)) return fail(res, "rate_limited", "Daily comment limit reached.");
    addComment(f.id, text, body.authorName?.trim() || "App User", false, device);
    const { isDeleted, ...c } = f.comments[f.comments.length - 1];
    return send(res, 201, c);
  }

  return send(res, 404, { error: { code: "not_found", message: "No such route." } });
}).listen(PORT, () => {
  console.log(`Fewchurs mock API on http://localhost:${PORT}/api/v1  (emulator: http://10.0.2.2:${PORT}/api/v1)`);
  console.log(`latency=${LATENCY}ms hideVoteCounts=${HIDE_VOTES} pro=${PRO}`);
});
