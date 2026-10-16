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
            <el-input
              v-model="queryParams.title"
              placeholder="商品标题"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
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
import { listProduct } from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'

const router = useRouter()

const productList = ref([])
const categoryOptions = ref([])
const loading = ref(true)
const total = ref(0)

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
    })
    .catch(() => {
      loading.value = false
    })
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
</style>
