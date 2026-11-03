# Floating navigation: iOS reference review

Reviewed 2026-11-03 against the supplied crops and Apple's current guidance.

## Sources

- [Apple HIG: Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)
- [Apple HIG: Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [WWDC25: Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/), especially Adaptivity (6:00) and Principles (10:31).
- [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)
- [Backdrop: effects and platform requirements](https://kyant.gitbook.io/backdrop/api/backdrop-effects)
- [Backdrop: glass bottom bar](https://kyant.gitbook.io/backdrop/tutorials/glass-bottom-bar)

## Findings

Apple's regular glass adapts to underlying content, including its light/dark
appearance and foreground colors. It is not a fixed dark overlay on a blur.
Lensing, scattering, highlights and touch response work together. Clear glass
is intended for media backgrounds and needs appropriate dimming; it is not a
universal replacement for regular navigation glass.

Apple recommends short labels and consistent, preferably filled tab symbols.
Selection should remain a thin overlay within the navigation material, rather
than another independently framed glass surface. Tab navigation is distinct
from actions; the separate voice action is our app-specific adaptation.

Our previous implementation always used light labels, applied a 65% dark tint
on light-theme pages, disabled highlights and omitted lensing. The selection
extended outside its tab cell, had its own rim, and stretched across intervening
tabs because one edge started 85ms later. These contributed to its heavy look.

## Implementation decisions

- Sample a 12×4-pixel layer of the actual backdrop at the navigation bounds,
  at most four times per second while resumed. Avoid full-screen bitmap reads
  and exclude navigation from its own backdrop. Hysteresis stabilizes contrast.
- Coordinate surface tint, tab labels, selected blue and voice glyph contrast.
- Use one interruptible 350ms eased transition for surface, outline, foreground,
  selected blue and selection fill. Share its palette between navigation and
  voice controls; tab-selection animation remains independent. Reversing scroll
  retargets the current transition instead of snapping to an endpoint.
- Use existing Backdrop vibrancy and blur with a thin, uniform outline. Remove
  lensing and directional highlights to avoid the unwanted bright fading rim.
  These are an Android approximation, not Apple's private rendering pipeline.
  Blur requires Android 12; the library supplies fallbacks on older versions.
- Let Backdrop draw the exterior-only shadow, masking the shape's interior.
  Both transparent Material surfaces use zero elevation: an elevated Surface
  inside the backdrop modifier draws its shadow after the tinted glass, rather
  than behind it. Keep adaptive light/dark tint and foreground colors enabled.
- Keep selection within one equal-width tab cell, with a 4dp inset and a thin
  translucent fill. Use immediate press feedback and a single moving capsule.
- Retain the app's five destinations and Ubuntu typography. Both navigation
  and the separate voice button are 56dp tall, with an 8dp gap; these are our
  chosen Android layout metrics, not dimensions mandated by Apple.

## Verification

Check dark artwork, light content, saturated content and scrolling between
them. Verify selection and voice-control alignment, tab switching, and that
the navigation does not capture its own pixels. Static screenshots cannot
establish motion fidelity to native iOS; assess interaction separately.

The local light-material test includes both the pill and circular control with
their transparent Material surfaces. It checks bright interiors and uniform
upper/interior luminance over a uniform light backdrop. A controlled-clock test
also checks intermediate palette colors, interruption and both final appearances.
All four focused tests and the demo debug build pass. These tests do not reproduce
the device's full hardware shadow pipeline or establish perceived motion quality.
The animated transition awaits device review; installation requires explicit
authorization.
