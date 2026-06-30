// Shared API types. The error model mirrors the backend's standardized shape
// (architecture.md §8 / HTS-031) so the whole SPA parses failures the same way.

/** A single field-level validation error (architecture.md §8). */
export interface FieldError {
  field: string;
  message: string;
}

/** Standardized error body returned by the backend for every 4xx/5xx (architecture.md §8). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  fieldErrors: FieldError[];
}

export interface HealthStatus {
  status: string;
}

/** Public view of a user returned by signup (mirrors backend UserResponse). */
export interface UserResponse {
  id: string;
  email: string;
  emailVerified: boolean;
  createdAt: string;
}
