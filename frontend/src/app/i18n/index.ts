import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ru from './ru.json';

// Все тексты интерфейса — в файлах локализации, даже пока язык один (раздел 14 SPEC.md).
void i18n.use(initReactI18next).init({
  resources: { ru: { translation: ru } },
  lng: 'ru',
  fallbackLng: 'ru',
  interpolation: { escapeValue: false },
});

export default i18n;
