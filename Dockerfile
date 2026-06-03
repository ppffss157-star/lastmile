# ===== 运行阶段：只复制本地打好包的 jar =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 把本地 mvnw 打好的 jar 拷进来
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
