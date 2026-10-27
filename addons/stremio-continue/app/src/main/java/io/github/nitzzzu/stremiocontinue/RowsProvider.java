package io.github.nitzzzu.stremiocontinue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands the Clean Your TV launcher (content_rows) Stremio's Continue Watching with its artwork, so the launcher
 * can draw the row and the backdrop without network access of its own.
 *
 * content://AUTHORITY/rows                      one row: _id, title
 * content://AUTHORITY/items                     _id, title, subtitle, progress (0-1000), poster, backdrop, card, logo, intent_uri
 * content://AUTHORITY/art/{kind}/{_id}          the image file, downloaded and cached here
 *
 * Only the launcher and this app may read it: it is the owner's watch history.
 */
public class RowsProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.nitzzzu.stremiocontinue.rows";
    private static final String LAUNCHER = "com.gathin.tvhome";
    private static final String[] ITEM_COLUMNS = {"_id", "title", "subtitle", "progress", "poster", "backdrop", "card",
            "logo", "intent_uri"};

    /** _id -> the item last listed, for openFile. */
    private final Map<Long, Rows.Item> items = new HashMap<>();

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        check();
        String what = uri.getLastPathSegment();
        if ("rows".equals(what)) {
            MatrixCursor c = new MatrixCursor(new String[] {"_id", "title"});
            c.addRow(new Object[] {1, getContext().getString(R.string.title)});
            return c;
        }
        if (!"items".equals(what)) return null;
        List<Rows.Item> list;
        try {
            list = Rows.read(getContext().getContentResolver());
        } catch (Rows.NoAccess e) {
            return new MatrixCursor(ITEM_COLUMNS);
        }
        MatrixCursor c = new MatrixCursor(ITEM_COLUMNS);
        synchronized (items) {
            items.clear();
            for (Rows.Item it : list) {
                items.put(it.id, it);
                int progress = it.duration > 0 ? (int) Math.min(1000, it.position * 1000 / it.duration) : 0;
                c.addRow(new Object[] {it.id, it.title, subtitle(it), progress,
                        art("poster", it.id, it.poster), art("backdrop", it.id, it.backdrop("medium")),
                        art("card", it.id, it.backdrop("small")), art("logo", it.id, it.logo()), explicit(it.intentUri)});
            }
        }
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        check();
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 3 || !"art".equals(seg.get(0)) || !"r".equals(mode)) throw new FileNotFoundException();
        long id;
        try {
            id = Long.parseLong(seg.get(2));
        } catch (NumberFormatException e) {
            throw new FileNotFoundException();
        }
        Rows.Item it;
        synchronized (items) { it = items.get(id); }
        if (it == null) throw new FileNotFoundException();
        String src;
        switch (seg.get(1)) {
            case "poster": src = it.poster; break;
            case "backdrop": src = it.backdrop("medium"); break;
            case "card": src = it.backdrop("small"); break;
            case "logo": src = it.logo(); break;
            default: throw new FileNotFoundException();
        }
        File f = Fetch.file(getContext(), src);
        if (f == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Stremio's link as an intent: URI bound to Stremio, so other stremio:// handlers (Nuvio) never get a chooser. */
    private static String explicit(String uri) {
        try {
            android.content.Intent i = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME);
            i.setPackage(Rows.STREMIO);
            i.setSelector(null);
            return i.toUri(android.content.Intent.URI_INTENT_SCHEME);
        } catch (java.net.URISyntaxException e) {
            return uri;
        }
    }

    private static String art(String kind, long id, String src) {
        return src == null ? null : "content://" + AUTHORITY + "/art/" + kind + "/" + id;
    }

    private String subtitle(Rows.Item it) {
        String left = null;
        if (it.duration > 0 && it.position > 0 && it.duration > it.position) {
            left = getContext().getString(R.string.left, (int) Math.max(1, (it.duration - it.position) / 60000));
        }
        if (it.subtitle == null) return left;
        return left == null ? it.subtitle : it.subtitle + "  ·  " + left;
    }

    private void check() {
        String caller = getCallingPackage();
        boolean ok = caller == null ? android.os.Binder.getCallingUid() == android.os.Process.myUid()
                : caller.equals(LAUNCHER) || caller.equals(getContext().getPackageName());
        if (!ok) {
            throw new SecurityException("Continue Watching is only shared with the launcher");
        }
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
