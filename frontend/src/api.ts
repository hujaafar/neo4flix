import { Injectable, signal } from '@angular/core';

export interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  twoFactorEnabled: boolean;
}
export interface Movie {
  id: string;
  title: string;
  genres: string[];
  year: number;
  releaseDate: string;
  runtime: number;
  director: string;
  overview: string;
  artwork: string;
  averageRating?: number;
  ratingCount?: number;
  reason?: string;
  recommendationScore?: number;
  watchlistNote?: string;
}
export interface Rating {
  id: string;
  score: number;
  review: string;
  createdAt: string;
  updatedAt: string;
  movie: Movie;
}
export interface Share {
  id: string;
  note: string;
  createdAt: string;
  movie: Movie;
}
interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  user: User;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class Api {
  user = signal<User | null>(null);
  private token = '';
  private refreshing: Promise<boolean> | null = null;
  async restore(): Promise<void> {
    await this.refresh();
  }
  private refresh(): Promise<boolean> {
    if (this.refreshing) return this.refreshing;
    const run = async () => {
      try {
        const r = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' });
        if (!r.ok) {
          this.clear();
          return false;
        }
        this.accept(await r.json());
        return true;
      } catch {
        this.clear();
        return false;
      }
    };
    // Serialize refresh-cookie rotation across browser tabs as well as concurrent API calls.
    const locked = async (): Promise<boolean> =>
      'locks' in navigator ? await navigator.locks.request('neo4flix-refresh', run) : await run();
    const pending = locked().finally(() => (this.refreshing = null));
    this.refreshing = pending;
    return pending;
  }
  private accept(data: AuthResponse) {
    this.token = data.accessToken;
    this.user.set(data.user);
  }
  clear() {
    this.token = '';
    this.user.set(null);
  }
  async authenticate(kind: 'login' | 'register', body: unknown) {
    this.accept(await this.request<AuthResponse>('/api/auth/' + kind, 'POST', body, false));
  }
  async logout() {
    try {
      await this.request('/api/auth/logout', 'POST', undefined, false);
    } finally {
      this.clear();
    }
  }
  async request<T>(
    path: string,
    method = 'GET',
    body?: unknown,
    authorized = true,
    retry = true,
  ): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (authorized && this.token) headers['Authorization'] = 'Bearer ' + this.token;
    let response: Response;
    try {
      response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'same-origin',
      });
    } catch {
      throw new ApiError(0, 'Cannot reach Neo4flix. Check your connection and try again.');
    }
    if (response.status === 401 && authorized && retry && (await this.refresh()))
      return this.request(path, method, body, authorized, false);
    if (!response.ok) {
      let message =
        response.status === 401
          ? 'Please sign in again.'
          : response.status === 429
            ? 'Too many requests. Please wait a minute.'
            : 'The request could not be completed.';
      try {
        const error = await response.json();
        if (error.message) message = error.message;
      } catch {}
      throw new ApiError(response.status, message);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
}
