# OkPost

Calendriers éditoriaux générés par IA, briefs visuels, scénarios vidéo et validation client par lien — pour community managers.

- `backend/` — Spring Boot 3.5, Java 21, Spring AI (Gemini puis Claude), PostgreSQL + Flyway
- `frontend/` — Angular 19 (composants autonomes, Signals)
- `deploy/` — Docker Compose, Nginx, sauvegarde
- Cahier des charges : https://claude.ai/artifact/FPS3aprxnFQ5einDSqUjLT

## Ce qui est en place (lots 0 à 4)

| Lot | Contenu |
| --- | --- |
| 0 · Socle | Inscription (espace + essai de 14 jours), connexion JWT 15 min + cookie de rafraîchissement 7 jours, blocage après 5 échecs, rôles SUPER_ADMIN / ADMIN / CM, isolation par espace |
| 1 · Fiches clients | Fiche complète en 7 onglets, logos et photos (PNG, JPG, WebP, SVG, 5 Mo, format vérifié par les octets), limites par formule |
| 2 · Calendrier IA | Grille générée en tâche de fond, validation du JSON + une relance, file d'attente avec débit maximum et relances sur 429, journal des appels, vue mois (glisser-déposer) et vue liste |
| 3 · Détail | Légende, hashtags, CTA, brief + prompt d'image, textes de carrousel, scénario plan par plan ; régénération d'un champ ou de toute la publication avec consigne |
| 4 · Validation | Lien sans compte (32 octets, 30 jours, révocable), OK / modifier la légende / demander une modification, envoi par WhatsApp ou e-mail, notification du CM |

Reste pour le lot 5 : paiement Flutterwave, activation en espèces depuis l'interface super-admin, reçu PDF.

## Lancer en local

Prérequis : Java 21, Maven, Node 22, Docker.

```bash
docker run -d --name okpost-db -e POSTGRES_DB=okpost -e POSTGRES_USER=okpost -e POSTGRES_PASSWORD=okpost -p 5432:5432 postgres:16-alpine

cd backend
GEMINI_API_KEY=ta-cle mvn spring-boot:run      # http://localhost:8080, Swagger : /api/docs

cd ../frontend
npm install
npx ng serve                                     # http://localhost:4200 (proxy /api vers 8080)
```

Clé Gemini gratuite : https://aistudio.google.com/apikey — sur l'offre gratuite, Google peut utiliser les données envoyées : à réserver au développement et aux pilotes.

Tests : `cd backend && mvn verify` (PostgreSQL lancé automatiquement par Testcontainers, IA simulée).

## Déployer sur le VPS Hostinger (à côté de My2-0)

```bash
# Sur le VPS
git clone git@github.com:Fride10/okpost.git /opt/okpost
cd /opt/okpost/deploy
cp .env.example .env && nano .env              # secrets, clé Gemini, super-admin
docker compose up -d --build                    # API sur 127.0.0.1:8090

# Interface
cd /opt/okpost/frontend && npm ci && npx ng build
sudo mkdir -p /var/www/okpost && sudo cp -r dist/frontend/browser /var/www/okpost/

# Nginx (le Nginx existant de My2-0)
sudo cp /opt/okpost/deploy/nginx-okpost.conf /etc/nginx/sites-available/okpost
sudo ln -s /etc/nginx/sites-available/okpost /etc/nginx/sites-enabled/
sudo certbot --nginx -d okpost.app -d www.okpost.app
sudo nginx -t && sudo systemctl reload nginx

# Sauvegarde quotidienne
(crontab -l; echo "30 2 * * * /opt/okpost/deploy/sauvegarde.sh") | crontab -
```

Mémoire : l'API est limitée à 512 Mo et PostgreSQL réglé petit. Vérifier `free -h` avant, pour ne pas gêner My2-0.

## Passer à Claude

Renseigner `ANTHROPIC_API_KEY` dans `.env`, redémarrer, puis choisir « claude » dans Réglages (compte super-admin). Aucun redéploiement nécessaire ensuite : le choix est enregistré en base.
