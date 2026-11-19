import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LogoComponent } from '../shared/logo.component';
import { INFOS_LEGALES } from '../shared/infos-legales';

/** Pages publiques exigées par Meta : politique de confidentialité, conditions d'utilisation, suppression des données. */
@Component({
  selector: 'app-legal',
  imports: [RouterLink, LogoComponent],
  template: `
    <div class="page legal">
      <header class="tete"><a routerLink="/"><app-logo [taille]="24" /></a></header>
      @switch (page) {
        @case ('confidentialite') {
          <h1>Politique de confidentialité</h1>
          <p class="aide">Dernière mise à jour : {{ i.misAJour }}</p>

          <h2>Qui sommes-nous</h2>
          <p>OkPost est un service qui aide les community managers à préparer, faire valider et publier le contenu des réseaux sociaux
            de leurs clients. Il est édité par {{ i.editeur }}. Pour toute question sur vos données : {{ i.email }}.</p>

          <h2>Les données que nous traitons</h2>
          <ul>
            <li><strong>Votre compte</strong> : nom, adresse e-mail et mot de passe (conservé sous forme chiffrée irréversible).</li>
            <li><strong>Les fiches clients que vous saisissez</strong> : nom de l'activité, secteur, ton, offres, coordonnées professionnelles,
              couleurs, logos et photos que vous téléversez.</li>
            <li><strong>Les contenus</strong> : calendriers, légendes, visuels et validations que vous ou vos clients créez dans OkPost.</li>
            <li><strong>Si vous connectez une Page Facebook</strong> : la liste des Pages que vous choisissez d'autoriser, leur identifiant et leur nom,
              un jeton d'accès à la Page, ses publications et leurs statistiques (réactions, nombre de commentaires, partages, vues).
              Si la Page est liée à un compte Instagram professionnel, nous enregistrons aussi son identifiant et son nom d'utilisateur
              pour publier dessus.
              Nous ne lisons ni vos messages privés, ni votre fil d'actualité, ni la liste de vos amis, et nous ne demandons pas d'autres
              informations de votre profil personnel que celles nécessaires pour lister vos Pages.</li>
          </ul>

          <h2>Pourquoi nous les utilisons</h2>
          <ul>
            <li>Publier ou programmer, en votre nom et après validation, les contenus sur la Page que vous avez autorisée.</li>
            <li>Vous présenter un bilan de la performance des publications de la Page et des recommandations.</li>
            <li>Préparer des calendriers et des textes avec l'aide de l'intelligence artificielle.</li>
          </ul>
          <p>Nous ne vendons pas vos données, nous ne les utilisons pas pour de la publicité et nous ne les partageons pas à d'autres fins.</p>

          <h2>Jetons d'accès Facebook</h2>
          <p>Le jeton d'accès à une Page est chiffré (AES-256) avant d'être enregistré, n'est jamais affiché et n'est utilisé que pour les
            actions décrites ci-dessus. Il est supprimé immédiatement lorsque vous déconnectez la Page dans OkPost.
            Vous pouvez aussi retirer l'accès à tout moment depuis les paramètres de votre compte Facebook
            (Paramètres &gt; Applications et sites web).</p>

          <h2>Qui reçoit des données</h2>
          <ul>
            <li><strong>Hébergement</strong> : le serveur de l'application et la base de données sont hébergés chez notre prestataire
              d'hébergement ; l'interface est diffusée par Vercel.</li>
            <li><strong>Services d'intelligence artificielle</strong>, selon la configuration : Google (Gemini), Cloudflare (Workers AI), Groq
              ou Anthropic (Claude). Ils reçoivent les informations de la fiche client, les idées de publication, les photos de la banque
              d'images (pour les décrire) et, pour le bilan, des statistiques agrégées avec de courts extraits des publications de la Page.
              Ils ne reçoivent ni les jetons d'accès, ni le contenu des commentaires, ni les mots de passe.</li>
            <li><strong>Meta</strong> : lorsque vous publiez sur une Page connectée, le contenu est transmis à Facebook.</li>
          </ul>

          <h2>Durée de conservation</h2>
          <p>Vos données sont conservées tant que votre compte est actif. Archiver un client dans OkPost ne les efface pas : demandez la suppression
            (voir ci-dessous). Les sauvegardes de la base sont conservées 14 jours.</p>

          <h2>Vos droits</h2>
          <p>Vous pouvez demander l'accès à vos données, leur rectification ou leur suppression en écrivant à {{ i.email }}.
            Les instructions détaillées sont sur la page
            <a routerLink="/suppression-donnees">Suppression des données</a>.</p>

          <h2>Cookies</h2>
          <p>OkPost utilise un seul cookie technique, nécessaire pour garder votre session ouverte (il n'est pas lisible par les scripts de la page).
            Nous n'utilisons aucun cookie publicitaire ni outil de suivi.</p>

          <h2>Modifications</h2>
          <p>Nous pouvons mettre à jour cette politique ; la date de dernière mise à jour figure en haut de la page.</p>
        }
        @case ('conditions') {
          <h1>Conditions d'utilisation</h1>
          <p class="aide">Dernière mise à jour : {{ i.misAJour }}</p>

          <h2>Le service</h2>
          <p>OkPost, édité par {{ i.editeur }}, permet de créer des calendriers éditoriaux, de rédiger et valider des publications et de les
            programmer sur les réseaux sociaux de vos clients. En créant un compte, vous acceptez ces conditions.</p>

          <h2>Votre compte</h2>
          <p>Vous êtes responsable de la confidentialité de votre mot de passe et de l'activité de votre compte. Les informations que vous
            fournissez doivent être exactes.</p>

          <h2>Vos contenus</h2>
          <p>Vous gardez tous les droits sur les contenus que vous ajoutez (textes, logos, photos). Vous garantissez avoir le droit de les utiliser
            et de les publier, y compris pour le compte de vos clients. Vous nous autorisez à les traiter uniquement pour fournir le service.</p>

          <h2>Intelligence artificielle</h2>
          <p>Les textes et les images proposés par l'IA sont des suggestions : relisez-les avant publication. Vous restez responsable de ce qui est
            publié. OkPost ne publie automatiquement que les contenus que vous avez validés.</p>

          <h2>Réseaux sociaux</h2>
          <p>La connexion à une Page Facebook se fait avec votre autorisation, que vous pouvez retirer à tout moment. L'utilisation de Facebook reste
            soumise aux conditions de Meta.</p>

          <h2>Usages interdits</h2>
          <p>Il est interdit d'utiliser OkPost pour publier des contenus illégaux, trompeurs, haineux ou qui portent atteinte aux droits d'autrui,
            ou pour perturber le fonctionnement du service.</p>

          <h2>Disponibilité et responsabilité</h2>
          <p>Nous faisons au mieux pour que le service soit disponible, sans garantie d'absence d'interruption. Les réseaux sociaux peuvent refuser ou
            retarder une publication ; nous ne sommes pas responsables de leurs décisions.</p>

          <h2>Résiliation</h2>
          <p>Vous pouvez arrêter d'utiliser OkPost et demander la suppression de votre compte à tout moment. Nous pouvons suspendre un compte qui ne
            respecte pas ces conditions.</p>

          <h2>Droit applicable</h2>
          <p>Ces conditions sont régies par le droit de : {{ i.pays }}. Contact : {{ i.email }}.</p>
        }
        @case ('suppression-donnees') {
          <h1>Suppression des données</h1>
          <p class="aide">Dernière mise à jour : {{ i.misAJour }}</p>
          <p>Vous pouvez à tout moment retirer l'accès d'OkPost à votre Page Facebook et demander la suppression de vos données.</p>

          <h2>1. Déconnecter une Page (immédiat)</h2>
          <p>Dans OkPost, ouvrez la fiche du client, rubrique « Comptes sociaux », puis supprimez la Page connectée. Le jeton d'accès est effacé
            immédiatement et OkPost ne peut plus lire ni publier sur cette Page.</p>

          <h2>2. Retirer l'application depuis Facebook</h2>
          <p>Sur Facebook : Paramètres et confidentialité &gt; Paramètres &gt; Applications et sites web (ou « Intégrations professionnelles »),
            sélectionnez OkPost puis « Supprimer ». L'autorisation est révoquée côté Facebook.</p>

          <h2>3. Supprimer vos données OkPost</h2>
          <p>Envoyez un e-mail à <strong>{{ i.email }}</strong> avec pour objet « Suppression de mes données OkPost », depuis l'adresse de votre compte.
            Indiquez si la demande concerne tout le compte ou un client en particulier. Nous supprimons les données concernées (fiches, contenus, images,
            jetons) dans un délai de 30 jours et vous confirmons par e-mail. Les sauvegardes techniques sont purgées dans les 14 jours qui suivent.</p>

          <p>Plus d'informations : <a routerLink="/confidentialite">Politique de confidentialité</a>.</p>
        }
      }
      <footer class="bas">
        <a routerLink="/confidentialite">Confidentialité</a> ·
        <a routerLink="/conditions">Conditions d'utilisation</a> ·
        <a routerLink="/suppression-donnees">Suppression des données</a>
      </footer>
    </div>
  `,
  styles: [`
    .legal { max-width: 760px; }
    .tete { margin-bottom: 24px; }
    h1 { margin-bottom: 4px; }
    h2 { margin: 24px 0 6px; font-size: 1.1rem; }
    li { margin: 4px 0; }
    .bas { margin-top: 40px; padding-top: 16px; border-top: 1px solid var(--gris-clair); font-size: .9rem; color: var(--gris); }
  `],
})
export class LegalComponent {
  i = INFOS_LEGALES;
  page = inject(ActivatedRoute).snapshot.data['page'] as string;
}
