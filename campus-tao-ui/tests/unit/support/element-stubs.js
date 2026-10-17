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

/**
 * 合并"模板传入的 class"与"替身自己的 class"。
 *
 * 为什么必须合并而不是二选一：
 *   - 若先写 class 再展开 attrs，模板上的 class 会整体顶掉替身类名，
 *     测试里按 `.xxx-stub` 选择器找不到元素；
 *   - 若先展开 attrs 再写 class，替身类名又会顶掉模板上的 class，
 *     页面上那些**带业务语义的 class**（如 `campus-product__fav--on` 表示"已收藏"）会消失，
 *     测试也就无法断言它们。
 * 两者都丢行为，故只能拼接。
 */
const mergeStubClass = (attrs, ownClass) => [ownClass, attrs.class].filter(Boolean).join(' ')

// 渲染默认插槽的通用替身
//
// 注意 inheritAttrs: false —— 这里手动把 attrs 展开到根元素，若同时保留默认的属性继承，
// Vue 会把同一批 attrs 再 merge 一次，@click 会被合并成函数数组从而触发两次。
//
// **class 必须写在 ...attrs 之后**：Vue 会把模板里的 class 也放进 attrs，
// 若先写 class 再展开 attrs，模板上的 class 会整体顶掉替身自己的类名，
// 导致测试里按 `.xxx-stub` 选择器找不到元素。
const slotStub = (name) =>
  defineComponent({
    name,
    inheritAttrs: false,
    setup(_, { slots, attrs }) {
      return () =>
        h('div', { ...attrs, class: mergeStubClass(attrs, `${name}-stub`) }, slots.default ? slots.default() : [])
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
  emits: ['selection-change'],
  setup(props, { slots, attrs, emit, expose }) {
    // 下发取值函数而非数组本身：:data 是 ref，列表加载完成后引用会被整体替换，
    // 只在 setup 阶段取一次会永远拿到挂载时的空数组，列替身也就渲染不出任何行。
    provide('elTableRows', () => props.data)
    // 选中状态在替身内部记账。语义必须与真实表格一致：toggleRowSelection /
    // clearSelection 操作选中集后**必须回抛 selection-change**——
    // 购物车页的「已选件数 / 待结算金额」全靠该事件联动，替身丢事件，
    // 这些路径一次都测不到（测出来的绿是假的）。
    const selected = new Map()
    const fireSelection = () => emit('selection-change', Array.from(selected.values()))
    const toggleRowSelection = (row, isSelected = true) => {
      if (isSelected) selected.set(row.id, row)
      else selected.delete(row.id)
      fireSelection()
    }
    const clearSelection = () => {
      selected.clear()
      fireSelection()
    }
    expose({ toggleRowSelection, clearSelection })
    return () =>
      h('div', { ...attrs, class: mergeStubClass(attrs, 'el-table-stub') }, [
        slots.default ? slots.default() : [],
        slots.empty ? slots.empty() : []
      ])
  }
})

/**
 * 列：按行调用 scoped slot，模拟真实表格的 scope 结构。
 *
 * 没有 #default 插槽时按 prop 取行内字段渲染——这正是 Element Plus 的行为：
 * `<el-table-column prop="title" />` 会显示 row.title。替身若不做这件事，
 * 所有"声明式列"（notifylog/notice 等页的标题、内容列）在测试里都是空的，
 * 断言页面渲染出来的测试会全红，逼着人把每列都改写成 scoped slot。
 */
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
  setup(props, { slots }) {
    const rowsOf = inject('elTableRows', () => [])
    return () => {
      const rows = rowsOf()
      return h(
        'div',
        { class: 'el-table-column-stub' },
        Array.isArray(rows)
          ? rows.map((row, index) => {
              if (slots.default) {
                return slots.default({ row, $index: index, column: {}, store: {}, _self: {} })
              }
              // 声明式列：真实组件取 row[prop]；值为 null/undefined 时渲染空串
              const value = props.prop ? row?.[props.prop] : ''
              return h('div', { class: 'el-table-column-stub__cell' }, value == null ? '' : String(value))
            })
          : []
      )
    }
  }
})

/** 弹窗：modelValue 为真才渲染内容；title 需如实渲染（页面对新增/修改两种弹窗标题有断言） */
export const ElDialog = defineComponent({
  name: 'el-dialog',
  props: { modelValue: Boolean, title: String, width: String, appendToBody: Boolean },
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'el-dialog-stub' }, [
            h('div', { class: 'el-dialog-stub__title' }, props.title),
            slots.default ? slots.default() : [],
            slots.footer ? slots.footer() : []
          ])
        : null
  }
})

