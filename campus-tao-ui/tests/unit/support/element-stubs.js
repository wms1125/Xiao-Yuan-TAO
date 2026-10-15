/**
 * Element Plus 轻量替身（仅供组件单测使用）
 *
 * 为什么不直接把 Element Plus 挂到 app 上：
 *   - el-table / el-scrollbar 依赖 ResizeObserver，jsdom 未实现，真实挂载需要额外 polyfill；
 *   - 真实挂载会把测试变成"验证 Element Plus 自己"，与本次要验证的业务联动逻辑无关。
 *
 * 替身的原则是"结构保真、能力最小"：
 *   - 需要渲染内容的位置渲染默认插槽（保证模板里的绑定表达式会被求值）；
 *   - el-table / el-table-column 之间用 provide/inject 传递行数据，使列的 scoped slot
 *     能像真实表格那样按行调用（否则 scope.row 为 undefined，模板表达式会直接抛错）；
 *   - el-form 暴露 validate/resetFields，使页面里的表单提交路径可被驱动；
 *   - 点击事件一律通过属性透传落到根元素上，因此测试可以直接 trigger('click')。
 */
import { defineComponent, h, inject, provide } from 'vue'
import { vi } from 'vitest'

// 渲染默认插槽的通用替身
//
// 注意 inheritAttrs: false —— 这里手动把 attrs 展开到根元素，若同时保留默认的属性继承，
// Vue 会把同一批 attrs 再 merge 一次，@click 会被合并成函数数组从而触发两次。
const slotStub = (name) =>
  defineComponent({
    name,
    inheritAttrs: false,
    setup(_, { slots, attrs }) {
      return () => h('div', { class: `${name}-stub`, ...attrs }, slots.default ? slots.default() : [])
    }
  })

export const ElButton = slotStub('el-button')
export const ElImage = slotStub('el-image')
export const ElTag = slotStub('el-tag')
export const ElTimeline = slotStub('el-timeline')
export const ElTimelineItem = slotStub('el-timeline-item')
export const ElRadio = slotStub('el-radio')
export const ElRadioGroup = slotStub('el-radio-group')
export const ElOption = slotStub('el-option')

/** 表格：把行数据通过 provide 下发给列替身；同时渲染 #empty 插槽 */
export const ElTable = defineComponent({
  name: 'el-table',
  inheritAttrs: false,
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots, attrs }) {
    // 下发取值函数而非数组本身：:data 是 ref，列表加载完成后引用会被整体替换，
    // 只在 setup 阶段取一次会永远拿到挂载时的空数组，列替身也就渲染不出任何行。
    provide('elTableRows', () => props.data)
    return () =>
      h('div', { class: 'el-table-stub', ...attrs }, [
        slots.default ? slots.default() : [],
        slots.empty ? slots.empty() : []
      ])
  }
})

/** 列：按行调用 scoped slot，模拟真实表格的 scope 结构 */
export const ElTableColumn = defineComponent({
  name: 'el-table-column',
  props: {
    label: String,
    prop: String,
    width: [String, Number],
    minWidth: [String, Number],
    align: String,
    fixed: [String, Boolean],
    showOverflowTooltip: Boolean
  },
  setup(_, { slots }) {
    const rowsOf = inject('elTableRows', () => [])
    return () => {
      const rows = rowsOf()
      return h(
        'div',
        { class: 'el-table-column-stub' },
        Array.isArray(rows)
          ? rows.map((row, index) =>
              slots.default ? slots.default({ row, $index: index, column: {}, store: {}, _self: {} }) : null
            )
          : []
      )
    }
  }
})

/** 弹窗：modelValue 为真才渲染内容，footer 插槽单独渲染 */
export const ElDialog = defineComponent({
  name: 'el-dialog',
  props: { modelValue: Boolean, title: String, width: String, appendToBody: Boolean },
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'el-dialog-stub' }, [
            slots.default ? slots.default() : [],
            slots.footer ? slots.footer() : []
          ])
        : null
  }
})

