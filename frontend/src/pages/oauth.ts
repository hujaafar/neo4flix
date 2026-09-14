import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../api';

interface PendingLogin {
  provider: 'google' | 'github';
  providerName: string;
  mode: 'register' | 'link' | 'login';
  email: string;
  name: string;
  twoFactor: boolean;
  returnUrl: string;
}

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="oauth-completion">
      <a class="brand" href="/experience/">
        <span class="brand-icon">N</span>
        neo4flix
        <span class="brand-dot">.</span>
      </a>
      <div class="panel">
        <span class="eyebrow">
          {{ pending() ? 'SIGN IN WITH ' + pending()!.providerName : 'SECURE SIGN-IN' }}
        </span>
        @if (pending(); as login) {
          <h1>
            {{
              login.mode === 'register'
                ? 'Welcome to your cinema.'
                : login.mode === 'link'
                  ? 'Connect your account.'
                  : 'One last check.'
            }}
          </h1>
          <p class="muted">{{ login.providerName }} verified {{ login.email }}.</p>
          @if (login.mode === 'register') {
            <p>
              Choose a Neo4flix password for account security and email sign-in. Next time, you can
              continue with {{ login.providerName }}.
            </p>
          } @else if (login.mode === 'link') {
            <p>
              This email already has a Neo4flix account. Enter its password to connect
              {{ login.providerName }} and keep your existing movies and ratings.
            </p>
          } @else if (login.twoFactor) {
            <p>Enter your Neo4flix authenticator code to finish signing in.</p>
          }
          <form (ngSubmit)="complete()" #form="ngForm">
            @if (login.mode === 'register') {
              <label>
                Your name
                <input
                  name="name"
                  [(ngModel)]="name"
                  required
                  minlength="2"
                  maxlength="80"
                  autocomplete="name"
                />
              </label>
            }
            @if (login.mode !== 'login') {
              <label>
                {{ login.mode === 'register' ? 'Create a Neo4flix password' : 'Neo4flix password' }}
                <input
                  name="password"
                  type="password"
                  [(ngModel)]="password"
                  required
                  [minlength]="login.mode === 'register' ? 12 : 1"
                  maxlength="72"
                  [autocomplete]="login.mode === 'register' ? 'new-password' : 'current-password'"
                />
              </label>
              @if (login.mode === 'register') {
                <p class="field-help">
                  At least 12 characters, with uppercase, lowercase, a number and a symbol.
                </p>
              }
            }
            @if (login.twoFactor) {
              <label>
                Authenticator code
                <input
                  name="code"
                  [(ngModel)]="code"
                  required
                  pattern="[0-9]{6}"
                  maxlength="6"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                />
              </label>
            }
            @if (error()) {
              <p class="error" role="alert">{{ error() }}</p>
            }
            <button class="button primary full" [disabled]="busy() || form.invalid">
              {{
                busy()
                  ? 'Signing you in…'
                  : login.mode === 'link'
                    ? 'Connect ' + login.providerName + ' and sign in'
                    : login.mode === 'register'
                      ? 'Create account'
                      : 'Sign in'
              }}
            </button>
          </form>
          <button class="button subtle full" [disabled]="busy()" (click)="cancel()">
            Cancel {{ login.providerName }} sign-in
          </button>
        } @else {
          <h1>{{ loading() ? 'Checking your sign-in…' : 'Let’s try again.' }}</h1>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
          @if (!loading()) {
            <a class="button primary" routerLink="/login">Back to sign in</a>
          }
        }
      </div>
    </section>
  `,
})
export class OAuthPage {
  api = inject(Api);
  router = inject(Router);
  pending = signal<PendingLogin | null>(null);
  loading = signal(true);
  busy = signal(false);
  error = signal('');
  name = '';
  password = '';
  code = '';
  constructor() {
    void this.load();
  }
  async load() {
    try {
      const pending = await this.api.request<PendingLogin>(
        '/api/auth/oauth2/pending',
        'GET',
        undefined,
        false,
      );
      this.pending.set(pending);
      this.name = pending.name;
      if (pending.mode === 'login' && !pending.twoFactor) await this.complete();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.loading.set(false);
    }
  }
  async complete() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.completeOAuth({ name: this.name, password: this.password, code: this.code });
      await this.router.navigateByUrl(this.pending()?.returnUrl || '/', { replaceUrl: true });
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
  async cancel() {
    this.busy.set(true);
    try {
      await this.api.request('/api/auth/oauth2/cancel', 'POST', undefined, false);
      await this.router.navigate(['/login'], { replaceUrl: true });
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
