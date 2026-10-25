import { useState } from 'react';
import { Select } from 'antd';
import { TeamOutlined } from '@ant-design/icons';
import { useListContactGroups, useListContacts } from '../../api/generated/mail/contacts';
import { ok } from '../../api/unwrap';

const GROUP = 'group:';

/**
 * Поле «Кому» / «Копия» / «Скрытая копия»: подсказки из адресной книги, выбор группы добавляет всех её
 * участников, можно ввести новый адрес вручную.
 */
export function RecipientSelect({
  value,
  onChange,
  placeholder,
}: {
  value: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
}) {
  const [q, setQ] = useState('');
  const contacts = ok(useListContacts({ q: q || undefined }).data) ?? [];
  const groups = ok(useListContactGroups().data) ?? [];
  const options = [
    ...groups
      .filter((g) => !q || g.name.toLowerCase().includes(q.toLowerCase()))
      .map((g) => ({
        value: GROUP + g.id,
        label: (
          <span>
            <TeamOutlined /> {g.name} <span className="cell-muted">({g.emails.length})</span>
          </span>
        ),
      })),
    ...contacts.map((c) => ({
      value: c.email,
      label: (
        <span>
          {c.fullName ? `${c.fullName} ` : ''}
          <span className="cell-muted">&lt;{c.email}&gt;</span>
          {c.organizationName && <span className="cell-muted"> · {c.organizationName}</span>}
        </span>
      ),
    })),
  ];
  return (
    <Select
      mode="tags"
      value={value}
      placeholder={placeholder}
      tokenSeparators={[',', ';', ' ']}
      filterOption={false}
      onSearch={setQ}
      options={options}
      style={{ width: '100%' }}
      onChange={(next: string[]) => {
        const expanded: string[] = [];
        for (const v of next) {
          if (v.startsWith(GROUP)) {
            const group = groups.find((g) => GROUP + g.id === v);
            group?.emails.forEach((e) => expanded.push(e));
          } else {
            expanded.push(v.trim());
          }
        }
        const seen = new Set<string>();
        onChange(expanded.filter((e) => e && !seen.has(e.toLowerCase()) && seen.add(e.toLowerCase())));
        setQ('');
      }}
    />
  );
}
