import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-top">
      <span class="eyebrow">CREDITS</span>
      <a routerLink="/" class="text-link">Back to Neo4flix ↗</a>
    </div>
    <header class="page-heading">
      <div>
        <h1>The stories behind the screen.</h1>
        <p>Movie data and artwork credits.</p>
      </div>
    </header>
    <section class="panel">
      <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer">
        <img class="tmdb-logo" src="/art/tmdb-logo.svg" alt="The Movie Database (TMDB)" />
      </a>
      <h2>Movie information and posters</h2>
      <p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
      <p>
        TMDB community scores are out of ten. Your Neo4flix ratings are out of five and drive your
        personal recommendations.
      </p>
      <p>
        Movie posters belong to their respective rights holders. Neo4flix is an educational project.
      </p>
      <a
        class="text-link"
        href="https://www.themoviedb.org/"
        target="_blank"
        rel="noopener noreferrer"
      >
        Visit TMDB ↗
      </a>
    </section>
  `,
})
export class CreditsPage {}
