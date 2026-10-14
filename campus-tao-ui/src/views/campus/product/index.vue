<template>
  <div class="app-container">
    <div class="campus-page">
      <!-- 页面标题 -->
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">商品管理</h2>
          <p class="campus-head__desc">发布与维护校园二手商品，标注成色与售价，供同学下单选购</p>
        </div>
        <div class="campus-head__extra">
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['campus:product:add']">发布商品</el-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="campus-toolbar" v-show="showSearch">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="72px">
          <el-form-item label="商品标题" prop="title">
            <el-input v-model="queryParams.title" placeholder="请输入商品标题" clearable style="width:220px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="商品状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width:160px">
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据表格 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">商品清单</span>
          <div class="campus-card__tools">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['campus:product:remove']">批量删除</el-button>
            <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
          </div>
        </div>

        <el-table v-loading="loading" :data="productList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="商品" min-width="240">
            <template #default="scope">
              <div class="product-cell">
                <el-image v-if="firstImage(scope.row.images)" :src="firstImage(scope.row.images)" class="campus-thumb" fit="cover" />
                <div v-else class="campus-thumb thumb-placeholder">无图</div>
                <div class="product-cell__main">
                  <span class="product-cell__title">{{ scope.row.title }}</span>
                  <span class="product-cell__meta">编号 {{ scope.row.id }} · 库存 {{ scope.row.stock }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="分类" align="center" width="110">
            <template #default="scope">
              <span class="campus-badge">{{ scope.row.categoryName || '未分类' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="售价" align="center" width="120">
            <template #default="scope">
              <span class="campus-price">¥{{ scope.row.price }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成色" align="center" width="110">
            <template #default="scope">
              <span class="campus-badge campus-badge--gold">{{ conditionText(scope.row.conditionLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="100">
            <template #default="scope">
              <el-switch v-model="scope.row.status" :active-value="1" :inactive-value="0" @change="handleStatusChange(scope.row)" />
            </template>
          </el-table-column>
          <el-table-column label="发布时间" align="center" prop="createTime" width="160">
            <template #default="scope"><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button link type="success" icon="ShoppingCart" @click="handleAddCart(scope.row)" v-hasPermi="['campus:cart:add']">加购</el-button>
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['campus:product:edit']">修改</el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['campus:product:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </div>
    </div>

    <!-- 添加或修改商品对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form :model="form" :rules="rules" ref="productRef" label-width="90px">
        <el-form-item label="商品标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：九成新 iPad 2022 64G" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width:100%">
                <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成色" prop="conditionLevel">
              <el-select v-model="form.conditionLevel" placeholder="请选择成色" style="width:100%">
                <el-option v-for="o in conditionOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价" prop="originalPrice">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图片链接" prop="images">
          <el-input v-model="form.images" placeholder="多个图片地址以英文逗号分隔" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="描述使用情况、配件是否齐全等信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { listProduct, getProduct, addProduct, updateProduct, delProduct, changeProductStatus } from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'
import { addCart } from '@/api/campus/cart'

const productList = ref([])
const categoryOptions = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const queryRef = ref()
const productRef = ref()

const conditionOptions = [
  { value: 1, label: '全新' },
  { value: 2, label: '几乎全新' },
  { value: 3, label: '轻微使用' },
  { value: 4, label: '明显使用' },
  { value: 5, label: '二手老旧' }
]

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  title: undefined,
  status: undefined
})

const form = reactive({
  id: undefined,
  categoryId: undefined,
  title: '',
  price: undefined,
  originalPrice: undefined,
  conditionLevel: 3,
  description: '',
  images: '',
  stock: 1,
  status: 1
})

const rules = {
  title: [{ required: true, message: '商品标题不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  price: [{ required: true, message: '售价不能为空', trigger: 'blur' }],
  stock: [{ required: true, message: '库存不能为空', trigger: 'blur' }]
}

function conditionText(level) {
  const map = { 1: '全新', 2: '几乎全新', 3: '轻微使用', 4: '明显使用', 5: '二手老旧' }
  return map[level] || '未知'
}

// 取商品首图用于列表缩略图
function firstImage(images) {
  if (!images) return ''
  const first = String(images).split(',')[0].trim()
  return first || ''
}

function getList() {
  loading.value = true
  listProduct(queryParams).then(res => {
    productList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function loadCategoryOptions() {
  listCategory({ pageSize: 999 }).then(res => {
    categoryOptions.value = res.rows || []
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value.resetFields()
  queryParams.pageNum = 1
  getList()
}

function reset() {
  Object.assign(form, {
    id: undefined,
    categoryId: undefined,
    title: '',
    price: undefined,
    originalPrice: undefined,
    conditionLevel: 3,
    description: '',
    images: '',
    stock: 1,
    status: 1
  })
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '发布商品'
}

function handleUpdate(row) {
  reset()
  getProduct(row.id).then(res => {
    Object.assign(form, res.data)
    open.value = true
    title.value = '修改商品'
  })
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleStatusChange(row) {
  changeProductStatus(row.id, row.status).then(() => {
    ElMessage.success(row.status === 1 ? '已上架' : '已下架')
  }).catch(() => {
    row.status = row.status === 1 ? 0 : 1
  })
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  productRef.value.validate(valid => {
    if (!valid) return
    // 卖家身份由服务端从登录态写入，前端不再传递 sellerId / sellerName
    if (form.id != null) {
      updateProduct(form).then(() => {
        ElMessage.success('修改成功')
        open.value = false
        getList()
      })
    } else {
      addProduct(form).then(() => {
        ElMessage.success('发布成功')
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const pids = row.id || ids.value
  ElMessageBox.confirm('确认删除商品编号为「' + pids + '」的数据项？', '删除确认', { type: 'warning' }).then(() => {
    delProduct(pids).then(() => {
      ElMessage.success('删除成功')
      getList()
    })
  }).catch(() => {})
}

function handleAddCart(row) {
  ElMessageBox.prompt('请输入加入购物车的数量', '加入购物车', {
    inputValue: 1, inputType: 'number', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(({ value }) => {
    const qty = parseInt(value) || 1
    if (row.stock != null && qty > row.stock) {
      ElMessage.warning('库存不足')
      return
    }
    addCart({ productId: row.id, quantity: qty }).then(() => {
      ElMessage.success('已加入购物车')
    })
  }).catch(() => {})
}

onMounted(() => {
  getList()
  loadCategoryOptions()
})
</script>

<style lang="scss" scoped>
.product-cell {
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
  letter-spacing: .1em;
}

.product-cell__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.product-cell__title {
  color: var(--c-ink);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-cell__meta {
  font-size: 12px;
  color: var(--c-ink-4);
  letter-spacing: .04em;
}

.campus-card__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.campus-card .pagination-container {
  padding: 4px 18px 16px;
}
</style>
