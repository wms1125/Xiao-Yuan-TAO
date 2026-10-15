<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">我的订单</h2>
          <p class="campus-head__desc">查看订单状态，完成支付、发货与确认收货</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="refresh">刷新</el-button>
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
          <div class="campus-stat__value">{{ total }}</div>
          <div class="campus-stat__hint">当前筛选条件下的订单数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待付款</div>
          <div class="campus-stat__value">{{ statsCount(0) }}</div>
          <div class="campus-stat__hint">请及时完成沙箱支付</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待收货</div>
          <div class="campus-stat__value">{{ statsCount(2) }}</div>
          <div class="campus-stat__hint">卖家已发货，待确认</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">已完成</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ statsCount(3) }}</div>
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
                <el-image
                  v-if="firstImage(scope.row.items)"
                  :src="firstImage(scope.row.items)"
                  class="campus-thumb"
                  fit="cover"
                />
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
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <el-table-column label="操作" align="center" width="260" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="handleDetail(scope.row)">详情</el-button>
              <el-button
                v-if="scope.row.status === 0"
                link
                type="success"
                @click="handlePay(scope.row)"
                v-hasPermi="['campus:order:edit']"
                >支付</el-button
              >
              <el-button
                v-if="scope.row.status === 0 || scope.row.status === 1"
                link
                type="danger"
                @click="handleCancel(scope.row)"
                v-hasPermi="['campus:order:edit']"
                >取消</el-button
              >
              <el-button
                v-if="scope.row.status === 1"
                link
                type="primary"
                @click="handleShip(scope.row)"
                v-hasRole="['admin']"
                >发货</el-button
              >
              <el-button
                v-if="scope.row.status === 2"
                link
                type="success"
                @click="handleFinish(scope.row)"
                v-hasPermi="['campus:order:edit']"
                >确认收货</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无订单</div>
              <div class="campus-empty__desc">从购物车结算后，订单会出现在这里</div>
            </div>
          </template>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
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
            <template #default="scope"
              ><span class="campus-price">¥{{ money(scope.row.price) }}</span></template
            >
          </el-table-column>
          <el-table-column label="数量" align="center" prop="quantity" width="80" />
          <el-table-column label="小计" align="center" width="110">
            <template #default="scope"
              ><span class="campus-price">¥{{ money(scope.row.subtotal) }}</span></template
            >
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

        <!-- 物流轨迹 -->
        <div class="detail-trace">
          <div class="detail-trace__head">
            <span class="detail-trace__title">物流轨迹</span>
            <el-button
              v-if="detail.logisticsNo && logisticsStatus"
              link
              type="primary"
              size="small"
              :loading="traceLoading"
              v-hasRole="['admin']"
              @click="handleSyncTrace"
              >同步轨迹</el-button
            >
          </div>
          <div v-if="!detail.logisticsNo" class="detail-trace__empty">暂无物流单号，卖家发货后可查询轨迹</div>
          <div v-else-if="!logisticsStatus" class="detail-trace__empty">
            物流轨迹查询通道未配置，已降级为仅记录物流单号
          </div>
          <div v-else-if="!traceList.length" class="detail-trace__empty">
            暂无轨迹节点，可点击「同步轨迹」向承运商拉取
          </div>
          <el-timeline v-else class="detail-trace__list">
            <el-timeline-item
              v-for="trace in traceList"
              :key="trace.traceId"
              :timestamp="parseTime(trace.traceTime)"
              placement="top"
            >
              <div class="trace-node">
                <span class="trace-node__label">{{ trace.statusLabel || '轨迹' }}</span>
                <span class="trace-node__context">{{ trace.context }}</span>
              </div>
            </el-timeline-item>
          </el-timeline>
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
import {
  listOrder,
  orderStats,
  getOrder,
  payOrder,
  cancelOrder,
  shipOrder,
  finishOrder,
  getLogisticsTrace,
  syncLogisticsTrace
} from '@/api/campus/order'

const orderList = ref([])
const loading = ref(true)
const detailOpen = ref(false)
const detail = ref(null)
const traceList = ref([])
const traceLoading = ref(false)
const logisticsStatus = ref(false)
const total = ref(0)
// 概览数字改由服务端按状态聚合（分页后不能再遍历当前页推导）
const statusStats = ref({})

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
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

