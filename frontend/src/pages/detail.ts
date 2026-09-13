import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api, ApiError, Movie, Rating, Share } from '../api';
import { MovieCard } from '../ui';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, MovieCard],
  template: `
    <a routerLink="/" class="back-link">← Back to discovery</a>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (movie(); as film) {
      <section class="film-detail">
        <img
          class="detail-poster"
          [src]="'/art/' + film.artwork + '.svg'"
          [alt]="film.title + ' illustrated artwork'"
          (error)="fallback($event)"
        />
        <div class="film-copy">
          <span class="eyebrow">{{ film.genres.join(' / ') }}</span>
          <h1>{{ film.title }}</h1>
          <div class="film-facts">
            <span>{{ film.year }}</span>
            <span>{{ film.runtime }} min</span>
            @if (film.ratingCount) {
              <span class="score">
                ★ {{ film.averageRating | number: '1.1-1' }}
                <small>({{ film.ratingCount }} ratings)</small>
              </span>
            } @else {
              <span>No ratings yet</span>
            }
          </div>
          <p class="synopsis">{{ film.overview }}</p>
          <dl>
            <div>
              <dt>Directed by</dt>
              <dd>{{ film.director }}</dd>
            </div>
            <div>
              <dt>Release date</dt>
              <dd>{{ film.releaseDate | date: 'longDate' }}</dd>
            </div>
          </dl>
          <div class="button-row">
            <button class="button primary" [disabled]="busy()" (click)="toggleWatch()">
              {{ watched() ? '✓ In your watchlist' : '+ Add to watchlist' }}
            </button>
            <button class="button subtle" (click)="showShare = !showShare">
              Share this film ↗
            </button>
          </div>
        </div>
      </section>
      <div class="detail-panels">
        <section class="panel rating-panel">
          <span class="eyebrow">YOUR TAKE</span>
          <h2>{{ existing() ? 'Your rating' : 'How did it land?' }}</h2>
          <p class="muted">A rating today helps find your next favorite.</p>
          <form (ngSubmit)="rate()">
            <fieldset class="star-picker">
              <legend>Your rating out of five</legend>
              @for (n of [1, 2, 3, 4, 5]; track n) {
                <label [class.filled]="score >= n">
                  <input
                    type="radio"
                    name="score"
                    [value]="n"
                    [(ngModel)]="score"
                    required
                    [attr.aria-label]="n + ' out of 5 stars'"
                  />
                  <span aria-hidden="true">★</span>
                </label>
              }
              <span class="rating-label">{{ score ? score + ' / 5' : 'Choose a rating' }}</span>
            </fieldset>
            <label>
              Your notes
              <span class="muted">(optional, private)</span>
              <textarea
                name="review"
                [(ngModel)]="review"
                maxlength="1000"
                rows="3"
                placeholder="What stayed with you?"
              ></textarea>
            </label>
            <div class="button-row">
              <button class="button primary" [disabled]="!score || busy()">
                {{ existing() ? 'Update rating' : 'Save rating' }}
              </button>
              @if (existing()) {
                <button
                  class="button subtle"
                  type="button"
                  [disabled]="busy()"
                  (click)="removeRating()"
                >
                  Delete rating
                </button>
              }
            </div>
          </form>
        </section>
        <aside class="panel taste-note">
          <span class="large-spark" aria-hidden="true">✧</span>
          <h2>A little more you.</h2>
          <p>
            Your ratings connect the dots between films, genres, and viewers who share your taste.
          </p>
          <a class="text-link" routerLink="/recommendations">See your recommendations →</a>
        </aside>
      </div>
      @if (showShare) {
        <section class="panel share-panel">
          <h2>Good films are better shared.</h2>
          <form (ngSubmit)="share()">
            <label>
              A note for your friend
              <textarea name="note" [(ngModel)]="note" required maxlength="500" rows="2"></textarea>
            </label>
            <button class="button primary" [disabled]="busy() || !note.trim()">
              Create a share link
            </button>
          </form>
          @if (shareUrl()) {
            <label for="share-link">Share link</label>
            <div class="copy-row">
              <input id="share-link" readonly [value]="shareUrl()" />
              <button class="button subtle" (click)="copy()">Copy</button>
            </div>
            <p class="field-help">
              Anyone with this link can view the pick after signing in. Manage or revoke it in
              Shared picks.
            </p>
          }
        </section>
      }
      @if (message()) {
        <p class="success" role="status">{{ message() }}</p>
      }
      @if (related().length) {
        <div class="section-line">
          <h2>Stay in this world</h2>
          <span>Related films</span>
        </div>
        <div class="movie-grid">
          @for (m of related(); track m.id) {
            <movie-card [movie]="m" />
          }
        </div>
      }
    } @else if (!error()) {
      <p class="loading" aria-busy="true">Finding your film…</p>
    }
  `,
})
export class DetailPage {
  api = inject(Api);
  route = inject(ActivatedRoute);
  movie = signal<Movie | null>(null);
  related = signal<Movie[]>([]);
  existing = signal(false);
  watched = signal(false);
  error = signal('');
  message = signal('');
  busy = signal(false);
  shareUrl = signal('');
  score = 0;
  review = '';
  note = 'Thought you might love this one.';
  showShare = false;
  private generation = 0;
  subscription = this.route.paramMap.subscribe((p) => void this.load(p.get('id')!));
  async load(id: string) {
    const request = ++this.generation;
    this.movie.set(null);
    this.error.set('');
    this.message.set('');
    this.shareUrl.set('');
    this.score = 0;
    this.review = '';
    this.existing.set(false);
    this.showShare = false;
    try {
      const [movie, rating, watchlist, related] = await Promise.all([
        this.api.request<Movie>('/api/movies/' + encodeURIComponent(id)),
        this.api.request<Rating | null>('/api/ratings/me/' + encodeURIComponent(id)).catch((e) => {
          if (e instanceof ApiError && e.status === 404) return null;
          throw e;
        }),
        this.api.request<Movie[]>('/api/users/me/watchlist'),
        this.api.request<Movie[]>('/api/movies/' + encodeURIComponent(id) + '/related'),
      ]);
      if (request !== this.generation) return;
      if (rating) {
        this.score = rating.score;
        this.review = rating.review;
        this.existing.set(true);
      }
      this.watched.set(watchlist.some((m) => m.id === id));
      this.related.set(related);
      this.movie.set(movie);
    } catch (e) {
      if (request === this.generation) this.error.set((e as Error).message);
    }
  }
  async action(fn: () => Promise<void>, message: string) {
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
  rate() {
    void this.action(async () => {
      await this.api.request('/api/ratings/me/' + this.movie()!.id, 'PUT', {
        score: this.score,
        review: this.review,
      });
      this.existing.set(true);
      this.movie.set(await this.api.request<Movie>('/api/movies/' + this.movie()!.id));
    }, 'Rating saved. Your recommendations are ready for another look.');
  }
  removeRating() {
    void this.action(async () => {
      await this.api.request('/api/ratings/me/' + this.movie()!.id, 'DELETE');
      this.existing.set(false);
      this.score = 0;
      this.review = '';
      this.movie.set(await this.api.request<Movie>('/api/movies/' + this.movie()!.id));
    }, 'Rating removed.');
  }
  toggleWatch() {
    void this.action(async () => {
      await this.api.request(
        '/api/users/me/watchlist/' + this.movie()!.id,
        this.watched() ? 'DELETE' : 'PUT',
      );
      this.watched.update((v) => !v);
    }, 'Watchlist updated.');
  }
  share() {
    void this.action(async () => {
      const share = await this.api.request<Share>('/api/recommendations/shares', 'POST', {
        movieId: this.movie()!.id,
        note: this.note,
      });
      this.shareUrl.set(location.origin + '/share/' + share.id);
    }, 'Your share link is ready.');
  }
  async copy() {
    try {
      await navigator.clipboard.writeText(this.shareUrl());
      this.message.set('Link copied.');
    } catch {
      this.message.set('Select and copy the link above.');
    }
  }
  fallback(e: Event) {
    (e.target as HTMLImageElement).src = '/art/default.svg';
  }
  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.generation++;
  }
}
