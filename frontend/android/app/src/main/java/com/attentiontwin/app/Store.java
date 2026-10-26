package com.attentiontwin.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** App-private SQLite store. Message content is nullable and omitted by default. */
public final class Store extends SQLiteOpenHelper {
    private static final String DB_NAME = "attentiontwin.db";
    private static final int DB_VERSION = 2;
    private static final String EVENTS = "events";
    private static final String STATS = "app_stats";

    public static final class Event {
        public String nkey = "";
        public String pkg = "";
        public String label = "";
        public String category = "other";
        public String role = "SMART";
        public String channel = "";
        public long ts;
        public boolean focus;
        public String action = "ALLOW";
        public String state = "SHOWN";
        public String reason = "";
        public double pOpen;
        public double cAllow;
        public double cDefer;
        public double cSuppress;
        public double importance;
        public long releaseAt;
        public String preview;
        public String source = "real";
        public boolean opened;
        public String feedback = "";
        public String exception = "";
    }

    public Store(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, nkey TEXT NOT NULL, pkg TEXT NOT NULL, label TEXT NOT NULL, category TEXT NOT NULL, role TEXT NOT NULL DEFAULT 'SMART', channel TEXT, ts INTEGER NOT NULL, focus INTEGER NOT NULL, action TEXT NOT NULL, state TEXT NOT NULL, reason TEXT, pOpen REAL, cAllow REAL, cDefer REAL, cSuppress REAL, importance REAL, releaseAt INTEGER, preview TEXT, source TEXT NOT NULL, opened INTEGER NOT NULL DEFAULT 0, feedback TEXT, exception TEXT)");
        db.execSQL("CREATE INDEX events_ts_idx ON events(ts DESC)");
        db.execSQL("CREATE INDEX events_nkey_idx ON events(nkey, ts DESC)");
        db.execSQL("CREATE TABLE app_stats (pkg TEXT NOT NULL, source TEXT NOT NULL, shown INTEGER NOT NULL DEFAULT 0, opened INTEGER NOT NULL DEFAULT 0, impUp INTEGER NOT NULL DEFAULT 0, impDown INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(pkg, source))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE events ADD COLUMN role TEXT NOT NULL DEFAULT 'SMART'");
            db.execSQL("UPDATE events SET role='BLOCK_IN_FOCUS' WHERE pkg IN ('com.whatsapp','com.instagram.android','com.google.android.gm')");
        }
    }

    public synchronized long insert(Event e) {
        SQLiteDatabase db = getWritableDatabase();
        long id = db.insertOrThrow(EVENTS, null, values(e));
        return id;
    }

    public synchronized JSONObject findByKey(String keyHash) {
        try (Cursor cursor = getReadableDatabase().query(EVENTS, null, "nkey=?", new String[]{keyHash}, null, null, "ts DESC,id DESC", "1")) {
            return cursor.moveToFirst() ? row(cursor) : null;
        }
    }

    public synchronized JSONObject findRecentByKey(String keyHash, long since) {
        try (Cursor cursor = getReadableDatabase().query(EVENTS, null, "nkey=? AND ts>=?", new String[]{keyHash, String.valueOf(since)}, null, null, "ts DESC,id DESC", "1")) {
            return cursor.moveToFirst() ? row(cursor) : null;
        }
    }

    public synchronized JSONObject getEvent(long id) {
        try (Cursor cursor = getReadableDatabase().query(EVENTS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null, "1")) {
            return cursor.moveToFirst() ? row(cursor) : null;
        }
    }

    public synchronized int updateState(long id, String state, long releaseAt, String action, String reason) {
        ContentValues values = new ContentValues();
        values.put("state", state);
        values.put("releaseAt", releaseAt);
        if (action != null) values.put("action", action);
        if (reason != null) values.put("reason", reason);
        return getWritableDatabase().update(EVENTS, values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized JSONArray listEvents(String state, int limit, boolean includeDemo) {
        JSONArray result = new JSONArray();
        StringBuilder selection = new StringBuilder();
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        if (state != null && !state.isEmpty()) { selection.append("state=?"); args.add(state); }
        if (!includeDemo) {
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("source!='demo'");
        }
        try (Cursor cursor = getReadableDatabase().query(EVENTS, null, selection.length() == 0 ? null : selection.toString(), args.toArray(new String[0]), null, null, "ts DESC,id DESC", String.valueOf(Math.max(1, Math.min(limit, 500)))) ) {
            while (cursor.moveToNext()) {
                JSONObject item = row(cursor);
                result.put(item);
            }
        }
        return result;
    }

    public synchronized JSONArray heldEvents(boolean includeDemo) {
        return listEvents("HELD", 300, includeDemo);
    }

    public synchronized int countDueHeld(long now) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events WHERE state='HELD' AND source!='demo' AND releaseAt>0 AND releaseAt<=?", new String[]{String.valueOf(now)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized int releaseExpired(long now) {
        ContentValues values = new ContentValues();
        values.put("state", "RELEASED"); values.put("action", "ALLOW"); values.put("releaseAt", 0L);
        values.put("reason", "Break time passed; the notification is allowed when Android re-posts it.");
        return getWritableDatabase().update(EVENTS, values, "state='HELD' AND releaseAt>0 AND releaseAt<=?", new String[]{String.valueOf(now)});
    }

    public synchronized int countSince(long since, String source) {
        String selection = "ts>=?" + (source == null ? " AND source!='demo'" : " AND source=?");
        String[] args = source == null ? new String[]{String.valueOf(since)} : new String[]{String.valueOf(since), source};
        try (Cursor c = getReadableDatabase().query(EVENTS, new String[]{"COUNT(*)"}, selection, args, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized void incrementShown(String pkg, String source) {
        upsertStats(pkg, source, 1, 0, 0, 0);
    }

    public synchronized void incrementOpened(String pkg, String source) {
        upsertStats(pkg, source, 0, 1, 0, 0);
    }

    public synchronized void voteImportance(String pkg, String source, boolean important) {
        upsertStats(pkg, source, 0, 0, important ? 1 : 0, important ? 0 : 1);
    }

    public synchronized DecisionEngine.Stats stats(String pkg, String source) {
        try (Cursor cursor = getReadableDatabase().query(STATS, new String[]{"shown", "opened", "impUp", "impDown"}, "pkg=? AND source=?", new String[]{pkg, source}, null, null, null, "1")) {
            if (cursor.moveToFirst()) return new DecisionEngine.Stats(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3));
        }
        return new DecisionEngine.Stats(0, 0, 0, 0);
    }

    public synchronized JSONObject feedback(long id, String value) {
        JSONObject event = getEvent(id);
        if (event == null) return null;
        String source = event.optString("source", "real");
        String pkg = event.optString("pkg", "");
        boolean important = "important".equals(value) || "wrong".equals(value);
        if ("important".equals(value) || "not_important".equals(value) || "wrong".equals(value)) {
            voteImportance(pkg, source, important);
        }
        ContentValues cv = new ContentValues();
        cv.put("feedback", value);
        getWritableDatabase().update(EVENTS, cv, "id=?", new String[]{String.valueOf(id)});
        return getEvent(id);
    }

    public synchronized boolean markOpenedByKey(String keyHash) {
        JSONObject event = findByKey(keyHash);
        if (event == null || event.optInt("opened") == 1) return false;
        ContentValues cv = new ContentValues();
        cv.put("opened", 1);
        getWritableDatabase().update(EVENTS, cv, "id=?", new String[]{String.valueOf(event.optLong("id"))});
        incrementOpened(event.optString("pkg"), event.optString("source", "real"));
        return true;
    }

    public synchronized void markDemoOpened(long id) {
        ContentValues cv = new ContentValues();
        cv.put("opened", 1);
        getWritableDatabase().update(EVENTS, cv, "id=? AND source='demo'", new String[]{String.valueOf(id)});
    }

    public synchronized JSONObject summaryToday() {
        long start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        JSONObject result = new JSONObject();
        try (Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT action, state, COUNT(*) FROM events WHERE ts>=? AND source='real' GROUP BY action,state",
            new String[]{String.valueOf(start)})) {
            int allowed = 0, held = 0, suppressed = 0, total = 0;
            while (cursor.moveToNext()) {
                String action = cursor.getString(0);
                String state = cursor.getString(1);
                int count = cursor.getInt(2);
                total += count;
                if ("ALLOW".equals(action)) allowed += count;
                if ("HELD".equals(state) || "RELEASED".equals(state)) held += count;
                if ("SUPPRESS".equals(action)) suppressed += count;
            }
            result.put("allowed", allowed);
            result.put("held", held);
            result.put("suppressed", suppressed);
            result.put("total", total);
        } catch (JSONException ignored) { }
        return result;
    }

    public synchronized void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(EVENTS, null, null);
            db.delete(STATS, null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void deleteDemo() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(EVENTS, "source='demo'", null);
        db.delete(STATS, "source='demo'", null);
    }

    private void upsertStats(String pkg, String source, int shown, int opened, int impUp, int impDown) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("pkg", pkg);
        cv.put("source", source);
        cv.put("shown", 0);
        cv.put("opened", 0);
        cv.put("impUp", 0);
        cv.put("impDown", 0);
        db.insertWithOnConflict(STATS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("UPDATE app_stats SET shown=shown+?, opened=opened+?, impUp=impUp+?, impDown=impDown+? WHERE pkg=? AND source=?",
            new Object[]{shown, opened, impUp, impDown, pkg, source});
    }

    private static ContentValues values(Event e) {
        ContentValues v = new ContentValues();
        v.put("nkey", e.nkey); v.put("pkg", e.pkg); v.put("label", e.label); v.put("category", e.category); v.put("role", e.role);
        v.put("channel", e.channel); v.put("ts", e.ts); v.put("focus", e.focus ? 1 : 0); v.put("action", e.action);
        v.put("state", e.state); v.put("reason", e.reason); v.put("pOpen", e.pOpen); v.put("cAllow", e.cAllow);
        v.put("cDefer", e.cDefer); v.put("cSuppress", e.cSuppress); v.put("importance", e.importance);
        v.put("releaseAt", e.releaseAt); if (e.preview != null) v.put("preview", e.preview);
        v.put("source", e.source); v.put("opened", e.opened ? 1 : 0); v.put("feedback", e.feedback); v.put("exception", e.exception);
        return v;
    }

    private static JSONObject row(Cursor c) {
        JSONObject o = new JSONObject();
        String[] names = c.getColumnNames();
        try {
            for (int i = 0; i < names.length; i++) {
                int type = c.getType(i);
                if (type == Cursor.FIELD_TYPE_NULL) o.put(names[i], JSONObject.NULL);
                else if (type == Cursor.FIELD_TYPE_INTEGER) o.put(names[i], c.getLong(i));
                else if (type == Cursor.FIELD_TYPE_FLOAT) o.put(names[i], c.getDouble(i));
                else o.put(names[i], c.getString(i));
            }
        } catch (JSONException ignored) { }
        return o;
    }
}
