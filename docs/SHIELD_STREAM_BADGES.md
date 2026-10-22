# Shield Pro — AIOStreams Elite Badges

This change preserves the original Nuvio stream cards, but positions imported badge images **in a separate strip immediately above each source bar** when the user chooses **Settings → Layout → Streams → Badge position → Above source bar**. The bottom-placement option remains unchanged. The same presentation applies to the source selector inside the player.

## Using the Elite Badges pack

Source JSON: https://raw.githubusercontent.com/leonevz/Elite-Badges/main/badges.json

1. Install Nuvio Shield Test, preserving the existing official Nuvio installation separately.
2. Install Cinemeta for metadata and the existing AIOStreams installation (custom named **English**) for streams.
3. Under **Layout → Streams → Fusion badge URLs**, import or activate the Elite Badges JSON.
4. Choose **Badge position → Above source bar**.
5. Turn off separate file-size badges if you want only the custom badge artwork.

The badge row remains outside the card's rounded clipping boundaries. The original stream title, source description, addon logo, focus handling, and selectability are preserved below it.

## Scope / known constraints

The image pack applies only to streams whose source addon manifest ID is the default AIOStreams ID `com.aiostreams.viren070` or has a per-installation suffix starting with `com.aiostreams.viren070.`. Nuvio carries this identifier through its flattened result lists; it **does not** depend on the changeable display name `English`. No custom imported badges are added to Cinemeta/TMDB or unrelated stream addons.

AIOStreams installs using a custom/self-hosted `ADDON_ID` are **not recognized** by this first version. A future enhancement can expose an explicit provider-identity selector in settings. Existing per-stream badges supplied by other addon responses are not erased.

## Acceptance tests on NVIDIA Shield Pro 2019

- [ ] Imported image badges (TrueHD, DTS:X, Atmos, REMUX, 4K, HDR10) show above source bars.
- [ ] Movie and TV episode result cards remain fully selectable with the Shield remote.
- [ ] Source badges also display above cards in the player source-switching panel.
- [ ] Vertical scroll and directional remote focus are smooth; badge strips do not clip or cover titles.
- [ ] Non-AIOStreams addon results do not receive imported Elite Badges.
- [ ] Switching Badge position back to **Bottom** restores the normal existing layout.
- [ ] Existing AIOStreams formatter and lossless-audio behavior remain unchanged.

This is a **draft feature** until Android CI compilation and on-device visual validation succeed.
