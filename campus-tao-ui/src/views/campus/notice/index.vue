<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">通知中心</h2>
          <p class="campus-head__desc">订单、发货、售后等事件的站内通知；点击列表项或「查看」标记已读</p>
        </div>
        <div class="campus-head__extra">
          <el-button v-if="unreadCount > 0" icon="Reading" @click="handleReadAll">全部已读</el-button>
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="campus-toolbar">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="72px">
          <el-form-item label="类型" prop="type">
            <el-select v-model="queryParams.type" placeholder="全部类型" clearable style="width: 150px">
              <el-option label="订单" value="order" />
              <el-option label="发货" value="ship" />
              <el-option label="售后" value="aftersale" />
              <el-option label="系统" value="system" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="isRead">
            <el-select v-model="queryParams.isRead" placeholder="全部状态" clearable style="width: 150px">
              <el-option label="未读" :value="0" />
              <el-option label="已读" :value="1" />
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

      <!-- 概览：未读数取服务端聚合，不是当前页筛出来的条数 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">未读通知</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ unreadCount }}</div>
          <div class="campus-stat__hint">铃铛红点与这里一致</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">当前筛选</div>
          <div class="campus-stat__value">{{ total }}</div>
          <div class="campus-stat__hint">符合筛选条件的通知总数</div>
        </div>
      </div>

      <!-- 通知清单 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">通知列表</span>
        </div>

        <el-table v-loading="loading" :data="noticeList">
          <el-table-column label="时间" align="center" width="170">
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <el-table-column label="类型" align="center" width="90">
            <template #default="scope">
              <el-tag :type="typeTag(scope.row.type).type" effect="light">{{ typeTag(scope.row.type).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
          <el-table-column label="内容" prop="content" min-width="260" show-overflow-tooltip />
          <el-table-column label="业务号" width="190">
            <template #default="scope">
              <span class="campus-num">{{ scope.row.bizNo || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.isRead === 1 ? 'info' : 'danger'" effect="plain">
                {{ scope.row.isRead === 1 ? '已读' : '未读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="110" fixed="right">
            <template #default="scope">
              <el-button v-if="scope.row.isRead !== 1" link type="primary" @click="handleRead(scope.row)"
                >标为已读</el-button
              >
              <span v-else class="campus-text--muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无通知</div>
              <div class="campus-empty__desc">下单、发货、售后审核等事件触发后，通知会出现在这里</div>
            </div>
          </template>
        </el-table>

        <!--
          分页器：通知数随订单量线性增长，不能一次全量返回。
          el-pagination 的 current-change 回传新页码（数字），size-change 回传新每页条数。
        -->
        <el-pagination
          v-if="total > 0"
          class="notice-pager"
          :current-page="queryParams.pageNum"
          :page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup name="CampusNotice">
import { onMounted, reactive, ref } from 'vue'
import { parseTime } from '@/utils/ruoyi'
import {
  getCampusNoticeUnreadCount,
  pageCampusNotice,
  readAllCampusNotice,
  readCampusNotice
} from '@/api/campus/notice'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const noticeList = ref([])
/** 未读总数由服务端聚合返回，与筛选条件解耦——否则改了筛选红点就"消失"了 */
const unreadCount = ref(0)
/** 通知总数由分页接口的 total 给出，不是当前页长度 */
const total = ref(0)

const queryParams = reactive({
  type: undefined,
  isRead: undefined,
  bizNo: undefined,
  pageNum: 1,
  pageSize: 10
})

const TYPE_MAP = {
  order: { text: '订单', type: 'warning' },
  ship: { text: '发货', type: 'primary' },
  aftersale: { text: '售后', type: 'danger' },
  system: { text: '系统', type: 'info' }
}

function typeTag(type) {
  return TYPE_MAP[type] || { text: type || '未知', type: 'info' }
}

/** 列表查询：type / isRead 由后端筛，bizNo 后端不支持故在前端按包含关系补一刀 */
async function getList() {
  loading.value = true
  try {
    const [pageRes, countRes] = await Promise.all([pageCampusNotice(buildQuery()), getCampusNoticeUnreadCount()])
    let rows = pageRes.rows || []
    const keyword = (queryParams.bizNo || '').trim()
    if (keyword) {
      // 前端补筛的代价是"总页数仍按后端口径算"，但结果是当前筛选的真实子集，
      // 比"完全不支持业务号查询"好；要彻底支持得后端加 biz_no like 条件。
      rows = rows.filter((r) => (r.bizNo || '').includes(keyword))
    }
    noticeList.value = rows
    total.value = pageRes.total || 0
    unreadCount.value = countRes.data ?? 0
  } finally {
    loading.value = false
  }
}

/**
 * 组装查询参数。
 *
 * 必须剔除 undefined：`{ isRead: undefined }` 序列化后会变成字符串 "undefined"
 * 传到后端，后端按非 null 处理就会拼出 `is_read = 'undefined'`，静默查不到数据。
 */
function buildQuery() {
  const q = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
  if (queryParams.type) q.type = queryParams.type
  if (queryParams.isRead !== undefined) q.isRead = queryParams.isRead
  return q
}

function handleQuery() {
  // 改筛选条件一律回到第 1 页，否则可能停留在"新条件下不存在的页码"上拿到空列表
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.type = undefined
  queryParams.isRead = undefined
  queryParams.bizNo = undefined
  queryParams.pageNum = 1
  getList()
}

/** 翻页：el-pagination 的 current-change 直接回传新页码（数字） */
function handleCurrentChange(pageNum) {
  queryParams.pageNum = pageNum
  getList()
}

/** 改每页条数：必须回到第 1 页，理由同 resetQuery */
function handleSizeChange(pageSize) {
  queryParams.pageSize = pageSize
  queryParams.pageNum = 1
  getList()
}

/** 单条已读：成功后必须重新拉未读总数，否则红点不降 */
async function handleRead(row) {
  await readCampusNotice(row.noticeId)
  ElMessage.success('已标记为已读')
  await getList()
}

async function handleReadAll() {
  await readAllCampusNotice()
  ElMessage.success('已全部标记已读')
  await getList()
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.campus-text--muted {
  color: var(--el-text-color-disabled);
}

.notice-pager {
  margin-top: 8px;
  padding: 8px 0 0;
  border-top: 1px solid var(--el-border-color-lighter);
  justify-content: flex-end;
}
</style>
