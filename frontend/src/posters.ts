import { Movie } from './api';

/** External images can only use TMDB's known CDN and a validated image path. */
export function moviePoster(movie: Movie): string {
  if (movie.tmdbId) {
    return /^\/[A-Za-z0-9_-]{1,100}\.(jpg|png|webp)$/.test(movie.posterPath || '')
      ? 'https://image.tmdb.org/t/p/w500' + movie.posterPath
      : '/art/default.svg';
  }
  return /^[a-z-]{1,50}$/.test(movie.artwork)
    ? '/art/' + movie.artwork + '.svg'
    : '/art/default.svg';
}
