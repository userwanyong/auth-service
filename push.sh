#!/bin/bash
# bash push.sh

# Ensure the script exits if any command fails
set -e

# Define variables for the registry and image
ALIYUN_REGISTRY="registry.cn-wulanchabu.aliyuncs.com"
NAMESPACE="wanyj"
IMAGE_NAME="auth-service"
IMAGE_TAG="1.2"

# 读取本地配置文件
if [ -f ".local-config" ]; then
  source .local-config
else
  echo ".local-config 文件不存在，请创建并填写 ALIYUN_USERNAME 和 ALIYUN_PASSWORD"
  exit 1
fi

# Login to Aliyun Docker Registry
echo "Logging into Aliyun Docker Registry..."
docker login --username="${ALIYUN_USERNAME}" --password="${ALIYUN_PASSWORD}" $ALIYUN_REGISTRY

# Tag the Docker image
echo "Tagging the Docker image..."
docker tag ${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG} ${ALIYUN_REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}

# Push the Docker image to Aliyun
echo "Pushing the Docker image to Aliyun..."
docker push ${ALIYUN_REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}

echo "Docker image pushed successfully! "

# Delete the tag Docker image
echo "Delete the tag Docker image..."
docker rmi ${ALIYUN_REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}

echo "拉取命令：docker pull ${ALIYUN_REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

# Logout from Aliyun Docker Registry
echo "Logging out from Aliyun Docker Registry..."
docker logout $ALIYUN_REGISTRY

