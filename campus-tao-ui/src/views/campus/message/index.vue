<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">消息沟通</h2>
          <p class="campus-head__desc">与订单的交易对方沟通成色、发货等事宜；会话以订单为上下文</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <!-- 概览 -->
      <div class="campus-stats">
        <div class="campus-stat">
          <div class="campus-stat__label">会话总数</div>
          <div class="campus-stat__value">{{ conversationList.length }}</div>
          <div class="campus-stat__hint">按最近消息排序</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">未读消息</div>
          <div class="campus-stat__value campus-stat__value--accent">{{ totalUnread }}</div>
          <div class="campus-stat__hint">打开会话后自动标记已读</div>
        </div>
        <div class="campus-stat">
          <div class="campus-stat__label">当前会话</div>
          <div class="campus-stat__value">{{ activeOrderNo || '未选择' }}</div>
          <div class="campus-stat__hint">左侧选择订单查看往来消息</div>
        </div>
      </div>

      <div class="msg-layout">
        <!-- 会话列表 -->
        <div class="campus-card msg-layout__side">
          <div class="campus-card__head">
            <span class="campus-card__title">会话列表</span>
          </div>
          <div v-loading="loading" class="msg-conv">
            <div
              v-for="item in conversationList"
              :key="item.orderNo"
              class="msg-conv__item"
              :class="{ 'msg-conv__item--active': item.orderNo === activeOrderNo }"
              @click="selectConversation(item.orderNo)"
            >
              <div class="msg-conv__top">
                <span class="msg-conv__order">{{ item.orderNo }}</span>
                <el-badge v-if="item.unread > 0" :value="item.unread" class="msg-conv__badge" />
              </div>
              <div class="msg-conv__last">{{ item.lastContent || '（暂无消息）' }}</div>
              <div class="msg-conv__meta">
                <span>{{ parseTime(item.lastTime) }}</span>
                <span>¥{{ money(item.totalAmount) }}</span>
              </div>
            </div>
            <div v-if="!conversationList.length" class="campus-empty">
              <div class="campus-empty__title">还没有会话</div>
              <div class="campus-empty__desc">在「我的订单」里对某笔订单点击「联系对方」即可开始沟通</div>
            </div>
          </div>
        </div>

        <!-- 消息区 -->
        <div class="campus-card msg-layout__main">
          <div class="campus-card__head">
            <span class="campus-card__title">{{ activeOrderNo ? '订单 ' + activeOrderNo : '消息' }}</span>
            <span v-if="activeOrderNo" class="campus-card__hint">仅买卖双方可见</span>
          </div>

          <div v-if="!activeOrderNo" class="campus-empty">
            <div class="campus-empty__title">请选择一个会话</div>
            <div class="campus-empty__desc">从左侧会话列表中选择一笔订单，查看与该交易对方的往来消息</div>
          </div>

          <template v-else>
            <div v-loading="msgLoading" ref="msgBoxRef" class="msg-body">
              <div v-for="m in messageList" :key="m.messageId" class="msg-item" :class="msgItemClass(m)">
                <div class="msg-item__meta">
                  <span class="msg-item__who">{{ m.fromUserId === myUserId ? '我' : m.fromUserName || '对方' }}</span>
                  <span class="campus-num">{{ parseTime(m.createTime) }}</span>
                </div>
                <div class="msg-item__bubble">{{ m.content }}</div>
              </div>
              <div v-if="!messageList.length" class="campus-empty">
                <div class="campus-empty__title">暂无消息</div>
                <div class="campus-empty__desc">在下方输入内容，向交易对方发送第一条消息</div>
              </div>
            </div>

            <div class="msg-input">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="2"
                :maxlength="500"
                placeholder="输入消息内容（最多 500 字）"
                @keydown.enter.exact.prevent="handleSend"
              />
              <el-button
                v-hasPermi="['campus:message:add']"
                type="primary"
                icon="Position"
                :disabled="!draft.trim()"
                @click="handleSend"
                >发送</el-button
              >
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="CampusMessage">
import { listConversation, listOrderMessage, readOrderMessage, sendMessage } from '@/api/campus/message'
import { getInfo } from '@/api/login'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()

