<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">商品详情</h2>
          <p class="campus-head__desc">查看商品信息与历史评价，确认后加入购物车结算</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Back" @click="goBack">返回商城</el-button>
        </div>
      </div>

      <div v-loading="loading" class="detail-wrap">
        <template v-if="product">
          <div class="detail-main">
            <!-- 图片区 -->
            <div class="detail-gallery">
              <div class="detail-gallery__main">
                <img v-if="activeImage" :src="activeImage" alt="" />
                <span v-else>暂无图片</span>
              </div>
              <div v-if="imageList.length > 1" class="detail-gallery__thumbs">
                <div
                  v-for="(img, idx) in imageList"
                  :key="idx"
                  class="detail-gallery__thumb"
                  :class="{ 'is-active': img === activeImage }"
                  @click="activeImage = img"
                >
                  <img :src="img" alt="" />
                </div>
              </div>
            </div>

            <!-- 信息区 -->
            <div class="detail-info">
              <h3 class="detail-info__title">{{ product.title }}</h3>

              <div class="detail-info__price">
                <span class="campus-price campus-price--lg">¥{{ money(product.price) }}</span>
                <span v-if="product.originalPrice" class="detail-info__origin">
                  原价 ¥{{ money(product.originalPrice) }}
                </span>
              </div>

              <div class="detail-info__tags">
                <span class="campus-badge campus-badge--gold">{{ conditionText(product.conditionLevel) }}</span>
                <span class="campus-badge campus-badge--primary">库存 {{ product.stock }} 件</span>
                <span class="campus-badge">{{ product.status === 1 ? '在售' : '已下架' }}</span>
              </div>

              <el-descriptions :column="1" border size="small" class="detail-info__desc">
                <el-descriptions-item label="卖家">{{ product.sellerName || '匿名同学' }}</el-descriptions-item>
                <el-descriptions-item label="分类">{{ categoryName }}</el-descriptions-item>
                <el-descriptions-item label="发布时间">{{ parseTime(product.createTime) }}</el-descriptions-item>
              </el-descriptions>

              <div class="detail-info__block">
                <div class="detail-block__title">商品描述</div>
                <div class="detail-block__text">{{ product.description || '卖家未填写描述' }}</div>
              </div>

              <div class="detail-actions">
                <el-input-number v-model="quantity" :min="1" :max="maxQuantity" size="large" />
                <el-button
                  type="primary"
                  size="large"
                  icon="ShoppingCart"
                  :disabled="!canBuy"
                  v-hasPermi="['campus:cart:add']"
                  @click="handleAddCart"
                  >加入购物车</el-button
                >
                <el-button size="large" @click="goCart">去购物车结算</el-button>
              </div>
              <div v-if="!canBuy" class="detail-actions__hint">
                {{ buyBlockReason }}
              </div>
            </div>
          </div>

          <!-- 评价区 -->
          <div class="campus-card detail-reviews">
            <div class="campus-card__head">
              <span class="campus-card__title">商品评价（{{ reviewList.length }}）</span>
              <span v-if="reviewList.length" class="detail-reviews__avg"> 平均 {{ avgRating }} 分 </span>
            </div>
            <div class="campus-card__body">
              <div v-if="!reviewList.length" class="campus-empty">
                <div class="campus-empty__title">暂无评价</div>
                <div class="campus-empty__desc">完成交易后即可评价这件商品</div>
              </div>
              <div v-else class="review-list">
                <div v-for="r in reviewList" :key="r.id" class="review-item">
                  <div class="review-item__head">
                    <span class="review-item__user">{{ r.userName || '匿名用户' }}</span>
                    <el-rate :model-value="r.rating" disabled size="small" />
                    <span class="review-item__time campus-num">{{ parseTime(r.createTime) }}</span>
                  </div>
                  <div class="review-item__content">{{ r.content || '该用户未填写评价内容' }}</div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <div v-else-if="!loading" class="campus-empty">
          <div class="campus-empty__title">商品不存在</div>
          <div class="campus-empty__desc">该商品可能已被删除或下架</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { getProduct } from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'
import { addCart } from '@/api/campus/cart'
import { listReviewByProduct } from '@/api/campus/review'
import useUserStore from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref(null)
const reviewList = ref([])
const categoryOptions = ref([])
const loading = ref(true)
const quantity = ref(1)
const activeImage = ref('')

const productId = computed(() => route.query.productId)

const imageList = computed(() => {
  if (!product.value || !product.value.images) return []
  return String(product.value.images)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
})

