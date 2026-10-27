# Continue Watching (Stremio)

A module card for Stremio's Continue Watching. The card opens one row of posters with the episode and
minutes left. OK resumes that title in Stremio (`stremio:///detail/...?autoPlay=true`), and Menu or the
last tile opens Stremio itself.

There is no login and no Stremio API. The add-on reads the "Continue Watching" preview channel that
Stremio (`com.stremio.one`) already publishes to the TV provider (`docs/watch-next.md`), so it shows
what Stremio's own row shows, in the same order.

## Install

```bash
TV_HOST=<tv ip> addons/stremio-continue/install.sh
```

Press Home and answer **Show card**. To put the card in the dock, add `io.github.nitzzzu.stremiocontinue`
to `dock_apps`.

## Row on the home screen

The add-on also serves the row to the launcher's `content_rows` switch (a local launcher change, not upstream yet)
through `content://io.github.nitzzzu.stremiocontinue.rows/items`. The posters, backdrops and title logos come from
`images.metahub.space`, the artwork service Stremio itself uses. The add-on downloads and caches them, and the
launcher reads them as `content://` files, so the launcher still has no network access. Only the launcher
(`com.gathin.tvhome`) may read the provider.

## Permissions

| Permission | Why |
|---|---|
| `READ_TV_LISTINGS` | Read Stremio's preview-channel rows. Granted by `install.sh` with `pm grant`. |
| `INTERNET` | Download the poster images the rows point at (`https://`). They are cached in the app's cache folder. Nothing is sent anywhere. |

No background service, no polling: the row is read each time the screen opens.

## Tested on

XGIMI Halo+ (XK03H), Android TV 11, RT.3.24.327, with Stremio for Android TV (`com.stremio.one`).
