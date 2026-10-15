import { describe, it, expect } from 'vitest'
import {
  isPathMatch,
  isEmpty,
  isExternal,
  validURL,
  validEmail,
  validLowerCase,
  validUpperCase,
  validAlphabets,
  isString,
  isArray
} from '@/utils/validate'

describe('isPathMatch', () => {
  it('单星号匹配同级路径段，不跨越斜杠', () => {
    expect(isPathMatch('/system/*', '/system/user')).toBe(true)
    expect(isPathMatch('/system/*', '/system/user/detail')).toBe(false)
  })

  it('双星号可跨越路径层级', () => {
    expect(isPathMatch('/system/**', '/system/user/detail')).toBe(true)
  })

  it('问号匹配单个非斜杠字符', () => {
    expect(isPathMatch('/a?c', '/abc')).toBe(true)
    expect(isPathMatch('/a?c', '/a/c')).toBe(false)
  })
})

describe('isEmpty', () => {
  it('null/空串/undefined 字面量判为空', () => {
    expect(isEmpty(null)).toBe(true)
    expect(isEmpty('')).toBe(true)
    expect(isEmpty(undefined)).toBe(true)
    expect(isEmpty('undefined')).toBe(true)
  })

  it('非空字符串判为非空；数字 0 因宽松比较同样视为空（若依既有行为）', () => {
    expect(isEmpty('x')).toBe(false)
    expect(isEmpty(0)).toBe(true)
  })
})

describe('isExternal / isHttp', () => {
  it('识别 http/https/mailto/tel 外链', () => {
    expect(isExternal('http://ruoyi.vip')).toBe(true)
    expect(isExternal('https://a.com')).toBe(true)
    expect(isExternal('mailto:a@b.com')).toBe(true)
    expect(isExternal('/system/user')).toBe(false)
  })
})

describe('validURL', () => {
  it('合法 URL 通过校验', () => {
    expect(validURL('https://gitee.com/y_project/RuoYi-Vue')).toBe(true)
    expect(validURL('http://campus.example.edu/api')).toBe(true)
  })

  it('非法 URL 被拒绝', () => {
    expect(validURL('not-a-url')).toBe(false)
    expect(validURL('ftp:/broken')).toBe(false)
    expect(validURL('http://localhost:8080/api')).toBe(false) // 该规则要求域名带点分后缀，无点的 localhost 不通过
  })
})

describe('validEmail', () => {
  it('合法邮箱通过', () => {
    expect(validEmail('student@campus.edu.cn')).toBe(true)
  })

  it('缺少 @ 或域名的邮箱被拒绝', () => {
    expect(validEmail('student.campus.edu.cn')).toBe(false)
    expect(validEmail('student@')).toBe(false)
  })
})

describe('大小写与字母校验', () => {
  it('validLowerCase 仅接受纯小写', () => {
    expect(validLowerCase('abc')).toBe(true)
    expect(validLowerCase('Abc')).toBe(false)
  })

  it('validUpperCase 仅接受纯大写', () => {
    expect(validUpperCase('ABC')).toBe(true)
    expect(validUpperCase('AbC')).toBe(false)
  })

  it('validAlphabets 接受大小写混合字母', () => {
    expect(validAlphabets('AbC')).toBe(true)
    expect(validAlphabets('Ab1')).toBe(false)
  })
})

describe('isString / isArray', () => {
  it('正确区分字符串与数组', () => {
    expect(isString('x')).toBe(true)
    expect(isString(1)).toBe(false)
    expect(isArray([1])).toBe(true)
    expect(isArray('x')).toBe(false)
  })
})
