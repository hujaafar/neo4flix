import { Component, computed, inject, signal } from '@angular/core';
import { Api } from '../api';

interface GraphNode {
  id: string;
  label: string;
  kind: 'User' | 'Movie' | 'Genre';
}
interface GraphEdge {
  source: string;
  target: string;
  type: string;
  score?: number;
  createdAt?: string;
  updatedAt?: string;
}
interface Snapshot {
  nodes: GraphNode[];
  relationships: GraphEdge[];
  movieLimit: number;
  userLimit: number;
  gdsVersion: string;
}

@Component({
  standalone: true,
  template: `
    <div class="page-top"><span class="eyebrow">BEHIND THE RECOMMENDATIONS</span></div>
    <header class="page-heading">
      <div>
        <h1>The movie graph.</h1>
        <p>Explore the stored connections that power discovery.</p>
      </div>
      <button class="button subtle" (click)="load()" [disabled]="busy()">Refresh graph</button>
    </header>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
    @if (snapshot(); as data) {
      <p class="muted">
        Live Neo4j data · GDS {{ data.gdsVersion }} · {{ data.nodes.length }} nodes ·
        {{ data.relationships.length }} relationships
      </p>
      <p class="field-help">
        Sample: up to {{ data.userLimit }} viewers and {{ data.movieLimit }} films. Viewer labels
        hide names and emails. Select a node to inspect its connections. On smaller screens, scroll
        the graph and table sideways to see all columns.
      </p>
      <section class="panel graph-panel" aria-label="Database graph">
        <svg
          [attr.viewBox]="'0 0 1040 ' + height()"
          [style.height.px]="height()"
          role="group"
          aria-label="Users, movies and genres"
        >
          <text x="45" y="24" class="graph-heading">VIEWERS</text>
          <text x="425" y="24" class="graph-heading">MOVIES</text>
          <text x="805" y="24" class="graph-heading">GENRES</text>
          @for (edge of edges(); track $index) {
            <line
              [attr.x1]="edge.start.x"
              [attr.y1]="edge.start.y"
              [attr.x2]="edge.end.x"
              [attr.y2]="edge.end.y"
              [class.rating-edge]="edge.type === 'RATED'"
              class="graph-edge"
            >
              <title>{{ edge.type }}{{ edge.score ? ': ' + edge.score + ' / 5' : '' }}</title>
            </line>
          }
          @for (node of nodes(); track node.id) {
            <g
              role="button"
              tabindex="0"
              [attr.aria-label]="node.kind + ': ' + node.label"
              [attr.aria-pressed]="selected() === node.id"
              (click)="selected.set(node.id)"
              (keydown.enter)="selected.set(node.id)"
              (keydown.space)="$event.preventDefault(); selected.set(node.id)"
              class="graph-node"
              [class.selected]="selected() === node.id"
            >
              <circle
                [attr.cx]="node.x"
                [attr.cy]="node.y"
                r="7"
                [class.viewer]="node.kind === 'User'"
                [class.genre]="node.kind === 'Genre'"
              />
              <text [attr.x]="node.x + 14" [attr.y]="node.y + 4">{{ node.label }}</text>
            </g>
          }
        </svg>
      </section>
      <section class="panel">
        <div class="section-line">
          <h2>Connections{{ selectedLabel() ? ': ' + selectedLabel() : '' }}</h2>
          <button class="button subtle" (click)="selected.set('')">Show all</button>
        </div>
        <div class="graph-table">
          <table>
            <thead>
              <tr>
                <th>From</th>
                <th>Relationship</th>
                <th>To</th>
                <th>Score</th>
                <th>Created</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              @for (edge of connections(); track $index) {
                <tr>
                  <td>{{ label(edge.source) }}</td>
                  <td>{{ edge.type }}</td>
                  <td>{{ label(edge.target) }}</td>
                  <td>{{ edge.score ?? '—' }}</td>
                  <td>{{ edge.createdAt ?? '—' }}</td>
                  <td>{{ edge.updatedAt ?? '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (!connections().length) {
          <p class="muted">
            No connections in this sample yet. Rate a film, then refresh the graph.
          </p>
        }
      </section>
    }
  `,
  styles: [
    `
      .graph-panel,
      .graph-table {
        overflow: auto;
      }
      .graph-panel svg {
        min-width: 1040px;
        width: 100%;
        display: block;
      }
      .graph-edge {
        stroke: var(--line, #49534b);
        stroke-width: 1;
        opacity: 0.45;
      }
      .graph-edge.rating-edge {
        stroke: #d2ec88;
        opacity: 0.7;
      }
      .graph-node {
        cursor: pointer;
      }
      .graph-node circle {
        fill: #ddd4c3;
        stroke: #151c18;
        stroke-width: 2;
      }
      .graph-node .viewer {
        fill: #d2ec88;
      }
      .graph-node .genre {
        fill: #d2a884;
      }
      .graph-node text {
        fill: currentColor;
        font: 12px sans-serif;
        paint-order: stroke;
        stroke: #17201a;
        stroke-width: 4px;
      }
      .graph-node.selected circle,
      .graph-node:focus circle {
        stroke: #fff;
        stroke-width: 3;
      }
      .graph-heading {
        fill: #b1beac;
        font: 11px sans-serif;
        letter-spacing: 2px;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.8rem;
      }
      th,
      td {
        padding: 12px;
        text-align: left;
        border-bottom: 1px solid #39483d;
        white-space: nowrap;
      }
    `,
  ],
})
export class GraphPage {
  private api = inject(Api);
  snapshot = signal<Snapshot | null>(null);
  busy = signal(false);
  error = signal('');
  selected = signal('');
  nodes = computed(() => {
    const counts = { User: 0, Movie: 0, Genre: 0 };
    const columns = { User: 45, Movie: 425, Genre: 805 };
    return (this.snapshot()?.nodes ?? []).map((node) => ({
      ...node,
      x: columns[node.kind],
      y: 60 + counts[node.kind]++ * 38,
    }));
  });
  height = computed(() => Math.max(360, ...this.nodes().map((n) => n.y + 45)));
  positions = computed(() => new Map(this.nodes().map((node) => [node.id, node])));
  edges = computed(() =>
    (this.snapshot()?.relationships ?? []).flatMap((edge) => {
      const start = this.positions().get(edge.source),
        end = this.positions().get(edge.target);
      return start && end ? [{ ...edge, start, end }] : [];
    }),
  );
  connections = computed(() =>
    this.edges().filter(
      (edge) =>
        !this.selected() || edge.source === this.selected() || edge.target === this.selected(),
    ),
  );
  selectedLabel = computed(() => this.label(this.selected()));
  constructor() {
    void this.load();
  }
  label(id: string) {
    return this.positions().get(id)?.label ?? '';
  }
  async load() {
    this.busy.set(true);
    this.error.set('');
    try {
      this.snapshot.set(await this.api.request<Snapshot>('/api/movies/graph'));
    } catch (error) {
      this.error.set((error as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
