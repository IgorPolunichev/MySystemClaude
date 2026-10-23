import { Route, Routes } from 'react-router-dom';
import { ActEditor } from './ActEditor';
import { ActsRegistry } from './ActsRegistry';

/** Маршруты раздела «Акты»: реестр и карточка акта. */
export function ActsRoutes() {
  return (
    <Routes>
      <Route index element={<ActsRegistry />} />
      <Route path=":id" element={<ActEditor />} />
    </Routes>
  );
}
