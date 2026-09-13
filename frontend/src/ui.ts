import { Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Movie } from './api';
import { FilmReveal } from './motion';

@Component({
  selector: 'movie-card',
  standalone: true,
  imports: [RouterLink, DecimalPipe, FilmReveal],
  template: `
    <article class="movie-card" filmReveal>
      <a
        class="poster-link"
        [routerLink]="['/movies', movie().id]"
        [attr.aria-label]="'View ' + movie().title"
      >
        <img
          [src]="'/art/' + movie().artwork + '.svg'"
          [alt]="movie().title + ' illustrated artwork'"
          loading="lazy"
          (error)="fallback($event)"
        />
        <span class="poster-action">
          Explore film
          <span aria-hidden="true">↗</span>
        </span>
      </a>
      @if (removable()) {
        <button
          class="card-remove"
          type="button"
          [attr.aria-label]="'Remove ' + movie().title"
          (click)="remove.emit(movie())"
        >
          ×
        </button>
      }
      <div class="movie-meta">
        <span>{{ movie().year }} · {{ movie().genres[0] }}</span>
        @if (movie().ratingCount) {
          <span class="score">★ {{ movie().averageRating | number: '1.1-1' }}</span>
        }
      </div>
      <h3>
        <a [routerLink]="['/movies', movie().id]">{{ movie().title }}</a>
      </h3>
      @if (movie().reason) {
        <p class="reason">{{ movie().reason }}</p>
      }
    </article>
  `,
})
export class MovieCard {
  movie = input.required<Movie>();
  removable = input(false);
  remove = output<Movie>();
  fallback(e: Event) {
    const image = e.target as HTMLImageElement;
    if (!image.src.endsWith('/art/default.svg')) image.src = '/art/default.svg';
  }
}

@Component({
  selector: 'empty-state',
  standalone: true,
  template: `
    <div class="empty-state">
      <span class="empty-mark" aria-hidden="true">◌</span>
      <h2>{{ title() }}</h2>
      <p>{{ message() }}</p>
      <ng-content />
    </div>
  `,
})
export class EmptyState {
  title = input('Nothing here yet');
  message = input('');
}
