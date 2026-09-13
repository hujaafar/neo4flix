/* Neo4flix-specific choreography. The upstream ScrollCraft files are unchanged. */
(() => {
  const root = document.documentElement;
  const reelHost = document.querySelector('.reel-world');
  import('./reel-world.js')
    .then(({ mountCinemaWorld }) => {
      const world = mountCinemaWorld(reelHost);
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

  document.querySelector('.genre-picker').disabled = false;
  const selections = {
    'Science Fiction': { title: 'Arrival', artwork: 'arrival', year: '2016' },
    Drama: { title: 'Moonlight', artwork: 'moonlight', year: '2016' },
    Animation: { title: 'Spirited Away', artwork: 'spirited-away', year: '2001' },
    Thriller: { title: 'Parasite', artwork: 'parasite', year: '2019' },
  };
  const destination = document.querySelector('#genre-destination');
  const genreImage = document.querySelector('#genre-image');
  const caption = document.querySelector('#genre-caption');
  document.querySelectorAll('input[name="genre"]').forEach((input) => {
    input.addEventListener('change', () => {
      if (!input.checked || !selections[input.value]) return;
      const film = selections[input.value];
      genreImage.src = '/art/' + film.artwork + '.svg';
      genreImage.alt = 'Original geometric ' + film.title + ' illustration';
      const facts = document.createElement('span');
      facts.textContent = film.year + ' · ' + input.value;
      caption.replaceChildren(document.createTextNode(film.title + ' '), facts);
      destination.href = '/?genre=' + encodeURIComponent(input.value);
      const arrow = document.createElement('span');
      arrow.setAttribute('aria-hidden', 'true');
      arrow.textContent = '↗';
      destination.replaceChildren(document.createTextNode('Browse ' + input.value + ' '), arrow);
    });
  });
})();
