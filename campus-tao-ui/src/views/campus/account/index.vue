<template>
  <div class="app-container">
    <div class="campus-page">
      <div class="campus-head">
        <div class="campus-head__main">
          <h2 class="campus-head__title">支付账户</h2>
          <p class="campus-head__desc">校园淘沙箱账户，用于模拟下单支付与退款，不涉及真实资金</p>
        </div>
        <div class="campus-head__extra">
          <el-button icon="Refresh" @click="getAccountInfo">刷新</el-button>
        </div>
      </div>

      <!-- 余额主卡 -->
      <div class="account-hero" v-loading="loading">
        <div class="account-hero__main">
          <div class="account-hero__label">可用余额</div>
          <div class="account-hero__value">¥{{ money(account.balance) }}</div>
          <div class="account-hero__owner">{{ account.userName || '—' }}</div>
        </div>
        <div class="account-hero__side">
          <div class="account-hero__side-item">
            <span class="account-hero__side-label">冻结金额</span>
            <span class="account-hero__side-value">¥{{ money(account.freeze) }}</span>
          </div>
          <div class="account-hero__side-item">
            <span class="account-hero__side-label">最近更新</span>
            <span class="account-hero__side-value campus-num">{{
              account.updateTime ? parseTime(account.updateTime) : '—'
            }}</span>
          </div>
          <el-button class="account-hero__btn" type="primary" @click="openRecharge" v-hasPermi="['campus:account:edit']"
            >充值</el-button
          >
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <div class="campus-card">
            <div class="campus-card__head">
              <span class="campus-card__title">账户说明</span>
            </div>
            <div class="campus-card__body">
              <ul class="account-notes">
                <li>下单支付时从「可用余额」扣减，余额不足将无法完成支付。</li>
                <li>取消已付款订单或售后审核通过后，款项原路退回该账户。</li>
                <li>账户余额由服务端原子更新，避免并发扣减导致金额错乱。</li>
              </ul>
              <div class="campus-note">
                <span>演示环境 · 沙箱账户仅供流程演练，请勿与真实资金混淆。</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="campus-card" v-hasPermi="['campus:account:edit']">
            <div class="campus-card__head">
              <span class="campus-card__title">快捷充值</span>
            </div>
            <div class="campus-card__body">
              <div class="quick-amounts">
                <button v-for="amount in quickAmounts" :key="amount" class="quick-amount" @click="openRecharge(amount)">
                  <span class="quick-amount__value">¥{{ amount }}</span>
                  <span class="quick-amount__hint">一键充值</span>
                </button>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- C2C 直付收款码：买家扫码直接付给卖家本人，平台不经手资金 -->
      <div class="campus-card pay-qr-card" v-hasPermi="['campus:account:edit']">
        <div class="campus-card__head">
          <span class="campus-card__title">我的收款码（C2C 直付）</span>
          <el-tag size="small" type="warning" effect="plain">真实资金 · 请谨慎设置</el-tag>
        </div>
        <div class="campus-card__body">
          <div class="pay-qr">
            <div class="pay-qr__preview">
              <img v-if="payQrUrl" :src="payQrUrl" alt="我的收款码" />
              <div v-else class="pay-qr__empty">尚未上传收款码<br />上传后买家可在下单时扫码直付给你</div>
            </div>
            <div class="pay-qr__form">
              <ImageUpload v-model="payQrInput" :limit="1" />
              <div class="pay-qr__notes">
                <ul class="account-notes">
                  <li>收款码将展示给购买你商品的买家，扫码付款直接进入你的账户。</li>
                  <li>买家点击「我已付款」后，你需在订单列表核对到账并点击「确认已收款」。</li>
                  <li>平台不经手资金、不提供担保，退款与纠纷需与买家线下协商。</li>
                </ul>
              </div>
              <el-button type="primary" :loading="payQrSaving" @click="submitPayQr">保存收款码</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 充值对话框 -->
    <el-dialog v-model="rechargeOpen" title="充值沙箱余额" width="440px" append-to-body>
      <el-form :model="rechargeForm" :rules="rechargeRules" ref="rechargeRef" label-width="80px">
        <el-form-item label="充值金额" prop="amount">
          <el-input-number v-model="rechargeForm.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="campus-note campus-note--accent">
        <span>充值将直接增加可用余额，仅用于演示下单支付流程。</span>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="rechargeOpen = false">取 消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitRecharge">确认充值</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { getAccount, recharge } from '@/api/campus/account'
import { getMyPayQr, saveMyPayQr } from '@/api/campus/payqr'

const account = ref({})
const loading = ref(true)
const rechargeOpen = ref(false)
const submitting = ref(false)
const rechargeRef = ref()

// C2C 直付收款码
const payQrUrl = ref('')
const payQrInput = ref('')
const payQrSaving = ref(false)

const quickAmounts = [50, 100, 200, 500]

const rechargeForm = reactive({
  amount: 100
})

const rechargeRules = {
  amount: [{ required: true, message: '充值金额不能为空', trigger: 'blur' }]
}

function money(value) {
  return (Number(value) || 0).toFixed(2)
}

