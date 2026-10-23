import type { FormInstance } from 'antd';
import { ApiError } from '../../api/http';

/** Текст ошибки для пользователя. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'Недостаточно прав для этого действия.';
    return error.message;
  }
  if (error instanceof Error && error.message === 'Failed to fetch') {
    return 'Нет связи с сервером. Проверьте подключение и повторите.';
  }
  return error instanceof Error ? error.message : 'Неизвестная ошибка';
}

/**
 * Показывает ошибку сервера в форме: ошибки полей — под полями, общий текст — сообщением.
 * Возвращает текст общего сообщения.
 */
export function applyFormError(form: FormInstance, error: unknown): string {
  if (error instanceof ApiError && error.problem?.errors) {
    const fields = Object.entries(error.problem.errors).map(([name, message]) => ({
      name,
      errors: [message],
    }));
    form.setFields(fields);
  }
  return errorText(error);
}
