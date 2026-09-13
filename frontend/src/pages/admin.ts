import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Movie } from '../api';

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
    void this.load();
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
