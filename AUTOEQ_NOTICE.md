# Offline headset EQ database

At build time, `.github/workflows/build_apk.yml` fetches the public AutoEq repository and generates `app/src/main/assets/headset_profiles.json` from its pre-computed Fixed Band EQ results. The app uses the packaged JSON at runtime and does not contact the internet.

AutoEq: https://github.com/jaakkopasanen/AutoEq
Measurement/result sources represented in AutoEq include oratory1990, crinacle, Innerfidelity, Rtings and legacy headphone.com measurements. The app exposes the source name next to each matched profile so the user can distinguish measured profiles from unsupported models.

A model is not given an invented correction curve. For models without a matching measured Fixed Band EQ result, the app leaves the signal source-preserving rather than claiming a scientifically measured tuning.
