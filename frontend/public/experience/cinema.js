/* Neo4flix-specific choreography. The upstream ScrollCraft files are unchanged. */
(() => {
  const root = document.documentElement;
  const reelHost = document.querySelector('.reel-world');
  let cinemaWorld;
  let catalogue = [];
  const poster = (film) =>
    film.tmdbId && /^\/[A-Za-z0-9_-]{1,100}\.(jpg|png|webp)$/.test(film.posterPath || '')
      ? 'https://image.tmdb.org/t/p/w500' + film.posterPath
      : '/art/' + (/^[a-z-]{1,50}$/.test(film.artwork || '') ? film.artwork : 'default') + '.svg';
  import('./reel-world.js')
    .then(({ mountCinemaWorld }) => {
      const world = (cinemaWorld = mountCinemaWorld(reelHost, { posters: catalogue.map(poster) }));
      window.addEventListener('pagehide', (event) => {
        if (!event.persisted) world.destroy();
      });
    })
    .catch(() => reelHost.classList.add('reel-fallback'));
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const compact = matchMedia('(max-width: 860px)');
  const collection = document.querySelector('.collection');
  let engine;
  const syncMode = () => {
    const staticMode = reduced.matches || compact.matches;
    root.classList.toggle('static-motion', staticMode);
    // Geometry is CSS-owned on phones; the unchanged engine can keep reading
    // progress while its decorative transforms are suppressed.
    if (engine) engine.layout();
  };
  syncMode();
  if (window.ScrollCraft) {
    try {
      engine = window.ScrollCraft.mount(document.querySelector('main'));
      root.classList.add('motion-ready');
    } catch (error) {
      root.classList.remove('motion-ready');
      root.classList.add('static-motion');
      console.error(
        'Cinema motion could not start; the static collection remains available.',
        error,
      );
    }
  }
  reduced.addEventListener('change', syncMode);
  compact.addEventListener('change', syncMode);

  // Expose the painted composition to the upstream verification harness.
  // This records actual transforms, not an unrelated progress counter.
  let reportFrame = 0;
  const report = () => {
    reportFrame = 0;
    collection.dataset.scVerifyState = [...collection.querySelectorAll('.print-art')]
      .map((el) => getComputedStyle(el).transform)
      .join('|');
    collection.dataset.scVerifyHold = String(
      root.classList.contains('static-motion') ||
        Number(getComputedStyle(collection).getPropertyValue('--sc-p')) >= 0.58,
    );
  };
  const scheduleReport = () => {
    if (!reportFrame) reportFrame = requestAnimationFrame(report);
  };
  addEventListener('scroll', scheduleReport, { passive: true });
  addEventListener('resize', scheduleReport, { passive: true });
  report();

  // Keyboard navigation must reveal each print before focus reaches it. This
  // only settles the stack; it never captures input or changes the tab order.
  collection.addEventListener('focusin', () => {
    if (compact.matches || reduced.matches || !engine) return;
    const target = collection.offsetTop + (collection.offsetHeight - innerHeight) * 0.64;
    window.scrollTo({ top: target, behavior: 'instant' });
    engine.read();
  });

  const picker = document.querySelector('.genre-picker');
  const destination = document.querySelector('#genre-destination');
  const genreImage = document.querySelector('#genre-image');
  const caption = document.querySelector('#genre-caption');
  const fallback = (event) => {
    if (!event.target.src.endsWith('/art/default.svg')) event.target.src = '/art/default.svg';
  };
  function pickGenre(genre) {
    const film = catalogue.find((m) => m.genres.includes(genre));
    if (!film) return;
    genreImage.src = poster(film);
    genreImage.alt = film.title + ' poster';
    const facts = document.createElement('span');
    facts.textContent = film.year + ' · ' + genre;
    caption.replaceChildren(document.createTextNode(film.title + ' '), facts);
    destination.href = '/?genre=' + encodeURIComponent(genre);
    const arrow = document.createElement('span');
    arrow.setAttribute('aria-hidden', 'true');
    arrow.textContent = '↗';
    destination.replaceChildren(document.createTextNode('Browse ' + genre + ' '), arrow);
  }
  genreImage.addEventListener('error', fallback);
  fetch('/api/catalogue/featured', { credentials: 'same-origin' })
    .then((response) => {
      if (!response.ok) throw new Error();
      return response.json();
    })
    .then((films) => {
      if (!Array.isArray(films) || !films.length) throw new Error();
      catalogue = films;
      reelHost.querySelector('.reel-copy-second p').textContent =
        films[0].title + ' · ' + films[0].year;
      reelHost.querySelector('.reel-copy-second a').href =
        '/movies/' + encodeURIComponent(films[0].id);
      cinemaWorld?.setPosters?.(films.map(poster));
      document.querySelectorAll('.screening-print').forEach((print, i) => {
        const film = films[i % films.length];
        const image = print.querySelector('img');
        image.src = poster(film);
        image.alt = film.title + ' poster';
        image.addEventListener('error', fallback);
        for (const link of print.querySelectorAll('a')) {
          link.href = '/movies/' + encodeURIComponent(film.id);
          link.setAttribute('aria-label', 'Explore ' + film.title);
        }
        print.querySelector('h3 a').textContent = film.title;
        const facts = document.createElement('span');
        facts.textContent = film.genres[0] + ' · ' + film.runtime + ' min';
        print
          .querySelector('.print-label p')
          .replaceChildren(document.createTextNode(film.year + ' '), facts);
      });
      const genres = [...new Set(films.flatMap((film) => film.genres))].slice(0, 6);
      const legend = document.createElement('legend');
      legend.textContent = 'Choose a film genre';
      picker.replaceChildren(legend);
      genres.forEach((genre, i) => {
        const label = document.createElement('label'),
          input = document.createElement('input'),
          text = document.createElement('span');
        input.type = 'radio';
        input.name = 'genre';
        input.value = genre;
        input.checked = i === 0;
        input.addEventListener('change', () => {
          if (input.checked) pickGenre(genre);
        });
        text.textContent = genre;
        label.append(input, text);
        picker.append(label);
      });
      picker.disabled = false;
      pickGenre(genres[0]);
      engine?.layout();
    })
    .catch(() => {
      caption.textContent =
        'The collection is temporarily unavailable. Enter Neo4flix to try again.';
      destination.href = '/login';
      destination.textContent = 'Enter Neo4flix ↗';
    });
})();
