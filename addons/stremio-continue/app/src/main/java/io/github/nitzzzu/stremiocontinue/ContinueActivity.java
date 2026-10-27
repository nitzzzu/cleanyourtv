package io.github.nitzzzu.stremiocontinue;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One row of Stremio's Continue Watching: OK resumes the episode in Stremio, Menu opens Stremio. */
public class ContinueActivity extends Activity {
    private static final int ACCENT = 0xFF7B5BF5;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Posters posters;
    private LinearLayout row;
    private TextView message;
    private HorizontalScrollView scroller;
    private long lastFocusedId = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        posters = new Posters(this, dp(160));
        setContentView(buildLayout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        posters.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openStremio();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void refresh() {
        io.execute(() -> {
            List<Rows.Item> items = null;
            int error = 0;
            try {
                items = Rows.read(getContentResolver());
            } catch (Rows.NoAccess e) {
                error = R.string.no_permission;
            } catch (RuntimeException e) {
                error = R.string.empty;
            }
            final List<Rows.Item> result = items;
            final int err = error;
            main.post(() -> show(result, err));
        });
    }

    private void show(List<Rows.Item> items, int error) {
        if (isFinishing()) return;
        row.removeAllViews();
        if (getPackageManager().getLaunchIntentForPackage(Rows.STREMIO) == null
                && getPackageManager().getLeanbackLaunchIntentForPackage(Rows.STREMIO) == null) {
            error = R.string.no_stremio;
        }
        if (error != 0 || items == null || items.isEmpty()) {
            message.setText(error != 0 ? error : R.string.empty);
            message.setVisibility(View.VISIBLE);
            row.addView(openTile());
            row.getChildAt(0).requestFocus();
            return;
        }
        message.setVisibility(View.GONE);
        View focus = null;
        for (Rows.Item it : items) {
            View card = card(it);
            row.addView(card);
            if (it.id == lastFocusedId || focus == null) focus = card;
        }
        row.addView(openTile());
        focus.requestFocus();
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B1433, 0xFF0A0A10, 0xFF000000});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(40), dp(40), 0, dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(getString(R.string.title), 34, Color.WHITE);
        title.setPadding(dp(16), 0, 0, 0);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        col.addView(title);

        TextView hint = text("Stremio  ·  OK to resume  ·  Menu opens Stremio", 14, 0x99FFFFFF);
        hint.setPadding(dp(16), dp(4), 0, dp(4));
        col.addView(hint);

        message = text("", 18, 0xCCFFFFFF);
        message.setPadding(dp(16), dp(12), dp(56), 0);
        message.setVisibility(View.GONE);
        col.addView(message);

        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(16), dp(20), dp(56), dp(20));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroller.addView(row);
        col.setClipChildren(false);
        col.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private View card(Rows.Item it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), -2);
        lp.rightMargin = dp(22);
        card.setLayoutParams(lp);

        FrameLayout art = new FrameLayout(this);
        art.setBackground(round(0xFF1E1A2E, 0, 0));
        art.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(img, new FrameLayout.LayoutParams(-1, -1));
        TextView fallback = text(it.title, 16, 0xCCFFFFFF);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(dp(8), 0, dp(8), 0);
        art.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        posters.into(img, it.poster);
        if (it.poster != null) fallback.setVisibility(View.INVISIBLE);

        if (it.duration > 0 && it.position > 0) {
            View track = new View(this);
            track.setBackgroundColor(0x66000000);
            art.addView(track, bottomBar(-1));
            View fill = new View(this);
            fill.setBackgroundColor(ACCENT);
            float frac = Math.min(1f, (float) it.position / it.duration);
            art.addView(fill, bottomBar(Math.max(dp(4), (int) (dp(160) * frac))));
        }
        card.addView(art, new LinearLayout.LayoutParams(dp(160), dp(240)));

        TextView name = text(it.title, 15, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, dp(10), 0, 0);
        card.addView(name);
        String sub = it.subtitle;
        if (it.duration > 0 && it.position > 0 && it.duration > it.position) {
            String left = getString(R.string.left, (int) Math.max(1, (it.duration - it.position) / 60000));
            sub = sub == null ? left : sub + "  ·  " + left;
        }
        if (sub != null) {
            TextView s = text(sub, 13, 0x99FFFFFF);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(s);
        }

        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
            art.setForeground(has ? round(0, Color.WHITE, dp(3)) : null);
            if (has) lastFocusedId = it.id;
        });
        card.setOnClickListener(v -> resume(it));
        return card;
    }

    private FrameLayout.LayoutParams bottomBar(int width) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width, dp(5));
        p.gravity = Gravity.BOTTOM | Gravity.START;
        return p;
    }

    private View openTile() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(round(0xFF241C40, 0, 0));
        TextView play = text("▶", 40, ACCENT);
        play.setGravity(Gravity.CENTER);
        card.addView(play);
        TextView label = text(getString(R.string.open_stremio), 16, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(10), 0, 0);
        card.addView(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), dp(240));
        card.setLayoutParams(lp);
        card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
            ((LinearLayout) v).setForeground(has ? round(0, Color.WHITE, dp(3)) : null);
        });
        card.setOnClickListener(v -> openStremio());
        return card;
    }

    private void resume(Rows.Item it) {
        try {
            Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
            // Only into Stremio: a row is data from another app, so it never picks the target.
            i.setPackage(Rows.STREMIO);
            i.setComponent(null);
            i.setSelector(null);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (URISyntaxException | RuntimeException e) {
            openStremio();
        }
    }

    private void openStremio() {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(Rows.STREMIO);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(Rows.STREMIO);
        if (i != null) startActivity(i);
    }

    private GradientDrawable round(int fill, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        d.setColor(fill);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
