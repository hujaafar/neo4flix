import { bootstrapApplication } from '@angular/platform-browser';
import { inject, provideAppInitializer } from '@angular/core';
import {
  CanActivateFn,
  provideRouter,
  Router,
  Routes,
  withInMemoryScrolling,
} from '@angular/router';
import { App } from './app';
import { Api } from './api';
const signedIn: CanActivateFn = (_, state) => {
  if (inject(Api).user()) return true;
  // The public entrance owns one document-lifetime ScrollCraft instance.
  // Direct movie links and filtered collections keep their authentication flow.
  if (state.url === '/') {
    window.location.replace('/experience/');
    return false;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
const admin: CanActivateFn = () =>
  inject(Api).user()?.role === 'ADMIN' ? true : inject(Router).createUrlTree(['/']);
const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/auth').then((m) => m.AuthPage) },
  { path: 'register', loadComponent: () => import('./pages/auth').then((m) => m.AuthPage) },
  {
    path: 'oauth2/complete',
    loadComponent: () => import('./pages/oauth').then((m) => m.OAuthPage),
  },
  {
    path: '',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/browse').then((m) => m.BrowsePage),
  },
  {
    path: 'recommendations',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/browse').then((m) => m.BrowsePage),
  },
  {
    path: 'movies/:id',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/detail').then((m) => m.DetailPage),
  },
  {
    path: 'watchlist',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/collections').then((m) => m.CollectionsPage),
  },
  {
    path: 'ratings',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/collections').then((m) => m.CollectionsPage),
  },
  {
    path: 'shares',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/collections').then((m) => m.CollectionsPage),
  },
  {
    path: 'share/:id',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/collections').then((m) => m.SharedPage),
  },
  {
    path: 'account',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/account').then((m) => m.AccountPage),
  },
  {
    path: 'admin',
    canActivate: [signedIn, admin],
    loadComponent: () => import('./pages/admin').then((m) => m.AdminPage),
  },
  {
    path: 'graph',
    canActivate: [signedIn, admin],
    loadComponent: () => import('./pages/graph').then((m) => m.GraphPage),
  },
  { path: '**', redirectTo: '' },
];
bootstrapApplication(App, {
  providers: [
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideAppInitializer(() => inject(Api).restore()),
  ],
}).catch(console.error);
