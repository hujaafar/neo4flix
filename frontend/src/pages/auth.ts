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
  constructor() {
    if (this.api.user())
      void this.router.navigateByUrl(
        this.returnUrl?.startsWith('/') && !this.returnUrl.startsWith('//') ? this.returnUrl : '/',
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
