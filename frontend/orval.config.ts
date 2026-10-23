import { defineConfig } from 'orval';

// Клиент API генерируется из контрактов OpenAPI. Сгенерированные файлы не правятся руками:
// после изменения контракта выполните `npm run api:generate`.
export default defineConfig({
  system: {
    input: '../contracts/openapi/system-api.yaml',
    output: {
      mode: 'tags-split',
      target: 'src/api/generated',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      override: {
        mutator: { path: 'src/api/http.ts', name: 'apiFetch' },
      },
    },
  },
});
