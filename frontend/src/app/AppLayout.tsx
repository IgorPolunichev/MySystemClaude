import { SyncIndicator } from '../offline/SyncIndicator';
import { clearLocalData, db } from '../offline/db';
import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { App, Button, Drawer, Grid, Layout, Menu, Space, Tag, Tooltip, Typography } from 'antd';

import { LogoutOutlined, MenuOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons';
import { useAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { modules, visibleModules } from './moduleRegistry';
import { rolesFromAccessToken } from './auth/roles';
import type { ThemeMode } from './theme';

interface Props {
  themeMode: ThemeMode;
  onToggleTheme: () => void;
}

function Nameplate() {
  const { t } = useTranslation();
  return (
    <div className="nameplate" aria-label={t('app.name')}>
      <span className="nameplate-code">АВР</span>
      <span className="nameplate-name">{t('app.name')}</span>
    </div>
  );
}

export function AppLayout({ themeMode, onToggleTheme }: Props) {
  const auth = useAuth();
  const { modal } = App.useApp();
  /** Выход очищает локальную базу; с неотправленными данными — только после подтверждения (раздел 17.7). */
  const logout = async () => {
    const pending = await db.outbox.count().catch(() => 0);
    const leave = async () => {
      await clearLocalData().catch(() => undefined);
      await auth.signoutRedirect();
    };
    if (pending === 0) return leave();
    modal.confirm({
      title: 'Есть неотправленные изменения',
      content: `На устройстве ${pending} неотправленных изменений. После выхода они будут удалены. Сначала синхронизируйтесь, когда появится связь.`,
      okText: 'Всё равно выйти',
      okButtonProps: { danger: true },
      cancelText: 'Остаться',
      onOk: leave,
    });
  };
  const { t } = useTranslation();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.lg;
  const [drawerOpen, setDrawerOpen] = useState(false);

  const roles = useMemo(() => rolesFromAccessToken(auth.user?.access_token), [auth.user]);
  const items = visibleModules(modules, roles).map((m) => ({
    key: m.path,
    icon: m.icon,
    label: <Link to={`/${m.path}`}>{t(m.titleKey)}</Link>,
  }));
  const selected = location.pathname.split('/')[1] || 'acts';
  const profile = auth.user?.profile;
  const fullName =
    [profile?.family_name, profile?.given_name].filter(Boolean).join(' ') || profile?.preferred_username;

  const menu = (
    <Menu
      theme="dark"
      mode="inline"
      selectedKeys={[selected]}
      items={items}
      onClick={() => setDrawerOpen(false)}
    />
  );

  return (
    <Layout className="app-shell">
      {!isMobile && (
        <Layout.Sider width={232} className="app-sider">
          <Nameplate />
          {menu}
        </Layout.Sider>
      )}
      {isMobile && (
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={260}
          styles={{ body: { padding: 0, background: '#1D2630' }, header: { display: 'none' } }}
        >
          <Nameplate />
          {menu}
        </Drawer>
      )}
      <Layout>
        <Layout.Header className="app-header">
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined />}
              aria-label="Меню"
              onClick={() => setDrawerOpen(true)}
            />
          )}
          <div className="app-header-spacer" />
          <Space size="middle" wrap={false}>
            <div className="user-block">
              <Typography.Text strong>{fullName}</Typography.Text>
              {!isMobile && roles.map((r) => <Tag key={r}>{t(`roles.${r}`)}</Tag>)}
            </div>
            <Tooltip title={themeMode === 'dark' ? t('app.themeLight') : t('app.themeDark')}>
              <Button
                type="text"
                aria-label={themeMode === 'dark' ? t('app.themeLight') : t('app.themeDark')}
                icon={themeMode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
                onClick={onToggleTheme}
              />
            </Tooltip>
            <SyncIndicator />
            <Button icon={<LogoutOutlined />} onClick={() => void logout()}>
              {!isMobile && t('app.signOut')}
            </Button>
          </Space>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
