import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api, User } from '../api';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-top"><span class="eyebrow">YOUR SPACE</span></div>
    <header class="page-heading">
      <div>
        <h1>Behind the profile.</h1>
        <p>A few details that make Neo4flix yours.</p>
      </div>
      <button class="button subtle" (click)="finishSecurity()">Sign out</button>
    </header>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (message()) {
      <p class="success" role="status">{{ message() }}</p>
    }
    <div class="account-grid">
      <section class="panel">
        <h2>Your profile</h2>
        <form (ngSubmit)="saveName()" #profile="ngForm">
          <label>
            Display name
            <input
              name="name"
              [(ngModel)]="name"
              required
              minlength="2"
              maxlength="80"
              autocomplete="name"
            />
          </label>
          <label>
            Email address
            <input [value]="api.user()?.email" readonly type="email" />
          </label>
          <button class="button primary" [disabled]="busy() || profile.invalid">
            Save profile
          </button>
        </form>
      </section>
      <section class="panel">
        <div class="section-line">
          <h2>Two-factor authentication</h2>
          <span class="status-pill" [class.enabled]="api.user()?.twoFactorEnabled">
            {{ api.user()?.twoFactorEnabled ? 'Enabled' : 'Not enabled' }}
          </span>
        </div>
        <p class="muted">Add a six-digit code from an authenticator app when you sign in.</p>
        <form (ngSubmit)="api.user()?.twoFactorEnabled ? disable() : setup()" #security="ngForm">
          <label>
            Current password
            <input
              name="password"
              [(ngModel)]="password"
              type="password"
              required
              autocomplete="current-password"
            />
          </label>
          @if (api.user()?.twoFactorEnabled) {
            <label>
              Authenticator code
              <input
                name="code"
                [(ngModel)]="code"
                required
                inputmode="numeric"
                pattern="[0-9]{6}"
                maxlength="6"
                autocomplete="one-time-code"
              />
            </label>
          }
          <button class="button subtle" [disabled]="busy() || security.invalid">
            {{ api.user()?.twoFactorEnabled ? 'Disable 2FA' : 'Set up authenticator' }}
          </button>
        </form>
        @if (secret()) {
          <div class="setup-box">
            <h3>Add a new account in your authenticator</h3>
            <p>
              Select “Enter a setup key”, use your Neo4flix email, choose a time-based key, and
              enter this secret:
            </p>
            <code class="secret">{{ secret() }}</code>
            <p class="field-help">Keep this key private. Setup expires in 10 minutes.</p>
            <form (ngSubmit)="confirmSetup()" #confirmForm="ngForm">
              <label>
                Code from your authenticator
                <input
                  name="confirmCode"
                  [(ngModel)]="code"
                  required
                  pattern="[0-9]{6}"
                  maxlength="6"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                />
              </label>
              <button class="button primary" [disabled]="busy() || confirmForm.invalid">
                Confirm and enable
              </button>
            </form>
          </div>
        }
      </section>
      <section class="panel">
        <h2>Change password</h2>
        <form (ngSubmit)="changePassword()" #change="ngForm">
          <label>
            Current password
            <input
              name="current"
              [(ngModel)]="changeCurrent"
              required
              type="password"
              autocomplete="current-password"
            />
          </label>
          <label>
            New password
            <input
              name="newPassword"
              [(ngModel)]="newPassword"
              required
              minlength="12"
              maxlength="72"
              type="password"
              autocomplete="new-password"
            />
          </label>
          <p class="field-help">12+ characters with uppercase, lowercase, a number and a symbol.</p>
          @if (api.user()?.twoFactorEnabled) {
            <label>
              Authenticator code
              <input
                name="changeCode"
                [(ngModel)]="changeCode"
                required
                pattern="[0-9]{6}"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
            </label>
          }
          <button class="button primary" [disabled]="busy() || change.invalid">
            Change password
          </button>
          <p class="field-help">Changing security settings signs out all your sessions.</p>
        </form>
      </section>
      @for (provider of providers; track provider.id) {
        <section class="panel">
          <div class="section-line">
            <h2>{{ provider.name }} sign-in</h2>
            <span class="status-pill" [class.enabled]="connected(provider.id)">
              {{ connected(provider.id) ? 'Connected' : 'Not connected' }}
            </span>
          </div>
          @if (connected(provider.id)) {
            <p class="muted">
              {{ provider.name }} signs you in to this account. Your Neo4flix password and
              authenticator settings still apply to account security.
            </p>
            <form (ngSubmit)="disconnectProvider(provider)" #disconnect="ngForm">
              <label>
                Current password
                <input
                  name="password"
                  type="password"
                  [(ngModel)]="provider.password"
                  required
                  maxlength="72"
                  autocomplete="current-password"
                />
              </label>
              @if (api.user()?.twoFactorEnabled) {
                <label>
                  Authenticator code
                  <input
                    name="code"
                    [(ngModel)]="provider.code"
                    required
                    pattern="[0-9]{6}"
                    maxlength="6"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                  />
                </label>
              }
              <button class="button subtle" [disabled]="busy() || disconnect.invalid">
                Disconnect {{ provider.name }}
              </button>
              <p class="field-help">
                This signs out all sessions. You can sign in again using your email and Neo4flix
                password.
              </p>
            </form>
          } @else {
            <p class="muted">
              To connect {{ provider.name }}, sign out and choose “Continue with
              {{ provider.name }}” using the same email. You will confirm your Neo4flix password
              before linking.
            </p>
          }
        </section>
      }
      <section class="panel danger-panel">
        <h2>Delete account</h2>
        <p class="muted">Permanently delete your profile, ratings, watchlist, and shared picks.</p>
        <form (ngSubmit)="deleteAccount()" #deletion="ngForm">
          <label>
            Current password
            <input
              name="deletePassword"
              [(ngModel)]="deletePassword"
              type="password"
              required
              autocomplete="current-password"
            />
          </label>
          @if (api.user()?.twoFactorEnabled) {
            <label>
              Authenticator code
              <input
                name="deleteCode"
                [(ngModel)]="deleteCode"
                required
                pattern="[0-9]{6}"
                maxlength="6"
                inputmode="numeric"
                autocomplete="one-time-code"
              />
            </label>
          }
          <button class="button danger" [disabled]="busy() || deletion.invalid">
            Delete my account
          </button>
        </form>
      </section>
    </div>
  `,
})
export class AccountPage {
  api = inject(Api);
  router = inject(Router);
  name = this.api.user()?.name || '';
  password = '';
  code = '';
  changeCurrent = '';
  newPassword = '';
  changeCode = '';
  deletePassword = '';
  deleteCode = '';
  providers = [
    { id: 'google', name: 'Google', password: '', code: '' },
    { id: 'github', name: 'GitHub', password: '', code: '' },
  ];
  connected(id: string) {
    return id === 'google' ? this.api.user()?.googleLinked : this.api.user()?.githubLinked;
  }
  secret = signal('');
  busy = signal(false);
  error = signal('');
  message = signal('');
  async action(fn: () => Promise<void>, message = '') {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    try {
      await fn();
      this.message.set(message);
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
  saveName() {
    void this.action(async () => {
      this.api.user.set(
        await this.api.request<User>('/api/users/me', 'PATCH', { name: this.name }),
      );
    }, 'Profile saved.');
  }
  setup() {
    void this.action(async () => {
      const data = await this.api.request<{ secret: string }>('/api/users/me/2fa/setup', 'POST', {
        password: this.password,
        code: '',
      });
      this.secret.set(data.secret);
    }, 'Enter the setup key in your authenticator, then confirm its code.');
  }
  confirmSetup() {
    void this.action(async () => {
      await this.api.request('/api/users/me/2fa/confirm', 'POST', {
        password: this.password,
        code: this.code,
      });
      await this.finishSecurity();
    });
  }
  disable() {
    void this.action(async () => {
      await this.api.request('/api/users/me/2fa', 'DELETE', {
        password: this.password,
        code: this.code,
      });
      await this.finishSecurity();
    });
  }
  changePassword() {
    void this.action(async () => {
      await this.api.request('/api/users/me/password', 'PUT', {
        password: this.changeCurrent,
        newPassword: this.newPassword,
        code: this.changeCode,
      });
      await this.finishSecurity();
    });
  }
  deleteAccount() {
    if (
      !confirm(
        'Permanently delete your Neo4flix account and all your saved data? This cannot be undone.',
      )
    )
      return;
    void this.action(async () => {
      await this.api.request('/api/users/me', 'DELETE', {
        password: this.deletePassword,
        code: this.deleteCode,
      });
      await this.finishSecurity();
    });
  }
  async finishSecurity() {
    await this.api.logout();
    await this.router.navigate(['/login']);
  }
  disconnectProvider(provider: { id: string; password: string; code: string }) {
    void this.action(async () => {
      await this.api.request('/api/users/me/oauth2/' + provider.id, 'DELETE', {
        password: provider.password,
        code: provider.code,
      });
      await this.finishSecurity();
    });
  }
}
