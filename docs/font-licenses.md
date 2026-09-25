# NeoCanvas bundled font licences

NeoCanvas bundles the following font families from the official Google Fonts repository. Each is distributed under the SIL Open Font License 1.1 and may be used in commercial artwork and software bundles.

| Family | Source directory | Licence shipped in app |
|---|---|---|
| Inter | `google/fonts/ofl/inter` | `inter-OFL.txt` |
| Noto Sans | `google/fonts/ofl/notosans` | `notosans-OFL.txt` |
| Lora | `google/fonts/ofl/lora` | `lora-OFL.txt` |
| Playfair Display | `google/fonts/ofl/playfairdisplay` | `playfairdisplay-OFL.txt` |
| Caveat | `google/fonts/ofl/caveat` | `caveat-OFL.txt` |
| JetBrains Mono | `google/fonts/ofl/jetbrainsmono` | `jetbrainsmono-OFL.txt` |

The complete upstream copyright and licence notice for every family is bundled under `font-licenses/` in the application resources. Apple system fonts are accessed through platform APIs and are not redistributed in the app bundle.

User-installed or future user-imported fonts remain the user’s responsibility. NeoCanvas does not embed raw user font files in shared `.neocanvas` documents by default.
