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

## Déployer : API sur le VPS, interface sur Vercel

Installation actuelle du VPS : **sans Docker** (Java 17 + PostgreSQL du serveur + service systemd).

```bash
# 1. VPS (DNS : api.okpost.my2-0.cloud -> IP du VPS)
sudo apt-get install -y openjdk-17-jre-headless postgresql fontconfig fonts-dejavu-core
git clone https://github.com/lafortunekadjo/okpost.git /opt/okpost

# Base dédiée (ne pas réutiliser celle d'une autre application)
sudo -u postgres psql -c "CREATE USER okpost WITH PASSWORD '<mot-de-passe>';"
sudo -u postgres psql -c "CREATE DATABASE okpost OWNER okpost;"

# Utilisateur du service et configuration (secrets : jamais dans git)
sudo useradd --system --home /opt/okpost --shell /usr/sbin/nologin okpost
sudo mkdir -p /var/lib/okpost/fichiers && sudo chown -R okpost:okpost /var/lib/okpost
sudo nano /etc/okpost.env     # variables de deploy/.env.example + PORT=8083, DB_URL=jdbc:postgresql://localhost:5432/okpost,
                              # DB_USER=okpost, FILES_DIR=/var/lib/okpost/fichiers
sudo chmod 600 /etc/okpost.env

# Service (le jar est ensuite déposé par le déploiement automatique, voir plus bas)
sudo cp /opt/okpost/deploy/okpost.service /etc/systemd/system/ && sudo systemctl daemon-reload
sudo systemctl enable okpost

sudo cp /opt/okpost/deploy/nginx-api.conf /etc/nginx/sites-available/okpost-api
sudo ln -s /etc/nginx/sites-available/okpost-api /etc/nginx/sites-enabled/
sudo certbot --nginx -d api.okpost.my2-0.cloud
sudo nginx -t && sudo systemctl reload nginx

# Sauvegarde quotidienne de la base (pg_dump, 14 jours) : tester une fois à la main
sudo /opt/okpost/deploy/sauvegarde.sh && ls -lh /opt/okpost/sauvegardes
(sudo crontab -l 2>/dev/null; echo "30 2 * * * /opt/okpost/deploy/sauvegarde.sh") | sudo crontab -
```

**Plus tard, avec Docker :** `backend/Dockerfile` et `deploy/docker-compose.yml` sont conservés pour cela. La migration se ferait ainsi :
`pg_dump` de la base actuelle, `docker compose up -d --build` (le fichier `.env` remplace `/etc/okpost.env`), restauration du dump
dans le conteneur, puis arrêt du service `okpost`. `sauvegarde.sh` détecte tout seul le conteneur `db` et l'utilise ;
seul le script de déploiement (`okpost-deploy`) serait à remplacer par un `docker compose up -d --build`.

2. Vercel : importer le dépôt, **Root Directory = `frontend`** (le reste vient de `frontend/vercel.json`).
   Le rewrite de `frontend/vercel.json` cible déjà `api.okpost.my2-0.cloud`.

Vercel relaie `/api/*` vers le VPS : le navigateur ne parle qu'à un seul domaine, donc le cookie de
rafraîchissement HttpOnly et le retour OAuth Meta fonctionnent sans réglage CORS.
`PUBLIC_URL` doit être l'URL Vercel ; l'URI de retour Meta est `<PUBLIC_URL>/api/v1/public/meta/retour`.

Mémoire : l'API est limitée à 512 Mo et PostgreSQL réglé petit. Vérifier `free -h` avant.

### Déploiement automatique

À chaque `git push` sur `main` : Vercel redéploie l'interface, et le workflow `.github/workflows/ci.yml` teste puis
déploie l'API (jar copié sur le VPS, service `okpost` redémarré, retour automatique à la version précédente si elle ne démarre pas).

Installation, une seule fois :

```bash
# 1. Sur votre PC : une clé SSH réservée au déploiement (sans mot de passe)
ssh-keygen -t ed25519 -f ~/.ssh/okpost_deploy -C github-deploy -N ""

# 2. Sur le VPS : créer l'utilisateur « deploy » (il ne peut que déposer un jar et lancer okpost-deploy)
cd /opt/okpost && git pull
sudo bash deploy/installer-deploiement.sh "ssh-ed25519 AAAA... github-deploy"   # contenu de okpost_deploy.pub

# 3. Sur votre PC : l'empreinte du serveur, pour que GitHub vérifie à qui il se connecte
ssh-keyscan -t ed25519 <IP_DU_VPS>
```

Puis dans GitHub, Settings > Secrets and variables > Actions > New repository secret :

| Secret | Valeur |
|---|---|
| `VPS_HOST` | l'IP (ou le nom) du VPS |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | le contenu du fichier `okpost_deploy` (clé **privée**) |
| `VPS_KNOWN_HOSTS` | la ligne affichée par `ssh-keyscan` |
| `VPS_PORT` | seulement si SSH n'écoute pas sur le port 22 |

Enfin, activez le déploiement : Settings > Secrets and variables > Actions > onglet **Variables** > New repository variable,
nom `DEPLOY_VPS`, valeur `true`. Tant qu'elle n'existe pas, la CI teste seulement, sans déployer.

Les secrets de l'application (base, clés IA, Meta…) restent dans `/etc/okpost.env` sur le serveur : `application.yml` n'en contient aucun.
En local, mettez vos valeurs dans `backend/config/application.yml` (ignoré par git, chargé automatiquement quand l'application est lancée depuis `backend/`).

## Passer à Claude

Renseigner `ANTHROPIC_API_KEY` dans `.env`, redémarrer, puis choisir « claude » dans Réglages (compte super-admin). Aucun redéploiement nécessaire ensuite : le choix est enregistré en base.
