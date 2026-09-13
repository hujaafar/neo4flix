import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api, Movie, Rating, Share } from '../api';
import { EmptyState, MovieCard } from '../ui';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MovieCard, EmptyState],
  template: `
    <div class="page-top">
      <span class="eyebrow">YOUR COLLECTION</span>
      <a class="text-link" routerLink="/">Discover more ↗</a>
    </div>
    <header class="page-heading">
      <div>
        <h1>
          {{
            kind === 'watchlist'
              ? 'For another evening.'
              : kind === 'ratings'
                ? 'The films that found you.'
                : 'Pass a good story along.'
          }}
        </h1>
        <p>
          {{
            kind === 'watchlist'
              ? 'All the films you want to come back to.'
              : kind === 'ratings'
                ? 'Your ratings and private notes, all in one place.'
                : 'Manage the picks you have shared with friends.'
          }}
        </p>
      </div>
    </header>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (message()) {
      <p class="success" role="status">{{ message() }}</p>
    }
    @if (loading()) {
      <p class="loading" aria-busy="true">Opening your collection…</p>
    } @else {
      @if (kind === 'watchlist') {
        <div class="movie-grid">
          @for (movie of movies(); track movie.id) {
            <movie-card [movie]="movie" [removable]="true" (remove)="removeWatch($event)" />
          }
        </div>
        @if (!movies().length) {
          <empty-state
            title="Your next movie night starts here."
            message="Open a film and add it to your watchlist."
          >
            <a routerLink="/" class="button primary">Explore films</a>
          </empty-state>
        }
      }
      @if (kind === 'ratings') {
        @for (r of ratings(); track r.id) {
          <article class="rating-row">
            <a [routerLink]="['/movies', r.movie.id]">
              <img
                [src]="'/art/' + r.movie.artwork + '.svg'"
                [alt]="r.movie.title"
                (error)="fallback($event)"
              />
            </a>
            <div>
              <span class="eyebrow">{{ r.movie.year }} · {{ r.movie.genres[0] }}</span>
              <h2>
                <a [routerLink]="['/movies', r.movie.id]">{{ r.movie.title }}</a>
              </h2>
              <p class="score">
                {{ '★'.repeat(r.score) }}
                <span>{{ r.score }} / 5</span>
              </p>
              <p class="review-text">{{ r.review || 'No notes added.' }}</p>
              <p class="field-help">Updated {{ r.updatedAt | date: 'mediumDate' }}</p>
            </div>
            <a class="button subtle" [routerLink]="['/movies', r.movie.id]">Edit rating ↗</a>
          </article>
        }
        @if (!ratings().length) {
          <empty-state
            title="Every opinion opens a door."
            message="Rate a film you have seen to start shaping your recommendations."
          >
            <a routerLink="/" class="button primary">Find a film</a>
          </empty-state>
        }
      }
      @if (kind === 'shares') {
        @for (s of shares(); track s.id) {
          <section class="panel shared-row">
            <a [routerLink]="['/share', s.id]">
              <span class="eyebrow">SHARED {{ s.createdAt | date: 'mediumDate' }}</span>
              <h2>{{ s.movie.title }}</h2>
            </a>
            <label>
              Your note
              <textarea [(ngModel)]="s.note" maxlength="500" rows="2"></textarea>
            </label>
            <div class="button-row">
              <button class="button primary" (click)="copy(s)">Copy link</button>
              <button class="button subtle" [disabled]="busy() || !s.note.trim()" (click)="save(s)">
                Save note
              </button>
              <button class="button danger" [disabled]="busy()" (click)="revoke(s)">
                Revoke link
              </button>
            </div>
          </section>
        }
        @if (!shares().length) {
          <empty-state
            title="Found a film for a friend?"
            message="Create a share link from any movie's details page."
          >
            <a routerLink="/" class="button primary">Discover films</a>
          </empty-state>
        }
      }
    }
  `,
})
export class CollectionsPage {
  api = inject(Api);
  route = inject(ActivatedRoute);
  kind = this.route.snapshot.routeConfig?.path;
  movies = signal<Movie[]>([]);
  ratings = signal<Rating[]>([]);
  shares = signal<Share[]>([]);
  loading = signal(true);
  busy = signal(false);
  error = signal('');
  message = signal('');
  constructor() {
    void this.load();
  }
  async load() {
    this.loading.set(true);
    try {
      if (this.kind === 'watchlist')
        this.movies.set(await this.api.request<Movie[]>('/api/users/me/watchlist'));
      else if (this.kind === 'ratings')
        this.ratings.set(await this.api.request<Rating[]>('/api/users/me/ratings'));
      else this.shares.set(await this.api.request<Share[]>('/api/recommendations/shares'));
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.loading.set(false);
    }
  }
  async action(fn: () => Promise<void>, message: string) {
    this.busy.set(true);
    this.error.set('');
    try {
      await fn();
      this.message.set(message);
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
  removeWatch(movie: Movie) {
    void this.action(async () => {
      await this.api.request('/api/users/me/watchlist/' + movie.id, 'DELETE');
      this.movies.update((ms) => ms.filter((m) => m.id !== movie.id));
    }, 'Removed from your watchlist.');
  }
  save(share: Share) {
    void this.action(async () => {
      await this.api.request('/api/recommendations/shares/' + share.id, 'PUT', {
        note: share.note,
      });
    }, 'Your note has been updated.');
  }
  revoke(share: Share) {
    if (!confirm('Revoke this share link? Your friend will no longer be able to open it.')) return;
    void this.action(async () => {
      await this.api.request('/api/recommendations/shares/' + share.id, 'DELETE');
      this.shares.update((ss) => ss.filter((s) => s.id !== share.id));
    }, 'Share link revoked.');
  }
  async copy(share: Share) {
    try {
      await navigator.clipboard.writeText(location.origin + '/share/' + share.id);
      this.message.set('Link copied.');
    } catch {
      this.message.set('Open the shared pick and copy the address from your browser.');
    }
  }
  fallback(e: Event) {
    (e.target as HTMLImageElement).src = '/art/default.svg';
  }
}

@Component({
  standalone: true,
  imports: [RouterLink, MovieCard],
  template: `
    <a class="back-link" routerLink="/">← Discover films</a>
    <header class="page-heading">
      <div>
        <span class="eyebrow">A PICK WORTH PASSING ON</span>
        <h1>A film for your evening.</h1>
      </div>
    </header>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (share(); as pick) {
      <div class="shared-detail">
        <movie-card [movie]="pick.movie" />
        <section>
          <span class="eyebrow">A NOTE FOR YOU</span>
          <blockquote>{{ pick.note }}</blockquote>
          <a class="button primary" [routerLink]="['/movies', pick.movie.id]">
            Explore {{ pick.movie.title }} ↗
          </a>
        </section>
      </div>
    }
  `,
})
export class SharedPage {
  api = inject(Api);
  route = inject(ActivatedRoute);
  share = signal<Share | null>(null);
  error = signal('');
  constructor() {
    void this.api
      .request<Share>(
        '/api/recommendations/shares/' +
          encodeURIComponent(this.route.snapshot.paramMap.get('id')!),
      )
      .then((s) => this.share.set(s))
      .catch(() => this.error.set('This share link is unavailable or has been revoked.'));
  }
}
