# Not Enough Rain Sounds · 更好的雨声

A Minecraft sound mod. In developing.

## Testing with audio resource packs

The mod loads its custom audio from enabled resource packs. For Minecraft 1.21.1, copy the ZIP files you want from [soundResource/resourcepacks](soundResource/resourcepacks) into your instance's resource pack folder. Enable the Ground background pack and the Wood C Soft-Tone pack in **Options → Resource Packs**. Enable the [Metal5 Wood-Matched pack](soundResource/resourcepacks/NERS-Rain-Material-Metal-Metal5-Wood-Matched.zip) for metal rain. It mixes 4.4-second Metal5 excerpts with reference metal recordings matched to the Wood C pack before mixing. If a pack provides no replacement for a weather sound, Minecraft plays its original sound. Material rain and plant drips require their corresponding pack events.

Use the [Glass4 Reference Mix](soundResource/resourcepacks/NERS-Rain-Material-Glass-Glass4-Reference-Mix.zip) for glass. Use [Foliage3](soundResource/resourcepacks/NERS-Rain-Material-Foliage-Foliage3-Audition.zip) for rain on leaves. Use [Water2](soundResource/resourcepacks/NERS-Rain-Material-Water-Water2-Audition.zip) for rain on water. Enable [Granular2](soundResource/resourcepacks/NERS-Rain-Material-Granular-Granular2-Audition.zip) to test rain on sand and gravel.

Use [Fabric1](soundResource/resourcepacks/NERS-Rain-Material-Soft-Fabric1-Audition.zip) for soft surfaces and [Aquatic Plant](soundResource/resourcepacks/NERS-Rain-Material-Aquatic-Plant.zip) for aquatic plants, including dripleaves. To compare background rain endings, switch between the original Wet Ground pack and the [100 ms release audition](soundResource/resourcepacks/NERS-Rain-C-wet-ground-Tail100ms.zip). Enable one background pack at a time.

The [legacy audio archive](soundResource/archives/NERS-Legacy-Audio) holds the previous assets and attribution. It stays outside the mod and development instances.

## License

Code: GPLv3-only with a Minecraft linking exception. Audio retains its own licenses; see [LICENSE](LICENSE).
