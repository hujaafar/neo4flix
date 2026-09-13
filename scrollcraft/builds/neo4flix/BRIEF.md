# Neo4flix: the cinema opens

Self-authored under explicit creative delegation. The user's words were: “apply this to my project” and, when asked about visual direction, “up to u”. Existing product requirements and artwork are the source of truth.

## Brief

1. Vibe: cinematic, tactile, curious, quietly playful. Authored direction: a screening-room programme and a physical stack of illustrated film prints.
2. Journey: meet one film, open the collection, choose a genre, enter the real application. Inside the application, discover, filter, open details and rate as before.
3. Energy: a quiet opening; a larger, satisfying expansion; a useful, calm selection; a definite entrance into the app.
4. Feeling curve: curiosity from the layered Interstellar composition; possibility as the stacked posters separate; agency from a working genre choice; ownership from registration or sign-in.
5. Signature move: the screening stack. Three overlapping film prints open into individually selectable works as scroll advances. A drawn orbital thread traces the connection between the prints. This is authored CSS driven by the engine's --sc-p, not an engine modification.
6. Aesthetic: retain the app's charcoal, olive and pale lime. Sans-serif labels plus restrained italic serif display. Original geometric illustrations are established project assets, so an illustrated world is appropriate.
7. Structure: distinct scenes. Gallery/catalog grammar, short and navigable. No continuous worldflight, video, or long marketing argument.
8. Assets: existing original SVG poster illustrations; semantic HTML titles and metadata; CSS/SVG scenery. No external asset generation or API spending.

## Purpose and action

Neo4flix is a movie discovery and recommendation application for people choosing their next film. The visitor should understand that they can explore films and make future recommendations more personal by rating them. Primary action: “Enter Neo4flix”. Object links lead to actual movie details through the existing authentication guard. The genre selection carries into the application's filters through login and registration.

## Peak and journey score

The peak: “The stack of film prints opened into a collection I could actually choose from.” It lives in the collection section, which receives the largest authored scroll span. Tell-someone sentence: “It's the site where the films unfold like a stack of cinema prints.”

| Beat | Feeling and cause | Device | Span |
|---|---|---|---|
| Featured object | Curiosity from separated orbital planes around Interstellar | Flow + parallax | One opening screen |
| Screening stack | Possibility as overlapping prints open into a readable collection | Pin + custom --sc-p transforms + pointer tilt | 2.1 viewport heights on desktop |
| Genre selection | Agency from a real selection carried into the app | Flow + reveal | Content height |
| Entrance | Ownership through a real account and collection | Flow + in | Content height, resolved close |

No cue hides essential text or controls. No empty authored silence. Mobile and reduced motion use three ordinary, fully reachable film objects instead of a pinned stack. Reduced-motion changes take effect live.

## Grammar and gate

Gallery/catalog wins because the product is a collection of films with real destinations. Filmic one-shot would hide navigation; chaptered editorial would imply an essay; live surface is the authenticated app rather than the entrance; continuous world adds unnecessary geography; typographic poster sidelines the film artwork; split stage invents a comparison; rhythmic cutlist is too noisy for choosing a film. This is the first registry entry, so there are no previous rows to compare against.

## Layer contract

| Plane | Content | Movement | Occlusion rule |
|---|---|---|---|
| Far | Star field on olive ground | Small parallax | Behind all readable content |
| Middle | Orbital tracks | Moderate translation | Frames the film print |
| Subject | Original Interstellar print | Subtle independent tilt | Complete silhouette, solid shadow |
| Near | Curved screening-room frame | Larger parallax | Cropped at outer edges; never over labels or links |
| Type | Title, factual label, destination | Stable | Own opaque ground, no scrim |

The Angular catalogue receives a compact version of the screening stack and one-time card entrances with independent cleanup. The upstream runtime stays unmodified and only mounts once in the standalone entrance document: upstream supplies no destroy API and retains global listeners, making repeated Angular route mounts unsuitable.

## Preflight

Node 22.12 and Chrome are present. Playwright resolves from frontend/. The upstream doctor reports FFmpeg missing and KIE_AI_API_KEY unset. This build uses existing SVG assets and no video or generated imagery, so neither is needed by the delivered page. Video encoding was not exercised. No system media packages were installed solely to satisfy an unused pipeline check.
