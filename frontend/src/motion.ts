import { AfterViewInit, DestroyRef, Directive, ElementRef, inject, NgZone } from '@angular/core';

/** One-shot entrances for live Angular content. Observers end when routes/cards do. */
@Directive({ selector: '[filmReveal]', standalone: true })
export class FilmReveal implements AfterViewInit {
  private element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private destroy = inject(DestroyRef);
  private zone = inject(NgZone);
  ngAfterViewInit() {
    this.zone.runOutsideAngular(() => {
      const reduced = matchMedia('(prefers-reduced-motion: reduce)');
      if (reduced.matches || !('IntersectionObserver' in window)) return;
      const element = this.element;
      // Keep already-visible cards visible. Only content below the fold enters.
      if (element.getBoundingClientRect().top < innerHeight) return;
      element.classList.add('film-waiting');
      const settle = () => {
        element.classList.remove('film-waiting');
        observer.disconnect();
      };
      const observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((e) => e.isIntersecting)) settle();
        },
        { rootMargin: '0px 0px 40px 0px', threshold: 0.01 },
      );
      observer.observe(element);
      element.addEventListener('focusin', settle);
      const motionChanged = () => {
        if (reduced.matches) settle();
      };
      reduced.addEventListener('change', motionChanged);
      this.destroy.onDestroy(() => {
        observer.disconnect();
        element.removeEventListener('focusin', settle);
        reduced.removeEventListener('change', motionChanged);
      });
    });
  }
}
