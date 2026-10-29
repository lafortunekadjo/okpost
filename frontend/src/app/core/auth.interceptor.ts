import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const estAuth = req.url.startsWith('/api/v1/auth/');
  const estPublic = req.url.startsWith('/api/v1/public/');

  const avecJeton = (jeton?: string) =>
    jeton && !estAuth && !estPublic ? req.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : req;

  return next(avecJeton(auth.session()?.jetonAcces).clone({ withCredentials: true })).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || estAuth || estPublic) {
        return throwError(() => err);
      }
      return auth.rafraichir().pipe(
        switchMap(ok => {
          if (!ok) {
            router.navigateByUrl('/connexion');
            return throwError(() => err);
          }
          return next(avecJeton(auth.session()?.jetonAcces));
        }),
      );
    }),
  );
};
