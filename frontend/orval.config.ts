import { defineConfig } from 'orval';

// Клиент API генерируется из контрактов OpenAPI (contracts/openapi). Сгенерированные файлы не правятся
// руками: после изменения контракта выполните `npm run api:generate`.
const output = (name: string) => ({
  mode: 'tags' as const,
  target: `src/api/generated/${name}`,
  schemas: `src/api/generated/${name}/model`,
  client: 'react-query' as const,
  httpClient: 'fetch' as const,
  clean: true,
  override: {
    mutator: { path: 'src/api/http.ts', name: 'apiFetch' },
  },
});

export default defineConfig({
  system: { input: '../contracts/openapi/system-api.yaml', output: output('system') },
  directory: { input: '../contracts/openapi/directory-api.yaml', output: output('directory') },
  parts: { input: '../contracts/openapi/parts-api.yaml', output: output('parts') },
  acts: { input: '../contracts/openapi/acts-api.yaml', output: output('acts') },
  media: { input: '../contracts/openapi/media-api.yaml', output: output('media') },
});
