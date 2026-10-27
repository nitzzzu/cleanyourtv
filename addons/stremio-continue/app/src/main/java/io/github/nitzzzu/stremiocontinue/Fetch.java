package io.github.nitzzzu.stremiocontinue;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** Downloads an image once into cacheDir/art and hands back the file. Blocking: call off the main thread. */
final class Fetch {
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private Fetch() {}

    static File file(Context ctx, String uri) {
        if (uri == null) return null;
        try {
            File f = new File(new File(ctx.getCacheDir(), "art"), sha1(uri));
            if (f.isFile() && f.length() > 0) return f;
            byte[] data = download(ctx, uri);
            if (data == null) return null;
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(data); }
            return tmp.renameTo(f) ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] download(Context ctx, String uri) throws Exception {
        Uri u = Uri.parse(uri);
        String scheme = u.getScheme();
        if ("content".equals(scheme) || "android.resource".equals(scheme)) {
            try (InputStream in = ctx.getContentResolver().openInputStream(u)) { return read(in); }
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) return null;
        HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(true);
        try {
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) { return read(in); }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        if (in == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }

    private static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }
}
