<template>
    <div class="register-container">
        <div class="register-box">
            <h1>注册账户</h1>
            <el-form 
                ref="formRef"
                :model="form" 
                :rules="rules"
                label-width="auto" 
                style="max-width: 500px"
            >
                <el-form-item label="用户名" prop="username">
                    <el-input 
                        v-model="form.username" 
                        placeholder="请输入用户名（6-20个字符）"
                        clearable
                    />
                </el-form-item>
                
                <el-form-item label="邮箱" prop="email">
                    <el-input 
                        v-model="form.email" 
                        placeholder="请输入邮箱地址"
                        clearable
                    />
                </el-form-item>
                
                <el-form-item label="手机号" prop="phone">
                    <el-input 
                        v-model="form.phone" 
                        placeholder="请输入手机号（11位）"
                        clearable
                    />
                </el-form-item>
                
                <el-form-item label="密码" prop="password">
                    <el-input 
                        v-model="form.password" 
                        type="password" 
                        placeholder="请输入密码（至少8位）"
                        show-password
                    />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="handleSubmit" style="width: 100%">注册</el-button>
                </el-form-item>
                
                <el-form-item>
                    <span>已有账户？<router-link to="/login">立即登录</router-link></span>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logger } from '../utils/logger'
import { Register } from '../api/user'

const router = useRouter()
const formRef = ref(null)

const form = reactive({
    username: '',
    password: '',
    email: '',
    phone: ''
})

// 验证规则
const rules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 6, max: 20, message: '用户名长度应为 6 到 20 个字符', trigger: 'blur' }
    ],
    email: [
        { required: true, message: '请输入邮箱地址', trigger: 'blur' },
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ],
    phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 8, message: '密码长度至少为 8 个字符', trigger: 'blur' }
    ]
}

const handleSubmit = async () => {
    if (!formRef.value) return
    
    try {
        await formRef.value.validate()
        // 提交表单数据
        console.log('表单数据：', form)
        
        // 调用注册 API
        const response = await Register(form)
        if (response.data.code !== 200) {
            return logger.error(response.data.msg, response.data)
        }
        
        logger.success('注册成功，跳转到登录页面')
        setTimeout(() => {
            router.push('/login')
        }, 1500)
    } catch (error) {
        logger.error('注册失败，请检查表单信息', error)
    }
}
</script>

<style scoped>
.register-container {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-box {
    background: white;
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
    width: 100%;
    max-width: 500px;
}

.register-box h1 {
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