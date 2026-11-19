# Revue de l'application Meta (Facebook) pour OkPost

Objectif : que vos clients puissent connecter **leur** Page Facebook en un clic, sans être ajoutés comme testeurs de l'app.
Sans la revue, l'app reste en « mode développement » : seuls les administrateurs, développeurs et testeurs de l'app peuvent la connecter.

> Les exigences de Meta changent souvent. Ce guide est basé sur le fonctionnement connu de la revue d'app ; vérifiez chaque étape dans le
> tableau de bord développeur (developers.facebook.com) et suivez ses messages si un écran diffère.

## 0. Avant de commencer (à faire de votre côté)

| À faire | Pourquoi |
|---|---|
| Remplir `frontend/src/app/shared/infos-legales.ts` (éditeur, e-mail de contact, pays) | Ces infos s'affichent sur les pages de confidentialité, conditions et suppression ; Meta les lit. |
| Déployer le frontend sur Vercel avec ces pages en ligne | Meta ouvre les URL : `/confidentialite`, `/conditions`, `/suppression-donnees`. Elles doivent répondre en HTTPS sans connexion. |
| Utiliser un domaine stable (idéalement le vôtre, pas `*.vercel.app`) | L'URL de redirection OAuth et les URL légales sont enregistrées chez Meta ; les changer oblige à tout reconfigurer. |
| Passer le plan IA en offre payante pour les données Meta (voir §5) | Risque de non-conformité avec les règles de Meta sur l'usage des données. |
| Créer un compte OkPost de démonstration pour les examinateurs | Ils doivent pouvoir se connecter et voir une démo complète, sans double authentification. |
| Avoir une Page Facebook de test avec quelques publications | Pour montrer la publication et le bilan dans la vidéo. |

## 1. Réglages de l'app (tableau de bord Meta)

**Paramètres > De base**
- Nom : OkPost. Icône 1024×1024. Catégorie : « Business et pages » ou équivalente.
- E-mail de contact : celui de `infos-legales.ts`.
- URL de la politique de confidentialité : `https://<votre-domaine>/confidentialite`
- URL des conditions d'utilisation : `https://<votre-domaine>/conditions`
- Suppression des données : choisir « Instructions de suppression des données » et saisir `https://<votre-domaine>/suppression-donnees`
- Domaines de l'app : votre domaine.

