<template>
  <div class="app-container home">
    <div class="campus-page">
      <!-- 问候区 -->
      <section class="home-hero">
        <div class="home-hero__main">
          <div class="home-hero__greet">{{ greeting }}，{{ nickname }}</div>
          <p class="home-hero__slogan">让每一件旧物，在校园里重新被需要</p>
          <div class="home-hero__actions">
            <el-button type="primary" icon="Plus" @click="go('/campus/product')">发布商品</el-button>
            <el-button icon="ShoppingCart" @click="go('/campus/cart')">购物车</el-button>
            <el-button icon="List" @click="go('/campus/order')">我的订单</el-button>
            <el-button icon="Money" @click="go('/campus/account')">支付账户</el-button>
          </div>
        </div>
        <div class="home-hero__side">
          <div class="hero-side__label">可用余额</div>
          <div class="hero-side__value">¥{{ money(account.balance) }}</div>
          <div class="hero-side__hint">校园淘沙箱账户 · 不涉及真实资金</div>
        </div>
      </section>

      <!-- 指标 -->
      <div class="campus-stats">
        <div class="campus-stat" v-for="item in statCards" :key="item.label">
          <div class="campus-stat__label">{{ item.label }}</div>
          <div class="campus-stat__value" :class="{ 'campus-stat__value--accent': item.accent }">{{ item.value }}</div>
          <div class="campus-stat__hint">{{ item.hint }}</div>
        </div>
      </div>

      <el-row :gutter="16">
        <!-- 最近上架 -->
        <el-col :xs="24" :lg="14">
          <div class="campus-card">
            <div class="campus-card__head">
              <span class="campus-card__title">最近上架</span>
              <el-button link type="primary" @click="go('/campus/product')">全部商品</el-button>
            </div>
            <div class="campus-card__body">
              <div v-if="recentProducts.length" class="recent-products">
                <div class="recent-product" v-for="p in recentProducts" :key="p.id">
                  <el-image v-if="firstImage(p.images)" :src="firstImage(p.images)" class="campus-thumb" fit="cover" />
                  <div v-else class="campus-thumb thumb-placeholder">无图</div>
                  <div class="recent-product__main">
                    <span class="recent-product__title">{{ p.title }}</span>
                    <span class="recent-product__meta">{{ p.sellerName || '匿名卖家' }} · {{ conditionText(p.conditionLevel) }}</span>
                  </div>
                  <span class="campus-price">¥{{ money(p.price) }}</span>
                </div>
              </div>
              <div v-else class="campus-empty">
                <div class="campus-empty__title">暂无在售商品</div>
                <div class="campus-empty__desc">成为第一个发布二手好物的同学</div>
              </div>
            </div>
          </div>
        </el-col>

        <!-- 订单动态 -->
        <el-col :xs="24" :lg="10">
          <div class="campus-card">
            <div class="campus-card__head">
              <span class="campus-card__title">订单动态</span>
              <el-button link type="primary" @click="go('/campus/order')">全部订单</el-button>
            </div>
            <div class="campus-card__body">
              <div v-if="recentOrders.length" class="recent-orders">
                <div class="recent-order" v-for="o in recentOrders" :key="o.orderNo">
                  <div class="recent-order__main">
                    <span class="recent-order__no campus-num">{{ o.orderNo }}</span>
                    <span class="recent-order__time">{{ parseTime(o.createTime) }}</span>
                  </div>
                  <div class="recent-order__right">
                    <span class="campus-price">¥{{ money(o.totalAmount) }}</span>
                    <el-tag :type="statusType(o.status)" effect="light" size="small">{{ statusText(o.status) }}</el-tag>
                  </div>
                </div>
              </div>
              <div v-else class="campus-empty">
                <div class="campus-empty__title">暂无订单</div>
                <div class="campus-empty__desc">从购物车结算后可在此查看进度</div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 平台说明 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">关于校园淘</span>
        </div>
        <div class="campus-card__body">
          <div class="about-grid">
            <div class="about-item">
              <div class="about-item__idx">01</div>
              <div class="about-item__title">面向校园的二手交易</div>
              <p class="about-item__desc">围绕同校场景设计，买卖双方可就近当面交付，降低交易成本与信任门槛。</p>
            </div>
            <div class="about-item">
              <div class="about-item__idx">02</div>
              <div class="about-item__title">完整业务闭环</div>
              <p class="about-item__desc">覆盖商品发布、分类维护、购物车、下单支付、发货收货到售后审核的全流程。</p>
            </div>
            <div class="about-item">
              <div class="about-item__idx">03</div>
              <div class="about-item__title">资金安全设计</div>
              <p class="about-item__desc">沙箱账户余额采用原子更新，下单预占库存带乐观锁保护，规避超卖与并发扣减问题。</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { parseTime } from '@/utils/ruoyi'
import useUserStore from '@/store/modules/user'
import { listProduct } from '@/api/campus/product'
import { listCart } from '@/api/campus/cart'
import { listOrder } from '@/api/campus/order'
import { getAccount } from '@/api/campus/account'
import { listAftersale } from '@/api/campus/aftersale'

const router = useRouter()
const userStore = useUserStore()

const account = ref({})
const cartList = ref([])
const orderList = ref([])
const aftersaleList = ref([])
const recentProducts = ref([])
const totalProduct = ref(0)

