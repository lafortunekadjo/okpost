import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const connecteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.connecte()) return true;
  return auth.rafraichir().pipe(map(ok => (ok ? true : router.parseUrl('/connexion'))));
};

export const inviteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.connecte()) return router.parseUrl('/');
  return auth.rafraichir().pipe(map(ok => (ok ? router.parseUrl('/') : true)));
};
