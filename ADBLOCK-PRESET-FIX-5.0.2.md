# MaTRIX Co 5.0.2 — Preset application fix

- Selecting Low / Recommended / High now immediately starts a refresh for the selected core block sources.
- The existing explicit "Apply and update now" action remains available for manual refresh.
- The updater rebuilds the remote hash database from the selected sources and refreshes the enabled content-filter lists after the core update.
- The previous saved database remains available if the selected-source update fails, per the existing transactional update behavior.
- This fixes the UI behavior where selecting a preset only changed preferences and did not rebuild the active blocking data.
- Build and on-device verification are still required.
