import { useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAccessToken } from '../../app/auth/tokenStore';
import { getGetMediaContentUrl } from '../../api/generated/media/media';
import { db } from '../../offline/db';

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
      // Сначала ПК: превью и оригиналы фото сохраняются при просмотре и синхронизации (для работы без связи).
      const key = `${mediaId}:${variant}`;
      const local = await db.files.get(key).catch(() => undefined);
      if (local) return local.blob;
      const token = getAccessToken();
      const response = await fetch(getGetMediaContentUrl(mediaId!, { variant }), {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!response.ok) throw new Error(`Ошибка ${response.status}`);
      const blob = await response.blob();
      await db.files.put({ key, blob, savedAt: Date.now() }).catch(() => undefined);
      return blob;
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
