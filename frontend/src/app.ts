import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Api } from './api';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip-link" href="#main">Skip to content</a>
    @if (api.user()) {
      <aside class="sidebar">
        <a routerLink="/" class="brand" aria-label="Neo4flix home">
          <span class="brand-icon">N</span>
          neo4flix
          <span class="brand-dot">.</span>
        </a>
        <span class="nav-label">YOUR CINEMA</span>
        <nav aria-label="Main navigation">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
            <span aria-hidden="true">▦</span>
            Discover
          </a>
          <a routerLink="/recommendations" routerLinkActive="active">
            <span aria-hidden="true">✧</span>
            For you
          </a>
          <a routerLink="/watchlist" routerLinkActive="active">
            <span aria-hidden="true">▤</span>
            Watchlist
          </a>
          <a routerLink="/ratings" routerLinkActive="active">
            <span aria-hidden="true">☆</span>
            My ratings
          </a>
          <a routerLink="/shares" routerLinkActive="active">
            <span aria-hidden="true">↗</span>
            Shared picks
          </a>
          <a href="/experience/">
            <span aria-hidden="true">◉</span>
            Cinema entrance
          </a>
          @if (api.user()?.role === 'ADMIN') {
            <a routerLink="/admin" routerLinkActive="active">
              <span aria-hidden="true">＋</span>
              Manage films
            </a>
            <a routerLink="/graph" routerLinkActive="active">
              <span aria-hidden="true">◇</span>
              Database graph
            </a>
          }
        </nav>
        <div class="sidebar-bottom">
          <p class="small-note">Good films. Better connections.</p>
          <a routerLink="/account" class="profile-link">
            <span class="avatar">{{ api.user()?.name?.charAt(0) }}</span>
            <span>
              <strong>{{ api.user()?.name }}</strong>
              <small>Account & security</small>
            </span>
            <span aria-hidden="true">↗</span>
          </a>
          <button class="signout" (click)="logout()">Sign out</button>
        </div>
      </aside>
    }
    <main id="main" [class.app-main]="api.user()" tabindex="-1"><router-outlet /></main>
  `,
})
export class App {
  api = inject(Api);
  router = inject(Router);
  async logout() {
    await this.api.logout();
    await this.router.navigate(['/login']);
  }
}
