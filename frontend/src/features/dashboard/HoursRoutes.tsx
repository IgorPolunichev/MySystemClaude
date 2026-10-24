import { Route, Routes } from 'react-router-dom';
import { HoursLayout } from './HoursLayout';

export function HoursRoutes() {
  return (
    <Routes>
      <Route index element={<HoursLayout />} />
      <Route path=":tab" element={<HoursLayout />} />
    </Routes>
  );
}
