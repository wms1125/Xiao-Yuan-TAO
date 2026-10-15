<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">我的订单</h2>
          <p class="campus-head__desc">查看订单状态，完成支付、发货与确认收货</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 状态筛选 -->
      <div class="status-filter">
        <button
          v-for="item in statusTabs"
          :key="item.value"
          class="status-chip"
          :class="{ 'is-active': queryParams.status === item.value }"
          @click="handleStatusFilter(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">订单总数</div>
          <div class="campus-stat__value">{{ orderList.length }}</div>
          <div class="campus-stat__hint">当前筛选条件下的订单数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待付款</div>
          <div class="campus-stat__value">{{ countByStatus(0) }}</div>
          <div class="campus-stat__hint">请及时完成沙箱支付</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待收货</div>
          <div class="campus-stat__value">{{ countByStatus(2) }}</div>
          <div class="campus-stat__hint">卖家已发货，待确认</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">已完成</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ countByStatus(3) }}</div>
          <div class="campus-stat__hint">交易已结束</div>
        </div>
      </div>

      <!-- 订单列表 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">订单清单</span>
        </div>

        <el-table v-loading="loading" :data="orderList">
          <el-table-column label="订单" min-width="300">
            <template #default="scope">
              <div class="order-cell">
                <el-image v-if="firstImage(scope.row.items)" :src="firstImage(scope.row.items)" class="campus-thumb" fit="cover" />
                <div v-else class="campus-thumb thumb-placeholder">无图</div>
                <div class="order-cell__main">
                  <span class="order-cell__title">{{ mainTitle(scope.row.items) }}</span>
                  <span class="order-cell__meta">
                    <span class="campus-num">{{ scope.row.orderNo }}</span>
                  </span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="件数" align="center" width="90">
            <template #default="scope">{{ itemCount(scope.row.items) }} 件</template>
          </el-table-column>
          <el-table-column label="金额" align="center" width="120">
            <template #default="scope">
              <span class="campus-price">¥{{ money(scope.row.totalAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="120">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.status)" effect="light">{{ statusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" align="center" width="165">
            <template #default="scope"><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="260" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="handleDetail(scope.row)">详情</el-button>
              <el-button v-if="scope.row.status === 0" link type="success" @click="handlePay(scope.row)" v-hasPermi="['campus:order:edit']">支付</el-button>
              <el-button v-if="scope.row.status === 0 || scope.row.status === 1" link type="danger" @click="handleCancel(scope.row)" v-hasPermi="['campus:order:edit']">取消</el-button>
              <el-button v-if="scope.row.status === 1" link type="primary" @click="handleShip(scope.row)" v-hasRole="['admin']">发货</el-button>
              <el-button v-if="scope.row.status === 2" link type="success" @click="handleFinish(scope.row)" v-hasPermi="['campus:order:edit']">确认收货</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无订单</div>
              <div class="campus-empty__desc">从购物车结算后，订单会出现在这里</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 订单详情 -->
    <el-dialog v-model="detailOpen" title="订单详情" width="680px" append-to-body>
      <div v-if="detail" class="order-detail">
        <div class="detail-head">
          <div>
            <div class="detail-order-no campus-num">{{ detail.orderNo }}</div>
            <div class="detail-order-time">下单时间 {{ parseTime(detail.createTime) }}</div>
          </div>
          <el-tag :type="statusType(detail.status)" effect="light">{{ statusText(detail.status) }}</el-tag>
        </div>

        <el-table :data="detail.items || []" size="small">
          <el-table-column label="商品" prop="productTitle" min-width="200" />
          <el-table-column label="单价" align="center" width="100">
            <template #default="scope"><span class="campus-price">¥{{ money(scope.row.price) }}</span></template>
          </el-table-column>
          <el-table-column label="数量" align="center" prop="quantity" width="80" />
          <el-table-column label="小计" align="center" width="110">
            <template #default="scope"><span class="campus-price">¥{{ money(scope.row.subtotal) }}</span></template>
          </el-table-column>
        </el-table>

        <div class="detail-total">
          <span>订单金额</span>
          <span class="campus-summary__total">¥{{ money(detail.totalAmount) }}</span>
        </div>

        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="收货人">{{ detail.receiverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.receiverPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ detail.address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="物流单号" :span="2">
            <span class="campus-num">{{ detail.logisticsNo || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-timeline">
          <div class="timeline-item" v-for="node in timelineNodes" :key="node.label">
            <span class="timeline-item__dot" :class="{ 'is-on': !!node.time }"></span>
            <span class="timeline-item__label">{{ node.label }}</span>
            <span class="timeline-item__time campus-num">{{ node.time ? parseTime(node.time) : '—' }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { listOrder, getOrder, payOrder, cancelOrder, shipOrder, finishOrder } from '@/api/campus/order'

const orderList = ref([])
const loading = ref(true)
const detailOpen = ref(false)
const detail = ref(null)

const queryParams = reactive({
  status: undefined,
  orderNo: undefined
})

const statusTabs = [
  { label: '全部', value: undefined },
  { label: '待付款', value: 0 },
  { label: '已付款', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 }
]

const statusMap = {
  0: { text: '待付款', type: 'warning' },
  1: { text: '已付款', type: 'primary' },
  2: { text: '已发货', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' },
  5: { text: '退款中', type: 'warning' },
  6: { text: '已退款', type: 'info' }
}

const timelineNodes = computed(() => {
  if (!detail.value) return []
  return [
    { label: '提交订单', time: detail.value.createTime },
    { label: '完成支付', time: detail.value.payTime },
    { label: '卖家发货', time: detail.value.shipTime },
    { label: '交易完成', time: detail.value.finishTime },
    { label: '订单取消', time: detail.value.cancelTime }
  ]
})

function statusText(status) {
  return statusMap[status] ? statusMap[status].text : '未知'
}

function statusType(status) {
  return statusMap[status] ? statusMap[status].type : 'info'
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function itemCount(items) {
  if (!items || !items.length) return 0
  return items.reduce((sum, item) => sum + (item.quantity || 0), 0)
}

function mainTitle(items) {
  if (!items || !items.length) return '（无商品明细）'
  const first = items[0].productTitle || '商品'
  return items.length > 1 ? first + ' 等 ' + items.length + ' 种商品' : first
}

function firstImage(items) {
  if (!items || !items.length || !items[0].image) return ''
  return String(items[0].image).split(',')[0].trim()
}

function countByStatus(status) {
  return orderList.value.filter(row => row.status === status).length
}

function getList() {
  loading.value = true
  listOrder(queryParams).then(res => {
    orderList.value = res.data || []
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleStatusFilter(status) {
  queryParams.status = status
  getList()
}

function handleDetail(row) {
  getOrder(row.orderNo).then(res => {
    detail.value = res.data
    detailOpen.value = true
  })
}

function handlePay(row) {
  ElMessageBox.confirm('确认支付订单「' + row.orderNo + '」？将从沙箱余额中扣款。', '支付确认', { type: 'warning' }).then(() => {
    payOrder(row.orderNo).then(() => {
      ElMessage.success('支付成功')
      getList()
    })
  }).catch(() => {})
}

function handleCancel(row) {
  ElMessageBox.confirm('确认取消订单「' + row.orderNo + '」？已付款订单将退回沙箱余额。', '取消确认', { type: 'warning' }).then(() => {
    cancelOrder(row.orderNo).then(() => {
      ElMessage.success('已取消')
      getList()
    })
  }).catch(() => {})
}

function handleShip(row) {
  ElMessageBox.prompt('请输入物流单号（可留空）', '发货确认：' + row.orderNo, {
    confirmButtonText: '确认发货',
    cancelButtonText: '取消',
    inputPlaceholder: '如 SF1234567890',
    inputValidator: value => !value || value.length <= 64 || '物流单号不超过 64 个字符',
    type: 'warning'
  }).then(({ value }) => {
    shipOrder(row.orderNo, value || '').then(() => {
      ElMessage.success('已发货')
      getList()
    })
  }).catch(() => {})
}

function handleFinish(row) {
  ElMessageBox.confirm('确认已收到商品「' + row.orderNo + '」？', '确认收货', { type: 'warning' }).then(() => {
    finishOrder(row.orderNo).then(() => {
      ElMessage.success('已确认收货')
      getList()
    })
  }).catch(() => {})
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.status-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-chip {
  height: 30px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--c-line);
  background: var(--c-card);
  color: var(--c-ink-2);
  font-size: 13px;
  font-family: inherit;
  letter-spacing: .06em;
  cursor: pointer;
  transition: all .18s ease;
}

.status-chip:hover {
  border-color: var(--c-primary-line);
  color: var(--c-primary);
}

.status-chip.is-active {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #ffffff;
  font-weight: 600;
}

.order-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--c-ink-4);
  letter-spacing: .1em;
}

.order-cell__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.order-cell__title {
  color: var(--c-ink);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-cell__meta {
  font-size: 12px;
  color: var(--c-ink-4);
  letter-spacing: .04em;
}

/* 详情弹窗 */
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--c-line-soft);
}

.detail-order-no {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-ink);
  letter-spacing: .04em;
}

.detail-order-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-ink-4);
}

.detail-total {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding: 14px 2px;
  font-size: 13px;
  color: var(--c-ink-3);
}

.detail-desc {
  margin-top: 4px;
}

.detail-timeline {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--c-line-soft);
}

.timeline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  font-size: 12.5px;
  color: var(--c-ink-3);
}

.timeline-item__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--c-line);
  flex-shrink: 0;
}

.timeline-item__dot.is-on {
  background: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-soft);
}

.timeline-item__label {
  width: 76px;
  letter-spacing: .06em;
}

.timeline-item__time {
  color: var(--c-ink-2);
}
</style>
