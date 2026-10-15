<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">通知外呼日志</h2>
          <p class="campus-head__desc">站内通知为必达链路；短信/邮件为可选外呼链路，未配置网关时自动降级并在本页留痕</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="campus-toolbar">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="72px">
          <el-form-item label="渠道" prop="channel">
            <el-select v-model="queryParams.channel" placeholder="全部渠道" clearable style="width: 140px">
              <el-option label="短信" value="sms" />
              <el-option label="邮件" value="email" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部结果" clearable style="width: 160px">
              <el-option label="发送成功" :value="1" />
              <el-option label="发送失败" :value="0" />
              <el-option label="未启用（已降级）" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务号" prop="bizNo">
            <el-input
              v-model="queryParams.bizNo"
              placeholder="订单号 / 售后单号"
              clearable
              style="width: 220px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">留痕总数</div>
          <div class="campus-stat__value">{{ logList.length }}</div>
          <div class="campus-stat__hint">当前筛选条件下的记录数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">发送成功</div>
          <div class="campus-stat__value">{{ countByStatus(1) }}</div>
          <div class="campus-stat__hint">网关已受理</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">发送失败</div>
          <div class="campus-stat__value">{{ countByStatus(0) }}</div>
          <div class="campus-stat__hint">目标缺失或网关异常</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">未启用（已降级）</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ countByStatus(2) }}</div>
          <div class="campus-stat__hint">未配置网关，仅站内通知</div>
        </div>
      </div>

      <!-- 留痕清单 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">外呼留痕清单</span>
        </div>

        <el-table v-loading="loading" :data="logList">
          <el-table-column label="时间" align="center" width="165">
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <el-table-column label="渠道" align="center" width="90">
            <template #default="scope">
              <span class="campus-badge">{{ channelText(scope.row.channel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="接收目标" width="170">
            <template #default="scope">
              <span class="campus-num">{{ scope.row.target || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
          <el-table-column label="内容" prop="content" min-width="220" show-overflow-tooltip />
          <el-table-column label="业务号" width="190">
            <template #default="scope">
              <span class="campus-num">{{ scope.row.bizNo || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" align="center" width="110">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.status)" effect="light">{{ statusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" align="center" width="90">
            <template #default="scope">{{ scope.row.costMs != null ? scope.row.costMs + ' ms' : '—' }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <span :class="{ 'trace-fail': scope.row.status === 0 }">{{
                scope.row.errorMsg || scope.row.response || '—'
              }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无外呼留痕</div>
              <div class="campus-empty__desc">下单、发货、售后审核等事件触发通知时会在此留痕</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { parseTime } from '@/utils/ruoyi'
import { listNotifyLog } from '@/api/campus/notifylog'

const logList = ref([])
const loading = ref(false)

const queryParams = reactive({
  channel: undefined,
  status: undefined,
  bizNo: undefined
})

const channelMap = {
  sms: '短信',
  email: '邮件'
}

const statusMap = {
  0: { text: '失败', type: 'danger' },
  1: { text: '成功', type: 'success' },
  2: { text: '未启用', type: 'info' }
}

function channelText(channel) {
  return channelMap[channel] || channel || '—'
}

function statusText(status) {
  return statusMap[status] ? statusMap[status].text : '未知'
}

function statusType(status) {
  return statusMap[status] ? statusMap[status].type : 'info'
}

function countByStatus(status) {
  return logList.value.filter((row) => row.status === status).length
}

function getList() {
  loading.value = true
  listNotifyLog(queryParams)
    .then((res) => {
      logList.value = res.data || []
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  queryParams.channel = undefined
  queryParams.status = undefined
  queryParams.bizNo = undefined
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.trace-fail {
  color: var(--el-color-danger);
}
</style>
