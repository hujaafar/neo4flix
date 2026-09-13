import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Api, Movie } from '../api';
import { EmptyState, MovieCard } from '../ui';
import { CinemaFeature } from '../feature';

@Component({
  standalone: true,
  imports: [FormsModule, MovieCard, EmptyState, CinemaFeature],
  template: `
    <div class="page-top">
      <span class="eyebrow">
        {{ personal ? 'CURATED BY YOUR TASTE' : 'THE NEO4FLIX COLLECTION' }}
      </span>
      <span class="top-note">
        A little discovery goes a long way
        <span aria-hidden="true">✧</span>
      </span>
    </div>

    @if (!personal && !q && !genre && page === 0 && !from && !to && featured()) {
      <cinema-feature [movie]="featured()!" />
    }
    <header class="page-heading">
      <div>
        <h1>{{ personal ? 'Made for your kind of movie night.' : 'Something worth watching.' }}</h1>
        <p>
          {{
            personal
              ? 'The more you rate, the closer we get to your next favorite.'
              : 'Explore the collection. Follow your curiosity.'
          }}
        </p>
      </div>
      <span class="edition">
        {{ personal ? 'FOR YOU' : 'NOW SHOWING' }}
        <b>{{ movies().length.toString().padStart(2, '0') }}</b>
      </span>
    </header>
    <section id="film-filters" tabindex="-1" aria-label="Movie filters" class="filter-section">
      <div class="search-row">
        @if (!personal) {
          <label class="search">
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Search movies"
              [(ngModel)]="q"
              (ngModelChange)="search()"
              placeholder="Search titles, genres, or years…"
            />
          </label>
        }
        <button
          class="button subtle"
          (click)="showDates = !showDates"
          [attr.aria-expanded]="showDates"
        >
          Release dates
          <span aria-hidden="true">⌄</span>
        </button>
        @if (personal) {
          <button class="button subtle" (click)="loadDismissed()">Hidden picks</button>
        }
      </div>
      <div class="genre-list" aria-label="Filter by genre">
        <button [class.selected]="!genre" (click)="setGenre('')">All films</button>
        @for (g of genres(); track g) {
          <button [class.selected]="genre === g" (click)="setGenre(g)">{{ g }}</button>
        }
      </div>
      @if (showDates) {
        <div class="date-row">
          <label>
            Released from
            <input type="date" [(ngModel)]="from" (change)="reload()" />
          </label>
          <label>
            Released through
            <input type="date" [(ngModel)]="to" (change)="reload()" />
          </label>
          <button class="button subtle" (click)="clearDates()">Clear dates</button>
        </div>
      }
    </section>
    @if (error()) {
      <div class="error" role="alert">
        {{ error() }}
        <button (click)="load()">Try again</button>
      </div>
    }
    <div class="section-line">
      <h2>
        {{
          personal
            ? 'Your next great watch'
            : q
              ? 'Search results'
              : genre || 'Explore the collection'
        }}
      </h2>
      <span>
        {{ loading() ? 'Finding films…' : movies().length + ' films' }}
        <span aria-hidden="true">↙</span>
      </span>
    </div>
    @if (loading()) {
      <div class="skeleton-grid" aria-label="Loading movies" aria-busy="true">
        @for (i of [1, 2, 3, 4, 5, 6]; track i) {
          <div></div>
        }
      </div>
    } @else {
      <div class="movie-grid">
        @for (movie of movies(); track movie.id) {
          <movie-card [movie]="movie" [removable]="personal" (remove)="dismiss($event)" />
        }
      </div>
      @if (!movies().length && !error()) {
        <empty-state
          title="A different scene, perhaps?"
          message="Try another genre or a wider release date range. Films you have rated are excluded from recommendations."
        >
          <button class="button primary" (click)="reset()">Reset filters</button>
        </empty-state>
      }
    }
    @if (!personal && (page > 0 || movies().length === 24)) {
      <div class="pagination">
        <button class="button subtle" [disabled]="page === 0 || loading()" (click)="changePage(-1)">
          ← Previous
        </button>
        <span>Page {{ page + 1 }}</span>
        <button
          class="button subtle"
          [disabled]="movies().length < 24 || loading()"
          (click)="changePage(1)"
        >
          Next →
        </button>
      </div>
    }
    @if (showHidden()) {
      <section class="panel">
        <div class="section-line">
          <h2>Hidden picks</h2>
          <button class="button subtle" (click)="showHidden.set(false)">Close</button>
        </div>
        @for (m of hidden(); track m.id) {
          <div class="list-row">
            <span>{{ m.title }}</span>
            <button class="button subtle" (click)="restore(m)">Show again</button>
          </div>
        }
        @if (!hidden().length) {
          <p class="muted">No hidden films.</p>
        }
      </section>
    }
    <footer class="page-footer">
      <span>Every great film starts a conversation.</span>
      <span>
        NEO4FLIX
        <span aria-hidden="true">✳</span>
      </span>
    </footer>
  `,
})
export class BrowsePage {
  api = inject(Api);
  route = inject(ActivatedRoute);
  personal = this.route.snapshot.routeConfig?.path === 'recommendations';
  movies = signal<Movie[]>([]);
  genres = signal<string[]>([]);
  featured = signal<Movie | null>(null);
  hidden = signal<Movie[]>([]);
  showHidden = signal(false);
  loading = signal(true);
  error = signal('');
  q = '';
  genre = '';
  from = '';
  to = '';
  showDates = false;
  page = 0;
  private generation = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  constructor() {
    if (!this.personal) this.genre = this.route.snapshot.queryParamMap.get('genre') || '';
    void this.load();
    void this.api
      .request<string[]>('/api/movies/genres')
      .then((x) => this.genres.set(x))
      .catch(() => {});
  }
  async load() {
    const id = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    const params = new URLSearchParams({ genre: this.genre });
    if (this.from) params.set('from', this.from);
    if (this.to) params.set('to', this.to);
    if (!this.personal) {
      params.set('q', this.q);
      params.set('page', String(this.page));
    }
    try {
      const rows = await this.api.request<Movie[]>(
        (this.personal ? '/api/recommendations' : '/api/movies') + '?' + params,
      );
      if (id !== this.generation) return;
      this.movies.set(rows);
      if (!this.personal && !this.q)
        this.featured.set(rows.find((m) => m.id === 'interstellar') || null);
    } catch (e) {
      if (id === this.generation) this.error.set((e as Error).message);
    } finally {
      if (id === this.generation) this.loading.set(false);
    }
  }
  search() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.reload(), 300);
  }
  reload() {
    this.page = 0;
    void this.load();
  }
  setGenre(g: string) {
    this.genre = g;
    this.reload();
  }
  clearDates() {
    this.from = '';
    this.to = '';
    this.reload();
  }
  reset() {
    this.q = '';
    this.genre = '';
    this.from = '';
    this.to = '';
    this.reload();
  }
  changePage(delta: number) {
    this.page += delta;
    void this.load();
  }
  async dismiss(movie: Movie) {
    try {
      await this.api.request('/api/recommendations/dismissed/' + movie.id, 'PUT');
      this.movies.update((rows) => rows.filter((m) => m.id !== movie.id));
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
  async loadDismissed() {
    try {
      this.hidden.set(await this.api.request<Movie[]>('/api/recommendations/dismissed'));
      this.showHidden.set(true);
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
  async restore(movie: Movie) {
    try {
      await this.api.request('/api/recommendations/dismissed/' + movie.id, 'DELETE');
      await this.loadDismissed();
      await this.load();
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
  ngOnDestroy() {
    clearTimeout(this.timer);
    this.generation++;
  }
}
