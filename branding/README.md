# Branding & store assets

Visual identity for **Arnout.pro DICOM Camera**.

## Wordmark

| Element | Treatment |
|---|---|
| **Arnout.pro** | Sansation Bold (700) |
| **DICOM Camera** | Sansation Regular |
| In-app chrome | Mark left · product name right-aligned |
| Primary fill | Forest teal `#0F766E` |
| Accent | Teal `#2DD4BF` · Gold `#D4A017` · Linen `#F4F7F7` |

Fonts live in `app/src/main/res/font/` (`sansation_bold.ttf`, `sansation_regular.ttf`). License: `docs/licenses/SIL-OFL-Sansation.txt`.

## Google Play (prepared)

| Asset | Required size | File |
|---|---|---|
| High-res icon | **512 × 512** PNG | [`play-store/icon-512.png`](play-store/icon-512.png) |
| Feature graphic | **1024 × 500** PNG | [`play-store/feature-graphic-1024x500.png`](play-store/feature-graphic-1024x500.png) |

These match current Play Console expectations for icon and feature graphic. Screenshots, promo video, and listing copy are still to be produced at release time.

## GitHub / social

| Asset | Size | File |
|---|---|---|
| Social / OG banner | 1280 × 640 | [`github-banner-1280x640.png`](github-banner-1280x640.png) |
| README hero strip | 1280 × 320 | [`readme-hero-1280x320.png`](readme-hero-1280x320.png) |
| App icon (same art) | 512 × 512 | [`app-icon-512.png`](app-icon-512.png) |
| README screenshots | phone UI | [`screenshots/`](screenshots/) — Worklist, Archive, Settings |

Set the GitHub repo **Social preview** image to `github-banner-1280x640.png` (Settings → General → Social preview).

## Regenerating

From the repo root (requires Pillow + the Sansation TTFs):

```bash
# see scripts in history / regenerate with the same sizes if brand colours change
python3 -c "print('Use the generator committed with the landing PR, or re-run the branding script.')"
```

Launcher mipmaps inside the Android app are generated separately under `app/src/main/res/mipmap-*`.
