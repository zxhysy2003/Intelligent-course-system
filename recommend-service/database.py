import os
from dataclasses import dataclass
from urllib.parse import quote_plus

from sqlalchemy import create_engine


@dataclass(frozen=True)
class DatabaseSettings:
    """推荐服务复用后端的数据库环境变量，只读取评分快照表。"""

    host: str
    port: int
    database: str
    username: str
    password: str

    @property
    def sqlalchemy_url(self) -> str:
        # 用户名和密码可能包含特殊字符，拼接 SQLAlchemy URL 前需要转义。
        user = quote_plus(self.username)
        password = quote_plus(self.password)
        host = self.host
        return f"mysql+pymysql://{user}:{password}@{host}:{self.port}/{self.database}?charset=utf8mb4"


def load_database_settings() -> DatabaseSettings:
    # 默认值与 backend/application.yaml 保持一致，便于本地直接联调。
    return DatabaseSettings(
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", "3306")),
        database=os.getenv("DB_NAME", "course_db"),
        username=os.getenv("DB_USERNAME", "dev"),
        password=os.getenv("DB_PASSWORD", "dev123"),
    )


def create_mysql_engine():
    settings = load_database_settings()
    # pool_pre_ping 可以在 MySQL 空闲连接断开后自动探活，降低 reload 时的偶发连接错误。
    return create_engine(settings.sqlalchemy_url, pool_pre_ping=True, future=True)
