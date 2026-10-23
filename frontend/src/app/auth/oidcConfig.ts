import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

const authority = import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/acts';
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'acts-frontend';

export const oidcConfig: AuthProviderProps = {
  authority,
  client_id: clientId,
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  scope: 'openid profile email',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // Убираем ?code=…&state=… из адресной строки после входа.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
