#!/usr/bin/env bash
# Package the transparent master into the platform-required sizes and masks.
set -euo pipefail
cd "$(dirname "$0")/.."
master=assets/branding/deenly-crescent.png
res=app/src/main/res
catalog=iosApp/Sources/Assets.xcassets/AppIcon.appiconset
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
magick "$master" -trim +repage "$scratch/crescent.png"
# iOS and store artwork must be opaque. Let each OS apply its own corner mask.
for appearance in light dark tinted; do
    background='#F7F3F9'
    test "$appearance" != dark || background='#201426'
    output="$catalog/AppIcon-1024.png"
    test "$appearance" = light || output="$catalog/AppIcon-$appearance-1024.png"
    magick "$scratch/crescent.png" -resize 780x780 -gravity center \
        -background "$background" -extent 1024x1024 -alpha remove -alpha off \
        -colorspace sRGB -depth 8 "$output"
    if test "$appearance" = tinted; then
        magick "$output" -colorspace Gray -colorspace sRGB "$output"
    fi
done
magick "$catalog/AppIcon-1024.png" -resize 512x512 app/src/main/ic_launcher-playstore.png
# Android foregrounds use a 108dp canvas and a centered 66dp safe area.
for spec in mdpi:48:108:66 hdpi:72:162:99 xhdpi:96:216:132 xxhdpi:144:324:198 xxxhdpi:192:432:264; do
    IFS=: read -r density legacy canvas logo <<< "$spec"
    magick "$scratch/crescent.png" -resize "${logo}x${logo}" -gravity center \
        -background none -extent "${canvas}x${canvas}" \
        "$res/drawable-$density/ic_launcher_foreground.png"
    magick "$res/drawable-$density/ic_launcher_foreground.png" \
        -channel RGB -fill white -colorize 100 +channel \
        "$res/drawable-$density/ic_launcher_monochrome.png"
    magick "$catalog/AppIcon-1024.png" -resize "${legacy}x${legacy}" \
        "$res/mipmap-$density/ic_launcher.png"
    magick -size "${legacy}x${legacy}" xc:none -fill white \
        -draw "circle $((legacy/2)),$((legacy/2)) $((legacy/2)),0" \
        "$scratch/round-mask.png"
    magick "$res/mipmap-$density/ic_launcher.png" "$scratch/round-mask.png" \
        -alpha off -compose CopyOpacity -composite \
        "$res/mipmap-$density/ic_launcher_round.png"
done