function statsCount(status) {
  const item = statusStats.value[status]
  return item ? item.count : 0
}

function getList() {
  loading.value = true
  listOrder(queryParams)
    .then((res) => {
      orderList.value = res.rows || []
      total.value = res.total || 0
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}

// 概览与列表使用同一筛选条件（分页参数不参与聚合）
function getStats() {
  orderStats({ status: queryParams.status, orderNo: queryParams.orderNo }).then((res) => {
    const map = {}
    const rows = res.data || []
    rows.forEach((row) => {
      map[row.status] = row
    })
    statusStats.value = map
  })
}

// 列表与概览一起刷新，避免两者不同步
function refresh() {
  getList()
  getStats()
}

function handleStatusFilter(status) {
  queryParams.status = status
  queryParams.pageNum = 1
  refresh()
}

function handleDetail(row) {
  getOrder(row.orderNo).then((res) => {
    detail.value = res.data
    detailOpen.value = true
    loadTrace(row.orderNo)
  })
}

// 加载物流轨迹：接口始终返回 providerEnabled，未配置通道时前端明确提示「已降级」
function loadTrace(orderNo) {
  traceList.value = []
  if (!orderNo) return
  getLogisticsTrace(orderNo)
    .then((res) => {
      const data = res.data || {}
      logisticsStatus.value = !!data.providerEnabled
      traceList.value = data.traces || []
    })
    .catch(() => {})
}

function handleSyncTrace() {
  if (!detail.value) return
  traceLoading.value = true
  syncLogisticsTrace(detail.value.orderNo)
    .then((res) => {
      ElMessage.success(res.msg || '同步完成')
      loadTrace(detail.value.orderNo)
      traceLoading.value = false
    })
    .catch(() => {
      traceLoading.value = false
    })
}

function handlePay(row) {
  ElMessageBox.confirm('确认支付订单「' + row.orderNo + '」？将从沙箱余额中扣款。', '支付确认', { type: 'warning' })
    .then(() => {
      payOrder(row.orderNo).then(() => {
        ElMessage.success('支付成功')
        refresh()
      })
    })
    .catch(() => {})
}

function handleCancel(row) {
  ElMessageBox.confirm('确认取消订单「' + row.orderNo + '」？已付款订单将退回沙箱余额。', '取消确认', {
    type: 'warning'
  })
    .then(() => {
      cancelOrder(row.orderNo).then(() => {
        ElMessage.success('已取消')
        refresh()
      })
    })
    .catch(() => {})
}

function handleShip(row) {
  ElMessageBox.prompt('请输入物流单号（可留空）', '发货确认：' + row.orderNo, {
    confirmButtonText: '确认发货',
    cancelButtonText: '取消',
    inputPlaceholder: '如 SF1234567890',
    inputValidator: (value) => !value || value.length <= 64 || '物流单号不超过 64 个字符',
    type: 'warning'
  })
    .then(({ value }) => {
      shipOrder(row.orderNo, value || '').then(() => {
        ElMessage.success('已发货')
        refresh()
      })
    })
    .catch(() => {})
}

function handleFinish(row) {
  ElMessageBox.confirm('确认已收到商品「' + row.orderNo + '」？', '确认收货', { type: 'warning' })
    .then(() => {
      finishOrder(row.orderNo).then(() => {
        ElMessage.success('已确认收货')
        refresh()
      })
    })
    .catch(() => {})
}

onMounted(() => {
  refresh()
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
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.18s ease;
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
  letter-spacing: 0.1em;
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
  letter-spacing: 0.04em;
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
  letter-spacing: 0.04em;
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
  letter-spacing: 0.06em;
}

.timeline-item__time {
  color: var(--c-ink-2);
}

/* 物流轨迹 */
.detail-trace {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--c-line-soft);
}

.detail-trace__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.detail-trace__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-ink);
  letter-spacing: 0.06em;
}

.detail-trace__empty {
  padding: 10px 0;
  font-size: 12.5px;
  color: var(--c-ink-4);
  letter-spacing: 0.04em;
}

.detail-trace__list {
  padding-top: 6px;
  padding-left: 2px;
}

.trace-node {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.trace-node__label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--c-primary);
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.trace-node__context {
  font-size: 12.5px;
  color: var(--c-ink-2);
}
</style>
