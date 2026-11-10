#!/bin/sh
# Export quotidien de la base, conservé 14 jours. Crontab : 30 2 * * * /opt/okpost/deploy/sauvegarde.sh
set -e
DOSSIER=/opt/okpost/sauvegardes
mkdir -p "$DOSSIER"
docker compose -f /opt/okpost/deploy/docker-compose.yml exec -T db pg_dump -U okpost okpost | gzip > "$DOSSIER/okpost-$(date +%F).sql.gz"
find "$DOSSIER" -name 'okpost-*.sql.gz' -mtime +14 -delete
