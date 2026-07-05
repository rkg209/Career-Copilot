export type Theme = "dark" | "light";

const STORAGE_KEY = "theme";

/** Persisted theme, defaulting to dark (the app's primary look). */
export function getTheme(): Theme {
  return localStorage.getItem(STORAGE_KEY) === "light" ? "light" : "dark";
}

/** Reflect a theme onto <html> so the token variables swap. */
export function applyTheme(theme: Theme): void {
  document.documentElement.classList.toggle("dark", theme === "dark");
}

export function setTheme(theme: Theme): void {
  localStorage.setItem(STORAGE_KEY, theme);
  applyTheme(theme);
}

/** Call before render (in main.tsx) to avoid a flash of the wrong theme. */
export function initTheme(): void {
  applyTheme(getTheme());
}
