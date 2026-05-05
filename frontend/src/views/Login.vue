<template>
    <div class="login-container">
        <div class="login-wrapper">
            <h2 class="main-title">智能课程推荐系统</h2>
            <div class="login-box">
                <h1>登录账户</h1>
                <el-form 
                    ref="formRef"
                    :rules="rules" 
                    :model="form" 
                    label-width="auto"
                    style="max-width: 500px"
                    @submit.prevent="doLogin"
                >
                    <el-form-item prop="username" label="用户名">
                        <el-input 
                            v-model="form.username" 
                            placeholder="请输入用户名"
                            clearable
                        />
                    </el-form-item>

                    <el-form-item prop="password" label="密码">
                        <el-input 
                            v-model="form.password" 
                            type="password" 
                            placeholder="请输入密码"
                            show-password
                        />
                    </el-form-item>
                    
                    <el-form-item>
                        <el-button type="primary" native-type="submit" :loading="submitting" style="width: 100%">
                            登录
                        </el-button>
                    </el-form-item>
                    
                    <el-form-item>
                        <span>没有账号？<router-link to="/register">立即注册</router-link></span>
                    </el-form-item>
                </el-form>
            </div>
        </div>
    </div>
</template>

<script setup>
import { reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { login } from "../api/user"
import { useUserStore } from "../store/user"
import { logger } from "../utils/logger"

const router = useRouter();
const userStore = useUserStore();
const formRef = ref(null);
const submitting = ref(false);

const form = reactive({
    username: "",
    password: ""
});

const rules = {
    username: [
        { required: true, message: "请输入用户名", trigger: "blur" }
    ],
    password: [
        { required: true, message: "请输入密码", trigger: "blur" }
    ]
};

const doLogin = async () => {
    if (!formRef.value) return;
    if (submitting.value) return;
    
    try {
        submitting.value = true;
        await formRef.value.validate();
        
        const res = await login(form);
        if (res.data.code !== 200) {
            return logger.error(res.data.msg, res.data);
        }
        const token = res.data.data;
        
        userStore.setToken(token);
        logger.success("登录成功");
        router.push("/");
    } catch (error) {
        logger.error("登录失败，请检查用户名和密码", error);
    } finally {
        submitting.value = false;
    }
};
</script>

<style scoped>
.login-container {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-wrapper {
    text-align: center;
}

.main-title {
    font-size: 32px;
    font-weight: bold;
    color: white;
    margin-bottom: 40px;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.login-box {
    background: white;
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
    width: 100%;
    max-width: 500px;
    margin: 0 auto;
}

.login-box h1 {
    text-align: center;
    margin-bottom: 30px;
    color: #333;
}

:deep(.el-form-item) {
    margin-bottom: 18px;
}

:deep(.el-input__wrapper) {
    background-color: #f5f5f5;
}

:deep(.el-button) {
    border-radius: 4px;
}
</style>