const maxQuantity = computed(() => {
  const stock = product.value ? Number(product.value.stock) || 0 : 0
  return stock > 0 ? stock : 1
})

// 自己发布的商品不能购买（与后端 createOrder 的校验口径一致）
const isOwner = computed(() => {
  if (!product.value || !product.value.sellerId) return false
  return String(product.value.sellerId) === String(userStore.id)
})

const canBuy = computed(() => {
  if (!product.value) return false
  if (product.value.status !== 1) return false
  if (Number(product.value.stock) <= 0) return false
  if (isOwner.value) return false
  return true
})

const buyBlockReason = computed(() => {
  if (!product.value) return ''
  if (product.value.status !== 1) return '商品已下架，无法购买'
  if (Number(product.value.stock) <= 0) return '商品库存不足，无法购买'
  if (isOwner.value) return '不能购买自己发布的商品'
  return ''
})

const categoryName = computed(() => {
  if (!product.value) return '—'
  const hit = categoryOptions.value.find((c) => String(c.id) === String(product.value.categoryId))
  return hit ? hit.name : '—'
})

const avgRating = computed(() => {
  if (!reviewList.value.length) return '0.0'
  const sum = reviewList.value.reduce((acc, r) => acc + (Number(r.rating) || 0), 0)
  return (sum / reviewList.value.length).toFixed(1)
})

function conditionText(level) {
  const map = { 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '二手老旧' }
  return map[level] || '未知'
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function loadProduct() {
  if (!productId.value) {
    loading.value = false
    return
  }
  getProduct(productId.value)
    .then((res) => {
      product.value = res.data
      activeImage.value = imageList.value[0] || ''
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}

function loadReviews() {
  if (!productId.value) return
  listReviewByProduct(productId.value)
    .then((res) => {
      reviewList.value = res.data || []
    })
    .catch(() => {})
}

function loadCategory() {
  listCategory({ pageNum: 1, pageSize: 200 }).then((res) => {
    categoryOptions.value = res.rows || []
  })
}

function handleAddCart() {
  addCart({ productId: Number(productId.value), quantity: quantity.value }).then((res) => {
    ElMessage.success(res.msg || '已加入购物车')
  })
}

function goCart() {
  router.push({ path: '/campus/cart' })
}

function goBack() {
  router.push({ path: '/campus/mall' })
}

onMounted(() => {
  loadCategory()
  loadProduct()
  loadReviews()
})
</script>

<style lang="scss" scoped>
.detail-wrap {
  min-height: 240px;
}

.detail-main {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.detail-gallery__main {
  height: 280px;
  border: 1px solid var(--c-line);
  border-radius: var(--c-radius);
  background: var(--c-line-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-ink-4);
  font-size: 13px;
  letter-spacing: 0.1em;
  overflow: hidden;
}

.detail-gallery__main img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-gallery__thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.detail-gallery__thumb {
  width: 56px;
  height: 56px;
  border-radius: var(--c-radius-sm);
  border: 1px solid var(--c-line);
  overflow: hidden;
  cursor: pointer;
}

.detail-gallery__thumb.is-active {
  border-color: var(--c-primary);
}

.detail-gallery__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-info {
  background: var(--c-card);
  border: 1px solid var(--c-line);
  border-radius: var(--c-radius);
  padding: 18px 20px;
}

.detail-info__title {
  margin: 0;
  font-family: var(--c-font-serif);
  font-size: 19px;
  font-weight: 600;
  color: var(--c-ink);
  line-height: 1.5;
}

.detail-info__price {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-top: 12px;
}

.detail-info__origin {
  font-size: 13px;
  color: var(--c-ink-4);
  text-decoration: line-through;
}

.detail-info__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-info__desc {
  margin-top: 16px;
}

.detail-info__block {
  margin-top: 16px;
}

.detail-block__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-ink);
  letter-spacing: 0.06em;
  margin-bottom: 6px;
}

.detail-block__text {
  font-size: 13px;
  color: var(--c-ink-2);
  line-height: 1.7;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--c-line-soft);
}

.detail-actions__hint {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--c-danger);
}

.detail-reviews {
  margin-top: 20px;
}

.detail-reviews__avg {
  font-size: 13px;
  color: var(--c-accent);
  font-weight: 600;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-item {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-line-soft);
}

.review-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.review-item__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-item__user {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-ink);
}

.review-item__time {
  margin-left: auto;
  font-size: 12px;
  color: var(--c-ink-4);
}

.review-item__content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--c-ink-2);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .detail-main {
    grid-template-columns: 1fr;
  }
}
</style>
