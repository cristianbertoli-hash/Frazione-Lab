# Frazione Lab

Mini web-app personale per trasformare testo o piccole immagini in una singola frazione esatta nel formato `FRA1`, e ricostruire il contenuto dalla frazione.

## Funzioni

- Testo → frazione
- Frazione → testo
- Foto → frazione
- Frazione → foto
- Riduzione dimostrativa delle immagini a massimo 64×64 pixel
- Elaborazione interamente locale nel browser: il contenuto scelto non viene inviato a un server dall'app

## Pubblicazione

Il sito è contenuto in `index.html` e il workflow `.github/workflows/pages.yml` pubblica tramite GitHub Pages quando Pages è configurato con **Source: GitHub Actions**.

Il repository può restare privato se il piano GitHub dell'account supporta GitHub Pages per repository privati.
