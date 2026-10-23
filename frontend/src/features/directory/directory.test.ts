import { describe, expect, it } from 'vitest';
import { departmentTree } from './departmentTree';
import { generatePassword } from './password';
import { emptyToNull, sortParam } from '../../shared/lib/table';
import { ok } from '../../api/unwrap';

describe('departmentTree', () => {
  it('строит дерево из плоского списка, сироты становятся корнями', () => {
    const tree = departmentTree([
      { id: 'a', organizationId: 'o', name: 'Цех', version: 0 },
      { id: 'b', organizationId: 'o', parentId: 'a', name: 'Участок', version: 0 },
      { id: 'c', organizationId: 'o', parentId: 'missing', name: 'Сирота', version: 0 },
    ]);
    expect(tree.map((n) => n.title)).toEqual(['Цех', 'Сирота']);
    expect(tree[0].children.map((n) => n.title)).toEqual(['Участок']);
  });
});

describe('sortParam', () => {
  it('переводит сортировку таблицы в параметр API', () => {
    expect(sortParam({ columnKey: 'name', order: 'ascend' })).toBe('name');
    expect(sortParam({ columnKey: 'inn', order: 'descend' })).toBe('-inn');
    expect(sortParam({ columnKey: 'inn', order: null })).toBeUndefined();
  });
});

describe('emptyToNull', () => {
  it('пустые строки превращает в null, остальное не трогает', () => {
    expect(emptyToNull({ a: '  ', b: 'x', c: 0, d: undefined })).toEqual({
      a: null,
      b: 'x',
      c: 0,
      d: undefined,
    });
  });
});

describe('generatePassword', () => {
  it('12 символов без похожих букв и цифр', () => {
    const p = generatePassword();
    expect(p).toHaveLength(12);
    expect(p).not.toMatch(/[0O1lI]/);
  });
});

describe('ok', () => {
  it('возвращает данные только успешного ответа', () => {
    expect(ok({ status: 200, data: { id: 1 } })).toEqual({ id: 1 });
    expect(ok({ status: 201, data: 'x' })).toBe('x');
    expect(ok({ status: 404, data: {} })).toBeUndefined();
    expect(ok(undefined)).toBeUndefined();
  });
});
