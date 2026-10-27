# Android home: reference analysis and implementation plan

This plan supersedes the hero-first Android layout proposed in
`HOME_LIVING_DAY_RND.md`. The target is the immersive, artwork-led home in the
shared wellness video, adapted to Deenly's real prayer, Quran, hadith, Qibla,
and recommendation content. Keep the existing tile artwork and product actions.

## What each video contributes

| Reference | Observed behavior | Use in Deenly |
| --- | --- | --- |
| `7eaa1db32327eb7533fd4695d801caa2.mp4` | A full-bleed featured image yields to a dark feed of large and small image cards. At about 5.5–6.5 seconds, a selected small card's image occupies the destination detail before its title, description, and action appear. At about 7.5–8 seconds, Play opens a distinct full-screen player. Back and completion are separate destinations. | Primary home hierarchy and two-stage card → detail → player journey. Preserve the tapped artwork, title, and reading position across transitions. |
| `original-d640999a8abd08ed64004ee14009b50c.mp4` | A purple upper card and a related lower card split and rejoin around a small central handle. A narrow, curved bridge briefly joins the shapes; lower text is revealed only after space opens. Reversing the interaction restores the original silhouette. | Use a liquid bridge for an **inline related-content disclosure**, such as a reading preview revealing its translation or a Salah card revealing its hadith context. Do not use this shape for page navigation. |
| `large-thumbnail20250217-3692886-76m9uj.mp4` | Shows the same split-card idea as an edited motion study and on a phone. The visible result depends on a drawn intermediate silhouette, not just scaling two rounded rectangles. | Prototype the bridge as a path whose control points and corner radii share one progress value; test opening and closing frames. |
| `original-cab6183030449e3799d7aac1c425fb4e.mp4` | A light assistant surface changes from waveform/input to generated text. The central content changes while the surrounding frame and controls remain stable; text enters after the surface settles. | Apply its restrained content-reveal sequence to AI Suggested and voice results, using real loading/search state. Do not copy the light color scheme into the dark home. |
| `a152f4b40f0afb58fe9ff6c84e8b966b.mp4` | A centered brand tile softly pulses, defocuses, and sends out fine radial lines. This is an ambient identity effect, not a card expansion. | Reserve a subtle version for active voice listening or AI preparation. Keep it quiet when idle and disable it for reduced motion. |

## Target Android home

The first viewport should read as one immersive, edge-to-edge artwork scene.
The later blue-toned phone reference clarifies its treatment: preserve Deenly's
own prayer artwork, give day and night a calm blue cast, float legible chrome
over the image, and let a dark gradient take over as the feed continues. A
compact live prayer cue (current/next prayer and countdown) stays easy to reach
without competing with the featured artwork. The featured card uses the most
relevant real insight. Smaller image cards below show distinct available content with
clear titles and source/status labels. The editable prayer schedule remains a
secondary section further down. Existing Home, For You, Saved, Courses, and
Interests navigation keeps its real destinations.

Card order must come from one home-card model keyed by stable content ID. The
featured card, small cards, reader, and player must all refer to that model.
Downloaded assets, Friday relevance, prayer window, and reading history drive
ranking; loading and missing-source states keep the card's final geometry and
show a truthful status. A card must never claim a verified source until that
source has actually loaded.

## Interaction contract

1. **Card to reader:** Tapping any featured or small card transfers that same
   artwork and title from its measured source bounds to a reader surface. The
   card's corners flatten and its image crop evolves continuously. Reader text
   and actions enter after the destination has enough space. The bottom bar
   leaves late in the transition. Back reverses to the same card and scroll
   position. A quick second tap cannot launch another destination mid-flight.
2. **Reader to player:** Play moves the reader artwork into a distinct playback
   surface with real title, progress, pause/seek controls, and the existing media
   session. Closing the player returns to the reader; Back from the reader
   returns to the originating card. The active audio source remains singular.
3. **Inline disclosure:** Use the purple reference's curved bridge only when a
   card reveals content belonging to itself. Both halves keep one connected
   visual identity as the lower content becomes readable. A tap or drag closes
   it without jumping or clipping text.
4. **Ambient state:** Voice and AI preparation may use a low-key pulse/radial
   signal. It stops when work finishes or the screen leaves, and never masks
   search progress or source information.

## Implementation sequence

1. **Lock a reviewable baseline.** Capture full Android screenshots and short
   screen recordings at rest, mid-transition, reader, player, and reverse on a
   phone. The current component screenshots cover only the artwork rail and a
   standalone overlay; they cannot establish full-screen fidelity.
2. **Unify the surfaces.** Introduce a stable `HomeCard` model and one artwork
   scene shared by feed and reader. Remove the separate image-only Popup handoff
   once a real shared-bounds transition is in place. Compose already includes a
   `SharedTransitionLayout` elsewhere in the app; use it with stable card keys
   and one transition owner. Keep card bounds and scroll position in the host.
3. **Build the card-to-reader transition.** Animate source/destination bounds,
   artwork crop, title position, scrim, corner radius, and navigation visibility
   on one timeline. Keep content at its destination height during loading. Make
   a second tap, Back, and rotation cancel or reverse predictably.
4. **Connect playback.** Use the existing Quran, hadith, and dua playback
   controls and media session in a reader-to-player transition. Preserve the
   reader state and return point. Keep the UI truthful if playback is preparing
   or a source is missing.
5. **Add the liquid disclosure selectively.** Prototype one related pair with a
   Canvas path/clip driven by a single progress value; then apply it to reading
   translation and Salah hadith context if it improves readability. Avoid a
   shader or blur that merely hides two disconnected rectangles.
6. **Verify before merging.** Record full-screen motion at 60 fps on Android,
   inspect first/mid/final/reverse frames, and profile frame timing. Test small
   and large phones, 2× text, RTL, offline/missing assets, animation scale zero,
   gestures while scrolled, rapid repeated taps, and Back during loading. Only
   merge to `main` when the source card, reader, and player are visually and
   functionally continuous.

The card data and interaction contract should be portable to iOS. Recreate the
approved motion with the iOS UI layer after the Android flow passes review;
Android's shared-bounds API itself is not the cross-platform contract.

## Acceptance checks

- Every tapped card opens its own content; no image-only overlay hands off to a
  different-looking card. The artwork and title have no one-frame disappearance.
- The reader never grows beyond system/navigation bounds, and its text never
  clips during expansion, collapse, or internal scrolling.
- The same action path works for Quran, Salah, AI Suggested, and Qibla. Audio
  controls appear only for playable content and reflect actual playback state.
- Closing returns to the exact originating card and scroll position. Landscape
  keeps a usable layout, and reduced motion preserves the same destination
  hierarchy without spatial effects.

The clip's literal wellness subjects, purple branding, and white AI shell are
references for interaction and rhythm; Deenly's own artwork, content, theme
accent, and navigation remain the product identity.
