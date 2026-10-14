<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">售后管理</h2>
          <p class="campus-head__desc">已发货或已完成的订单可申请售后，审核通过后自动退款并回补库存</p>
        </div>
        <div class="campus-head__extra">
          <el-button type="primary" icon="Plus" @click="openApply" v-hasPermi="['campus:aftersale:add']">申请售后</el-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="campus-toolbar" v-show="showSearch">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="72px">
          <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable style="width:240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="售后状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width:160px">
              <el-option label="待审核" :value="0" />
              <el-option label="已驳回" :value="2" />
              <el-option label="已退款" :value="3" />
            </el-select>
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
          <div class="campus-stat__label">售后单总数</div>
          <div class="campus-stat__value">{{ aftersaleList.length }}</div>
          <div class="campus-stat__hint">当前筛选条件下的记录数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">待审核</div>
          <div class="campus-stat__value">{{ countByStatus(0) }}</div>
          <div class="campus-stat__hint">需要处理</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">涉及退款金额</div>
          <div class="campus-stat__value campus-stat__value--accent">¥{{ refundTotal }}</div>
          <div class="campus-stat__hint">所有售后单的退款金额合计</div>
        </div>
      </div>

      <!-- 售后清单 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">售后清单</span>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </div>

        <el-table v-loading="loading" :data="aftersaleList">
          <el-table-column label="售后单号" prop="aftersaleNo" width="200">
            <template #default="scope"><span class="campus-num">{{ scope.row.aftersaleNo }}</span></template>
          </el-table-column>
          <el-table-column label="关联订单" width="200">
            <template #default="scope"><span class="campus-num">{{ scope.row.orderNo }}</span></template>
          </el-table-column>
          <el-table-column label="类型" align="center" width="110">
            <template #default="scope">
              <span class="campus-badge campus-badge--accent">{{ typeText(scope.row.type) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="退款金额" align="center" width="120">
            <template #default="scope"><span class="campus-price">¥{{ money(scope.row.refundAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="110">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.status)" effect="light">{{ statusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请原因" prop="reason" min-width="180" show-overflow-tooltip />
          <el-table-column label="申请人" align="center" prop="userName" width="120" />
          <el-table-column label="申请时间" align="center" width="165">
            <template #default="scope"><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="120" fixed="right">
            <template #default="scope">
              <el-button v-if="scope.row.status === 0" link type="primary" @click="openAudit(scope.row)" v-hasPermi="['campus:aftersale:edit']">审核</el-button>
              <span v-else class="campus-badge">{{ scope.row.handler || '已处理' }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无售后记录</div>
              <div class="campus-empty__desc">已发货的订单可在此发起售后申请</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 申请售后 -->
    <el-dialog v-model="applyOpen" title="申请售后" width="500px" append-to-body>
      <el-form :model="applyForm" :rules="applyRules" ref="applyRef" label-width="90px">
        <el-form-item label="订单号" prop="orderNo">
          <el-input v-model="applyForm.orderNo" placeholder="请输入需要售后的订单号" />
        </el-form-item>
        <el-form-item label="售后类型" prop="type">
          <el-radio-group v-model="applyForm.type">
            <el-radio :value="1">仅退款</el-radio>
            <el-radio :value="2">退货退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input v-model="applyForm.reason" type="textarea" :rows="4" placeholder="请说明商品问题与诉求" />
        </el-form-item>
      </el-form>
      <div class="campus-note">
        <span>仅「已发货」或「已完成」的订单可以申请售后。</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="applyOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitApply">提交申请</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 审核售后 -->
    <el-dialog v-model="auditOpen" title="审核售后单" width="500px" append-to-body>
      <div class="audit-summary">
        <span class="audit-summary__no campus-num">{{ auditForm.aftersaleNo }}</span>
        <span class="campus-summary__total">¥{{ money(auditForm.refundAmount) }}</span>
      </div>
      <el-form :model="auditForm" :rules="auditRules" ref="auditRef" label-width="90px">
        <el-form-item label="审核结论" prop="pass">
          <el-radio-group v-model="auditForm.pass">
            <el-radio :value="true">通过并退款</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="auditForm.remark" type="textarea" :rows="3" placeholder="填写审核意见" />
        </el-form-item>
      </el-form>
      <div class="campus-note campus-note--accent">
        <span>通过后将向申请人沙箱账户退款并回补商品库存，该操作不可撤销。</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="auditOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitAudit">提交审核</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { listAftersale, applyAftersale, auditAftersale } from '@/api/campus/aftersale'

const aftersaleList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const applyOpen = ref(false)
const auditOpen = ref(false)
const submitting = ref(false)

const queryRef = ref()
const applyRef = ref()
const auditRef = ref()

const queryParams = reactive({
  orderNo: undefined,
  status: undefined
})

const applyForm = reactive({
  orderNo: '',
  type: 1,
  reason: ''
})

const auditForm = reactive({
  aftersaleNo: '',
  refundAmount: 0,
  pass: true,
  remark: ''
})

const applyRules = {
  orderNo: [{ required: true, message: '订单号不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择售后类型', trigger: 'change' }],
  reason: [{ required: true, message: '申请原因不能为空', trigger: 'blur' }]
}

const auditRules = {
  pass: [{ required: true, message: '请选择审核结论', trigger: 'change' }]
}

const refundTotal = computed(() => {
  const total = aftersaleList.value.reduce((sum, row) => sum + (Number(row.refundAmount) || 0), 0)
  return total.toFixed(2)
})

const statusMap = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'primary' },
  2: { text: '已驳回', type: 'danger' },
  3: { text: '已退款', type: 'success' }
}

function statusText(status) {
  return statusMap[status] ? statusMap[status].text : '未知'
}

function statusType(status) {
  return statusMap[status] ? statusMap[status].type : 'info'
}

function typeText(type) {
  return type === 2 ? '退货退款' : '仅退款'
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function countByStatus(status) {
  return aftersaleList.value.filter(row => row.status === status).length
}

function getList() {
  loading.value = true
  listAftersale(queryParams).then(res => {
    aftersaleList.value = res.data || []
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  queryRef.value.resetFields()
  getList()
}

function openApply() {
  applyForm.orderNo = ''
  applyForm.type = 1
  applyForm.reason = ''
  applyOpen.value = true
}

function submitApply() {
  applyRef.value.validate(valid => {
    if (!valid) return
    submitting.value = true
    applyAftersale({ ...applyForm }).then(res => {
      ElMessage.success('申请已提交，售后单号 ' + res.data)
      applyOpen.value = false
      getList()
    }).catch(() => {}).finally(() => {
      submitting.value = false
    })
  })
}

function openAudit(row) {
  auditForm.aftersaleNo = row.aftersaleNo
  auditForm.refundAmount = row.refundAmount
  auditForm.pass = true
  auditForm.remark = ''
  auditOpen.value = true
}

function submitAudit() {
  auditRef.value.validate(valid => {
    if (!valid) return
    submitting.value = true
    auditAftersale({
      aftersaleNo: auditForm.aftersaleNo,
      pass: auditForm.pass,
      remark: auditForm.remark
    }).then(() => {
      ElMessage.success('审核完成')
      auditOpen.value = false
      getList()
    }).catch(() => {}).finally(() => {
      submitting.value = false
    })
  })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.audit-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 18px;
  border-radius: var(--c-radius-sm);
  background: var(--c-line-soft);
}

.audit-summary__no {
  font-size: 13px;
  color: var(--c-ink-2);
  letter-spacing: .04em;
}

.campus-card .pagination-container {
  padding: 0;
}
</style>
