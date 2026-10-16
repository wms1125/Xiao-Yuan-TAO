<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">校园商城</h2>
          <p class="campus-head__desc">浏览同学发布的在售闲置，点击卡片查看详情与评价</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="handleQuery">刷新</el-button>
        </div>
      </div>

      <!-- 筛选工具条 -->
      <div class="campus-toolbar">
        <el-form :inline="true" :model="queryParams" @submit.prevent>
          <el-form-item label="关键词">
            <div class="mall-search">
              <el-input
                v-model="queryParams.title"
                placeholder="商品标题"
                clearable
                style="width: 200px"
                @keyup.enter="handleQuery"
                @focus="toggleHistory"
              />
              <!-- 搜索历史下拉：聚焦时展示最近搜索，点击回填关键词 -->
              <div v-if="historyVisible" v-loading="historyLoading" class="mall-history">
                <div class="mall-history__head">
                  <span>最近搜索</span>
                  <el-button link type="primary" size="small" @click="handleClearHistory">清空</el-button>
                </div>
                <div v-if="!searchHistory.length" class="mall-history__empty">暂无搜索历史</div>
                <div
                  v-for="h in searchHistory"
                  :key="h.historyId"
                  class="mall-history__item"
                  @click="handlePickHistory(h.keyword)"
                >
                  <span class="mall-history__keyword">{{ h.keyword }}</span>
                  <el-icon class="mall-history__del" @click.stop="handleDelHistory(h)"><Close /></el-icon>
                </div>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="queryParams.categoryId" placeholder="全部分类" clearable style="width: 160px">
              <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="成色">
            <el-select v-model="queryParams.conditionLevel" placeholder="全部成色" clearable style="width: 140px">
              <el-option v-for="o in conditionOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 商品卡片网格 -->
      <div v-loading="loading" class="mall-body">
        <div v-if="productList.length" class="campus-grid">
          <div v-for="item in productList" :key="item.id" class="campus-product" @click="goDetail(item)">
            <div class="campus-product__cover">
              <img v-if="firstImage(item.images)" :src="firstImage(item.images)" alt="" />
              <span v-else>暂无图片</span>
              <!-- 收藏按钮：stop 阻止冒泡，否则点收藏会顺带跳进详情页 -->
              <el-tooltip :content="isFavored(item.id) ? '取消收藏' : '收藏'" placement="top">
                <el-icon
                  class="campus-product__fav"
                  :class="{ 'campus-product__fav--on': isFavored(item.id) }"
                  @click.stop="handleToggleFavorite(item)"
                  ><StarFilled v-if="isFavored(item.id)" /><Star v-else
                /></el-icon>
              </el-tooltip>
            </div>
            <div class="campus-product__info">
              <div class="campus-product__title">{{ item.title }}</div>
              <div class="mall-tags">
                <span class="campus-badge campus-badge--gold">{{ conditionText(item.conditionLevel) }}</span>
                <span class="campus-badge">库存 {{ item.stock }}</span>
              </div>
              <div class="campus-product__foot">
                <span class="campus-price">¥{{ money(item.price) }}</span>
                <span class="mall-seller">{{ item.sellerName || '匿名同学' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="!loading" class="campus-empty">
          <div class="campus-empty__title">暂无在售商品</div>
          <div class="campus-empty__desc">换个关键词或分类试试</div>
        </div>
      </div>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { listProduct } from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'
import { favoriteStatus, toggleFavorite } from '@/api/campus/favorite'
import {
  clearSearchHistory,
  delSearchHistory,
  listSearchHistory,
  recordSearchHistory
} from '@/api/campus/searchhistory'

const router = useRouter()

const productList = ref([])
const categoryOptions = ref([])
const loading = ref(true)
const total = ref(0)

/** 已收藏的商品 ID 集合：批量查询一次，避免逐商品 N+1 */
const favoredIds = ref([])

// ---- 搜索历史 ----
const historyVisible = ref(false)
const historyLoading = ref(false)
const searchHistory = ref([])

const conditionOptions = [
  { value: 1, label: '全新' },
  { value: 2, label: '几乎全新' },
  { value: 3, label: '轻微使用' },
  { value: 4, label: '明显使用' },
  { value: 5, label: '二手老旧' }
]

const queryParams = reactive({
  pageNum: 1,
  pageSize: 12,
  title: undefined,
  categoryId: undefined,
  conditionLevel: undefined,
  // 买家侧只看在售商品（下架商品不进商城）
  status: 1
})

function conditionText(level) {
  const map = { 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '二手老旧' }
  return map[level] || '未知'
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function firstImage(images) {
  if (!images) return ''
  const first = String(images).split(',')[0].trim()
  return first || ''
}

function getList() {
  loading.value = true
  listProduct(queryParams)
    .then((res) => {
      productList.value = res.rows || []
      total.value = res.total || 0
      loading.value = false
      // 商品到位后再批量取收藏状态
      loadFavored()
      // 搜索即记录历史：失败不影响搜索本身（记录是旁路，不是主流程）
      if (queryParams.title) {
        recordSearchHistory(queryParams.title).catch(() => {})
      }
    })
    .catch(() => {
      loading.value = false
    })
}

/** 批量取当前页商品的收藏状态 */
function loadFavored() {
  const ids = productList.value.map((p) => p.id)
  if (!ids.length) {
    favoredIds.value = []
    return
  }
  favoriteStatus(ids)
    .then((res) => {
      favoredIds.value = (res.data && res.data.favored) || []
    })
    .catch(() => {
      // 收藏状态拉取失败不阻塞商品浏览，只是按钮显示为未收藏
      favoredIds.value = []
    })
}

function isFavored(productId) {
  return favoredIds.value.includes(productId)
}

/** 切换收藏：成功/失败都据后端返回的 favored 前翻按钮 */
async function handleToggleFavorite(item) {
  try {
    const res = await toggleFavorite(item.id)
    const favored = res.data && res.data.favored
    if (favored) {
      if (!favoredIds.value.includes(item.id)) favoredIds.value.push(item.id)
    } else {
      favoredIds.value = favoredIds.value.filter((id) => id !== item.id)
    }
  } catch {
    /* 失败提示由请求拦截器统一弹出 */
  }
}

// ---- 搜索历史 ----

function toggleHistory() {
  historyVisible.value = !historyVisible.value
  if (historyVisible.value) loadHistory()
}

function loadHistory() {
  historyLoading.value = true
  listSearchHistory(10)
    .then((res) => {
      searchHistory.value = res.data || []
    })
    // 失败必须兜底：否则 rejected promise 没有任何 handler 会被浏览器当作
    // "Uncaught (in promise)" 抛到全局，既污染控制台、又可能打断聚焦后的渲染。
    // 历史查询是搜索框聚焦这一主流程上的旁路，失败应静默降级为「空态」而非报错。
    .catch(() => {
      searchHistory.value = []
    })
    .finally(() => {
      historyLoading.value = false
    })
}

/** 点击历史词回填并直接搜索 */
function handlePickHistory(keyword) {
  queryParams.title = keyword
  historyVisible.value = false
  handleQuery()
}

async function handleDelHistory(h) {
  try {
    await delSearchHistory(h.historyId)
    searchHistory.value = searchHistory.value.filter((x) => x.historyId !== h.historyId)
  } catch {
    /* 失败提示由请求拦截器统一弹出 */
  }
}

async function handleClearHistory() {
  try {
    await clearSearchHistory()
    searchHistory.value = []
  } catch {
    /* 失败提示由请求拦截器统一弹出 */
  }
}

function loadCategory() {
  listCategory({ pageNum: 1, pageSize: 200 }).then((res) => {
    categoryOptions.value = res.rows || []
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.title = undefined
  queryParams.categoryId = undefined
  queryParams.conditionLevel = undefined
  handleQuery()
}

function goDetail(item) {
  router.push({ path: '/campus/mall-detail', query: { productId: item.id } })
}

onMounted(() => {
  loadCategory()
  getList()
})
</script>

<style lang="scss" scoped>
.mall-body {
  min-height: 200px;
}

.mall-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mall-seller {
  font-size: 12px;
  color: var(--c-ink-4);
  letter-spacing: 0.04em;
}

/* 收藏按钮浮在商品封面右上角 */
.campus-product {
  position: relative;
}

.campus-product__cover {
  position: relative;
}

.campus-product__fav {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 6px;
  font-size: 18px;
  color: var(--c-ink-4);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  cursor: pointer;
  transition:
    color 0.2s ease,
    transform 0.2s ease;
}

.campus-product__fav:hover {
  color: var(--el-color-warning);
  transform: scale(1.12);
}

.campus-product__fav--on {
  color: var(--el-color-warning);
}

/* 搜索历史下拉 */
.mall-search {
  position: relative;
  display: inline-block;
}

.mall-history {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  width: 260px;
  padding: 8px 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
}

.mall-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px 6px;
  font-size: 12px;
  color: var(--c-ink-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.mall-history__empty {
  padding: 14px 12px;
  font-size: 12px;
  color: var(--c-ink-4);
  text-align: center;
}

.mall-history__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 12px;
  font-size: 13px;
  cursor: pointer;
}

.mall-history__item:hover {
  background: var(--el-fill-color-light);
}

.mall-history__keyword {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mall-history__del {
  flex-shrink: 0;
  margin-left: 8px;
  color: var(--c-ink-4);
  cursor: pointer;
}

.mall-history__del:hover {
  color: var(--el-color-danger);
}
</style>