function getAccountInfo() {
  loading.value = true
  getAccount()
    .then((res) => {
      account.value = res.data || {}
      loading.value = false
    })
    .catch(() => {
      loading.value = false
    })
}

function openRecharge(amount) {
  rechargeForm.amount = typeof amount === 'number' ? amount : 100
  rechargeOpen.value = true
}

function submitRecharge() {
  rechargeRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    recharge({ amount: rechargeForm.amount })
      .then(() => {
        ElMessage.success('充值成功')
        rechargeOpen.value = false
        getAccountInfo()
      })
      .catch(() => {})
      .finally(() => {
        submitting.value = false
      })
  })
}

function getPayQrInfo() {
  getMyPayQr()
    .then((res) => {
      const data = res.data || {}
      payQrUrl.value = data.qrUrl || ''
      payQrInput.value = payQrUrl.value
    })
    .catch(() => {
      // 收款码加载失败不阻塞账户页，保持未上传态即可
    })
}

function submitPayQr() {
  const url = (payQrInput.value || '').trim()
  if (!url) {
    ElMessage.warning('请先上传收款码图片')
    return
  }
  payQrSaving.value = true
  saveMyPayQr(url)
    .then(() => {
      ElMessage.success('收款码已保存')
      payQrUrl.value = url
      payQrSaving.value = false
    })
    .catch(() => {
      payQrSaving.value = false
    })
}

onMounted(() => {
  getAccountInfo()
  getPayQrInfo()
})
</script>

<style lang="scss" scoped>
/* 余额主卡：延续品牌深绿，作为页面视觉锚点 */
.account-hero {
  position: relative;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 28px;
  padding: 32px 36px;
  border-radius: var(--c-radius-lg);
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(90% 120% at 0% 0%, rgba(63, 157, 134, 0.55) 0%, rgba(63, 157, 134, 0) 52%),
    linear-gradient(150deg, #12705a 0%, #0a4237 62%, #073029 100%);
  box-shadow: var(--c-shadow-2);
}

.account-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(90% 100% at 85% 50%, #000 10%, transparent 70%);
  pointer-events: none;
}

.account-hero__main {
  position: relative;
  z-index: 1;
}

.account-hero__label {
  font-size: 12.5px;
  letter-spacing: 0.3em;
  color: rgba(255, 255, 255, 0.66);
}

.account-hero__value {
  margin-top: 10px;
  font-family: var(--c-font-serif);
  font-size: 46px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.account-hero__owner {
  margin-top: 12px;
  font-size: 13px;
  letter-spacing: 0.1em;
  color: rgba(255, 255, 255, 0.72);
}

.account-hero__side {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
  gap: 14px;
  min-width: 210px;
}

.account-hero__side-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}

.account-hero__side-label {
  font-size: 11.5px;
  letter-spacing: 0.16em;
  color: rgba(255, 255, 255, 0.6);
}

.account-hero__side-value {
  font-size: 15px;
  font-weight: 500;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
}

.account-hero__btn {
  margin-top: 4px;
  background: #ffffff !important;
  border-color: #ffffff !important;
  color: #0a4237 !important;
  font-weight: 600;
}

.account-hero__btn:hover {
  background: rgba(255, 255, 255, 0.88) !important;
  border-color: rgba(255, 255, 255, 0.88) !important;
  color: #0a4237 !important;
}

/* 说明列表 */
.account-notes {
  margin: 0 0 16px;
  padding-left: 18px;
  font-size: 13px;
  line-height: 2.1;
  color: var(--c-ink-2);
  letter-spacing: 0.02em;
}

.account-notes li::marker {
  color: var(--c-primary);
}

/* 快捷充值 */
.quick-amounts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.quick-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid var(--c-line);
  border-radius: var(--c-radius);
  background: var(--c-card);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: all 0.18s ease;
}

.quick-amount:hover {
  border-color: var(--c-primary-line);
  background: var(--c-primary-soft);
  transform: translateY(-1px);
}

.quick-amount__value {
  font-family: var(--c-font-serif);
  font-size: 19px;
  font-weight: 600;
  color: var(--c-ink);
}

.quick-amount__hint {
  font-size: 11.5px;
  letter-spacing: 0.1em;
  color: var(--c-ink-4);
}

/* C2C 收款码卡片 */
.pay-qr-card {
  margin-top: 16px;
}

.pay-qr {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.pay-qr__preview {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
  border: 1px dashed var(--c-line);
  border-radius: var(--c-radius);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--c-card);
}

.pay-qr__preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.pay-qr__empty {
  padding: 12px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--c-ink-4);
  text-align: center;
}

.pay-qr__form {
  flex: 1;
  min-width: 260px;
}

.pay-qr__notes {
  margin: 8px 0 14px;
}

.pay-qr__notes .account-notes {
  margin-bottom: 0;
}

@media (max-width: 768px) {
  .account-hero {
    flex-direction: column;
    padding: 26px 22px;
  }

  .account-hero__side {
    align-items: flex-start;
    min-width: 0;
  }

  .account-hero__side-item {
    align-items: flex-start;
  }

  .account-hero__value {
    font-size: 36px;
  }
}
</style>
