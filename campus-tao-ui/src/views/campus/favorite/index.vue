<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">我的收藏</h2>
          <p class="campus-head__desc">收藏感兴趣的商品，便于之后比价与下单</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
          <el-button v-hasPermi="['campus:favorite:remove']" type="danger" plain icon="Delete" @click="handleClearAll"
            >清空收藏</el-button
          >
        </div>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">收藏总数</div>
          <div class="campus-stat__value">{{ favoriteList.length }}</div>
          <div class="campus-stat__hint">
            <template v-if="atLimit">已达上限，需先取消部分收藏再添加</template>
            <template v-else>同一商品不会重复收藏</template>
          </div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">在售商品</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ onSaleCount }}</div>
          <div class="campus-stat__hint">可直接前往下单</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">已下架</div>
          <div class="campus-stat__value">{{ offSaleCount }}</div>
          <div class="campus-stat__hint">卖家可能已售出或下架</div>
        </div>
      </div>

      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">收藏清单</span>
        </div>

        <el-table v-loading="loading" :data="favoriteList">
          <el-table-column label="商品" min-width="260" show-overflow-tooltip>
            <template #default="scope">
              <div class="fav-product">
                <img v-if="cover(scope.row)" :src="cover(scope.row)" class="fav-product__cover" alt="" />
                <div v-else class="fav-product__cover fav-product__cover--empty">无图</div>
                <div class="fav-product__main">
                  <div class="fav-product__title">{{ scope.row.productTitle || '商品已下架' }}</div>
                  <div class="fav-product__seller">卖家：{{ scope.row.sellerName || '—' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="价格" align="center" width="120">
            <template #default="scope">
              <span class="campus-price">¥{{ money(scope.row.productPrice) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="100">
            <template #default="scope">
              <el-tag v-if="scope.row.productStatus === 1" type="success" size="small" effect="plain">在售</el-tag>
              <el-tag v-else type="info" size="small" effect="plain">已下架</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="收藏时间" align="center" width="165">
            <template #default="scope"
              ><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template
            >
          </el-table-column>
          <el-table-column label="操作" align="center" width="160">
            <template #default="scope">
              <el-button link type="primary" icon="ShoppingCart" @click="goDetail(scope.row)">去看看</el-button>
              <el-button
                v-hasPermi="['campus:favorite:remove']"
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
                >取消收藏</el-button
              >
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">还没有收藏任何商品</div>
              <div class="campus-empty__desc">在校园商城浏览商品时点击「收藏」，之后可在此处快速找到</div>
            </div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup name="CampusFavorite">
import { listFavorite, removeFavorite } from '@/api/campus/favorite'
import { useRouter } from 'vue-router'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const favoriteList = ref([])

const onSaleCount = computed(() => favoriteList.value.filter((f) => f.productStatus === 1).length)
const offSaleCount = computed(() => favoriteList.value.length - onSaleCount.value)

function parseTime(time) {
  if (!time) return ''
  return time.length > 19 ? time.substring(0, 19).replace('T', ' ') : time
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

/** 商品图片字段是逗号分隔的多图串，取第一张作为封面 */
function cover(row) {
  if (!row.productImages) return ''
  return String(row.productImages).split(',')[0].trim()
}

function goDetail(row) {
  router.push({ path: '/campus/mall/detail/' + row.productId })
}

/** 是否已达上限：达到后入口处直接提示，不再发一次注定失败的请求 */
const atLimit = computed(() => favoriteList.value.length >= MAX_FAVORITES)

async function getList() {
  loading.value = true
  try {
    const res = await listFavorite()
    favoriteList.value = res.data || []
  } finally {
    loading.value = false
  }
}

/**
 * 收藏上限：与后端 ICampusFavoriteService.MAX_FAVORITES 保持一致。
 * 上限是**服务端强制**的（超限接口直接报错），前端这里只是把边界显示出来，
 * 让用户在看到提示时就知道为什么加不进去——不靠前端拦截成功率。
 */
const MAX_FAVORITES = 200

/** 取消与请求统一包在 try 内：用户点取消时 confirm 的拒绝被 catch 吞掉，
 *  避免 `await confirm().catch(throw)` 把取消异常抛出 async 函数形成未处理的 Promise 拒绝。 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认取消收藏「${row.productTitle || '该商品'}」吗？`, '提示', { type: 'warning' })
    await removeFavorite(row.productId)
    ElMessage.success('已取消收藏')
    getList()
  } catch {
    /* 用户取消不提示；接口失败的提示由请求拦截器统一弹出 */
  }
}

async function handleClearAll() {
  if (!favoriteList.value.length) {
    ElMessage.warning('收藏夹已经是空的')
    return
  }
  try {
    await ElMessageBox.confirm(`确认清空全部 ${favoriteList.value.length} 条收藏吗？`, '提示', { type: 'warning' })
    // 后端未提供批量清空接口，逐条取消（数量受 MAX_HISTORY 之外的业务上限约束，规模可控）
    for (const item of favoriteList.value) {
      await removeFavorite(item.productId)
    }
    ElMessage.success('已清空收藏')
    getList()
  } catch {
    /* 用户取消不提示；接口失败的提示由请求拦截器统一弹出 */
  }
}

onMounted(getList)
</script>

<style lang="scss" scoped>
.fav-product {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fav-product__cover {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.fav-product__cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--c-ink-4);
  background: var(--el-fill-color-light);
}

.fav-product__main {
  min-width: 0;
}

.fav-product__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.fav-product__seller {
  margin-top: 2px;
  font-size: 12px;
  color: var(--c-ink-4);
}
</style>
