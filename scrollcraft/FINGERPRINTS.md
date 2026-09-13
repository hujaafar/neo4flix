# Fingerprints

Every site you build with **scroll-craft** gets one row here, appended after it
ships. The registry exists so your next build can prove it is a different page
rather than a re-skin of one you already made.

This file is **yours**. It starts empty on purpose: the gate is about not
repeating *yourself*, so it has nothing to say until you have built something.

The rules and the gate live in the skill's
`references/uniqueness.md`. Short version:

**A new build must differ from EVERY row below on at least 4 of the 6
dimensions.** Four against each row individually, not four on average across the
table. If a planned build fails, change the plan. Never edit a row to make room
for it.

The six dimensions are: **grammar**, **nav treatment**, **hero device**,
**act-sequence shape**, **close pattern**, **signature move**.

Dimension 6 is free, because a signature move is unique by definition. So the
gate really asks for three more out of the remaining five, and a build that
changes only grammar and world will fail it.

---

## The registry

| Build | Grammar | Nav treatment | Hero device | Act-sequence shape | Close pattern | Signature move | World | Port |
|---|---|---|---|---|---|---|---|---|
| Neo4flix | Gallery/catalog | Film index plus real app entrance; existing app sidebar | Layered illustrated film print in natural flow | Four beats, flow/parallax → pin/unfold/tilt → flow/reveal → flow/in; 4.4 screens desktop | Account entrance as collection plate | Screening stack opens into individually selectable prints with an orbital thread | Existing geometric film illustrations, charcoal/olive/lime | 8443 HTTPS |

First build: no preceding rows to compare. Future builds must compare against this row.

---

## What is taken

Add a bullet here whenever a build claims something a later build should avoid
reusing: a grammar, a nav treatment, a close pattern, a signature move, an
act-count-and-length band. The shared columns are what the next build inherits
as a constraint, so writing them down is the whole point.

- Neo4flix claims the screening-stack unfold and orbital thread, film-object index, layered illustrated-print opening, four-beat gallery structure, and account entrance plate.

---

## Appending a row

After shipping, add one line to the table and one bullet to **What is taken** if
the build claimed something new. Fill every column. Say what the build shares
with existing rows.

Rows are append-only. A build that has been superseded stays in the table,
because the space it occupies is still occupied.

---

## Worked example

The skill's author kept a registry of twelve builds across eight page grammars.
If you want to see what a filled-in table looks like, and which shapes tend to
collide, read `EXAMPLES.md` in the scroll-craft repository. Treat it as
illustration only: those rows are somebody else's builds and they do **not**
constrain yours.

## User-directed revision: dimensional cinema

This is a correction to the same Neo4flix build after the user supplied a stronger motion reference. It deliberately retains the product's catalogue, navigation destinations and account close rather than claiming a new unrelated site under the novelty gate. The original fingerprint above remains historical.

| Build | Grammar | Nav treatment | Hero device | Act-sequence shape | Close pattern | Signature move | World | Port |
|---|---|---|---|---|---|---|---|---|
| Neo4flix motion revision | Gallery/catalog | Overlay film index plus existing app sidebar | Actual 3D reel and camera through aperture | Dimensional pin → print unfold → genre choice → account flow; 6.6 desktop screens | Existing account entrance | Travelling through a physical cinema reel into its landscape | Original moon landscape, live metal/film geometry, olive/lime | 8443 HTTPS |
