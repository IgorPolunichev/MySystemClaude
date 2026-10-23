import { Empty, Typography } from 'antd';
import { useTranslation } from 'react-i18next';

/** Заглушка раздела, который ещё не реализован. */
export function PlaceholderPage({
  titleKey,
  textKey,
  stage,
}: {
  titleKey: string;
  textKey: string;
  stage: number;
}) {
  const { t } = useTranslation();
  return (
    <section className="page">
      <Typography.Title level={2} className="page-title">
        {t(titleKey)}
      </Typography.Title>
      <div className="page-panel">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <>
              <Typography.Paragraph>{t(textKey)}</Typography.Paragraph>
              <Typography.Text type="secondary">{t('placeholder.stage', { stage })}</Typography.Text>
            </>
          }
        />
      </div>
    </section>
  );
}
