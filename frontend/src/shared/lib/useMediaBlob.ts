import { useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAccessToken } from '../../app/auth/tokenStore';
import { getGetMediaContentUrl } from '../../api/generated/media/media';

/**
 * Изображение из media-service: запрос с токеном (обычный <img src> токен не передаёт), результат — адрес
 * blob: для <img>. Содержимое по id не меняется, поэтому кэшируется на всю сессию.
 */
export function useMediaUrl(mediaId: string | undefined, variant: 'thumbnail' | 'original', enabled = true) {
  const {
    data: blob,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['media-content', mediaId, variant],
    enabled: !!mediaId && enabled,
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
    queryFn: async () => {
      const token = getAccessToken();
      const response = await fetch(getGetMediaContentUrl(mediaId!, { variant }), {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!response.ok) throw new Error(`Ошибка ${response.status}`);
      return response.blob();
    },
  });
  const url = useMemo(() => (blob ? URL.createObjectURL(blob) : undefined), [blob]);
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );
  return { url, isLoading, isError };
}
