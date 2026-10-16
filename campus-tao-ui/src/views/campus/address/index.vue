<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">收货地址</h2>
          <p class="campus-head__desc">维护本人收货地址；下单时选中后会被写入订单作为地址快照</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
          <el-button v-hasPermi="['campus:address:add']" type="primary" icon="Plus" :disabled="full" @click="handleAdd"
            >新增地址</el-button
          >
        </div>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">地址总数</div>
          <div class="campus-stat__value">{{ addressList.length }}</div>
          <div class="campus-stat__hint">最多 20 条</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">默认地址</div>
          <div class="campus-stat__value campus-stat__value--accent">
            {{ defaultAddress ? defaultAddress.receiver : '未设置' }}
          </div>
          <div class="campus-stat__hint">下单时自动选中</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">默认地址电话</div>
          <div class="campus-stat__value">{{ defaultAddress ? defaultAddress.phone : '—' }}</div>
          <div class="campus-stat__hint">用于联系收货人</div>
        </div>
      </div>

      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">地址清单</span>
        </div>

        <el-table v-loading="loading" :data="addressList">
          <el-table-column label="收货人" align="center" width="120">
            <template #default="scope">
              <span>{{ scope.row.receiver }}</span>
              <el-tag v-if="scope.row.isDefault === 1" type="success" size="small" effect="plain">默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="联系电话" align="center" width="140">
            <template #default="scope">
              <span class="campus-num">{{ scope.row.phone }}</span>
            </template>
          </el-table-column>
          <el-table-column label="所在地区" min-width="140" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.region || '—' }}</template>
          </el-table-column>
          <el-table-column label="详细地址" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.detail }}</template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" width="165">
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <el-table-column label="操作" align="center" width="220">
            <template #default="scope">
              <el-button
                v-if="scope.row.isDefault !== 1"
                v-hasPermi="['campus:address:edit']"
                link
                type="primary"
                icon="Check"
                @click="handleSetDefault(scope.row)"
                >设为默认</el-button
              >
              <el-button
                v-hasPermi="['campus:address:edit']"
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
                >修改</el-button
              >
              <el-button
                v-hasPermi="['campus:address:remove']"
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无收货地址</div>
              <div class="campus-empty__desc">新增地址后，下单时即可直接选用</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 新增 / 修改弹窗 -->
    <el-dialog :title="openTitle" v-model="open" width="560px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="form.receiver" placeholder="请输入收货人姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入 11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="所在地区" prop="region">
          <el-input v-model="form.region" placeholder="如：东校区 3 号宿舍楼（选填）" maxlength="100" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="2"
            placeholder="如：501 室 / 菜鸟驿站"
            maxlength="200"
          />
        </el-form-item>
        <el-form-item label="默认地址" prop="isDefault">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="campus-note campus-note--accent" style="margin-left: 10px">设为默认后会替代此前的默认地址</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CampusAddress">
import { listAddress, addAddress, updateAddress, setDefaultAddress, delAddress } from '@/api/campus/address'
import { onMounted, nextTick, reactive, ref, toRefs } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const addressList = ref([])
const open = ref(false)
const formRef = ref(null)
const openTitle = ref('')

/** 上限与后端 MAX_ADDRESSES_PER_USER 保持一致 */
const MAX_ADDRESSES = 20
const full = ref(false)

const data = reactive({
  form: {}
})

const rules = {
  receiver: [{ required: true, message: '收货人不能为空', trigger: 'blur' }],
  phone: [
    { required: true, message: '联系电话不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }
  ],
  detail: [{ required: true, message: '详细地址不能为空', trigger: 'blur' }]
}

// 必须用 toRefs 而非裸解构：reset()/handleUpdate() 会整体替换 form 对象
// （`form.value = {...}`），裸解构拿到的是旧对象的引用，替换后模板里
// `v-model="form.receiver"` 仍指向旧对象，输入框永远是空的。
const { form } = toRefs(data)

/** 默认地址：优先取被标记的，缺失时回落最新一条，与后端 selectDefault 口径一致 */
const defaultAddress = ref(null)

function parseTime(time) {
  if (!time) return ''
  return time.length > 19 ? time.substring(0, 19).replace('T', ' ') : time
}

function reset() {
  form.value = {
    addressId: undefined,
    receiver: undefined,
    phone: undefined,
    region: undefined,
    detail: undefined,
    isDefault: 0
  }
  nextTick(() => formRef.value?.clearValidate())
}

async function getList() {
  loading.value = true
  try {
    const res = await listAddress()
    addressList.value = res.data || []
    full.value = addressList.value.length >= MAX_ADDRESSES
    // 后端已按 isDefault desc 排序，首条即为默认（可能没有显式默认，与 selectDefault 回落一致）
    defaultAddress.value = addressList.value.length ? addressList.value[0] : null
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  reset()
  openTitle.value = '新增收货地址'
  open.value = true
}

function handleUpdate(row) {
  reset()
  openTitle.value = '修改收货地址'
  form.value = {
    addressId: row.addressId,
    receiver: row.receiver,
    phone: row.phone,
    region: row.region,
    detail: row.detail,
    isDefault: row.isDefault
  }
  open.value = true
}

function submitForm() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (form.value.addressId !== undefined) {
        await updateAddress(form.value)
        ElMessage.success('地址已更新')
      } else {
        await addAddress(form.value)
        ElMessage.success('地址已保存')
      }
      open.value = false
      getList()
    } catch {
      /* 错误提示由请求拦截器统一弹出 */
    }
  })
}

// 确认与请求统一包在 try 内：用户点取消时 confirm 的拒绝被 catch 吞掉，
// 避免 `await confirm().catch(throw)` 把取消异常抛出 async 函数形成未处理的 Promise 拒绝。
async function handleSetDefault(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.receiver}」设为默认收货地址吗？`, '提示', {
      type: 'warning'
    })
    await setDefaultAddress(row.addressId)
    ElMessage.success('已设为默认地址')
    getList()
  } catch {
    /* 用户取消不提示；接口失败的提示由请求拦截器统一弹出 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.receiver}」的收货地址吗？此操作不可恢复`, '提示', {
      type: 'warning'
    })
    await delAddress(row.addressId)
    ElMessage.success('地址已删除')
    getList()
  } catch {
    /* 用户取消不提示；接口失败的提示由请求拦截器统一弹出 */
  }
}

onMounted(() => getList())
</script>

<style scoped lang="scss"></style>
