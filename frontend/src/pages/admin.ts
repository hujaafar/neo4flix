import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Movie } from '../api';
import { moviePoster } from '../posters';
interface ImportJob {
  id: string;
  state: 'FETCHING' | 'READY' | 'FAILED' | 'APPLIED';
  error: string;
  films: Movie[];
  result: { imported?: number; removed?: number; backupId?: string };
}

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-top"><span class="eyebrow">CATALOGUE MANAGEMENT</span></div>
    <header class="page-heading">
      <div>
        <h1>Make room for a good story.</h1>
        <p>Create and update the film collection.</p>
      </div>
    </header>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (message()) {
      <p class="success" role="status">{{ message() }}</p>
    }
    <section class="panel tmdb-panel" aria-labelledby="tmdb-heading">
      <span class="eyebrow">REAL MOVIE DATA</span>
      <h2 id="tmdb-heading">Bring the world of film into Neo4flix.</h2>
      <p>
        Import titles, posters, release dates, genres, directors and community scores from TMDB.
      </p>
      @if (tmdbConfigured()) {
        <form (ngSubmit)="fetchImport()" #importForm="ngForm">
          <div class="form-row">
            <label>
              Collection
              <select name="selection" [(ngModel)]="selection">
                <option value="top_rated">Top rated</option>
                <option value="popular">Popular now</option>
              </select>
            </label>
            <label>
              Number of films
              <input name="count" type="number" [(ngModel)]="count" min="1" max="50" required />
            </label>
          </div>
          <button class="button primary" [disabled]="importBusy() || busy() || importForm.invalid">
            {{ importBusy() ? 'Fetching movie data…' : 'Fetch a preview' }}
          </button>
        </form>
      } @else {
        <p class="field-help">
          TMDB import is awaiting setup. Configure the private API Read Access Token on the server.
        </p>
      }
      @if (job(); as preview) {
        @if (preview.state === 'FETCHING') {
          <p role="status" aria-busy="true">
            Fetching complete film information. Your current catalogue stays available.
          </p>
        }
        @if (preview.state === 'READY') {
          <p class="success" role="status">{{ preview.films.length }} films are ready to import.</p>
          <div class="tmdb-preview">
            @for (film of preview.films; track film.id) {
              <figure>
                <img
                  [src]="poster(film)"
                  [alt]="film.title + ' poster'"
                  loading="lazy"
                  (error)="fallback($event)"
                />
                <figcaption>{{ film.title }} · {{ film.year }}</figcaption>
              </figure>
            }
          </div>
          <p>
            Replacement removes the current films and their ratings, watchlist entries and shared
            links. User accounts stay intact. A private catalogue backup is saved first.
          </p>
          <button
            class="button danger"
            [disabled]="importBusy() || busy()"
            (click)="replaceCatalogue()"
          >
            Replace catalogue with these films
          </button>
        }
      }
      <p class="field-help"><a href="/credits">TMDB data and poster credits ↗</a></p>
    </section>
    <div class="admin-grid">
      <section class="panel">
        <div class="section-line">
          <h2>{{ editing ? 'Edit film' : 'Add a film' }}</h2>
          @if (editing) {
            <button class="button subtle" (click)="reset()">New film</button>
          }
        </div>
        <form (ngSubmit)="save()" #form="ngForm">
          <label>
            Title
            <input name="title" [(ngModel)]="title" required maxlength="180" />
          </label>
          <div class="form-row">
            <label>
              Release date
              <input name="release" [(ngModel)]="releaseDate" required type="date" />
            </label>
            <label>
              Runtime in minutes
              <input
                name="runtime"
                [(ngModel)]="runtime"
                required
                type="number"
                min="1"
                max="600"
              />
            </label>
          </div>
          <label>
            Genres, separated by commas
            <input name="genres" [(ngModel)]="genres" required placeholder="Drama, Adventure" />
          </label>
          <label>
            Director
            <input name="director" [(ngModel)]="director" required maxlength="120" />
          </label>
          <label>
            Synopsis
            <textarea
              name="overview"
              [(ngModel)]="overview"
              required
              maxlength="2500"
              rows="4"
            ></textarea>
          </label>
          <label>
            Artwork
            <select name="artwork" [(ngModel)]="artwork">
              @for (a of artworks; track a) {
                <option [value]="a">{{ a === 'default' ? 'Neo4flix original' : a }}</option>
              }
            </select>
          </label>
          <button class="button primary" [disabled]="busy() || form.invalid">
            {{ editing ? 'Save changes' : 'Add film' }}
          </button>
        </form>
      </section>
      <section class="panel">
        <h2>Films in the collection</h2>
        @for (m of movies(); track m.id) {
          <div class="admin-row">
            <span>
              <strong>{{ m.title }}</strong>
              <small>{{ m.year }}</small>
            </span>
            <button class="button subtle" (click)="edit(m)">Edit</button>
            <button class="button danger" [disabled]="busy()" (click)="remove(m)">Delete</button>
          </div>
        }
        <div class="pagination">
          <button class="button subtle" [disabled]="page === 0" (click)="paginate(-1)">
            Previous
          </button>
          <span>{{ page + 1 }}</span>
          <button class="button subtle" [disabled]="movies().length < 100" (click)="paginate(1)">
            Next
          </button>
        </div>
      </section>
    </div>
  `,
})
export class AdminPage {
  api = inject(Api);
  poster = moviePoster;
  tmdbConfigured = signal(false);
  importBusy = signal(false);
  job = signal<ImportJob | null>(null);
  selection = 'popular';
  count = 50;
  private polling: ReturnType<typeof setTimeout> | undefined;
  private disposed = false;
  private destroy = inject(DestroyRef);
  movies = signal<Movie[]>([]);
  error = signal('');
  message = signal('');
  busy = signal(false);
  editing = '';
  title = '';
  releaseDate = '';
  genres = '';
  director = '';
  overview = '';
  runtime = 120;
  artwork = 'default';
  page = 0;
  artworks = [
    'default',
    'interstellar',
    'arrival',
    'dune',
    'blade-runner',
    'inception',
    'matrix',
    'grand-budapest',
    'whiplash',
    'parasite',
    'spirited-away',
    'soul',
    'everything-everywhere',
    'fantastic-fox',
    'moonlight',
    'la-la-land',
    'truman-show',
    'oppenheimer',
    'batman',
  ];
  constructor() {
    this.destroy.onDestroy(() => {
      this.disposed = true;
      clearTimeout(this.polling);
    });
    void this.load();
    void this.api
      .request<{ configured: boolean }>('/api/movies/tmdb/status')
      .then((r) => this.tmdbConfigured.set(r.configured))
      .catch((e) => this.error.set(e.message));
  }
  fallback(event: Event) {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/art/default.svg')) image.src = '/art/default.svg';
  }
  async fetchImport() {
    this.error.set('');
    this.message.set('');
    this.importBusy.set(true);
    this.job.set(null);
    try {
      const job = await this.api.request<ImportJob>('/api/movies/tmdb/imports', 'POST', {
        selection: this.selection,
        count: this.count,
      });
      if (this.disposed) return;
      this.job.set(job);
      await this.pollImport(job.id);
    } catch (e) {
      this.error.set((e as Error).message);
      this.importBusy.set(false);
    }
  }
  private async pollImport(id: string) {
    if (this.disposed) return;
    try {
      const job = await this.api.request<ImportJob>('/api/movies/tmdb/imports/' + id);
      if (this.disposed) return;
      this.job.set(job);
      if (job.state === 'FETCHING')
        this.polling = setTimeout(() => {
          void this.pollImport(id);
        }, 1500);
      else {
        this.importBusy.set(false);
        if (job.state === 'FAILED') this.error.set(job.error);
      }
    } catch (e) {
      this.error.set((e as Error).message);
      this.importBusy.set(false);
    }
  }
  async replaceCatalogue() {
    const preview = this.job();
    if (
      !preview ||
      preview.state !== 'READY' ||
      !confirm(
        'Replace the current catalogue and its movie-related ratings, watchlists, and shares with ' +
          preview.films.length +
          ' TMDB films? A private backup will be saved.',
      )
    )
      return;
    this.importBusy.set(true);
    this.error.set('');
    try {
      const result = await this.api.request<ImportJob>(
        '/api/movies/tmdb/imports/' + preview.id + '/replace',
        'POST',
      );
      this.job.set(result);
      this.message.set(
        result.result.imported + ' real films imported. Previous catalogue backed up.',
      );
      this.page = 0;
      this.reset();
      await this.load();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.importBusy.set(false);
    }
  }
  async load() {
    try {
      this.movies.set(await this.api.request<Movie[]>('/api/movies?size=100&page=' + this.page));
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }
  reset() {
    this.editing = '';
    this.title = '';
    this.releaseDate = '';
    this.genres = '';
    this.director = '';
    this.overview = '';
    this.runtime = 120;
    this.artwork = 'default';
  }
  edit(m: Movie) {
    this.editing = m.id;
    this.title = m.title;
    this.releaseDate = m.releaseDate;
    this.genres = m.genres.join(', ');
    this.director = m.director;
    this.overview = m.overview;
    this.runtime = m.runtime;
    this.artwork = m.artwork;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  async save() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.api.request(
        '/api/movies' + (this.editing ? '/' + this.editing : ''),
        this.editing ? 'PUT' : 'POST',
        {
          title: this.title,
          releaseDate: this.releaseDate,
          genres: this.genres
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean),
          director: this.director,
          overview: this.overview,
          runtime: this.runtime,
          artwork: this.artwork,
        },
      );
      this.message.set('Film saved.');
      this.reset();
      await this.load();
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
  async remove(m: Movie) {
    if (
      !confirm(
        'Delete ' + m.title + ' and its ratings, watchlist entries, and recommendation links?',
      )
    )
      return;
    this.busy.set(true);
    try {
      await this.api.request('/api/movies/' + m.id, 'DELETE');
      await this.load();
      if (this.editing === m.id) this.reset();
      this.message.set('Film deleted.');
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
  paginate(delta: number) {
    this.page += delta;
    void this.load();
  }
}
