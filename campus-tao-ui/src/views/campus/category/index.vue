<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">分类管理</h2>
          <p class="campus-head__desc">维护校园二手商品的分类目录，决定商品发布时可选的一级/二级分类</p>
        </div>
        <div class="campus-head__extra">
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['campus:category:add']">新增分类</el-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="campus-toolbar" v-show="showSearch">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="72px">
          <el-form-item label="分类名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入分类名称" clearable style="width:220px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 分类清单 -->
      <div class="campus-card">
        <div class="campus-card__head">
          <span class="campus-card__title">分类目录</span>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </div>

        <el-table v-loading="loading" :data="categoryList">
          <el-table-column label="分类编号" align="center" prop="id" width="110">
            <template #default="scope"><span class="campus-num">{{ scope.row.id }}</span></template>
          </el-table-column>
          <el-table-column label="分类名称" min-width="200">
            <template #default="scope">
              <span class="category-name">{{ scope.row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="层级" align="center" width="120">
            <template #default="scope">
              <span v-if="!scope.row.parentId" class="campus-badge campus-badge--primary">顶级分类</span>
              <span v-else class="campus-badge">二级分类</span>
            </template>
          </el-table-column>
          <el-table-column label="排序" align="center" prop="sort" width="100">
            <template #default="scope"><span class="campus-num">{{ scope.row.sort }}</span></template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" width="170">
            <template #default="scope"><span class="campus-num">{{ parseTime(scope.row.createTime) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="180" fixed="right">
            <template #default="scope">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['campus:category:edit']">修改</el-button>
              <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['campus:category:remove']">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="campus-empty">
              <div class="campus-empty__title">暂无分类</div>
              <div class="campus-empty__desc">点击右上角「新增分类」创建第一个商品分类</div>
            </div>
          </template>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </div>
    </div>

    <!-- 新增 / 修改分类 -->
    <el-dialog :title="title" v-model="open" width="480px" append-to-body>
      <el-form :model="form" :rules="rules" ref="categoryRef" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：教材书籍、数码电子" />
        </el-form-item>
        <el-form-item label="上级分类" prop="parentId">
          <el-select v-model="form.parentId" placeholder="不选则为顶级分类" clearable style="width:100%">
            <el-option v-for="c in topLevelOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :step="1" style="width:100%" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { listCategory, getCategory, addCategory, updateCategory, delCategory } from '@/api/campus/category'

const categoryList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)

const queryRef = ref()
const categoryRef = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: undefined
})

const form = reactive({
  id: undefined,
  name: '',
  parentId: undefined,
  sort: 0
})

const rules = {
  name: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
  sort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }]
}

// 顶级分类可作为二级分类的上级
const topLevelOptions = computed(() => categoryList.value.filter(row => !row.parentId))

function getList() {
  loading.value = true
  listCategory(queryParams).then(res => {
    categoryList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
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
  form.id = undefined
  form.name = ''
  form.parentId = undefined
  form.sort = 0
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增分类'
}

function handleUpdate(row) {
  reset()
  getCategory(row.id).then(res => {
    Object.assign(form, res.data)
    open.value = true
    title.value = '修改分类'
  })
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  categoryRef.value.validate(valid => {
    if (!valid) return
    const request = form.id != null ? updateCategory(form) : addCategory(form)
    request.then(() => {
      ElMessage.success(form.id != null ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  ElMessageBox.confirm('确认删除分类「' + row.name + '」？', '删除确认', { type: 'warning' }).then(() => {
    delCategory(row.id).then(() => {
      ElMessage.success('删除成功')
      getList()
    })
  }).catch(() => {})
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.category-name {
  color: var(--c-ink);
  font-weight: 500;
  letter-spacing: .02em;
}

.campus-card .pagination-container {
  padding: 4px 18px 16px;
}
</style>
