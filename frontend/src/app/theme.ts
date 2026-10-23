import { theme, type ThemeConfig } from 'antd';

/**
 * Палитра: графитовая навигация, стальной синий для действий, сигнальный янтарь для акцентов —
 * цвета мастерской и шильдиков техники. Шрифт IBM Plex Sans с кириллицей (локально, работает офлайн).
 */
export const palette = {
  graphite: '#1D2630',
  steel: '#1F5C99',
  amber: '#D99A1E',
  canvas: '#EEF1F4',
  ink: '#1B232B',
  canvasDark: '#12171C',
};

const fontFamily = "'IBM Plex Sans', 'Segoe UI', Roboto, Arial, sans-serif";

export type ThemeMode = 'light' | 'dark';

export function buildTheme(mode: ThemeMode): ThemeConfig {
  const dark = mode === 'dark';
  return {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: dark ? '#4A8BD0' : palette.steel,
      colorWarning: palette.amber,
      colorInfo: dark ? '#4A8BD0' : palette.steel,
      colorBgLayout: dark ? palette.canvasDark : palette.canvas,
      colorTextBase: dark ? '#E4E8EC' : palette.ink,
      borderRadius: 4,
      fontFamily,
      fontSize: 14,
    },
    components: {
      Layout: { siderBg: palette.graphite, headerBg: dark ? '#1A2129' : '#FFFFFF', headerHeight: 56 },
      Menu: {
        darkItemBg: palette.graphite,
        darkItemSelectedBg: '#2B3846',
        darkItemSelectedColor: '#FFFFFF',
      },
    },
  };
}
