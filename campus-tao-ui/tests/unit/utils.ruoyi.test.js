import { describe, it, expect } from 'vitest'
import {
  parseTime,
  selectDictLabel,
  selectDictLabels,
  addDateRange,
  handleTree,
  tansParams,
  getNormalPath,
  parseStrEmpty
} from '@/utils/ruoyi'

describe('parseTime', () => {
  it('无参数或空值返回 null', () => {
    expect(parseTime()).toBeNull()
    expect(parseTime(null)).toBeNull()
    expect(parseTime('')).toBeNull()
  })

  it('按默认格式格式化 Date 对象并补零', () => {
    expect(parseTime(new Date(2024, 0, 2, 3, 4, 5))).toBe('2024-01-02 03:04:05')
  })

  it('支持自定义格式与星期占位符 a', () => {
    const d = new Date(2024, 0, 7, 9, 8, 7) // 2024-01-07 是周日
    expect(parseTime(d, '{y}/{m}/{d}')).toBe('2024/01/07')
    expect(parseTime(d, '{a}')).toBe('日')
  })
})

describe('selectDictLabel', () => {
  const datas = [
    { value: '1', label: '选项一' },
    { value: '2', label: '选项二' }
  ]

  it('按 value 命中时返回 label', () => {
    expect(selectDictLabel(datas, '1')).toBe('选项一')
    expect(selectDictLabel(datas, 2)).toBe('选项二')
  })

  it('value 为 undefined 返回空串', () => {
    expect(selectDictLabel(datas, undefined)).toBe('')
  })

  it('未命中时原样返回 value', () => {
    expect(selectDictLabel(datas, '9')).toBe('9')
  })
})

describe('selectDictLabels', () => {
  const datas = [
    { value: '1', label: '选项一' },
    { value: '2', label: '选项二' }
  ]

  it('数组值以逗号连接后回显 label', () => {
    expect(selectDictLabels(datas, ['1', '2'])).toBe('选项一,选项二')
  })

  it('支持自定义分隔符', () => {
    expect(selectDictLabels(datas, '1,2', ',')).toBe('选项一,选项二')
  })

  it('空值返回空串，未命中项原样保留', () => {
    expect(selectDictLabels(datas, '')).toBe('')
    expect(selectDictLabels(datas, ['1', '9'])).toBe('选项一,9')
  })
})

describe('addDateRange', () => {
  it('无 propName 时写入 beginTime/endTime 到 params.params', () => {
    const result = addDateRange({ name: 'x' }, ['2024-01-01', '2024-01-31'])
    expect(result.params.beginTime).toBe('2024-01-01')
    expect(result.params.endTime).toBe('2024-01-31')
    expect(result.name).toBe('x')
  })

  it('带 propName 时拼出 begin/end 前缀字段', () => {
    const result = addDateRange({}, ['a', 'b'], 'CreateTime')
    expect(result.params.beginCreateTime).toBe('a')
    expect(result.params.endCreateTime).toBe('b')
  })
})

describe('handleTree', () => {
  it('按 parentId 组装父子层级', () => {
    const tree = handleTree([
      { id: 1, parentId: 0, name: 'root' },
      { id: 2, parentId: 1, name: 'child' }
    ])
    expect(tree).toHaveLength(1)
    expect(tree[0].id).toBe(1)
    expect(tree[0].children).toHaveLength(1)
    expect(tree[0].children[0].id).toBe(2)
  })
})

describe('tansParams', () => {
  it('拼接普通参数并跳过空值', () => {
    expect(tansParams({ a: '1', b: '' })).toBe('a=1&')
  })

  it('嵌套对象按 key[subKey] 形式展开', () => {
    expect(tansParams({ params: { beginTime: '2024' } })).toBe('params%5BbeginTime%5D=2024&')
  })
})

describe('getNormalPath / parseStrEmpty', () => {
  it('getNormalPath 合并重复斜杠并去掉末尾斜杠', () => {
    expect(getNormalPath('x//y/')).toBe('x/y')
    expect(getNormalPath('')).toBe('')
  })

  it('parseStrEmpty 将 null/undefined 字面量转为空串', () => {
    expect(parseStrEmpty(null)).toBe('')
    expect(parseStrEmpty('undefined')).toBe('')
    expect(parseStrEmpty('ok')).toBe('ok')
  })
})
