<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">评价管理</h2>
          <p class="campus-head__desc">查看本人名下商品收到的买家评价，据此改进描述与成色标注</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">评价总数</div>
          <div class="campus-stat__value">{{ total }}</div>
          <div class="campus-stat__hint">名下商品累计收到的评价数</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">平均评分</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ avgRating }}</div>
          <div class="campus-stat__hint">按当前页评价计算</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">好评数</div>
          <div class="campus-stat__value">{{ goodCount }}</div>
          <div class="campus-stat__hint">4 分及以上</div>
        </div>
      </div>

      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">评价清单</span>
        </div>

        <el-table v-loading="loading" :data="reviewList">
          <el-table-column label="商品" min-width="220">
            <template #default="scope">
              <span class="review-product">{{ scope.row.productTitle || '（商品已删除）' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评分" align="center" width="160">
            <template #default="scope">
              <el-rate :model-value="scope.row.rating" disabled size="small" />
            </template>
          </el-table-column>
          <el-table-column label="评价内容" min-width="300">
            <template #default="scope">
              <span class="review-content">{{ scope.row.content || '该用户未填写评价内容' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="评价人" align="center" width="130">
            <template #default="scope">{{ scope.row.userName || '匿名用户' }}</template>
          </el-table-column>
          <el-table-column label="评价时间" align="center" width="165">
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无评价</div>
              <div class="campus-empty__desc">买家完成交易并评价后，评价会出现在这里</div>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { parseTime } from '@/utils/ruoyi'
import { listSellerReview } from '@/api/campus/review'

const reviewList = ref([])
const loading = ref(true)
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const avgRating = computed(() => {
  if (!reviewList.value.length) return '0.0'
  const sum = reviewList.value.reduce((acc, r) => acc + (Number(r.rating) || 0), 0)
  return (sum / reviewList.value.length).toFixed(1)
})

const goodCount = computed(() => reviewList.value.filter((r) => Number(r.rating) >= 4).length)

function getList() {
  loading.value = true
  listSellerReview(queryParams)
    .then((res) => {
      reviewList.value = res.rows || []
      total.value = res.total || 0
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.review-product {
  color: var(--c-ink);
  font-weight: 500;
}

.review-content {
  color: var(--c-ink-2);
  font-size: 13px;
  line-height: 1.6;
}
</style>