const nickname = computed(() => userStore.nickName || userStore.name || '同学')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const statCards = computed(() => [
  { label: '在售商品', value: totalProduct.value, hint: '平台当前在售的二手商品' },
  { label: '购物车', value: cartList.value.length, hint: '待结算的商品条目' },
  { label: '待付款订单', value: countOrder(0), hint: '请及时完成沙箱支付' },
  { label: '待收货订单', value: countOrder(2), hint: '卖家已发货，待确认收货', accent: true },
  { label: '待审核售后', value: countAftersale(0), hint: '需要处理的售后申请' }
])

const recentOrders = computed(() => orderList.value.slice(0, 6))

const statusMap = {
  0: { text: '待付款', type: 'warning' },
  1: { text: '已付款', type: 'primary' },
  2: { text: '已发货', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' },
  5: { text: '退款中', type: 'warning' },
  6: { text: '已退款', type: 'info' }
}

function statusText(status) {
  return statusMap[status] ? statusMap[status].text : '未知'
}

function statusType(status) {
  return statusMap[status] ? statusMap[status].type : 'info'
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function firstImage(images) {
  if (!images) return ''
  return String(images).split(',')[0].trim()
}

function conditionText(level) {
  const map = { 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '二手老旧' }
  return map[level] || '成色未标注'
}

function countOrder(status) {
  return orderList.value.filter(row => row.status === status).length
}

function countAftersale(status) {
  return aftersaleList.value.filter(row => row.status === status).length
}

function go(path) {
  router.push(path)
}

onMounted(() => {
  // 各区块独立容错，任一接口异常不影响整体渲染
  listProduct({ pageNum: 1, pageSize: 5, status: 1 }).then(res => {
    recentProducts.value = res.rows || []
    totalProduct.value = res.total || 0
  }).catch(() => {})

  listCart().then(res => { cartList.value = res.data || [] }).catch(() => {})
  listOrder({}).then(res => { orderList.value = res.data || [] }).catch(() => {})
  getAccount().then(res => { account.value = res.data || {} }).catch(() => {})
  listAftersale({ status: 0 }).then(res => { aftersaleList.value = res.data || [] }).catch(() => {})
})
</script>

<style lang="scss" scoped>
/* 问候区：沿用品牌深绿，作为首页视觉锚点 */
.home-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 32px 36px;
  border-radius: var(--c-radius-lg);
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(95% 120% at 0% 0%, rgba(63, 157, 134, .55) 0%, rgba(63, 157, 134, 0) 54%),
    linear-gradient(150deg, #12705a 0%, #0a4237 62%, #073029 100%);
  box-shadow: var(--c-shadow-2);
}

.home-hero::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .05) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(85% 100% at 88% 50%, #000 8%, transparent 68%);
  pointer-events: none;
}

.home-hero__main {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.home-hero__greet {
  font-family: var(--c-font-serif);
  font-size: 24px;
  font-weight: 600;
  letter-spacing: .1em;
}

.home-hero__slogan {
  margin: 10px 0 24px;
  font-size: 13.5px;
  letter-spacing: .16em;
  color: rgba(255, 255, 255, .66);
}

.home-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.home-hero__actions :deep(.el-button) {
  background: rgba(255, 255, 255, .12);
  border-color: rgba(255, 255, 255, .28);
  color: #ffffff;
}

.home-hero__actions :deep(.el-button--primary) {
  background: #ffffff;
  border-color: #ffffff;
  color: #0a4237;
  font-weight: 600;
}

.home-hero__actions :deep(.el-button:hover) {
  background: rgba(255, 255, 255, .92);
  border-color: rgba(255, 255, 255, .92);
  color: #0a4237;
}

.home-hero__side {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  text-align: right;
}

.hero-side__label {
  font-size: 12px;
  letter-spacing: .3em;
  color: rgba(255, 255, 255, .62);
}

.hero-side__value {
  margin-top: 8px;
  font-family: var(--c-font-serif);
  font-size: 38px;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}

.hero-side__hint {
  margin-top: 8px;
  font-size: 12px;
  letter-spacing: .06em;
  color: rgba(255, 255, 255, .58);
}

/* 最近上架 */
.recent-products {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-product {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--c-line-soft);
  transition: background .18s ease;
}

.recent-product:last-child {
  border-bottom: none;
}

.recent-product:hover {
  background: var(--c-primary-soft);
}

.recent-product__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.recent-product__title {
  color: var(--c-ink);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-product__meta {
  font-size: 12px;
  color: var(--c-ink-4);
  letter-spacing: .04em;
}

.thumb-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--c-ink-4);
  letter-spacing: .1em;
}

/* 订单动态 */
.recent-orders {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.recent-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 4px;
  border-bottom: 1px solid var(--c-line-soft);
}

.recent-order:last-child {
  border-bottom: none;
}

.recent-order__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.recent-order__no {
  font-size: 12.5px;
  color: var(--c-ink);
  letter-spacing: .04em;
}

.recent-order__time {
  font-size: 11.5px;
  color: var(--c-ink-4);
}

.recent-order__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 平台说明 */
.about-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.about-item__idx {
  font-family: var(--c-font-serif);
  font-size: 12px;
  letter-spacing: .12em;
  color: var(--c-accent);
}

.about-item__title {
  margin: 8px 0 8px;
  font-family: var(--c-font-serif);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: .06em;
  color: var(--c-ink);
}

.about-item__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--c-ink-3);
  letter-spacing: .02em;
}

@media (max-width: 768px) {
  .home-hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 26px 22px;
  }

  .home-hero__side {
    text-align: left;
  }

  .hero-side__value {
    font-size: 30px;
  }
}
</style>
