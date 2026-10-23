import { useEffect, type ReactNode } from 'react';
import { hasAuthParams, useAuth } from 'react-oidc-context';
import { Button, Result, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { setAccessToken } from './tokenStore';

/** Пускает в приложение только после входа через Keycloak и держит токен HTTP-клиента актуальным. */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const { t } = useTranslation();

  useEffect(() => {
    setAccessToken(auth.user?.access_token);
  }, [auth.user]);

  useEffect(() => {
    if (
      !hasAuthParams() &&
      !auth.isAuthenticated &&
      !auth.activeNavigator &&
      !auth.isLoading &&
      !auth.error
    ) {
      void auth.signinRedirect();
    }
  }, [auth]);

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

  return <>{children}</>;
}
