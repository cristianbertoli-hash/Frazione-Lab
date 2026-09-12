# GitHub-first audit — Frazione-Lab — 2026-09-12

## Regola di conservazione
Nessuna versione storica viene cancellata. Ogni modifica/recupero parte da branch separato.

## Branch live verificati
- `main`
- `feature/fra1-qr-separate-site`
- `feature/fra1-qr-v0`
- `feature/fra1e-offline-completion`
- `ci/medlab-care-v0.1`
- `ci/medlab-care-v0.2`
- `ci/medlab-care-v0.3`
- `ops/global-repository-governance-20260912`
- `ops/github-first-consolidation-20260912`

## Materiale presente
`main` contiene:
- web app Frazione-Lab;
- sito/QR separato;
- sorgente Android offline;
- test;
- workflow build/test/pages.

## MEDLAB Care
Il branch `ci/medlab-care-v0.3` conserva in Git:
- sorgenti/build v0.1-v0.3;
- APK debug v0.1/v0.2/v0.3 codificati Base64;
- file SHA-256;
- verifiche `apksigner` e `badging`.

Questa è una copia persistente del contenuto dei tre APK, anche se non è ancora organizzata come GitHub Release scaricabile direttamente.

## Releases
- Nessuna GitHub Release presente al momento dell'audit.

## Prossimi passi
- Mantenere i branch storici invariati.
- Convertire i binari finali verificati in Release persistenti quando opportuno, mantenendo anche SHA-256.
- Non usare artifact Actions come unica copia.
- Per nuove versioni: branch dedicato, build, verifica, Release, aggiornamento Scheda Madre.
