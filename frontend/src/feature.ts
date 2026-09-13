import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  NgZone,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Movie } from './api';

@Component({
  selector: 'cinema-feature',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="reel-world app-reel" aria-label="Your cinema in motion">
      <div class="reel-stage">
        <img class="reel-landscape" src="/experience/assets/cinema-world.webp" alt="" />
        <picture class="reel-poster" aria-hidden="true">
          <source media="(max-width:700px)" srcset="/experience/assets/reel-mobile.webp" />
          <img src="/experience/assets/reel-desktop.webp" alt="" />
        </picture>
        <div class="reel-copy reel-copy-first">
          <span class="reel-kicker">WELCOME TO YOUR CINEMA</span>
          <h2>
            Every film.
            <em>A new world.</em>
          </h2>
          <p>Find the stories that stay with you.</p>
        </div>
        <div class="reel-surface"></div>
        <div class="reel-copy reel-copy-second" inert>
          <h2>
            A little further
            <br />
            from the everyday.
          </h2>
          <p>{{ movie().title }} · {{ movie().year }} · {{ movie().runtime }} min</p>
          <a [routerLink]="['/movies', movie().id]">
            Explore the film
            <span aria-hidden="true">↗</span>
          </a>
        </div>
        <div class="reel-controls">
          <a class="reel-skip" href="#film-filters" data-reel-skip>
            Browse films
            <span aria-hidden="true">↗</span>
          </a>
          <button class="reel-pause" type="button" data-reel-pause disabled aria-pressed="false">
            Pause motion
          </button>
        </div>
      </div>
    </section>
  `,
})
export class CinemaFeature implements AfterViewInit {
  movie = input.required<Movie>();
  private element = inject<ElementRef<HTMLElement>>(ElementRef);
  private zone = inject(NgZone);
  private destroy = inject(DestroyRef);
  private disposed = false;
  private scene: { destroy: () => void } | undefined;
  constructor() {
    this.destroy.onDestroy(() => {
      this.disposed = true;
      this.scene?.destroy();
    });
  }
  ngAfterViewInit() {
    this.zone.runOutsideAngular(async () => {
      try {
        const modulePath = '/experience/reel-world.js';
        const { mountCinemaWorld } = await import(/* @vite-ignore */ modulePath);
        if (!this.disposed)
          this.scene = mountCinemaWorld(this.element.nativeElement.querySelector('.reel-world'), {
            target: '#film-filters',
          });
      } catch {
        this.element.nativeElement.querySelector('.reel-world')?.classList.add('reel-fallback');
      }
    });
  }
}
