import { useMemo, useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import { App as AntApp, Button, ConfigProvider, Result } from 'antd';
import ruRU from 'antd/locale/ru_RU';
import dayjs from 'dayjs';
import 'dayjs/locale/ru';
import { useTranslation } from 'react-i18next';
import { oidcConfig } from './auth/oidcConfig';
import { AuthGate } from './auth/AuthGate';
import { AppLayout } from './AppLayout';
import { modules } from './moduleRegistry';
import { buildTheme, type ThemeMode } from './theme';
import { readSetting, writeSetting } from '../shared/lib/storage';

dayjs.locale('ru');

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
});

const THEME_KEY = 'acts.theme';

function initialTheme(): ThemeMode {
  const saved = readSetting(THEME_KEY);
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function NotFound() {
  const { t } = useTranslation();
  return (
    <Result
      status="404"
      title={t('app.notFound')}
      extra={
        <Link to="/acts">
          <Button type="primary">{t('app.toHome')}</Button>
        </Link>
      }
    />
  );
}

export function App() {
  const [themeMode, setThemeMode] = useState<ThemeMode>(initialTheme);
  const themeConfig = useMemo(() => buildTheme(themeMode), [themeMode]);

  const toggleTheme = () => {
    const next = themeMode === 'dark' ? 'light' : 'dark';
    setThemeMode(next);
    writeSetting(THEME_KEY, next);
  };

  return (
    <ConfigProvider locale={ruRU} theme={themeConfig}>
      <AntApp>
        <div className="theme-root" data-theme={themeMode}>
          <AuthProvider {...oidcConfig}>
            <QueryClientProvider client={queryClient}>
              <AuthGate>
                <BrowserRouter>
                  <Routes>
                    <Route element={<AppLayout themeMode={themeMode} onToggleTheme={toggleTheme} />}>
                      <Route index element={<Navigate to="/acts" replace />} />
                      {modules.map((m) => (
                        <Route key={m.key} path={`${m.path}/*`} element={m.element} />
                      ))}
                      <Route path="*" element={<NotFound />} />
                    </Route>
                  </Routes>
                </BrowserRouter>
              </AuthGate>
            </QueryClientProvider>
          </AuthProvider>
        </div>
      </AntApp>
    </ConfigProvider>
  );
}
