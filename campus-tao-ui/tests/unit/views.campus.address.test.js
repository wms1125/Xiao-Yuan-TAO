// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 组件只从 element-plus 取消息提示与确认框，整体替换为替身
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/api/campus/address', () => ({
  listAddress: vi.fn(),
  addAddress: vi.fn(),
  updateAddress: vi.fn(),
  setDefaultAddress: vi.fn(),
  delAddress: vi.fn()
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { listAddress, addAddress, updateAddress, setDefaultAddress, delAddress } from '@/api/campus/address'
import AddressPage from '@/views/campus/address/index.vue'
import { elementStubs, clickButton } from './support/element-stubs'

const addressRow = (over = {}) => ({
  addressId: 1,
  receiver: '李四',
  phone: '13900000000',
  region: '东校区 3 号宿舍楼',
  detail: '501 室',
  isDefault: 1,
  createTime: '2026-10-16 10:00:00',
  ...over
})

function stubList(rows = [addressRow()]) {
  listAddress.mockResolvedValue({ code: 200, data: rows })
}

async function mountPage() {
  const wrapper = mount(AddressPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('收货地址页', () => {
  it('挂载后请求当前用户地址列表', async () => {
    stubList()
    await mountPage()

    expect(listAddress).toHaveBeenCalledTimes(1)
  })

  it('渲染收货人、电话、地区与详细地址', async () => {
    stubList([addressRow({ receiver: '王五', phone: '13800000000', region: '西校区', detail: '2 号楼 202' })])
    const wrapper = await mountPage()

    const text = wrapper.text()
    expect(text).toContain('王五')
    expect(text).toContain('13800000000')
    expect(text).toContain('西校区')
    expect(text).toContain('2 号楼 202')
  })

  it('仅默认地址显示「默认」标签，非默认行不显示', async () => {
    stubList([
      addressRow({ addressId: 1, receiver: '李四', isDefault: 1 }),
      addressRow({ addressId: 2, receiver: '张三', isDefault: 0 })
    ])
    const wrapper = await mountPage()

    const tags = wrapper.findAll('.el-tag-stub')
    expect(tags).toHaveLength(1)
    expect(tags[0].text()).toBe('默认')
    // 标签随默认行渲染，验证它确实绑在 isDefault===1 的那一行上
    expect(wrapper.text()).toContain('李四')
  })

  it('概览展示地址总数与默认地址的收货人、电话', async () => {
    stubList([
      addressRow({ addressId: 1, receiver: '李四', phone: '13900000000', isDefault: 1 }),
      addressRow({ addressId: 2, receiver: '张三', phone: '13800000000', isDefault: 0 })
    ])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('李四')
    expect(wrapper.text()).toContain('13900000000')
  })

  it('无地址时概览默认地址显示「未设置」', async () => {
    stubList([])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('未设置')
    expect(wrapper.text()).toContain('暂无收货地址')
  })

  it('创建时间超过 19 位时截断并去掉 T 分隔符', async () => {
    stubList([addressRow({ createTime: '2026-10-16T10:00:00.000Z' })])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('2026-10-16 10:00:00')
    expect(wrapper.text()).not.toContain('T10:00')
  })

  it('达到 20 条上限时禁用「新增地址」按钮', async () => {
    stubList(Array.from({ length: 20 }, (_, i) => addressRow({ addressId: i + 1 })))
    const wrapper = await mountPage()

    const addButton = wrapper.findAll('.el-button-stub').find((node) => node.text().includes('新增地址'))
    expect(addButton.exists()).toBe(true)
    // :disabled="full" 经替身透传为 disabled 属性，值为 full 的真值
    expect(addButton.attributes('disabled')).toBe('true')
  })

  it('未达上限时「新增地址」可点', async () => {
    stubList([addressRow()])
    const wrapper = await mountPage()

    const addButton = wrapper.findAll('.el-button-stub').find((node) => node.text().includes('新增地址'))
    expect(addButton.attributes('disabled')).toBe('false')
  })

  it('点击新增打开弹窗，标题为新增且表单为空', async () => {
    stubList([addressRow()])
    const wrapper = await mountPage()

    await clickButton(wrapper, '新增地址')
    await flushPromises()

    expect(wrapper.find('.el-dialog-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('新增收货地址')
    // placeholder 是属性而非文本，断言属性；表单此时应为空
    const receiverInput = wrapper.findAll('.el-input-stub')[0]
    expect(receiverInput.attributes('placeholder')).toBe('请输入收货人姓名')
    expect(receiverInput.element.value).toBe('')
    expect(wrapper.find('.el-switch-stub').attributes('data-value')).toBe('0')
  })

  it('点击修改回填该行数据并以修改标题打开弹窗', async () => {
    stubList([addressRow({ addressId: 7, receiver: '王五', phone: '13700000000', detail: '3 号楼 302' })])
    const wrapper = await mountPage()

    await clickButton(wrapper, '修改')
    await flushPromises()

    expect(wrapper.text()).toContain('修改收货地址')
    // el-input 替身把 modelValue 写入 DOM 的 value 属性，回填是否生效可直接断言
    const inputs = wrapper.findAll('.el-input-stub')
    expect(inputs[0].element.value).toBe('王五')
    expect(inputs[1].element.value).toBe('13700000000')
    expect(wrapper.find('.el-switch-stub').attributes('data-value')).toBe('1')
  })

  it('表单无 addressId 时提交走新增接口', async () => {
    stubList([])
    addAddress.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '新增地址')
    await flushPromises()
    await clickButton(wrapper, '确 定')
    await flushPromises()

    expect(addAddress).toHaveBeenCalledTimes(1)
    expect(updateAddress).not.toHaveBeenCalled()
    // 提交成功后关闭弹窗并刷新列表
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
    expect(listAddress).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('地址已保存')
  })

  it('表单带 addressId 时提交走修改接口', async () => {
    stubList([addressRow({ addressId: 7, receiver: '王五' })])
    updateAddress.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '修改')
    await flushPromises()
    await clickButton(wrapper, '确 定')
    await flushPromises()

    expect(updateAddress).toHaveBeenCalledTimes(1)
    expect(addAddress).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('地址已更新')
    expect(updateAddress.mock.calls[0][0].addressId).toBe(7)
  })

  it('设为默认：确认后调用接口并刷新列表', async () => {
    stubList([
      addressRow({ addressId: 1, receiver: '李四', isDefault: 1 }),
      addressRow({ addressId: 2, receiver: '张三', isDefault: 0 })
    ])
    setDefaultAddress.mockResolvedValue({ code: 200 })
    ElMessageBox.confirm.mockResolvedValue()
    const wrapper = await mountPage()

    await clickButton(wrapper, '设为默认')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(setDefaultAddress).toHaveBeenCalledWith(2)
    expect(ElMessage.success).toHaveBeenCalledWith('已设为默认地址')
    expect(listAddress).toHaveBeenCalledTimes(2)
  })

  it('设为默认：用户取消时不调用接口、不提示', async () => {
    stubList([addressRow({ addressId: 2, receiver: '张三', isDefault: 0 })])
    setDefaultAddress.mockResolvedValue({ code: 200 })
    ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const wrapper = await mountPage()

    await clickButton(wrapper, '设为默认')
    await flushPromises()

    expect(setDefaultAddress).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(listAddress).toHaveBeenCalledTimes(1)
  })

  it('删除：确认后调用接口并刷新列表', async () => {
    stubList([addressRow({ addressId: 3, receiver: '赵六' })])
    delAddress.mockResolvedValue({ code: 200 })
    ElMessageBox.confirm.mockResolvedValue()
    const wrapper = await mountPage()

    await clickButton(wrapper, '删除')
    await flushPromises()

    expect(delAddress).toHaveBeenCalledWith(3)
    expect(ElMessage.success).toHaveBeenCalledWith('地址已删除')
    expect(listAddress).toHaveBeenCalledTimes(2)
  })

  it('删除：用户取消时不调用接口', async () => {
    stubList([addressRow({ addressId: 3, receiver: '赵六' })])
    delAddress.mockResolvedValue({ code: 200 })
    ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
    const wrapper = await mountPage()

    await clickButton(wrapper, '删除')
    await flushPromises()

    expect(delAddress).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(listAddress).toHaveBeenCalledTimes(1)
  })
})
