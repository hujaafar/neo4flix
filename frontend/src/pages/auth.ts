import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../api';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-art">
        <a class="brand" href="/experience/">
          <span class="brand-icon">N</span>
          neo4flix
          <span class="brand-dot">.</span>
        </a>
        <div class="auth-orbit" aria-hidden="true">
          <span></span>
          <i></i>
        </div>
        <div class="auth-copy">
          <span class="eyebrow">A WORLD OF STORIES</span>
          <h1>
            Your next favorite
            <br />
            is out there.
          </h1>
          <p>Find the films that stay with you.</p>
        </div>
        <span class="auth-foot">DISCOVER · RATE · CONNECT</span>
      </section>
      <section class="auth-form-wrap">
        <div class="auth-form">
          <span class="eyebrow">{{ register ? 'JOIN THE AUDIENCE' : 'WELCOME BACK' }}</span>
          <h2>{{ register ? 'Make it your cinema.' : 'Take your seat.' }}</h2>
          <p class="muted">
            {{
              register
                ? 'A few details, a whole world of films.'
                : 'Sign in to your collection and recommendations.'
            }}
          </p>
          @for (provider of providers(); track provider.id) {
            <button
              type="button"
              class="button oauth-button full"
              [disabled]="!provider.enabled || busy()"
              (click)="providerLogin(provider.id)"
            >
              @if (provider.id === 'google') {
                <span class="google-mark" aria-hidden="true">G</span>
              } @else {
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path
                    d="M12 .75a11.25 11.25 0 0 0-3.558 21.923c.563.104.77-.244.77-.542 0-.267-.01-1.155-.016-2.095-3.13.68-3.79-1.327-3.79-1.327-.512-1.3-1.25-1.646-1.25-1.646-1.022-.7.078-.686.078-.686 1.13.08 1.725 1.16 1.725 1.16 1.005 1.723 2.637 1.225 3.28.937.102-.729.393-1.225.715-1.507-2.5-.284-5.128-1.25-5.128-5.56 0-1.228.44-2.232 1.16-3.02-.117-.284-.503-1.43.11-2.98 0 0 .945-.303 3.095 1.154a10.8 10.8 0 0 1 5.626 0c2.148-1.457 3.092-1.154 3.092-1.154.615 1.55.23 2.696.113 2.98.722.788 1.16 1.792 1.16 3.02 0 4.32-2.632 5.273-5.14 5.552.404.35.764 1.034.764 2.084 0 1.506-.014 2.72-.014 3.09 0 .3.204.65.774.54A11.25 11.25 0 0 0 12 .75Z"
                  />
                </svg>
              }
              Continue with {{ provider.name }}
            </button>
            @if (!provider.enabled) {
              <p class="field-help">
                {{ provider.name }} sign-in is awaiting setup. You can use email below.
              </p>
            }
          }
          @if (providers().length) {
            <div class="auth-divider"><span>or use your email</span></div>
          }
          <form (ngSubmit)="submit()" #form="ngForm">
            @if (register) {
              <label>
                Your name
                <input
                  name="name"
                  [(ngModel)]="name"
                  required
                  minlength="2"
                  maxlength="80"
                  autocomplete="name"
                  placeholder="How should we call you?"
                />
              </label>
            }
            <label>
              Email address
              <input
                name="email"
                type="email"
                [(ngModel)]="email"
                required
                email
                maxlength="254"
                autocomplete="email"
                placeholder="you@example.com"
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                [(ngModel)]="password"
                required
                [minlength]="register ? 12 : 1"
                maxlength="72"
                [autocomplete]="register ? 'new-password' : 'current-password'"
                placeholder="Enter your password"
              />
            </label>
            @if (register) {
              <p class="field-help">
                At least 12 characters, with uppercase, lowercase, a number and a symbol.
              </p>
            } @else {
              <details class="twofactor">
                <summary>Using two-factor authentication?</summary>
                <label>
                  Authenticator code
                  <input
                    name="code"
                    [(ngModel)]="code"
                    inputmode="numeric"
                    pattern="[0-9]{6}"
                    maxlength="6"
                    autocomplete="one-time-code"
                    placeholder="6-digit code"
                  />
                </label>
              </details>
            }
            @if (error()) {
              <p class="error" role="alert">{{ error() }}</p>
            }
            <button class="button primary full" [disabled]="busy() || form.invalid">
              {{ busy() ? 'One moment…' : register ? 'Create account' : 'Sign in' }}
              <span aria-hidden="true">→</span>
            </button>
          </form>
          <p class="auth-switch">
            {{ register ? 'Already have an account?' : 'New to Neo4flix?' }}
            <a
              [routerLink]="register ? '/login' : '/register'"
              [queryParams]="returnUrl ? { returnUrl } : {}"
            >
              {{ register ? 'Sign in' : 'Create an account' }}
            </a>
          </p>
          <div class="secure-caption">
            <span aria-hidden="true">◇</span>
            Your watchlist. Your taste. Your space.
          </div>
        </div>
      </section>
    </div>
  `,
})
export class AuthPage {
  api = inject(Api);
  router = inject(Router);
  route = inject(ActivatedRoute);
  register = this.route.snapshot.routeConfig?.path === 'register';
  name = '';
  email = '';
  password = '';
  code = '';
  busy = signal(false);
  error = signal('');
  returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
  providers = signal<{ id: string; name: string; enabled: boolean }[]>([]);
  constructor() {
    if (this.route.snapshot.queryParamMap.has('oauthError'))
      this.error.set(
        (this.route.snapshot.queryParamMap.get('oauthError') === 'github' ? 'GitHub' : 'Google') +
          ' sign-in was cancelled or could not be verified. Please try again, or use your email.',
      );
    void this.api
      .request<{ id: string; name: string; enabled: boolean }[]>(
        '/api/auth/oauth2/providers',
        'GET',
        undefined,
        false,
      )
      .then((providers) =>
        this.providers.set(providers.filter((p) => ['google', 'github'].includes(p.id))),
      )
      .catch(() => this.providers.set([]));
    if (this.api.user())
      void this.router.navigateByUrl(
        this.returnUrl?.startsWith('/') && !this.returnUrl.startsWith('//') ? this.returnUrl : '/',
      );
  }
  providerLogin(providerId: string) {
    if (!this.providers().some((p) => p.id === providerId && p.enabled)) return;
    window.location.assign(
      '/api/auth/oauth2/authorize/' +
        providerId +
        '?returnUrl=' +
        encodeURIComponent(this.returnUrl || '/'),
    );
  }
  async submit() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.authenticate(
        this.register ? 'register' : 'login',
        this.register
          ? { name: this.name, email: this.email, password: this.password }
          : { email: this.email, password: this.password, code: this.code },
      );
      const back = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(back?.startsWith('/') && !back.startsWith('//') ? back : '/');
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
