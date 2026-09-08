import data from './seed-preview.json'
export { data }
export type Product = (typeof data.product)[number]
export type Material = (typeof data.supplier_material)[number]
export const groups: Record<string, string> = {
  NO_ACTION_BASELINE_SOY: '已声明大豆',
  REVIEW_REQUIRED_BASELINE_NO_SOY: '未声明大豆',
  NEGATIVE_CONTROL_NO_CHOCOLATE: '不含巧克力物料',
}
export const groupColors: Record<string, string> = {
  NO_ACTION_BASELINE_SOY: 'teal',
  REVIEW_REQUIRED_BASELINE_NO_SOY: 'violet',
  NEGATIVE_CONTROL_NO_CHOCOLATE: 'blue',
}
