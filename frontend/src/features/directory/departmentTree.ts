import type { Department } from '../../api/generated/directory/model';

/** Плоский список подразделений → дерево для TreeSelect и Tree. */
export function departmentTree(departments: Department[]) {
  type Node = { value: string; key: string; title: string; children: Node[] };
  const nodes = new Map<string, Node>();
  departments.forEach((d) => nodes.set(d.id, { value: d.id, key: d.id, title: d.name, children: [] }));
  const roots: Node[] = [];
  departments.forEach((d) => {
    const node = nodes.get(d.id)!;
    const parent = d.parentId ? nodes.get(d.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  return roots;
}
