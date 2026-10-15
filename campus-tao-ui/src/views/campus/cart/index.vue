<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">购物车</h2>
          <p class="campus-head__desc">勾选想要的商品，确认收货信息后一键下单</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">商品种类</div>
          <div class="campus-stat__value">{{ cartList.length }}</div>
          <div class="campus-stat__hint">购物车中的商品条目数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">已选件数</div>
          <div class="campus-stat__value">{{ selectedCount }}</div>
          <div class="campus-stat__hint">选中项的数量合计</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待结算金额</div>
          <div class="campus-stat__value campus-stat__value--accent">¥{{ selectedAmount }}</div>
          <div class="campus-stat__hint">沙箱余额支付，不涉及真实资金</div>
        </div>
      </div>

      <!-- 购物车清单 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">商品清单</span>
          <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="!selectedRows.length"
            @click="handleDeleteSelected"
            v-hasPermi="['campus:cart:remove']"
            >删除选中</el-button
          >
        </div>

        <el-table
          ref="cartTableRef"
          v-loading="loading"
          :data="cartList"
          row-key="id"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" align="center" reserve-selection />
          <el-table-column label="商品" min-width="280">
            <template #default="scope">
              <div class="cart-cell">
                <el-image
                  v-if="scope.row.image"
                  :src="scope.row.image"
                  class="campus-thumb campus-thumb--lg"
                  fit="cover"
                />
                <div v-else class="campus-thumb campus-thumb--lg thumb-placeholder">无图</div>
                <div class="cart-cell__main">
                  <span class="cart-cell__title">{{ scope.row.title || '商品 #' + scope.row.productId }}</span>
                  <span class="cart-cell__meta">
                    单价 <span class="campus-price">¥{{ scope.row.price }}</span>
                    <span v-if="scope.row.stock != null"> · 库存 {{ scope.row.stock }}</span>
                  </span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="110">
            <template #default="scope">
              <span v-if="scope.row.status === 1" class="campus-badge campus-badge--primary">在售</span>
              <span v-else class="campus-badge campus-badge--accent">已下架</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" align="center" width="160">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantity"
                :min="1"
                :max="scope.row.stock || 999"
                size="small"
                @change="handleQuantityChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" align="center" width="130">
            <template #default="scope">
              <span class="campus-price">¥{{ subtotal(scope.row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="100">
            <template #default="scope">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
                v-hasPermi="['campus:cart:remove']"
                >移除</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">购物车还是空的</div>
              <div class="campus-empty__desc">去「商品管理」挑选心仪的二手好物吧</div>
            </div>
          </template>
        </el-table>

        <!-- 结算条 -->
        <div class="campus-summary">
          <span class="campus-summary__meta">已选 {{ selectedCount }} 件商品</span>
          <span class="campus-summary__meta">合计</span>
          <span class="campus-summary__total">¥{{ selectedAmount }}</span>
          <el-button
            type="primary"
            size="large"
            :disabled="!selectedRows.length"
            @click="openCheckout"
            v-hasPermi="['campus:order:add']"
            >去结算</el-button
          >
        </div>
      </div>
    </div>

    <!-- 结算对话框 -->
    <el-dialog v-model="checkoutOpen" title="确认订单信息" width="520px" append-to-body>
      <el-form :model="checkoutForm" :rules="checkoutRules" ref="checkoutRef" label-width="90px">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="checkoutForm.receiverName" placeholder="请输入收货人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="receiverPhone">
          <el-input v-model="checkoutForm.receiverPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="收货地址" prop="address">
          <el-input v-model="checkoutForm.address" placeholder="例如：3 号宿舍楼 502 室" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="checkoutForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，可约定当面交易的时间地点"
          />
        </el-form-item>
      </el-form>

      <div class="checkout-total">
        <span>应付金额</span>
        <span class="campus-summary__total">¥{{ selectedAmount }}</span>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="checkoutOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitOrder">提交订单</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCart, updateCartQty, delCart } from '@/api/campus/cart'
import { getProduct } from '@/api/campus/product'
import { createOrder } from '@/api/campus/order'

const router = useRouter()

const cartList = ref([])
const loading = ref(true)
const selectedRows = ref([])
const cartTableRef = ref()
const checkoutRef = ref()
const checkoutOpen = ref(false)
const submitting = ref(false)

const checkoutForm = reactive({
  receiverName: '',
  receiverPhone: '',
  address: '',
  remark: ''
})

const checkoutRules = {
  receiverName: [{ required: true, message: '收货人不能为空', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '联系电话不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  address: [{ required: true, message: '收货地址不能为空', trigger: 'blur' }]
}

const selectedCount = computed(() => selectedRows.value.reduce((sum, row) => sum + (row.quantity || 0), 0))
const selectedAmount = computed(() => {
  const total = selectedRows.value.reduce((sum, row) => sum + (Number(row.price) || 0) * (row.quantity || 0), 0)
  return total.toFixed(2)
})

function subtotal(row) {
  const value = (Number(row.price) || 0) * (row.quantity || 0)
  return value.toFixed(2)
}

// 首图取商品 images 的第一段
function firstImage(images) {
  if (!images) return ''
  return String(images).split(',')[0].trim()
}

// 购物车表只返回商品 ID，这里逐条补齐标题、价格、图片与库存
function enrichCart(rows) {
  return Promise.all(
    rows.map((row) => {
      return getProduct(row.productId)
        .then((res) => {
          const p = res.data || {}
          return {
            ...row,
            title: p.title,
            price: p.price,
            image: firstImage(p.images),
            stock: p.stock,
            status: p.status
          }
        })
        .catch(() => ({ ...row, title: '商品 #' + row.productId, price: 0 }))
    })
  )
}

function getList() {
  loading.value = true
  const keepSelected = selectedRows.value.map((row) => row.id)
  listCart()
    .then((res) => {
      return enrichCart(res.data || [])
    })
    .then((rows) => {
      cartList.value = rows
      loading.value = false
      nextTick(() => {
        rows.forEach((row) => {
          if (keepSelected.includes(row.id) || row.checked === 1) {
            cartTableRef.value && cartTableRef.value.toggleRowSelection(row, true)
          }
        })
      })
    })
    .catch(() => {
      loading.value = false
    })
}

function handleSelectionChange(selection) {
  selectedRows.value = selection
}

function handleQuantityChange(row) {
  if (!row.quantity || row.quantity < 1) {
    row.quantity = 1
  }
  updateCartQty({ id: row.id, quantity: row.quantity }).then(() => {
    // 数量变化会影响小计与合计，保持选中状态即可
  })
}

function handleDelete(row) {
  ElMessageBox.confirm('确认将该商品移出购物车？', '移除确认', { type: 'warning' })
    .then(() => {
      delCart(row.id).then(() => {
        ElMessage.success('已移除')
        getList()
      })
    })
    .catch(() => {})
}

function handleDeleteSelected() {
  const ids = selectedRows.value.map((row) => row.id)
  if (!ids.length) return
  ElMessageBox.confirm('确认删除选中的 ' + ids.length + ' 项商品？', '删除确认', { type: 'warning' })
    .then(() => {
      delCart(ids.join(',')).then(() => {
        ElMessage.success('删除成功')
        selectedRows.value = []
        getList()
      })
    })
    .catch(() => {})
}

function openCheckout() {
  checkoutForm.receiverName = ''
  checkoutForm.receiverPhone = ''
  checkoutForm.address = ''
  checkoutForm.remark = ''
  checkoutOpen.value = true
}

function submitOrder() {
  checkoutRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    createOrder({
      cartIds: selectedRows.value.map((row) => row.id),
      ...checkoutForm
    })
      .then((res) => {
        checkoutOpen.value = false
        selectedRows.value = []
        ElMessage.success('下单成功，订单号 ' + res.data)
        router.push('/campus/order')
      })
      .catch(() => {})
      .finally(() => {
        submitting.value = false
      })
  })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.cart-cell {
  display: flex;
  align-items: center;
  gap: 14px;
}

.thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--c-ink-4);
  letter-spacing: 0.1em;
}

.cart-cell__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.cart-cell__title {
  color: var(--c-ink);
  font-weight: 500;
  line-height: 1.5;
}

.cart-cell__meta {
  font-size: 12.5px;
  color: var(--c-ink-4);
  letter-spacing: 0.04em;
}

.campus-card .pagination-container {
  padding: 0;
}

.checkout-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  padding: 14px 4px 0;
  border-top: 1px solid var(--c-line-soft);
  font-size: 13px;
  color: var(--c-ink-3);
  letter-spacing: 0.06em;
}
</style>