export const ElDescriptions = slotStub('el-descriptions')
export const ElDescriptionsItem = slotStub('el-descriptions-item')
export const ElFormItem = slotStub('el-form-item')

/** 表单：暴露 validate / resetFields，供页面里的提交与重置路径驱动 */
export const ElForm = defineComponent({
  name: 'el-form',
  props: { model: Object, rules: Object, inline: Boolean, labelWidth: String },
  setup(_, { slots, expose }) {
    const validate = (cb) => cb(true)
    const resetFields = vi.fn()
    expose({ validate, resetFields })
    return () => h('form', { class: 'el-form-stub' }, slots.default ? slots.default() : [])
  }
})

/** 输入框：把 update:modelValue 透出，测试可直接驱动 v-model */
export const ElInput = defineComponent({
  name: 'el-input',
  inheritAttrs: false,
  props: { modelValue: [String, Number], placeholder: String, clearable: Boolean, type: String, rows: Number },
  emits: ['update:modelValue'],
  setup(props, { attrs }) {
    return () => h('input', { class: 'el-input-stub', value: props.modelValue, ...attrs })
  }
})

export const ElSelect = defineComponent({
  name: 'el-select',
  inheritAttrs: false,
  props: { modelValue: [String, Number], placeholder: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(_, { slots, attrs }) {
    return () => h('div', { class: 'el-select-stub', ...attrs }, slots.default ? slots.default() : [])
  }
})

/** 分页：页面通过 v-model:page + @pagination 联动，替身需如实透出这两个事件 */
export const Pagination = defineComponent({
  name: 'Pagination',
  inheritAttrs: false,
  props: { total: Number, page: { type: Number, default: 1 }, limit: { type: Number, default: 20 } },
  emits: ['update:page', 'update:limit', 'pagination'],
  setup(_, { attrs }) {
    return () => h('div', { class: 'pagination-stub', ...attrs })
  }
})

/** 右工具栏：透出 update:showSearch 与 queryTable */
export const RightToolbar = defineComponent({
  name: 'RightToolbar',
  inheritAttrs: false,
  props: { showSearch: Boolean },
  emits: ['update:showSearch', 'queryTable'],
  setup(_, { attrs }) {
    return () => h('div', { class: 'right-toolbar-stub', ...attrs })
  }
})

/** 页面里用到的全局指令替身（本轮只关心业务行为，不做权限断言，故为空实现） */
export const directives = {
  loading: {},
  hasPermi: {},
  hasRole: {}
}

/** 一次性注册全部替身，供 mount 的 global 选项使用 */
export function elementStubs() {
  return {
    directives,
    components: {
      'el-button': ElButton,
      'el-image': ElImage,
      'el-tag': ElTag,
      'el-table': ElTable,
      'el-table-column': ElTableColumn,
      'el-dialog': ElDialog,
      'el-descriptions': ElDescriptions,
      'el-descriptions-item': ElDescriptionsItem,
      'el-form': ElForm,
      'el-form-item': ElFormItem,
      'el-input': ElInput,
      'el-select': ElSelect,
      'el-option': ElOption,
      'el-radio-group': ElRadioGroup,
      'el-radio': ElRadio,
      'el-timeline': ElTimeline,
      'el-timeline-item': ElTimelineItem,
      pagination: Pagination,
      'right-toolbar': RightToolbar
    }
  }
}

/** 按按钮文案点击替身按钮（比按下标取更抗改动） */
export function clickButton(wrapper, text) {
  const target = wrapper.findAll('.el-button-stub').find((node) => node.text().includes(text))
  if (!target) throw new Error(`未找到文案为「${text}」的按钮`)
  return target.trigger('click')
}

/** 读取概览卡片的数值，按出现顺序返回 */
export function statValues(wrapper) {
  return wrapper.findAll('.campus-stat__value').map((node) => node.text())
}