const loading = ref(false)
const msgLoading = ref(false)
const conversationList = ref([])
const messageList = ref([])
const activeOrderNo = ref('')
const draft = ref('')
const msgBoxRef = ref(null)

/** 当前登录用户 ID：由 JWT 解出，仅用于区分"我发的"与"对方发的" */
const myUserId = ref(null)

const totalUnread = ref(0)

function parseTime(time) {
  if (!time) return ''
  return time.length > 19 ? time.substring(0, 19).replace('T', ' ') : time
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

/** 自己发的靠右显示，对方靠左 */
function msgItemClass(m) {
  return m.fromUserId === myUserId.value ? 'msg-item--mine' : 'msg-item--theirs'
}

/** 取当前登录用户 ID，仅用于区分「我发的」与「对方发的」。
 *  走若依标准的 /getInfo 而不是解 JWT——后端 token 的 payload 里只有 uuid 与
 *  username，没有 userId，解出来只会得到 null。 */
async function loadMyUserId() {
  try {
    const res = await getInfo()
    myUserId.value = res.user?.userId ?? null
  } catch {
    // 取不到也不阻塞页面，只是消息无法区分左右
  }
}

function scrollToBottom() {
  const el = msgBoxRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function getList() {
  loading.value = true
  try {
    const res = await listConversation()
    conversationList.value = res.data || []
    totalUnread.value = conversationList.value.reduce((sum, c) => sum + (Number(c.unread) || 0), 0)
  } finally {
    loading.value = false
  }
}

async function selectConversation(orderNo) {
  activeOrderNo.value = orderNo
  draft.value = ''
  msgLoading.value = true
  try {
    const res = await listOrderMessage(orderNo)
    messageList.value = res.data || []
    // 打开会话即把发给我的未读标记为已读（失败不阻塞查看）
    await readOrderMessage(orderNo).catch(() => {})
    await getList()
  } finally {
    msgLoading.value = false
    scrollToBottom()
  }
}

/** 发送与刷新统一包在 try 内：失败由请求拦截器统一提示，这里只做成功后的清空 */
async function handleSend() {
  const content = draft.value.trim()
  if (!content || !activeOrderNo.value) return
  try {
    await sendMessage({ orderNo: activeOrderNo.value, content })
    draft.value = ''
    await selectConversation(activeOrderNo.value)
    ElMessage.success('消息已发送')
  } catch {
    /* 失败提示由请求拦截器统一弹出 */
  }
}

onMounted(() => {
  loadMyUserId()
  getList()
  // 支持从订单页带 orderNo 直接进入指定会话
  if (route.query.orderNo) {
    selectConversation(route.query.orderNo)
  }
})
</script>

<style lang="scss" scoped>
.msg-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}

.msg-layout__side {
  position: sticky;
  top: 16px;
}

.msg-layout__main {
  min-height: 420px;
}

/* 会话列表 */
.msg-conv {
  max-height: 460px;
  overflow-y: auto;
}

.msg-conv__item {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}

.msg-conv__item:hover {
  background: var(--el-fill-color-light);
}

.msg-conv__item--active {
  background: var(--el-color-primary-light-9);
}

.msg-conv__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.msg-conv__order {
  font-family: monospace;
  font-size: 12px;
  color: var(--c-ink-3);
}

.msg-conv__badge {
  margin-top: -4px;
}

.msg-conv__last {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.msg-conv__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-ink-4);
}

/* 消息区 */
.msg-body {
  height: 340px;
  padding: 12px;
  overflow-y: auto;
  background: var(--el-fill-color-lighter);
}

.msg-item {
  margin-bottom: 12px;
}

.msg-item--mine {
  text-align: right;
}

.msg-item__meta {
  margin-bottom: 3px;
  font-size: 11px;
  color: var(--c-ink-4);
}

.msg-item__who {
  margin-right: 6px;
  font-weight: 500;
}

.msg-item__bubble {
  display: inline-block;
  max-width: 78%;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  word-break: break-word;
}

.msg-item--mine .msg-item__bubble {
  background: var(--el-color-primary-light-8);
  border-color: var(--el-color-primary-light-5);
}

.msg-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 12px;
}
</style>
