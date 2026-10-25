import { Segmented, Typography } from 'antd';
import { Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { ContactsPage } from './ContactsPage';
import { JournalPage } from './JournalPage';
import { TemplatesPage } from './TemplatesPage';

function MailTabs() {
  const { tab = 'journal' } = useParams();
  const navigate = useNavigate();
  return (
    <section className="page">
      <div className="page-head">
        <Typography.Title level={2} className="page-title">
          Почта
        </Typography.Title>
      </div>
      <Typography.Paragraph type="secondary" style={{ marginTop: 4 }}>
        Письмо с актами формируется в реестре актов (выберите акты галочками) или в карточке акта.
      </Typography.Paragraph>
      <Segmented
        style={{ marginBottom: 16 }}
        value={tab}
        onChange={(v) => navigate(`/mail/${String(v)}`)}
        options={[
          { value: 'journal', label: 'Журнал отправок' },
          { value: 'contacts', label: 'Адресная книга' },
          { value: 'templates', label: 'Шаблоны' },
        ]}
      />
      {tab === 'contacts' ? <ContactsPage /> : tab === 'templates' ? <TemplatesPage /> : <JournalPage />}
    </section>
  );
}

export function MailRoutes() {
  return (
    <Routes>
      <Route index element={<MailTabs />} />
      <Route path=":tab" element={<MailTabs />} />
    </Routes>
  );
}