export const ElDescriptions = slotStub('el-descriptions')
export const ElDescriptionsItem = slotStub('el-descriptions-item')
export const ElFormItem = slotStub('el-form-item')

/** 表单：暴露 validate / resetFields / clearValidate，供页面里的提交、重置与校验清理路径驱动 */
export const ElForm = defineComponent({
  name: 'el-form',
  props: { model: Object, rules: Object, inline: Boolean, labelWidth: String },
  setup(_, { slots, expose }) {
    const validate = (cb) => cb(true)
    const resetFields = vi.fn()
    // 真实 ElForm 暴露该方法；页面在 reset() 里经 formRef 调用，替身必须齐备
    const clearValidate = vi.fn()
    expose({ validate, resetFields, clearValidate })
    return () => h('form', { class: 'el-form-stub' }, slots.default ? slots.default() : [])
  }
})

/** 输入框：把 update:modelValue 透出，测试可直接驱动 v-model；placeholder 需如实渲染 */
export const ElInput = defineComponent({
  name: 'el-input',
  inheritAttrs: false,
  props: {
    modelValue: [String, Number],
    placeholder: String,
    clearable: Boolean,
    type: String,
    rows: Number
  },
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h('input', {
        class: mergeStubClass(attrs, 'el-input-stub'),
        value: props.modelValue,
        placeholder: props.placeholder,
        ...attrs,
        // 必须如实回传 update:modelValue：否则 v-model 的双向绑定是断的，
        // "输入内容后能发送"这类路径根本测不到——替身丢行为，测出来的绿是假的。
        onInput: (e) => emit('update:modelValue', e.target.value)
      })
  }
})

export const ElSelect = defineComponent({
  name: 'el-select',
  inheritAttrs: false,
  props: { modelValue: [String, Number], placeholder: String, clearable: Boolean },
  emits: ['update:modelValue'],
  setup(_, { slots, attrs }) {
    return () =>
      h('div', { ...attrs, class: mergeStubClass(attrs, 'el-select-stub') }, slots.default ? slots.default() : [])
  }
})

/** 数字输入：把当前值写进 data-value，便于断言「加购数量」是否被页面正确约束 */
export const ElInputNumber = defineComponent({
  name: 'el-input-number',
  inheritAttrs: false,
  props: { modelValue: Number, min: Number, max: Number, size: String },
  emits: ['update:modelValue'],
  setup(props, { attrs }) {
    return () =>
      h('div', { ...attrs, class: mergeStubClass(attrs, 'el-input-number-stub'), 'data-value': props.modelValue })
  }
})

/** 开关：点按切换并如实回抛 update:modelValue 与 change（新值）——
 *  与真实开关一致：先翻转值再触发 change。商品管理页的「上下架」、
 *  地址页的「默认地址」都由 change/v-model 驱动，替身不回抛事件，
 *  这些路径一次都测不到（替身丢行为，测出来的绿是假的）。 */
export const ElSwitch = defineComponent({
  name: 'el-switch',
  inheritAttrs: false,
  props: { modelValue: [Number, String, Boolean], activeValue: null, inactiveValue: null, disabled: Boolean },
  emits: ['update:modelValue', 'change'],
  setup(props, { attrs, emit }) {
    return () =>
      h('div', {
        ...attrs,
        class: mergeStubClass(attrs, 'el-switch-stub'),
        'data-value': props.modelValue,
        onClick: () => {
          if (props.disabled) return
          const next = props.modelValue === props.activeValue ? props.inactiveValue : props.activeValue
          emit('update:modelValue', next)
          emit('change', next)
        }
      })
  }
})

/** 评分：把当前分值写进 data-rate，便于断言评价弹窗的默认分与回填 */
export const ElRate = defineComponent({
  name: 'el-rate',
  inheritAttrs: false,
  props: { modelValue: Number, disabled: Boolean, size: String },
  emits: ['update:modelValue'],
  setup(props, { attrs }) {
    return () => h('div', { ...attrs, class: mergeStubClass(attrs, 'el-rate-stub'), 'data-rate': props.modelValue })
  }
})

/** 图标容器：只渲染默认插槽（图标本身是 svg，jsdom 下无需渲染细节） */
export const ElIcon = defineComponent({
  name: 'el-icon',
  inheritAttrs: false,
  setup(_, { slots, attrs }) {
    return () =>
      h('span', { ...attrs, class: mergeStubClass(attrs, 'el-icon-stub') }, slots.default ? slots.default() : [])
  }
})

