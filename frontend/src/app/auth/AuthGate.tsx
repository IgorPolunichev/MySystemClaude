import { useEffect, useRef, useState, type ReactNode } from 'react';
import { hasAuthParams, useAuth } from 'react-oidc-context';
import { Button, Result, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { authServerReachable, rememberOnlineLogin, useOfflineSession } from '../../offline/offlineAuth';
import { OfflineLogin } from '../../offline/OfflineLogin';
import { PinSetup } from '../../offline/PinSetup';
import { authority } from './oidcConfig';
import { rolesFromAccessToken } from './roles';
import { setAccessToken } from './tokenStore';

/**
 * Пускает в приложение после входа через Keycloak и держит токен HTTP-клиента актуальным. Если сервер входа
 * недоступен, предлагает вход без связи по ПИН-коду (решение 19.12 SPEC.md).
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const { t } = useTranslation();
  const offline = useOfflineSession();
  const [unreachable, setUnreachable] = useState(false);
  const checking = useRef(false);

  useEffect(() => {
    setAccessToken(auth.user?.access_token);
    const profile = auth.user?.profile;
    if (auth.user && profile?.preferred_username) {
      const fullName =
        [profile.family_name, profile.given_name].filter(Boolean).join(' ') || profile.preferred_username;
      void rememberOnlineLogin(
        profile.preferred_username,
        fullName,
        rolesFromAccessToken(auth.user.access_token),
      );
    }
  }, [auth.user]);

  useEffect(() => {
    if (offline || unreachable || checking.current) return;
    if (
      !hasAuthParams() &&
      !auth.isAuthenticated &&
      !auth.activeNavigator &&
      !auth.isLoading &&
      !auth.error
    ) {
      checking.current = true;
      void authServerReachable(authority).then((ok) => {
        checking.current = false;
        if (ok) void auth.signinRedirect();
        else setUnreachable(true);
      });
    }
  }, [auth, offline, unreachable]);

  if (offline && !auth.isAuthenticated) return <>{children}</>;

  if (unreachable && !auth.isAuthenticated) {
    return <OfflineLogin onRetry={() => setUnreachable(false)} />;
  }

  if (auth.error) {
    return (
      <Result
        status="error"
        title={t('app.authError', { message: auth.error.message })}
        extra={
          <Button type="primary" onClick={() => void auth.signinRedirect()}>
            {t('app.retry')}
          </Button>
        }
      />
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="fullscreen-center">
        <Spin size="large" tip={t('app.signingIn')}>
          <div style={{ width: 160, height: 80 }} />
        </Spin>
      </div>
    );
  }

  const username = auth.user?.profile.preferred_username;
  return (
    <>
      {username && <PinSetup username={username} />}
      {children}
    </>
  );
}
