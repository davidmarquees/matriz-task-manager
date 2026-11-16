// Single place for the backend address (Worksheet 03, Ex. 7). Set VITE_API_URL in the root .env file.
export const API_URL: string = import.meta.env.VITE_API_URL ?? '/api';
