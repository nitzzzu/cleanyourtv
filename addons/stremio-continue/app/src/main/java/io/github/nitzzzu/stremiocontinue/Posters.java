package io.github.nitzzzu.stremiocontinue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads poster art into views: memory cache, then Fetch's disk cache. */
final class Posters {
    private final Context ctx;
    private final int targetWidth;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    Posters(Context ctx, int targetWidth) {
        this.ctx = ctx.getApplicationContext();
        this.targetWidth = targetWidth;
    }

    void into(ImageView view, String uri) {
        view.setTag(uri);
        if (uri == null) return;
        Bitmap hit = mem.get(uri);
        if (hit != null) { view.setImageBitmap(hit); return; }
        pool.execute(() -> {
            Bitmap b = decode(Fetch.file(ctx, uri), targetWidth);
            if (b == null) return;
            mem.put(uri, b);
            main.post(() -> { if (uri.equals(view.getTag())) view.setImageBitmap(b); });
        });
    }

    void shutdown() { pool.shutdownNow(); }

    static Bitmap decode(File f, int targetWidth) {
        if (f == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetWidth) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
