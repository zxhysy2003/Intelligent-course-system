import { ElMessage } from 'element-plus';

const formatMessage = (message) => {
  if (message instanceof Error) {
    return message.message || '未知错误';
  }
  if (typeof message === 'string') {
    return message;
  }
  try {
    return JSON.stringify(message);
  } catch {
    return String(message);
  }
};

export const logger = {
  success(message) {
    ElMessage.success(formatMessage(message));
  },
  warn(message) {
    ElMessage.warning(formatMessage(message));
  },
  error(message, detail) {
    const text = formatMessage(message);
    ElMessage.error(text);
    if (detail) {
      // 保留开发调试信息
      console.error(text, detail);
    }
  },
  info(message) {
    ElMessage.info(formatMessage(message));
  },
  debug(message, detail) {
    console.log(formatMessage(message), detail ?? '');
  }
};