/** 文字提示：必须渲染默认插槽，否则被包裹的收藏按钮根本不会出现在 DOM 里，
 *  也就无法点击——这是"替身丢行为导致测不出东西"的典型。 */
export const ElTooltip = defineComponent({
  name: 'el-tooltip',
  inheritAttrs: false,
  setup(_, { slots }) {
    return () => h('span', { class: 'el-tooltip-stub' }, slots.default ? slots.default() : [])
  }
})

/** 未读角标：把 value 写进 data-value，便于断言未读数是否正确传递 */
export const ElBadge = defineComponent({
  name: 'el-badge',
  inheritAttrs: false,
  props: { value: { type: [Number, String], default: null } },
  setup(props, { slots, attrs }) {
    return () =>
      h(
        'span',
        { ...attrs, class: mergeStubClass(attrs, 'el-badge-stub'), 'data-value': props.value },
        slots.default ? slots.default() : []
      )
  }
})

/**
 * 提示条：把 title 渲染成文本、type 渲染成 data-type。
 *
 * 替身丢属性等于这条路径测不到——资金语义提示正是靠 title 与 type 表达的，
 * 若替身只渲染一个空 div，那么"沙箱提示有没有出现、配色对不对"就无法断言。
 */
export const ElAlert = defineComponent({
  name: 'el-alert',
  inheritAttrs: false,
  props: {
    title: { type: String, default: '' },
    type: { type: String, default: 'info' },
    closable: Boolean,
    showIcon: Boolean
  },
  setup(props, { attrs }) {
    return () =>
      h('div', { ...attrs, class: mergeStubClass(attrs, 'el-alert-stub'), 'data-type': props.type }, props.title)
  }
})

/** 分页：页面通过 v-model:page + @pagination 联动，替身需如实透出这两个事件 */
export const Pagination = defineComponent({
  name: 'Pagination',
  inheritAttrs: false,
  props: { total: Number, page: { type: Number, default: 1 }, limit: { type: Number, default: 20 } },
  emits: ['update:page', 'update:limit', 'pagination'],
  setup(_, { attrs }) {
    return () => h('div', { ...attrs, class: mergeStubClass(attrs, 'pagination-stub') })
  }
})

/**
 * el-pagination：分页控件替身。
 *
 * 需要暴露三个事实供测试断言：total、当前页码、当前每页条数。
 * 由于 props 不会出现在 attrs 里，这里刻意**不声明 props**，
 * 让它们以 attr 形式落到根元素上，测试用 data-* 属性即可读回。
 *
 * 翻页动作用「下一页」按钮模拟：真实组件 next-click/current-change
 * 回传的就是新页码（数字，不是对象），替身如实照搬这个签名。
 */
export const ElPagination = defineComponent({
  name: 'el-pagination',
  inheritAttrs: false,
  emits: ['current-change', 'size-change'],
  setup(_, { attrs, emit }) {
    // 当前页码取自绑定值 currentPage；点击"下一页"即翻到 currentPage + 1，
    // 与真实组件 current-change 回传新页码（数字）的签名一致。
    const current = Number(attrs.currentPage) || 1
    return () =>
      h(
        'div',
        {
          ...attrs,
          class: mergeStubClass(attrs, 'el-pagination-stub'),
          'data-total': attrs.total,
          'data-page': current
        },
        [
          h(
            'button',
            {
              class: 'el-pagination__next-stub',
              onClick: () => emit('current-change', current + 1)
            },
            'next'
          )
        ]
      )
  }
})

/** 右工具栏：透出 update:showSearch 与 queryTable */
export const RightToolbar = defineComponent({
  name: 'RightToolbar',
  inheritAttrs: false,
  props: { showSearch: Boolean },
  emits: ['update:showSearch', 'queryTable'],
  setup(_, { attrs }) {
    return () => h('div', { ...attrs, class: mergeStubClass(attrs, 'right-toolbar-stub') })
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
      'el-input-number': ElInputNumber,
      'el-select': ElSelect,
      'el-option': ElOption,
      'el-switch': ElSwitch,
      'el-rate': ElRate,
      'el-radio-group': ElRadioGroup,
      'el-radio': ElRadio,
      'el-timeline': ElTimeline,
      'el-timeline-item': ElTimelineItem,
      'el-icon': ElIcon,
      'el-tooltip': ElTooltip,
      'el-badge': ElBadge,
      'el-alert': ElAlert,
      'el-pagination': ElPagination,
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
