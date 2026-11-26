<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { Lock, User, UserRound } from "@lucide/vue";
import { ElMessage } from "element-plus";
import { notifyError } from "~/api/http";
import { useAuthStore } from "~/stores/auth";

const { t } = useI18n();
const authStore = useAuthStore();

// Keep in sync with backend RegisterRequest (@Size(min = 8, max = 72)).
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 72;

const form = ref({
  name: "",
  password: "",
  confirmPassword: "",
  displayName: "",
});

async function handleRegister() {
  if (!form.value.name || !form.value.password) {
    ElMessage.warning(t("auth.inputUsernamePassword"));
    return;
  }
  if (
    form.value.password.length < PASSWORD_MIN_LENGTH ||
    form.value.password.length > PASSWORD_MAX_LENGTH
  ) {
    ElMessage.warning(t("auth.passwordLengthInvalid"));
    return;
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.warning(t("auth.passwordMismatch"));
    return;
  }
  try {
    await authStore.register(
      form.value.name,
      form.value.password,
      form.value.displayName || undefined
    );
    ElMessage.success(t("auth.registerSuccess"));
  } catch (err: any) {
    notifyError(err, t("auth.registerFailed"));
  }
}
</script>

<template>
  <AuthShell>
    <div class="auth-form">
      <div class="auth-form-header">
        <h2>{{ t("auth.registerTitle") }}</h2>
        <p>{{ t("home.consoleLabel") }}</p>
      </div>

      <el-form :model="form" label-position="top" size="large" @submit.prevent="handleRegister">
        <el-form-item :label="t('common.username')">
          <el-input
            v-model="form.name"
            :placeholder="t('common.placeholder.name')"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item :label="t('auth.displayName')">
          <el-input
            v-model="form.displayName"
            :placeholder="t('auth.displayNamePlaceholder')"
            :prefix-icon="UserRound"
          />
        </el-form-item>

        <el-form-item :label="t('common.password')">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('auth.passwordPlaceholder')"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item :label="t('auth.confirmPassword')">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('auth.confirmPasswordPlaceholder')"
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
            {{ t("auth.register") }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-switch">
        <span>{{ t("auth.hasAccount") }}</span>
        <RouterLink to="/login">
          {{ t("auth.toLogin") }}
        </RouterLink>
      </div>
    </div>
  </AuthShell>
</template>

<style scoped>
.auth-form {
  width: 100%;
}

.auth-form-header {
  margin-bottom: 24px;
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

.auth-switch {
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
</style>
