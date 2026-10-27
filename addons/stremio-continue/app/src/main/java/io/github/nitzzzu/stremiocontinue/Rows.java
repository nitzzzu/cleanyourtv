package io.github.nitzzzu.stremiocontinue;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads Stremio's "Continue Watching" preview channel from the TV provider (docs/watch-next.md). */
final class Rows {
    static final String STREMIO = "com.stremio.one";
    private static final String TAG = "StremioContinue";

    static final class Item {
        long id;
        String title;
        String subtitle;
        String poster;
        String intentUri;
        long position;
        long duration;
        int weight;

        /** The IMDb id in Stremio's link (stremio:///detail/series/tt0000001/...), for the artwork service. */
        String imdbId() {
            if (intentUri == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/detail/[a-z]+/(tt\\d{1,10})").matcher(intentUri);
            return m.find() ? m.group(1) : null;
        }

        /** Wide background art (https://images.metahub.space, the service Stremio itself uses), or null. */
        String backdrop(String size) {
            String id = imdbId();
            return id == null ? null : "https://images.metahub.space/background/" + size + "/" + id + "/img";
        }

        String logo() {
            String id = imdbId();
            return id == null ? null : "https://images.metahub.space/logo/medium/" + id + "/img";
        }
    }

    /** Thrown when the provider refuses the read: READ_TV_LISTINGS was not granted. */
    static final class NoAccess extends Exception {
        NoAccess(Throwable cause) { super(cause); }
    }

    private Rows() {}

    static List<Item> read(ContentResolver cr) throws NoAccess {
        Set<Long> channels = continueChannels(cr);
        List<Item> out = new ArrayList<>();
        // No selection: without ACCESS_ALL_EPG_DATA the provider throws on one, so filter in code.
        try (Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            if (c == null) return out;
            int pkg = c.getColumnIndex("package_name");
            int chan = c.getColumnIndex(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID);
            while (c.moveToNext()) {
                if (pkg >= 0 && !STREMIO.equals(c.getString(pkg))) continue;
                if (!channels.isEmpty() && chan >= 0 && !channels.contains(c.getLong(chan))) continue;
                Item it = new Item();
                it.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                it.title = str(c, TvContract.PreviewPrograms.COLUMN_TITLE);
                it.subtitle = subtitle(c);
                it.poster = str(c, TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI);
                if (it.poster == null) it.poster = str(c, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI);
                it.intentUri = str(c, TvContract.PreviewPrograms.COLUMN_INTENT_URI);
                it.position = num(c, TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
                it.duration = num(c, TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS);
                it.weight = (int) num(c, TvContract.PreviewPrograms.COLUMN_WEIGHT);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "row " + it.id + " w=" + it.weight + " " + it.title + " | " + it.subtitle
                            + " | " + it.intentUri);
                }
                if (it.title != null && it.intentUri != null) out.add(it);
            }
        } catch (SecurityException e) {
            throw new NoAccess(e);
        }
        // Stremio gives its rows no date; its own weight, then the order it wrote them in.
        out.sort((a, b) -> a.weight != b.weight ? Integer.compare(b.weight, a.weight) : Long.compare(a.id, b.id));
        return out;
    }

    /** Stremio's channels named like "Continue Watching"; empty means "take every Stremio row". */
    private static Set<Long> continueChannels(ContentResolver cr) throws NoAccess {
        Set<Long> ids = new HashSet<>();
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            if (c == null) return ids;
            int pkg = c.getColumnIndex("package_name");
            int name = c.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
            while (c.moveToNext()) {
                if (pkg < 0 || name < 0 || !STREMIO.equals(c.getString(pkg))) continue;
                String n = c.getString(name);
                if (n != null && n.toLowerCase().contains("continue")) {
                    ids.add(c.getLong(c.getColumnIndexOrThrow("_id")));
                }
            }
        } catch (SecurityException e) {
            throw new NoAccess(e);
        }
        return ids;
    }

    private static String subtitle(Cursor c) {
        String s = str(c, TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
        String e = str(c, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
        String t = str(c, TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE);
        StringBuilder b = new StringBuilder();
        if (s != null) b.append('S').append(s);
        if (e != null) b.append('E').append(e);
        if (t != null) b.append(b.length() > 0 ? " · " : "").append(t);
        if (b.length() == 0) {
            String d = str(c, TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION);
            if (d != null) b.append(d);
        }
        return b.length() > 0 ? b.toString() : null;
    }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return null;
        String v = c.getString(i);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static long num(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }
}