**Facebook Login > Paramètres**
- URI de redirection OAuth valides : `https://<votre-domaine>/api/v1/public/meta/retour`
  (c'est `PUBLIC_URL` du `.env` du serveur + `/api/v1/public/meta/retour`).
- Connexion OAuth cliente et web : activées.

**Rôles** : ajoutez votre compte comme administrateur et vos clients pilotes comme testeurs pendant la phase de développement.

**Vérification d'entreprise** : Meta l'exige généralement pour l'accès avancé (voir §4). Dans « Paramètres de l'entreprise »,
rattachez l'app à votre portefeuille d'entreprise et lancez la vérification (pièces d'identité de l'entreprise, adresse, téléphone ou domaine).
Prévoyez plusieurs jours à quelques semaines : **c'est l'étape la plus longue, lancez-la en premier.**

## 2. Permissions à demander (celles que le code utilise vraiment)

Ne demandez **que** celles-ci : Meta refuse les permissions dont l'usage n'est pas démontré dans la vidéo.
Les textes sont en anglais pour les examinateurs ; copiez-les dans le champ « Comment utilisez-vous cette permission ».

### `pages_show_list`
> OkPost is a social media management tool for community managers. After the user logs in with Facebook, we use `pages_show_list` to
> show the list of Pages the user manages so they can choose which Page to connect to a client in OkPost. Without it we cannot
> let the user select a Page.

### `pages_manage_posts`
> Users prepare a content calendar in OkPost, and their client validates each post. Once a post is approved, OkPost uses
> `pages_manage_posts` to publish it (text, photo or video) on the connected Page, immediately or at the scheduled time. Posts are published only
> after explicit approval, only on Pages the user selected, and the user can disconnect the Page at any time.

### `instagram_basic`
> After the user connects a Facebook Page, OkPost uses `instagram_basic` to find the Instagram professional account linked to that Page
> (its ID and username), so the user can choose to publish approved content to it. We do not read the account's followers, messages or personal data.

### `instagram_content_publish`
> Once a post is approved by the client, OkPost uses `instagram_content_publish` to publish it (photo, carousel, Reel or Story) on the Instagram
> professional account the user connected, immediately or at the scheduled time. Content is published only after explicit approval and only on the
> account the user selected; the user can disconnect it at any time.

### `pages_read_engagement`
> OkPost reads the Page's own posts and their public engagement counts (reactions, shares) with `pages_read_engagement` to build the
> performance report shown to the community manager and their client, so that the next month's content can be adjusted.

### `read_insights`
> OkPost uses `read_insights` to read post-level metrics (views) for the Page's own posts and display them in the performance report
> (best and weakest posts, evolution compared with the previous period). Data is used only to display reports to the Page's manager.

### `pages_read_user_content`
> OkPost uses `pages_read_user_content` only to count comments on the Page's own posts, so the report can include the number of comments per post.
> We do not store or display the content or authors of comments.

> Vérifiez ce dernier point avant de soumettre : le code lit le **nombre** de commentaires. Si vous n'avez pas besoin de cette permission
> pour le bilan, retirez-la de `MetaApi.PERMISSIONS` et ne la demandez pas : moins de permissions = revue plus rapide.

## 3. Vidéo de démonstration (une par permission, ou une seule qui les couvre toutes)

Enregistrez l'écran en 1080p, **en anglais** (sous-titres ou voix), 2 à 4 minutes. Montrez dans cet ordre :

1. La page de connexion OkPost, puis votre connexion (compte de démonstration).
2. Une fiche client, rubrique « Comptes sociaux » : cliquer sur **Connecter Facebook**.
3. La fenêtre d'autorisation Facebook **en entier**, avec chaque permission demandée visible, la sélection de la Page, puis le retour dans OkPost
   avec la Page connectée (`pages_show_list`).
4. Un calendrier : une publication validée par le client, programmée, puis **publiée sur la Page** ; ouvrir ensuite la Page sur Facebook pour montrer
   le post publié (`pages_manage_posts`).
5. L'écran **Bilan** du client : publications, réactions, partages, vues, commentaires, meilleurs et moins bons posts
   (`pages_read_engagement`, `read_insights`, `pages_read_user_content`).
6. La suppression de la Page dans OkPost, puis la page publique « Suppression des données ».

Conseils : langue de l'interface Facebook en anglais, aucun bandeau d'erreur, pas de données personnelles réelles de clients.

## 4. Dépôt de la revue

1. **Tableau de bord > Revue de l'app > Autorisations et fonctionnalités** : demander l'accès avancé pour les sept permissions.
2. Pour chacune : coller le texte du §2, joindre la vidéo, indiquer les identifiants du compte de démonstration OkPost et les étapes pour reproduire
   (« Se connecter, ouvrir le client Démo, cliquer sur Connecter Facebook… »).
3. **Utilisation des données (Data Use Checklist)** : répondre honnêtement (voir §5).
4. Passer l'app en mode **Live** (nécessaire pour que les utilisateurs non-testeurs puissent la connecter une fois la revue acceptée).
5. Délai habituel : de quelques jours à deux semaines ; en cas de refus, Meta explique pourquoi : corrigez, ré-enregistrez la vidéo si besoin et resoumettez.

Causes de refus fréquentes : examinateurs qui ne peuvent pas se connecter (double authentification, compte sans données), vidéo où la fenêtre de
permissions n'apparaît pas, permission demandée mais non utilisée, URL légales qui ne chargent pas, domaine ou URI de redirection différents de la vidéo.

## 5. Points de conformité à régler avant de soumettre

- **Entraînement des modèles d'IA.** Les règles de la plateforme Meta interdisent d'utiliser les données reçues via l'API pour entraîner des
  modèles. Le bilan envoie à l'IA des statistiques et de courts extraits des publications de la Page. Si `IA_FOURNISSEUR=gemini` utilise l'**offre gratuite**
  (Google peut s'en servir pour s'améliorer), ce n'est pas compatible : passez à l'offre payante (ou à Claude via l'API) pour cette fonction,
  ou retirez les extraits de publications de la requête. Déclarez le fournisseur choisi dans la politique de confidentialité (déjà citée).
- **Jetons.** Ils sont chiffrés en base (`OKPOST_CLE_CHIFFREMENT`) et supprimés à la déconnexion de la Page. Archiver un client **ne les supprime pas** :
  si vous voulez l'inverse, dites-le-moi et j'ajoute la suppression des jetons à l'archivage.
- **Pas de données de profil.** L'app ne lit que les Pages. Ne demandez pas `email` ni `public_profile` sans raison : ils sont inutiles ici.

## 6. Instagram

Géré dans l app : la Page Facebook reliée ramène aussi son compte Instagram professionnel. Instagram demande des permissions supplémentaires (`instagram_basic`, `instagram_content_publish`,
et `instagram_manage_insights` pour les statistiques) et un compte Instagram professionnel lié à la Page. Chaque nouvelle permission passe par une revue :
le plus efficace est de **déposer la revue avec Facebook et Instagram ensemble**, pour tout faire valider en une fois.

## 7. Après l'approbation

- Les clients connectent leur Page eux-mêmes (bouton « Connecter Facebook »), sans être testeurs.
- Mettez en place la **déclaration annuelle de conformité** (Data Use Checklist) : Meta la redemande chaque année.
- Gardez le compte de démonstration et la vidéo : ils servent à chaque nouvelle demande de permission.
