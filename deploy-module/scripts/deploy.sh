
set -Eeu pipefail

# 部署目录
APP_DIR="/home/ubuntu/services/note-app"

cd "$APP_DIR"

echo "========== 开始部署 =========="
# 0. 检查环境变量
if [[ -z "$TCR_USERNAME" || -z "$TCR_PASSWORD" ]]; then
    echo "错误：TCR账号或密码未配置"
    exit 1
fi
# 1. 登录腾讯云 TCR
echo "[1/4] Logging in to TCR..."

printf '%s' "$TCR_PASSWORD" | docker login "$TCR_REGISTRY" \
    -u "$TCR_USERNAME" \
    --password-stdin

echo "TCR 登录成功"
# 2. 拉取最新镜像
echo "[1/4] Pulling images..."
docker compose pull

# 3. 重建并启动容器
echo "[2/4] Starting services..."
docker compose up -d --remove-orphans

# 4. 检查容器状态
echo "[3/4] Checking containers..."
docker compose ps

# 5. 清理无用镜像
echo "[4/4] Cleaning dangling images..."
docker image prune -f

echo "========== 部署完成 =========="
