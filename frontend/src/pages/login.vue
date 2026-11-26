<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { Lock, User } from "@lucide/vue";
import { ElMessage } from "element-plus";
import { notifyError } from "~/api/http";
import { useAuthStore } from "~/stores/auth";

const { t } = useI18n();
const authStore = useAuthStore();

const form = ref({
  name: "",
  password: "",
});

async function handleLogin() {
  if (!form.value.name || !form.value.password) {
    ElMessage.warning(t("auth.inputUsernamePassword"));
    return;
  }
  try {
    await authStore.login(form.value.name, form.value.password);
  } catch (err: any) {
    notifyError(err, t("auth.loginFailed"));
  }
}

async function handleGuestLogin() {
  try {
    await authStore.loginAsGuest();
  } catch (err: any) {
    notifyError(err, t("auth.guestLoginFailed"));
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-form">
      <div class="auth-form-header">
        <h2>{{ t("auth.loginTitle") }}</h2>
        <p>{{ t("home.consoleLabel") }}</p>
      </div>

      <el-form :model="form" label-position="top" size="large" @submit.prevent="handleLogin">
        <el-form-item :label="t('common.username')">
          <el-input
            v-model="form.name"
            :placeholder="t('common.placeholder.name')"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item :label="t('common.password')">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('common.placeholder.password')"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item class="!mb-3">
          <el-button
            type="primary"
            native-type="submit"
            class="auth-submit"
            :loading="authStore.loading"
          >
            {{ t("auth.login") }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-switch">
        <span>{{ t("auth.noAccount") }}</span>
        <RouterLink to="/register">
          {{ t("auth.toRegister") }}
        </RouterLink>
      </div>

      <el-divider class="auth-divider" />

      <div class="auth-guest">
        <span>{{ t("auth.guestFooterHint") }}</span>
        <el-button
          type="primary"
          link
          class="!p-0 font-medium"
          :loading="authStore.loading"
          @click="handleGuestLogin"
        >
          {{ t("auth.guestLoginAction") }}
        </el-button>
      </div>
    </div>
  </AuthShell>
</template>

<style scoped>
.auth-form {
  width: 100%;
}

.auth-form-header {
  margin-bottom: 30px;
}

.auth-form-header h2 {
  margin: 0;
  color: var(--ep-text-color-primary);
  font-size: 28px;
  font-weight: 740;
  letter-spacing: -0.03em;
}

.auth-form-header p {
  margin: 8px 0 0;
  color: var(--ep-text-color-secondary);
  font-size: 13px;
}

.auth-submit {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-weight: 650;
}

.auth-switch,
.auth-guest {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ep-text-color-secondary);
  font-size: 13px;
}

.auth-switch a {
  color: var(--ep-color-primary);
  font-weight: 620;
  text-decoration: none;
}

.auth-switch a:hover {
  text-decoration: underline;
}

.auth-divider {
  margin: 18px 0 14px;
}
</style>
