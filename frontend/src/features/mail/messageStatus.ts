import type { MessageStatus } from '../../api/generated/mail/model';

/** Подписи и цвета статусов письма. */
export const MESSAGE_STATUS: Record<MessageStatus, { label: string; color: string }> = {
  QUEUED: { label: 'в очереди', color: 'processing' },
  SENT: { label: 'отправлено', color: 'success' },
  FAILED: { label: 'не отправлено', color: 'error' },
  EML: { label: '.eml для Outlook', color: 'default' },
};
