import { useEffect, useState } from 'react';
import { Alert, App, Input, Modal, Space, Typography } from 'antd';
import { hasPin, setPin, validatePin } from './offlineAuth';

/**
 * После входа со связью: предложить задать ПИН для работы без связи (если ещё не задан). Можно отложить —
 * тогда без связи этот пользователь войти не сможет. Открывается и вручную — «Сменить ПИН».
 */
export function PinSetup({
  username,
  force,
  onClose,
}: {
  username: string;
  force?: boolean;
  onClose?: () => void;
}) {
  const { message } = App.useApp();
  const [open, setOpen] = useState(!!force);
  const [pin, setPinValue] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (force) return;
    if (sessionStorage.getItem('acts.pinPromptShown')) return;
    void hasPin(username).then((has) => {
      if (!has) setOpen(true);
      sessionStorage.setItem('acts.pinPromptShown', '1');
    });
  }, [username, force]);

  const close = () => {
    setOpen(false);
    setPinValue('');
    setRepeat('');
    setError(undefined);
    onClose?.();
  };

  const save = async () => {
    const problem = validatePin(pin) ?? (pin !== repeat ? 'ПИН-коды не совпадают' : null);
    if (problem) {
      setError(problem);
      return;
    }
    await setPin(username, pin);
    message.success('ПИН-код сохранён: без связи с сервером войдите с ним на этом ПК');
    close();
  };

  const digits = (v: string) => v.replace(/\D/g, '').slice(0, 8);
  return (
    <Modal
      open={open}
      title="ПИН-код для работы без связи"
      okText="Сохранить"
      cancelText="Позже"
      onOk={() => void save()}
      onCancel={close}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Если на этом ПК пропадёт связь с сервером компании, вы сможете войти по ПИН-коду и работать с
          данными на ПК до 7 дней. Пароль от учётной записи на ПК не сохраняется.
        </Typography.Paragraph>
        <Input.Password
          value={pin}
          onChange={(e) => setPinValue(digits(e.target.value))}
          placeholder="ПИН-код, 4–8 цифр"
          inputMode="numeric"
          autoFocus
        />
        <Input.Password
          value={repeat}
          onChange={(e) => setRepeat(digits(e.target.value))}
          placeholder="Повторите ПИН-код"
          inputMode="numeric"
          onPressEnter={() => void save()}
        />
        {error && <Alert type="error" showIcon message={error} />}
      </Space>
    </Modal>
  );
}
